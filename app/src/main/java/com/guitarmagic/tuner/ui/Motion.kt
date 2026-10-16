package com.guitarmagic.tuner.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** 全局统一的动效参数，保证各处节奏一致。 */
object Motion {
    /** Material 3 “强调”缓动曲线：起步快、收尾柔和。 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT = 180
    const val MEDIUM = 320

    fun <T> standard(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, easing = Emphasized)

    /** 轻微回弹，用于缩放等“有弹性”的反馈。 */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** 无回弹的平滑跟随，用于颜色等。 */
    fun <T> smooth(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}
