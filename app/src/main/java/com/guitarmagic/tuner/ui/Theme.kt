package com.guitarmagic.tuner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.guitarmagic.tuner.ui.ios.DarkIosColors
import com.guitarmagic.tuner.ui.ios.IosColors
import com.guitarmagic.tuner.ui.ios.IosType
import com.guitarmagic.tuner.ui.ios.LightIosColors
import com.guitarmagic.tuner.ui.ios.LocalIosColors

/** 调音状态专用颜色：调准为绿色，偏离为橙色。 */
@Immutable
data class TunerColors(val inTune: Color, val offTune: Color)

val LocalTunerColors = staticCompositionLocalOf { TunerColors(LightIosColors.green, LightIosColors.orange) }

/**
 * iOS 风格主题：使用 iOS 系统配色与字号，跟随系统深浅色。
 * 同时把配色映射到 MaterialTheme，确保少量沿用的 Material 组件外观一致。
 */
@Composable
fun TunerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ios = if (darkTheme) DarkIosColors else LightIosColors
    CompositionLocalProvider(
        LocalIosColors provides ios,
        LocalTunerColors provides TunerColors(inTune = ios.green, offTune = ios.orange),
    ) {
        MaterialTheme(colorScheme = materialScheme(ios)) {
            CompositionLocalProvider(
                LocalContentColor provides ios.label,
                LocalTextStyle provides IosType.body,
                content = content,
            )
        }
    }
}

private fun materialScheme(ios: IosColors) = if (ios.isDark) {
    darkColorScheme(
        primary = ios.blue,
        onPrimary = Color.White,
        secondary = ios.blue,
        background = ios.background,
        onBackground = ios.label,
        surface = ios.surface,
        onSurface = ios.label,
        surfaceVariant = ios.surfaceSecondary,
        onSurfaceVariant = ios.secondaryLabel,
        surfaceContainerLow = ios.background,
        error = ios.red,
        outline = ios.separator,
        outlineVariant = ios.separator,
    )
} else {
    lightColorScheme(
        primary = ios.blue,
        onPrimary = Color.White,
        secondary = ios.blue,
        background = ios.background,
        onBackground = ios.label,
        surface = ios.surface,
        onSurface = ios.label,
        surfaceVariant = ios.surfaceSecondary,
        onSurfaceVariant = ios.secondaryLabel,
        surfaceContainerLow = ios.background,
        error = ios.red,
        outline = ios.separator,
        outlineVariant = ios.separator,
    )
}
