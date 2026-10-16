package com.guitarmagic.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Pendulum(angle = currentAngle, bpm = config.bpm, modifier = Modifier.fillMaxWidth().height(170.dp))
        Spacer(Modifier.height(12.dp))
        BeatDots(config, currentVisual)
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton("−", enabled = config.bpm > MetronomeConfig.MIN_BPM, tag = "bpmMinus") { onChangeBpm(-1) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 20.dp)) {
                AnimatedNumber(
                    value = config.bpm,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.testTag("bpm"),
                )
                Text("BPM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RoundButton("+", enabled = config.bpm < MetronomeConfig.MAX_BPM, tag = "bpmPlus") { onChangeBpm(1) }
        }
        AnimatedContent(
            targetState = TempoMarkings.of(config.bpm),
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard(Motion.SHORT)) },
            label = "marking",
        ) { marking ->
            Text(marking, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = config.bpm.toFloat(),
            onValueChange = { onSetBpm(Math.round(it)) },
            valueRange = MetronomeConfig.MIN_BPM.toFloat()..MetronomeConfig.MAX_BPM.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TapButton(onTap, Modifier.weight(1f))
            StartButton(state.running, onToggle, Modifier.weight(1.4f))
        }
        Spacer(Modifier.height(20.dp))

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每小节拍数", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onSetBeats(config.beatsPerBar - 1) },
                    enabled = config.beatsPerBar > MetronomeConfig.MIN_BEATS,
                    modifier = Modifier.testTag("beatsMinus"),
                ) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                AnimatedNumber(config.beatsPerBar, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("beats"))
                TextButton(
                    onClick = { onSetBeats(config.beatsPerBar + 1) },
                    enabled = config.beatsPerBar < MetronomeConfig.MAX_BEATS,
                    modifier = Modifier.testTag("beatsPlus"),
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text("细分", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Subdivision.entries.forEach { s ->
                    Pill(
                        text = s.label,
                        selected = s == config.subdivision,
                        onClick = { onSetSubdivision(s) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("第一拍重音", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = config.accentFirst, onCheckedChange = onSetAccent)
            }
        }
    }
}

/** 根据播放位置计算摆锤角度：每一拍摆到一侧尽头，中间按余弦运动（接近真实摆的速度变化）。 */
private fun pendulumAngle(v: MetronomeEngine.Visual): Float {
    val side = if (v.beatSerial % 2L == 0L) 1f else -1f
    return MAX_SWING_DEG * side * cos(PI * v.beatProgress).toFloat()
}

@Composable
private fun Pendulum(angle: () -> Float, bpm: Int, modifier: Modifier = Modifier) {
    val body = MaterialTheme.colorScheme.surfaceVariant
    val arm = MaterialTheme.colorScheme.primary
    val weight = MaterialTheme.colorScheme.primary
    val pivot = MaterialTheme.colorScheme.onSurfaceVariant
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
            moveTo(w / 2 - baseWidth * 0.18f, h * 0.02f)
            lineTo(w / 2 + baseWidth * 0.18f, h * 0.02f)
            lineTo(w / 2 + baseWidth * 0.45f, h)
            lineTo(w / 2 - baseWidth * 0.45f, h)
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
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val wy = pivotPoint.y - length * weightPos
            val ww = 22.dp.toPx()
            val wh = 14.dp.toPx()
            drawRoundRect(
                color = weight,
                topLeft = Offset(pivotPoint.x - ww / 2, wy - wh / 2),
                size = Size(ww, wh),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        drawCircle(pivot, radius = 6.dp.toPx(), center = pivotPoint)
        drawCircle(body, radius = 2.5.dp.toPx(), center = pivotPoint, style = Stroke(1.dp.toPx()))
    }
}

/** 拍点：当前拍高亮，每次滴答时弹跳一下（在绘制阶段计算，随声音同步）。 */
@Composable
private fun BeatDots(config: MetronomeConfig, visual: () -> MetronomeEngine.Visual?) {
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val normal = MaterialTheme.colorScheme.primary
    val accentColor = LocalTunerColors.current.offTune
    val ring = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val beats = config.beatsPerBar
    val accentOn = config.accentFirst && beats > 1
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("beatDots"),
    ) {
        val v = visual()
        val baseRadius = (if (beats > 8) 8.dp else 11.dp).toPx()
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
            if (accent) drawCircle(ring, radius = r, center = Offset(x, cy), style = Stroke(2.dp.toPx()))
            x += baseRadius * 2 + gap
        }
    }
}

@Composable
private fun RoundButton(text: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
    ) {
        Text(
            text,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

@Composable
private fun TapButton(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    OutlinedButton(
        onClick = {
            onTap()
            scope.launch {
                press.snapTo(0.92f)
                press.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            }
        },
        modifier = modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .testTag("tap"),
    ) {
        Text("敲击测速")
    }
}

@Composable
private fun StartButton(running: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        if (running) LocalTunerColors.current.offTune else MaterialTheme.colorScheme.primary,
        Motion.standard(),
        label = "startBg",
    )
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(containerColor = container),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .height(56.dp)
            .testTag("startStop"),
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard(Motion.SHORT)) },
            label = "startText",
        ) { isRunning ->
            Text(if (isRunning) "■  停止" else "▶  开始", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        Motion.standard(Motion.SHORT * 2),
        label = "pillBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.standard(Motion.SHORT * 2),
        label = "pillFg",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp),
    ) {
        content()
    }
}
