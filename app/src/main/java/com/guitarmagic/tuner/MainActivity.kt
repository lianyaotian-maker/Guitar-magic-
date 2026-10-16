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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guitarmagic.tuner.ui.PermissionScreen
import com.guitarmagic.tuner.ui.TunerScreen
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

@Composable
private fun TunerApp(viewModel: TunerViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var granted by remember { mutableStateOf(hasMicPermission(context)) }
    var permanentlyDenied by remember {
        mutableStateOf(
            !granted && viewModel.permissionRequested && activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        viewModel.permissionRequested = true
        granted = ok
        permanentlyDenied = !ok && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    // 从系统设置返回时重新检查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasMicPermission(context)
                if (granted) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首次打开时直接弹出系统授权框
    LaunchedEffect(Unit) {
        if (!granted && !viewModel.permissionRequested) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (granted) {
        TunerScreen(viewModel)
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
}

private fun hasMicPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
