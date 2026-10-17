package com.guitarmagic.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarmagic.tuner.MetronomeUiState
import com.guitarmagic.tuner.MetronomeViewModel
import com.guitarmagic.tuner.metronome.MetronomeConfig
import com.guitarmagic.tuner.metronome.MetronomeEngine
import com.guitarmagic.tuner.metronome.Subdivision
import com.guitarmagic.tuner.metronome.TempoMarkings
import com.guitarmagic.tuner.ui.ios.IosCircleButton
import com.guitarmagic.tuner.ui.ios.IosDivider
import com.guitarmagic.tuner.ui.ios.IosLargeTitle
import com.guitarmagic.tuner.ui.ios.IosRow
import com.guitarmagic.tuner.ui.ios.IosSection
import com.guitarmagic.tuner.ui.ios.IosSegmentedControl
import com.guitarmagic.tuner.ui.ios.IosSlider
import com.guitarmagic.tuner.ui.ios.IosStepper
import com.guitarmagic.tuner.ui.ios.IosSwitch
import com.guitarmagic.tuner.ui.ios.IosType
import com.guitarmagic.tuner.ui.ios.IosType.tabular
import com.guitarmagic.tuner.ui.ios.LocalIosColors
import com.guitarmagic.tuner.ui.ios.iosClickable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

private const val MAX_SWING_DEG = 28f

@Composable
fun MetronomeTab(viewModel: MetronomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MetronomeContent(
        state = state,
        visualProvider = viewModel::visual,
        onToggle = viewModel::toggle,
        onChangeBpm = viewModel::changeBpm,
        onSetBpm = viewModel::setBpm,
        onSetBeats = viewModel::setBeatsPerBar,
        onSetSubdivision = viewModel::setSubdivision,
        onSetAccent = viewModel::setAccentFirst,
        onTap = viewModel::tap,
        modifier = modifier,
    )
}

@Composable
fun MetronomeContent(
    state: MetronomeUiState,
    visualProvider: () -> MetronomeEngine.Visual?,
    onToggle: () -> Unit,
    onChangeBpm: (Int) -> Unit,
    onSetBpm: (Int) -> Unit,
    onSetBeats: (Int) -> Unit,
    onSetSubdivision: (Subdivision) -> Unit,
    onSetAccent: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIosColors.current
    val config = state.config
    val provider by rememberUpdatedState(visualProvider)
    // 播放时用无限动画驱动每一帧重绘；绘制时直接读取音频播放位置，
    // 摆锤与拍点和声音精确同步，且只触发重绘、不触发重组
    val frameTick: State<Float>? = if (state.running) {
        rememberInfiniteTransition(label = "frames").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "frame",
        )
    } else {
        null
    }
    val lastAngle = remember { floatArrayOf(0f) }
    val settle = remember { Animatable(0f) }
    LaunchedEffect(state.running) {
        if (!state.running) {
            // 停止后摆锤从当前位置柔和地回到中间
            settle.snapTo(lastAngle[0])
            settle.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    val currentAngle: () -> Float = {
        val tick = frameTick?.value
        val v = if (tick != null) provider() else null
        val a = if (v != null) pendulumAngle(v) else if (tick != null) lastAngle[0] else settle.value
        lastAngle[0] = a
        a
    }
    val currentVisual: () -> MetronomeEngine.Visual? = {
        if (frameTick?.value != null) provider() else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IosLargeTitle("节拍器")

        // 摆锤 + 拍点卡片
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(top = 16.dp, bottom = 10.dp),
        ) {
            Pendulum(angle = currentAngle, bpm = config.bpm, modifier = Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(8.dp))
            BeatDots(config, currentVisual)
        }
        Spacer(Modifier.height(14.dp))

        // 速度
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("−", enabled = config.bpm > MetronomeConfig.MIN_BPM, tag = "bpmMinus") { onChangeBpm(-1) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(176.dp)
                    .padding(horizontal = 8.dp),
            ) {
                AnimatedNumber(
                    value = config.bpm,
                    style = IosType.largeTitle.copy(fontSize = 76.sp, lineHeight = 84.sp, fontWeight = FontWeight.Light).tabular(),
                    modifier = Modifier.testTag("bpm"),
                )
                Text("BPM", style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.secondaryLabel)
            }
            RoundIconButton("+", enabled = config.bpm < MetronomeConfig.MAX_BPM, tag = "bpmPlus") { onChangeBpm(1) }
        }
        AnimatedContent(
            targetState = TempoMarkings.of(config.bpm),
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard(Motion.SHORT)) },
            label = "marking",
        ) { marking ->
            Text(marking, style = IosType.subheadline, color = colors.blue)
        }
        IosSlider(
            value = config.bpm.toFloat(),
            onValueChange = { onSetBpm(Math.round(it)) },
            valueRange = MetronomeConfig.MIN_BPM.toFloat()..MetronomeConfig.MAX_BPM.toFloat(),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(10.dp))

        // 时钟 App 秒表风格的两个圆形按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            IosCircleButton(
                text = "敲击",
                onClick = onTap,
                color = colors.gray,
                modifier = Modifier.testTag("tap"),
            )
            Text(
                "连续轻点“敲击”\n可测出速度",
                style = IosType.caption1,
                color = colors.tertiaryLabel,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IosCircleButton(
                text = if (state.running) "停止" else "开始",
                onClick = onToggle,
                color = if (state.running) colors.red else colors.green,
                modifier = Modifier.testTag("startStop"),
            )
        }
        Spacer(Modifier.height(22.dp))

        IosSection(
            header = "节拍",
            footer = "节拍按音频采样精确计时，长时间运行也不会忽快忽慢。",
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IosRow(
                title = "每小节拍数",
                trailing = {
                    AnimatedNumber(
                        config.beatsPerBar,
                        style = IosType.body.tabular(),
                        color = colors.secondaryLabel,
                        modifier = Modifier.testTag("beats"),
                    )
                    Spacer(Modifier.width(12.dp))
                    IosStepper(
                        onDecrement = { onSetBeats(config.beatsPerBar - 1) },
                        onIncrement = { onSetBeats(config.beatsPerBar + 1) },
                        decrementEnabled = config.beatsPerBar > MetronomeConfig.MIN_BEATS,
                        incrementEnabled = config.beatsPerBar < MetronomeConfig.MAX_BEATS,
                        decrementTag = "beatsMinus",
                        incrementTag = "beatsPlus",
                    )
                },
            )
            IosDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("细分", style = IosType.body, color = colors.label)
                Spacer(Modifier.height(8.dp))
                IosSegmentedControl(
                    options = Subdivision.entries.map { it.label },
                    selectedIndex = Subdivision.entries.indexOf(config.subdivision),
                    onSelect = { onSetSubdivision(Subdivision.entries[it]) },
                )
            }
            IosDivider()
            IosRow(
                title = "第一拍重音",
                trailing = { IosSwitch(checked = config.accentFirst, onCheckedChange = onSetAccent) },
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** 根据播放位置计算摆锤角度：每一拍摆到一侧尽头，中间按余弦运动（接近真实摆的速度变化）。 */
private fun pendulumAngle(v: MetronomeEngine.Visual): Float {
    val side = if (v.beatSerial % 2L == 0L) 1f else -1f
    return MAX_SWING_DEG * side * cos(PI * v.beatProgress).toFloat()
}

@Composable
private fun RoundIconButton(text: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val colors = LocalIosColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(colors.secondaryFill)
            .iosClickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
    ) {
        Text(text, fontSize = 26.sp, color = if (enabled) colors.blue else colors.tertiaryLabel)
    }
}

