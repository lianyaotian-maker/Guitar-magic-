package com.guitarmagic.tuner.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
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
import com.guitarmagic.tuner.ui.ios.IosAlert
import com.guitarmagic.tuner.ui.ios.IosAlertAction
import com.guitarmagic.tuner.ui.ios.IosLargeTitle
import com.guitarmagic.tuner.ui.ios.IosSlider
import com.guitarmagic.tuner.ui.ios.IosStepper
import com.guitarmagic.tuner.ui.ios.IosTextButton
import com.guitarmagic.tuner.ui.ios.IosTintedButton
import com.guitarmagic.tuner.ui.ios.IosType
import com.guitarmagic.tuner.ui.ios.IosType.tabular
import com.guitarmagic.tuner.ui.ios.LocalIosColors
import com.guitarmagic.tuner.ui.ios.iosClickable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 调音页（有状态）：连接 ViewModel、调弦选择面板与振动反馈。 */
@Composable
fun TunerTab(viewModel: TunerViewModel, onStopMetronome: () -> Unit, modifier: Modifier = Modifier) {
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
        onStopTone = viewModel::stopTone,
        onStopMetronome = onStopMetronome,
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

/** 页面顶部的提示条。 */
private sealed interface Banner {
    data class Tone(val label: String) : Banner
    data object Metronome : Banner
    data object AllTuned : Banner
    data class Error(val message: String) : Banner
}

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
    onStopTone: () -> Unit = {},
    onStopMetronome: () -> Unit = {},
) {
    val display = state.toDisplay()
    var a4DialogOpen by rememberSaveable { mutableStateOf(false) }
    val banner: Banner? = when {
        state.playingToneMidi != null -> Banner.Tone(MusicTheory.label(state.playingToneMidi, state.useFlats))
        state.metronomeRunning -> Banner.Metronome
        state.error != null -> Banner.Error(state.error)
        state.allTuned -> Banner.AllTuned
        else -> null
    }

    // 根据可用高度自适应：矮屏手机上缩小表盘和音名；特别矮时整页改为可滚动，保证内容不被截断
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val scrollable = maxHeight < 600.dp
        val reserved = if (compact) 510.dp else 570.dp
        val gaugeMax = maxOf(80.dp, (maxWidth - 64.dp) / 2)
        val gaugeHeight = if (scrollable) gaugeMax.coerceAtMost(140.dp) else (maxHeight - reserved).coerceIn(80.dp, gaugeMax)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IosLargeTitle(
                title = "调音",
                trailing = {
                    IosTintedButton(
                        text = "A4 · ${state.a4.roundToInt()} Hz",
                        onClick = { a4DialogOpen = true },
                        modifier = Modifier.testTag("a4Button"),
                    )
                },
            )
            TuningSelector(state, onOpenTunings)
            BannerArea(banner, onStopTone, onStopMetronome, onResetProgress, onRetry)

            if (scrollable) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
            CentsGauge(
                cents = display.cents,
                inTune = display.inTune,
                dimmed = state.holding,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(gaugeHeight),
            )
            NoteReadout(display, state.useFlats, dimmed = state.holding, compact = compact)
            Spacer(Modifier.height(6.dp))
            StatusLine(state, display)
            if (scrollable) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(1f))

            InfoCard(display)
            Spacer(Modifier.height(18.dp))
            StringRow(state, display, onToggleString, onPlayString)
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = state.lockedString,
                transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
                label = "hint",
            ) { locked ->
                Text(
                    text = locked?.let { "已锁定 ${Tuning.STRING_COUNT - it} 弦 · 再次点击恢复自动" }
                        ?: "轻点琴弦锁定 · 长按试听参考音",
                    style = IosType.footnote,
                    color = LocalIosColors.current.secondaryLabel,
                )
            }
            Spacer(Modifier.height(12.dp))
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

/** 当前调弦（点击打开调弦面板）。 */
@Composable
private fun TuningSelector(state: TunerUiState, onOpenTunings: () -> Unit) {
    val colors = LocalIosColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .iosClickable(onClick = onOpenTunings)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("tuningButton"),
    ) {
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.tuning.displayName,
                transitionSpec = {
                    (fadeIn(Motion.standard()) + slideInVertically(Motion.standard()) { it / 2 }) togetherWith
                        (fadeOut(Motion.standard()) + slideOutVertically(Motion.standard()) { -it / 2 })
                },
                label = "tuningName",
            ) { name ->
                Text(name, style = IosType.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(state.tuning.notesSummary(state.useFlats), style = IosType.footnote, color = colors.secondaryLabel)
        }
        Text("更改", style = IosType.subheadline, color = colors.blue)
        Text(" ›", fontSize = 22.sp, color = colors.tertiaryLabel)
    }
}

