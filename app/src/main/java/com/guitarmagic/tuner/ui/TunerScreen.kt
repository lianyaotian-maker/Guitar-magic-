package com.guitarmagic.tuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarmagic.tuner.TunerDisplay
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.TunerViewModel
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import com.guitarmagic.tuner.toDisplay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TunerScreen(viewModel: TunerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 调音时保持屏幕常亮
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 前台时录音，切到后台立即停止
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startListening()
                Lifecycle.Event.ON_STOP -> viewModel.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.startListening()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopListening()
        }
    }

    TunerContent(
        state = state,
        onSelectTuning = viewModel::setTuning,
        onSetA4 = viewModel::setA4,
        onToggleString = viewModel::toggleLock,
        onRetry = viewModel::retry,
    )
}

@Composable
fun TunerContent(
    state: TunerUiState,
    onSelectTuning: (Tuning) -> Unit,
    onSetA4: (Double) -> Unit,
    onToggleString: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val display = state.toDisplay()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(state, onSelectTuning, onSetA4)
            Spacer(Modifier.weight(1f))

            CentsGauge(
                cents = display.cents,
                inTune = display.inTune,
                dimmed = state.holding,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
            )
            Spacer(Modifier.height(8.dp))
            NoteReadout(display, dimmed = state.holding)
            Spacer(Modifier.height(16.dp))
            StatusLine(state, display)

            Spacer(Modifier.weight(1f))
            StringRow(state, display, onToggleString)
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.lockedString?.let { "已锁定 ${6 - it} 弦 · 再次点击恢复自动识别" }
                    ?: "自动识别琴弦 · 点击某根弦可手动锁定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: TunerUiState,
    onSelectTuning: (Tuning) -> Unit,
    onSetA4: (Double) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var a4DialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box {
            OutlinedButton(onClick = { menuOpen = true }) {
                Text("${state.tuning.displayName} ▾")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Tuning.ALL.forEach { tuning ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    tuning.displayName,
                                    fontWeight = if (tuning == state.tuning) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    tuning.strings.joinToString(" ") { MusicTheory.label(it) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            menuOpen = false
                            onSelectTuning(tuning)
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = { a4DialogOpen = true }) {
            Text("A4 = ${state.a4.roundToInt()} Hz")
        }
    }

    if (a4DialogOpen) {
        A4Dialog(
            initial = state.a4,
            onConfirm = {
                onSetA4(it)
                a4DialogOpen = false
            },
            onDismiss = { a4DialogOpen = false },
        )
    }
}

@Composable
private fun A4Dialog(initial: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("基准音 A4") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${value.roundToInt()} Hz",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        value = (value - 1).coerceAtLeast(MusicTheory.MIN_A4.toFloat())
                    }) { Text("−", fontSize = 22.sp) }
                    Slider(
                        value = value,
                        onValueChange = { value = it.roundToInt().toFloat() },
                        valueRange = MusicTheory.MIN_A4.toFloat()..MusicTheory.MAX_A4.toFloat(),
                        steps = (MusicTheory.MAX_A4 - MusicTheory.MIN_A4).toInt() - 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        value = (value + 1).coerceAtMost(MusicTheory.MAX_A4.toFloat())
                    }) { Text("+", fontSize = 22.sp) }
                }
                Text(
                    "范围 430–450 Hz，大多数情况使用 440 Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { value = MusicTheory.DEFAULT_A4.toFloat() }) {
                    Text("恢复默认 440 Hz")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toDouble()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun NoteReadout(display: TunerDisplay, dimmed: Boolean) {
    val note = display.note
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (dimmed) 0.5f else 1f),
    ) {
        Text(
            text = if (note == null) {
                buildAnnotatedString { append("—") }
            } else {
                buildAnnotatedString {
                    append(note.name)
                    withStyle(SpanStyle(fontSize = 44.sp, fontWeight = FontWeight.Normal)) {
                        append(note.octave.toString())
                    }
                }
            },
            fontSize = 104.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 112.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            InfoCell(
                label = "实际频率",
                modifier = Modifier.weight(1f),
                value = display.frequency?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "--",
            )
            InfoCell(
                label = "偏差",
                modifier = Modifier.weight(1f),
                value = display.cents?.let { formatCents(it) } ?: "--",
            )
            InfoCell(
                label = display.referenceLabel?.let { "标准音 $it" } ?: "标准音",
                modifier = Modifier.weight(1f),
                value = display.referenceFrequency?.let { String.format(Locale.US, "%.2f Hz", it) } ?: "--",
            )
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun formatCents(cents: Double): String {
    val rounded = cents.roundToInt()
    return when {
        rounded > 0 -> "+$rounded 音分"
        else -> "$rounded 音分"
    }
}

@Composable
private fun StatusLine(state: TunerUiState, display: TunerDisplay) {
    val colors = LocalTunerColors.current
    val cents = display.cents
    val (text, color) = when {
        cents == null -> (if (state.listening) "请弹奏一根琴弦" else "麦克风未开启") to
            MaterialTheme.colorScheme.onSurfaceVariant
        display.inTune -> "✓ 已调准" to colors.inTune
        else -> statusText(state, display) to colors.offTune
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

private fun statusText(state: TunerUiState, display: TunerDisplay): String {
    val cents = display.cents ?: return ""
    val stringCents = display.stringCents
    val note = display.note
    // 自动模式下，若听到的音与最接近的弦不是同一个音，提示离目标弦还差多少
    if (state.lockedString == null && stringCents != null && note != null && abs(stringCents) > 50) {
        val direction = if (stringCents > 0) "偏高，请调松" else "偏低，请调紧"
        return "${display.stringLabel} $direction\n（相差 ${abs(stringCents).roundToInt()} 音分）"
    }
    val direction = if (cents > 0) "偏高 · 请调松" else "偏低 · 请调紧"
    return if (abs(cents) > 50) "$direction\n（相差 ${abs(cents).roundToInt()} 音分）" else direction
}

@Composable
private fun StringRow(state: TunerUiState, display: TunerDisplay, onToggle: (Int) -> Unit) {
    val colors = LocalTunerColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tuning.strings.forEachIndexed { index, midi ->
            val locked = state.lockedString == index
            val active = display.activeString == index && display.frequency != null
            val tuned = active && display.stringCents?.let { abs(it) <= TunerDisplay.IN_TUNE_CENTS } == true
            val background = when {
                locked -> MaterialTheme.colorScheme.primary
                active -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val content = when {
                locked -> MaterialTheme.colorScheme.onPrimary
                active -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val border = if (tuned) BorderStroke(3.dp, colors.inTune) else BorderStroke(0.dp, Color.Transparent)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(background)
                    .border(border, RoundedCornerShape(14.dp))
                    .clickable { onToggle(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    MusicTheory.label(midi),
                    color = content,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (locked) "🔒${6 - index}弦" else "${6 - index}弦",
                    color = content.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
