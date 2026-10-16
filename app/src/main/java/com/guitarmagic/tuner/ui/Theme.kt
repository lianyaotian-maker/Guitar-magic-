package com.guitarmagic.tuner.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 调音状态专用颜色：调准为绿色，偏离为橙色。 */
@Immutable
data class TunerColors(val inTune: Color, val offTune: Color)

private val LightTunerColors = TunerColors(inTune = Color(0xFF1B8A3A), offTune = Color(0xFFD9480F))
private val DarkTunerColors = TunerColors(inTune = Color(0xFF5BD47A), offTune = Color(0xFFFFA25C))

val LocalTunerColors = staticCompositionLocalOf { LightTunerColors }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2B4C6F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    background = Color(0xFFFBFBFB),
    surface = Color(0xFFFBFBFB),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF1F4875),
    onPrimaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

@Composable
fun TunerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalTunerColors provides if (darkTheme) DarkTunerColors else LightTunerColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
