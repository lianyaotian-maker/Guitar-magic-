package com.guitarmagic.tuner.ui.ios

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS 系统配色（取自 Apple Human Interface Guidelines 的系统颜色）。 */
@Immutable
data class IosColors(
    val isDark: Boolean,
    /** systemGroupedBackground：页面底色 */
    val background: Color,
    /** secondarySystemGroupedBackground：分组卡片底色 */
    val surface: Color,
    /** tertiarySystemGroupedBackground：卡片内的次级底色 */
    val surfaceSecondary: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    /** systemFill：控件填充（分段控件、步进器底色等） */
    val fill: Color,
    val secondaryFill: Color,
    /** 分段控件选中块 */
    val segmentThumb: Color,
    val tabBar: Color,
    val tabInactive: Color,
    val blue: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    val gray: Color,
)

val LightIosColors = IosColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    segmentThumb = Color(0xFFFFFFFF),
    tabBar = Color(0xF2F9F9F9),
    tabInactive = Color(0xFF8E8E93),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    gray = Color(0xFF8E8E93),
)

val DarkIosColors = IosColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceSecondary = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    segmentThumb = Color(0xFF636366),
    tabBar = Color(0xF2161616),
    tabInactive = Color(0xFF8E8E93),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    gray = Color(0xFF8E8E93),
)

val LocalIosColors = staticCompositionLocalOf { LightIosColors }

/** iOS 动态字体的默认字号（Large Title 34、Body 17、Footnote 13 ……）。 */
object IosType {
    private const val TABULAR = "tnum"

    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption1 = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)

    /** 等宽数字：数值变化时文字宽度不跳动。 */
    fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)
}