@Composable
private fun BannerArea(
    banner: Banner?,
    onStopTone: () -> Unit,
    onStopMetronome: () -> Unit,
    onResetProgress: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalIosColors.current
    AnimatedContent(
        targetState = banner,
        transitionSpec = {
            (fadeIn(Motion.standard()) + expandVertically(Motion.standard())) togetherWith
                (fadeOut(Motion.standard(Motion.SHORT)) + shrinkVertically(Motion.standard()))
        },
        contentKey = { it?.javaClass },
        label = "banner",
    ) { b ->
        when (b) {
            null -> Spacer(Modifier.fillMaxWidth().height(0.dp))
            is Banner.Tone -> BannerRow("♪  正在播放参考音 ${b.label}，暂停识别", colors.blue, "停止", onStopTone)
            Banner.Metronome -> BannerRow("节拍器运行中，调音已暂停", colors.orange, "停止节拍器", onStopMetronome)
            Banner.AllTuned -> BannerRow("🎉 6 根弦全部调准", colors.green, "重新开始", onResetProgress)
            is Banner.Error -> BannerRow(b.message, colors.red, "重试", onRetry)
        }
    }
}

@Composable
private fun BannerRow(text: String, color: Color, action: String, onAction: () -> Unit) {
    val colors = LocalIosColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (colors.isDark) 0.22f else 0.12f))
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .testTag("banner"),
    ) {
        Text(
            text,
            style = IosType.subheadline.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.weight(1f),
        )
        IosTextButton(action, onAction, color = color, bold = true, style = IosType.subheadline)
    }
}

@Composable
fun A4Dialog(initial: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    IosAlert(
        onDismiss = onDismiss,
        title = "基准音 A4",
        message = "范围 430–450 Hz，大多数情况使用 440 Hz",
        actions = listOf(
            IosAlertAction("取消", IosAlertAction.Style.Cancel, onClick = onDismiss),
            IosAlertAction("确定") { onConfirm(value.toDouble()) },
        ),
    ) {
        Spacer(Modifier.height(10.dp))
        A4Editor(value = value, onChange = { value = it })
    }
}

/** A4 数值 + 步进器 + 滑块，在对话框和设置页中共用。 */
@Composable
fun A4Editor(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, showReset: Boolean = true) {
    val min = MusicTheory.MIN_A4.toFloat()
    val max = MusicTheory.MAX_A4.toFloat()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedNumber(
                value = value.roundToInt(),
                suffix = " Hz",
                style = IosType.title1.tabular(),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(16.dp))
            IosStepper(
                onDecrement = { onChange((value - 1).coerceAtLeast(min)) },
                onIncrement = { onChange((value + 1).coerceAtMost(max)) },
                decrementEnabled = value > min,
                incrementEnabled = value < max,
                decrementTag = "a4Minus",
                incrementTag = "a4Plus",
            )
        }
        IosSlider(
            value = value,
            onValueChange = { onChange(it.roundToInt().toFloat().coerceIn(min, max)) },
            valueRange = min..max,
            steps = (max - min).toInt() - 1,
        )
        if (showReset) {
            IosTextButton("恢复默认 440 Hz", { onChange(MusicTheory.DEFAULT_A4.toFloat()) }, style = IosType.subheadline)
        }
    }
}

@Composable
private fun NoteReadout(display: TunerDisplay, useFlats: Boolean, dimmed: Boolean, compact: Boolean) {
    val colors = LocalIosColors.current
    val contentAlpha by animateFloatAsState(if (dimmed) 0.45f else 1f, Motion.standard(), label = "readoutAlpha")
    val noteColor by animateColorAsState(
        if (display.inTune && !dimmed) colors.green else colors.label,
        Motion.standard(),
        label = "noteColor",
    )
    val glow by animateFloatAsState(if (display.inTune && !dimmed) 1f else 0f, Motion.standard(), label = "glow")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseValue",
    )
    val glowColor = colors.green

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 92.dp else 116.dp)
            .alpha(contentAlpha)
            .drawBehind {
                // 调准时音名背后浮现柔和的绿色光晕（只在绘制阶段读取动画值，不触发重组）
                if (glow > 0.01f) {
                    val r = size.height * 0.7f * pulse
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.26f * glow), Color.Transparent),
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
                    else -> scaleIn(Motion.standard(), initialScale = 0.88f)
                }
                val exit = fadeOut(Motion.standard(Motion.SHORT)) + when {
                    up -> slideOutVertically(Motion.standard()) { -it / 3 }
                    down -> slideOutVertically(Motion.standard()) { it / 3 }
                    else -> scaleOut(Motion.standard(), targetScale = 0.88f)
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
                        withStyle(SpanStyle(fontSize = if (compact) 32.sp else 40.sp, fontWeight = FontWeight.Medium)) {
                            append(MusicTheory.octave(midi).toString())
                        }
                    }
                },
                fontSize = if (compact) 76.sp else 96.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (compact) 84.sp else 104.sp,
                color = if (midi == null) colors.tertiaryLabel else noteColor,
                modifier = Modifier.testTag("noteLabel"),
            )
        }
    }
}

