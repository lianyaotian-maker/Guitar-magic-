package com.guitarmagic.tuner.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 调音页（有状态）：连接 ViewModel、调弦选择面板与振动反馈。 */
@Composable
fun TunerTab(viewModel: TunerViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    // 某根弦刚调准时轻振一下
    val view = LocalView.current
    val hapticsEnabled by rememberUpdatedState(state.hapticsEnabled)
    LaunchedEffect(viewModel) {
        viewModel.stringTuned.collect {
            if (hapticsEnabled) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                view.performHapticFeedback(type)
            }
        }
    }

    TunerContent(
        state = state,
        onOpenTunings = { sheetOpen = true },
        onSetA4 = viewModel::setA4,
        onToggleString = viewModel::toggleLock,
        onPlayString = { index -> viewModel.playTone(state.tuning.strings[index]) },
        onRetry = viewModel::retry,
        onResetProgress = viewModel::resetProgress,
        modifier = modifier,
    )

    if (sheetOpen) {
        TuningSheet(
            state = state,
            onDismiss = { sheetOpen = false },
            onSelect = {
                viewModel.setTuning(it)
                sheetOpen = false
            },
            onSave = { id, name, strings ->
                val saved = viewModel.saveCustomTuning(id, name, strings)
                if (saved != null) sheetOpen = false
                saved != null
            },
            onDelete = viewModel::deleteCustomTuning,
            onPreview = viewModel::playTone,
            defaultName = viewModel::defaultCustomName,
        )
    }
}

private enum class StatusKind { IDLE, IN_TUNE, HIGH, LOW }

/** 调音页（无状态）：只根据 [state] 绘制，方便测试。 */
@Composable
fun TunerContent(
    state: TunerUiState,
    onOpenTunings: () -> Unit,
    onSetA4: (Double) -> Unit,
    onToggleString: (Int) -> Unit,
    onPlayString: (Int) -> Unit,
    onRetry: () -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = state.toDisplay()
    // 根据可用高度自适应：矮屏手机上缩小表盘和音名，保证所有内容都能完整显示
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val compact = maxHeight < 600.dp
    val reserved = if (compact) 400.dp else 480.dp
    val gaugeMax = maxOf(80.dp, (maxWidth - 40.dp) / 2)
    val gaugeHeight = (maxHeight - reserved).coerceIn(80.dp, gaugeMax)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state, onOpenTunings, onSetA4)
        Spacer(Modifier.weight(1f))

        CentsGauge(
            cents = display.cents,
            inTune = display.inTune,
            dimmed = state.holding,
            modifier = Modifier
                .fillMaxWidth()
                .height(gaugeHeight),
        )
        Spacer(Modifier.height(4.dp))
        NoteReadout(display, state.useFlats, dimmed = state.holding, compact = compact)
        Spacer(Modifier.height(14.dp))
        StatusLine(state, display)

        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = state.allTuned,
            enter = fadeIn(Motion.standard()) + expandVertically(Motion.standard()),
            exit = fadeOut(Motion.standard()) + shrinkVertically(Motion.standard()),
        ) {
            AllTunedBanner(onResetProgress)
        }
        StringRow(state, display, onToggleString, onPlayString)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = state.lockedString,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
            label = "hint",
        ) { locked ->
            Text(
                text = locked?.let { "已锁定 ${Tuning.STRING_COUNT - it} 弦 · 再次点击恢复自动" }
                    ?: "点击琴弦锁定 · 长按试听参考音",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = state.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
    }
}