@Composable
private fun Pendulum(angle: () -> Float, bpm: Int, modifier: Modifier = Modifier) {
    val colors = LocalIosColors.current
    val body = colors.surfaceSecondary
    val arm = colors.blue
    val pivot = colors.secondaryLabel
    // 速度越慢，摆锤上的砝码越靠上（与机械节拍器一致），并平滑移动
    val weightPos by animateFloatAsState(
        targetValue = 0.85f - 0.5f * (bpm - MetronomeConfig.MIN_BPM) / (MetronomeConfig.MAX_BPM - MetronomeConfig.MIN_BPM).toFloat(),
        animationSpec = Motion.smooth(),
        label = "weightPos",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val baseWidth = h * 0.9f
        val path = Path().apply {
            moveTo(w / 2 - baseWidth * 0.16f, h * 0.02f)
            lineTo(w / 2 + baseWidth * 0.16f, h * 0.02f)
            lineTo(w / 2 + baseWidth * 0.42f, h)
            lineTo(w / 2 - baseWidth * 0.42f, h)
            close()
        }
        drawPath(path, body)
        val pivotPoint = Offset(w / 2, h * 0.86f)
        val length = h * 0.8f
        rotate(degrees = angle(), pivot = pivotPoint) {
            drawLine(
                color = arm,
                start = pivotPoint,
                end = Offset(pivotPoint.x, pivotPoint.y - length),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val wy = pivotPoint.y - length * weightPos
            val ww = 20.dp.toPx()
            val wh = 12.dp.toPx()
            drawRoundRect(
                color = arm,
                topLeft = Offset(pivotPoint.x - ww / 2, wy - wh / 2),
                size = Size(ww, wh),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        drawCircle(pivot, radius = 5.dp.toPx(), center = pivotPoint)
        drawCircle(body, radius = 2.dp.toPx(), center = pivotPoint, style = Stroke(1.dp.toPx()))
    }
}

/** 拍点：当前拍高亮，每次滴答时弹跳一下（在绘制阶段计算，随声音同步）。 */
@Composable
private fun BeatDots(config: MetronomeConfig, visual: () -> MetronomeEngine.Visual?) {
    val colors = LocalIosColors.current
    val inactive = colors.fill
    val normal = colors.blue
    val accentColor = colors.orange
    val ring = colors.separator
    val beats = config.beatsPerBar
    val accentOn = config.accentFirst && beats > 1
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag("beatDots"),
    ) {
        val v = visual()
        val baseRadius = (if (beats > 8) 7.dp else 10.dp).toPx()
        val gap = (if (beats > 8) 10.dp else 14.dp).toPx()
        val totalWidth = beats * baseRadius * 2 + (beats - 1) * gap
        var x = (size.width - totalWidth) / 2 + baseRadius
        val cy = size.height / 2
        for (i in 0 until beats) {
            val accent = i == 0 && accentOn
            val active = v != null && v.beat == i
            var r = if (accent) baseRadius * 1.25f else baseRadius
            if (v != null && active) {
                val strength = if (v.sub == 0) 0.45f else 0.15f
                r *= 1f + strength * exp(-v.secondsSinceTick / 0.09f)
            }
            val color = when {
                active && accent -> accentColor
                active -> normal
                else -> inactive
            }
            drawCircle(color, radius = r, center = Offset(x, cy))
            if (accent) drawCircle(ring, radius = r, center = Offset(x, cy), style = Stroke(1.5.dp.toPx()))
            x += baseRadius * 2 + gap
        }
    }
}