/** 三栏信息卡：实际频率 / 偏差 / 标准音。 */
@Composable
private fun InfoCard(display: TunerDisplay) {
    val colors = LocalIosColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(vertical = 10.dp),
    ) {
        InfoCell(
            label = "实际频率",
            value = display.frequency?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "--",
            modifier = Modifier.weight(1f),
        )
        VerticalHairline()
        InfoCell(
            label = "偏差",
            value = display.cents?.let { formatCents(it) } ?: "--",
            valueColor = when {
                display.cents == null -> null
                display.inTune -> colors.green
                else -> colors.orange
            },
            modifier = Modifier.weight(1f),
        )
        VerticalHairline()
        InfoCell(
            label = display.referenceLabel?.let { "标准音 $it" } ?: "标准音",
            value = display.referenceFrequency?.let { String.format(Locale.US, "%.2f Hz", it) } ?: "--",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .width(0.5.dp)
            .fillMaxHeight()
            .background(LocalIosColors.current.separator),
    )
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val colors = LocalIosColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, style = IosType.caption1, color = colors.secondaryLabel, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = IosType.headline.tabular(),
            color = valueColor ?: colors.label,
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
    val colors = LocalIosColors.current
    val cents = display.cents
    val kind = when {
        cents == null -> StatusKind.IDLE
        display.inTune -> StatusKind.IN_TUNE
        statusDirectionHigh(state, display) -> StatusKind.HIGH
        else -> StatusKind.LOW
    }
    val targetColor = when (kind) {
        StatusKind.IDLE -> colors.secondaryLabel
        StatusKind.IN_TUNE -> colors.green
        else -> colors.orange
    }
    val color by animateColorAsState(targetColor, Motion.standard(), label = "statusColor")
    val text = when (kind) {
        StatusKind.IDLE -> when {
            state.paused -> "识别已暂停"
            state.listening -> "请弹奏一根琴弦"
            else -> "麦克风未开启"
        }
        StatusKind.IN_TUNE -> inTuneText(state, display)
        else -> statusText(state, display)
    }
    // 只在状态类别变化时做过渡，数值变化时直接更新文字，避免反复闪动
    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (fadeIn(Motion.standard()) + scaleIn(Motion.bouncy(), initialScale = 0.92f)) togetherWith
                fadeOut(Motion.standard(Motion.SHORT))
        },
        label = "status",
    ) { _ ->
        Text(
            text = text,
            color = color,
            style = IosType.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("status"),
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
private fun StringRow(
    state: TunerUiState,
    display: TunerDisplay,
    onToggle: (Int) -> Unit,
    onPlay: (Int) -> Unit,
) {
    val colors = LocalIosColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.tuning.strings.forEachIndexed { index, midi ->
            val locked = state.lockedString == index
            val active = display.activeString == index && display.frequency != null
            val activeInTune = active && display.stringCents?.let { abs(it) <= TunerDisplay.IN_TUNE_CENTS } == true
            val tuned = index in state.tunedStrings
            val playing = state.playingToneMidi == midi

            val background by animateColorAsState(
                when {
                    locked -> colors.blue
                    active -> colors.blue.copy(alpha = if (colors.isDark) 0.28f else 0.14f)
                    else -> colors.surface
                },
                Motion.standard(),
                label = "stringBg",
            )
            val content by animateColorAsState(
                when {
                    locked -> Color.White
                    active -> colors.blue
                    else -> colors.label
                },
                Motion.standard(),
                label = "stringContent",
            )
            val ring by animateColorAsState(
                when {
                    activeInTune -> colors.green
                    playing -> colors.blue
                    else -> Color.Transparent
                },
                Motion.standard(),
                label = "stringRing",
            )
            val scale by animateFloatAsState(if (active || locked || playing) 1.08f else 1f, Motion.bouncy(), label = "stringScale")

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(background)
                            .border(2.5.dp, ring, CircleShape)
                            .iosClickable(
                                onLongClick = { onPlay(index) },
                                onClick = { onToggle(index) },
                            )
                            .testTag("string$index"),
                    ) {
                        Text(
                            MusicTheory.label(midi, state.useFlats),
                            color = content,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = tuned,
                        enter = scaleIn(Motion.bouncy()) + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(colors.green)
                                .border(1.5.dp, colors.background, CircleShape),
                        ) {
                            Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (locked) "🔒${Tuning.STRING_COUNT - index}弦" else "${Tuning.STRING_COUNT - index}弦",
                    style = IosType.caption2,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                )
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
    style: TextStyle = IosType.title1.tabular(),
    color: Color? = null,
) {
    val c = color ?: LocalIosColors.current.label
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
        Text("$v$suffix", style = style, color = c)
    }
}