@Composable
private fun Header(
    state: TunerUiState,
    onOpenTunings: () -> Unit,
    onSetA4: (Double) -> Unit,
) {
    var a4DialogOpen by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onOpenTunings,
            modifier = Modifier
                .weight(1f)
                .testTag("tuningButton"),
        ) {
            AnimatedContent(
                targetState = state.tuning.displayName,
                transitionSpec = {
                    (fadeIn(Motion.standard()) + slideInVertically(Motion.standard()) { it / 2 }) togetherWith
                        (fadeOut(Motion.standard()) + slideOutVertically(Motion.standard()) { -it / 2 })
                },
                label = "tuningName",
            ) { name ->
                Text("$name ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun A4Dialog(initial: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("基准音 A4") },
        text = { A4Editor(value = value, onChange = { value = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toDouble()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** A4 数值 + 滑块 + 微调按钮，在对话框和设置页中共用。 */
@Composable
fun A4Editor(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val min = MusicTheory.MIN_A4.toFloat()
    val max = MusicTheory.MAX_A4.toFloat()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        AnimatedNumber(
            value = value.roundToInt(),
            suffix = " Hz",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−", fontSize = 22.sp) }
            Slider(
                value = value,
                onValueChange = { onChange(it.roundToInt().toFloat().coerceIn(min, max)) },
                valueRange = min..max,
                steps = (max - min).toInt() - 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+", fontSize = 22.sp) }
        }
        Text(
            "范围 430–450 Hz，大多数情况使用 440 Hz",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onChange(MusicTheory.DEFAULT_A4.toFloat()) }) {
            Text("恢复默认 440 Hz")
        }
    }
}

@Composable
private fun NoteReadout(display: TunerDisplay, useFlats: Boolean, dimmed: Boolean, compact: Boolean) {
    val colors = LocalTunerColors.current
    val contentAlpha by animateFloatAsState(if (dimmed) 0.5f else 1f, Motion.standard(), label = "readoutAlpha")
    val glow by animateFloatAsState(if (display.inTune && !dimmed) 1f else 0f, Motion.standard(), label = "glow")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseValue",
    )
    val glowColor = colors.inTune

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(contentAlpha),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 96.dp else 124.dp)
                .drawBehind {
                    // 调准时音名背后浮现柔和的绿色光晕（仅在绘制阶段读取动画值，不触发重组）
                    if (glow > 0.01f) {
                        val r = size.height * 0.62f * pulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowColor.copy(alpha = 0.32f * glow), Color.Transparent),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = r,
                            ),
                            radius = r,
                        )
                    }
                },
        ) {
            AnimatedContent(
                targetState = display.note?.midi,
                transitionSpec = {
                    val from = initialState
                    val to = targetState
                    val up = from != null && to != null && to > from
                    val down = from != null && to != null && to < from
                    val enter = fadeIn(Motion.standard(Motion.SHORT + 60)) + when {
                        up -> slideInVertically(Motion.standard()) { it / 3 }
                        down -> slideInVertically(Motion.standard()) { -it / 3 }
                        else -> scaleIn(Motion.standard(), initialScale = 0.85f)
                    }
                    val exit = fadeOut(Motion.standard(Motion.SHORT)) + when {
                        up -> slideOutVertically(Motion.standard()) { -it / 3 }
                        down -> slideOutVertically(Motion.standard()) { it / 3 }
                        else -> scaleOut(Motion.standard(), targetScale = 0.85f)
                    }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "note",
            ) { midi ->
                Text(
                    text = if (midi == null) {
                        buildAnnotatedString { append("—") }
                    } else {
                        buildAnnotatedString {
                            append(MusicTheory.noteName(midi, useFlats))
                            withStyle(SpanStyle(fontSize = if (compact) 34.sp else 44.sp, fontWeight = FontWeight.Normal)) {
                                append(MusicTheory.octave(midi).toString())
                            }
                        }
                    },
                    fontSize = if (compact) 80.sp else 104.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = if (compact) 88.sp else 112.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("noteLabel"),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCell(
                label = "实际频率",
                value = display.frequency?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            InfoCell(
                label = "偏差",
                value = display.cents?.let { formatCents(it) } ?: "--",
                modifier = Modifier.weight(1f),
            )
            InfoCell(
                label = display.referenceLabel?.let { "标准音 $it" } ?: "标准音",
                value = display.referenceFrequency?.let { String.format(Locale.US, "%.2f Hz", it) } ?: "--",
                modifier = Modifier.weight(1f),
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
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

private fun formatCents(cents: Double): String {
    val rounded = cents.roundToInt()
    return if (rounded > 0) "+$rounded 音分" else "$rounded 音分"
}

@Composable
private fun StatusLine(state: TunerUiState, display: TunerDisplay) {
    val colors = LocalTunerColors.current
    val cents = display.cents
    val kind = when {
        cents == null -> StatusKind.IDLE
        display.inTune -> StatusKind.IN_TUNE
        statusDirectionHigh(state, display) -> StatusKind.HIGH
        else -> StatusKind.LOW
    }
    val targetColor = when (kind) {
        StatusKind.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusKind.IN_TUNE -> colors.inTune
        else -> colors.offTune
    }
    val color by animateColorAsState(targetColor, Motion.standard(), label = "statusColor")
    val text = when (kind) {
        StatusKind.IDLE -> if (state.listening) "请弹奏一根琴弦" else "麦克风未开启"
        StatusKind.IN_TUNE -> inTuneText(state, display)
        else -> statusText(state, display)
    }
    // 只在状态类别变化时做过渡，数值变化时直接更新文字，避免反复闪动
    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (fadeIn(Motion.standard()) + scaleIn(Motion.bouncy(), initialScale = 0.9f)) togetherWith
                fadeOut(Motion.standard(Motion.SHORT))
        },
        label = "status",
    ) { _ ->
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("status"),
        )
    }
}

/** 是否应提示“偏高”。自动模式下离最近的弦超过半音时，按目标弦判断方向。 */
private fun statusDirectionHigh(state: TunerUiState, display: TunerDisplay): Boolean {
    val stringCents = display.stringCents
    if (state.lockedString == null && stringCents != null && abs(stringCents) > 50) return stringCents > 0
    return (display.cents ?: 0.0) > 0
}

/** 音准了；但自动模式下如果这个音不是任何一根弦的目标音，再提示离最近的弦还差多少。 */
private fun inTuneText(state: TunerUiState, display: TunerDisplay): String {
    val stringCents = display.stringCents
    if (state.lockedString == null && stringCents != null && abs(stringCents) > 50 && display.note != null) {
        val direction = if (stringCents > 0) "偏高，请调松" else "偏低，请调紧"
        return "✓ ${display.note.label} 已调准\n${display.stringLabel} $direction（相差 ${abs(stringCents).roundToInt()} 音分）"
    }
    return "✓ 已调准"
}

private fun statusText(state: TunerUiState, display: TunerDisplay): String {
    val cents = display.cents ?: return ""
    val stringCents = display.stringCents
    if (state.lockedString == null && stringCents != null && abs(stringCents) > 50) {
        val direction = if (stringCents > 0) "偏高，请调松" else "偏低，请调紧"
        return "${display.stringLabel} $direction\n（相差 ${abs(stringCents).roundToInt()} 音分）"
    }
    val direction = if (cents > 0) "偏高 · 请调松" else "偏低 · 请调紧"
    return if (abs(cents) > 50) "$direction\n（相差 ${abs(cents).roundToInt()} 音分）" else direction
}

@Composable
private fun AllTunedBanner(onReset: () -> Unit) {
    val colors = LocalTunerColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.inTune.copy(alpha = 0.15f))
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text("🎉 6 根弦全部调准", color = colors.inTune, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onReset) { Text("重新开始") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StringRow(
    state: TunerUiState,
    display: TunerDisplay,
    onToggle: (Int) -> Unit,
    onPlay: (Int) -> Unit,
) {
    val colors = LocalTunerColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tuning.strings.forEachIndexed { index, midi ->
            val locked = state.lockedString == index
            val active = display.activeString == index && display.frequency != null
            val activeInTune = active && display.stringCents?.let { abs(it) <= TunerDisplay.IN_TUNE_CENTS } == true
            val tuned = index in state.tunedStrings

            val background by animateColorAsState(
                when {
                    locked -> MaterialTheme.colorScheme.primary
                    active -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                Motion.standard(),
                label = "stringBg",
            )
            val content by animateColorAsState(
                when {
                    locked -> MaterialTheme.colorScheme.onPrimary
                    active -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                Motion.standard(),
                label = "stringContent",
            )
            val borderColor by animateColorAsState(
                if (activeInTune) colors.inTune else Color.Transparent,
                Motion.standard(),
                label = "stringBorder",
            )
            val scale by animateFloatAsState(if (active || locked) 1.07f else 1f, Motion.bouncy(), label = "stringScale")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(background)
                        .border(BorderStroke(2.5.dp, borderColor), RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { onToggle(index) },
                            onLongClick = { onPlay(index) },
                            onLongClickLabel = "试听参考音",
                        )
                        .padding(vertical = 10.dp)
                        .testTag("string$index"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        MusicTheory.label(midi, state.useFlats),
                        color = content,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        if (locked) "🔒${Tuning.STRING_COUNT - index}弦" else "${Tuning.STRING_COUNT - index}弦",
                        color = content.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = tuned,
                    enter = scaleIn(Motion.bouncy()) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.inTune),
                    ) {
                        Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** 数字变化时上下滚动切换的文本。 */
@Composable
fun AnimatedNumber(
    value: Int,
    modifier: Modifier = Modifier,
    suffix: String = "",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (fadeIn(Motion.standard(Motion.SHORT)) + slideInVertically(Motion.standard(Motion.SHORT + 60)) { if (up) it / 2 else -it / 2 }) togetherWith
                (fadeOut(Motion.standard(Motion.SHORT)) + slideOutVertically(Motion.standard(Motion.SHORT + 60)) { if (up) -it / 2 else it / 2 })
        },
        label = "number",
        modifier = modifier,
    ) { v ->
        Text("$v$suffix", style = style, fontWeight = fontWeight)
    }
}
