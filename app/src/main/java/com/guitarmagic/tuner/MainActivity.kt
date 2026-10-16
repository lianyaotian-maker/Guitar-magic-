package com.guitarmagic.tuner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guitarmagic.tuner.ui.MetronomeTab
import com.guitarmagic.tuner.ui.Motion
import com.guitarmagic.tuner.ui.PermissionScreen
import com.guitarmagic.tuner.ui.SettingsTab
import com.guitarmagic.tuner.ui.TunerTab
import com.guitarmagic.tuner.ui.TunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TunerTheme {
                TunerApp()
            }
        }
    }
}

enum class AppTab(val label: String, val icon: Int) {
    TUNER("调音", R.drawable.ic_tab_tuner),
    METRONOME("节拍器", R.drawable.ic_tab_metronome),
    SETTINGS("设置", R.drawable.ic_tab_settings),
}

@Composable
fun TunerApp(
    tunerViewModel: TunerViewModel = viewModel(),
    metronomeViewModel: MetronomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    var tabIndex by rememberSaveable { mutableIntStateOf(AppTab.TUNER.ordinal) }
    val tab = AppTab.entries[tabIndex.coerceIn(0, AppTab.entries.lastIndex)]

    // ---------- 麦克风权限 ----------
    var granted by remember { mutableStateOf(hasMicPermission(context)) }
    var permanentlyDenied by remember {
        mutableStateOf(
            !granted && tunerViewModel.permissionRequested && activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tunerViewModel.permissionRequested = true
        granted = ok
        permanentlyDenied = !ok && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从系统设置返回时重新检查权限
                granted = hasMicPermission(context)
                if (granted) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!granted && !tunerViewModel.permissionRequested) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---------- 音频开关：统一在这里根据“当前页面 + 是否在前台”决定，避免页面切换动画期间的竞态 ----------
    val shouldListen = granted && foreground && tab == AppTab.TUNER
    LaunchedEffect(shouldListen) {
        if (shouldListen) tunerViewModel.startListening() else tunerViewModel.stopListening()
    }
    LaunchedEffect(foreground) {
        if (!foreground) {
            metronomeViewModel.stop()
            tunerViewModel.stopTone()
        }
    }
    val metronomeState by metronomeViewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val keepScreenOn = tab == AppTab.TUNER || metronomeState.running
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // 在节拍器/设置页按返回键先回到调音页
    BackHandler(enabled = tab != AppTab.TUNER) { tabIndex = AppTab.TUNER.ordinal }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tabIndex = t.ordinal },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label) },
                        modifier = Modifier.testTag("tab_${t.name}"),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (fadeIn(Motion.standard()) + slideInHorizontally(Motion.standard()) { w -> if (forward) w / 8 else -w / 8 }) togetherWith
                    (fadeOut(Motion.standard(Motion.SHORT)) + slideOutHorizontally(Motion.standard()) { w -> if (forward) -w / 8 else w / 8 })
            },
            label = "tabs",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { current ->
            when (current) {
                AppTab.TUNER -> if (granted) {
                    TunerTab(tunerViewModel)
                } else {
                    PermissionScreen(
                        permanentlyDenied = permanentlyDenied,
                        onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    )
                }
                AppTab.METRONOME -> MetronomeTab(metronomeViewModel)
                AppTab.SETTINGS -> SettingsTab(tunerViewModel)
            }
        }
    }
}

private fun hasMicPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
