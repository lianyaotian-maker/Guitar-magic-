package com.guitarmagic.tuner.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MAX_CENTS = 50f
private const val MAX_ANGLE = 75f // ±50 音分对应 ±75°

/**
 * 半圆形刻度盘，指针显示 -50..+50 音分的偏差。
 * @param cents 当前偏差；null 表示无信号，指针回到中间并变淡
 */
@Composable
fun CentsGauge(
    cents: Double?,
    inTune: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val target = (cents ?: 0.0).toFloat().coerceIn(-MAX_CENTS, MAX_CENTS)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val tunerColors = LocalTunerColors.current
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = when {
        cents == null -> MaterialTheme.colorScheme.outlineVariant
        inTune -> tunerColors.inTune
        else -> tunerColors.offTune
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 12.sp, color = labelColor)

    Canvas(modifier) {
        val radius = min(size.width / 2f, size.height) * 0.92f
        val center = Offset(size.width / 2f, size.height - 6.dp.toPx())

        fun point(angleDeg: Float, r: Float): Offset {
            val rad = Math.toRadians(angleDeg.toDouble())
            return Offset(center.x + (r * sin(rad)).toFloat(), center.y - (r * cos(rad)).toFloat())
        }

        // 绿色“已调准”区域（±5 音分）
        val zoneAngle = 5f / MAX_CENTS * MAX_ANGLE
        val zoneRadius = radius - 4.dp.toPx()
        drawArc(
            color = tunerColors.inTune.copy(alpha = 0.35f),
            startAngle = -90f - zoneAngle,
            sweepAngle = zoneAngle * 2,
            useCenter = false,
            topLeft = Offset(center.x - zoneRadius, center.y - zoneRadius),
            size = Size(zoneRadius * 2, zoneRadius * 2),
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
        )

        // 刻度
        for (c in -50..50 step 5) {
            val angle = c / MAX_CENTS * MAX_ANGLE
            val length = when {
                c % 25 == 0 -> 18.dp.toPx()
                c % 10 == 0 -> 12.dp.toPx()
                else -> 7.dp.toPx()
            }
            val color = if (abs(c) <= 5) tunerColors.inTune else tickColor
            drawLine(
                color = color.copy(alpha = if (c % 10 == 0) 0.9f else 0.5f),
                start = point(angle, radius),
                end = point(angle, radius - length),
                strokeWidth = (if (c % 25 == 0) 2.5f else 1.5f).dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 刻度数字
        for (c in listOf(-50, -25, 0, 25, 50)) {
            val text = if (c > 0) "+$c" else "$c"
            val layout = textMeasurer.measure(text, labelStyle)
            val p = point(c / MAX_CENTS * MAX_ANGLE, radius - 32.dp.toPx())
            drawText(
                layout,
                topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f),
            )
        }

        // 指针
        val alpha = if (dimmed) 0.45f else 1f
        val needleAngle = animated / MAX_CENTS * MAX_ANGLE
        drawLine(
            color = needleColor.copy(alpha = alpha),
            start = center,
            end = point(needleAngle, radius - 10.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = needleColor.copy(alpha = alpha), radius = 8.dp.toPx(), center = center)
    }
}
