package com.guitarmagic.tuner.ui.ios

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 点击反馈：iOS 不用水波纹，而是按下时变淡（按钮）或高亮底色（列表行）
// ---------------------------------------------------------------------------

/** 按下时变淡的点击效果（iOS 按钮风格）。 */
fun Modifier.iosClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.35f else 1f,
        animationSpec = tween(if (pressed) 60 else 260),
        label = "pressAlpha",
    )
    this
        .graphicsLayer { this.alpha = alpha }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** 按下时底色高亮的点击效果（iOS 列表行风格）。 */
fun Modifier.iosHighlightClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val colors = LocalIosColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) colors.fill else Color.Transparent,
        animationSpec = tween(if (pressed) 0 else 280),
        label = "pressBg",
    )
    this
        .background(bg)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

// ---------------------------------------------------------------------------
// 页面结构
// ---------------------------------------------------------------------------

/** 大标题（Large Title）。 */
@Composable
fun IosLargeTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosType.largeTitle, color = LocalIosColors.current.label, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 弹出面板顶部的导航栏：左按钮 / 居中标题 / 右按钮。 */
@Composable
fun IosNavBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) { leading?.invoke() }
        Text(
            title,
            style = IosType.headline,
            color = LocalIosColors.current.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 88.dp),
        )
        Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
    }
}

/** 分组列表（Inset Grouped）：小标题 + 圆角白色卡片 + 说明文字。 */
@Composable
fun IosSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalIosColors.current
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = IosType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = IosType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/** 列表行之间的细分隔线（左侧缩进）。 */
@Composable
fun IosDivider(startIndent: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalIosColors.current.separator),
    )
}

/** 标准列表行：标题、可选副标题、右侧数值/控件/箭头。 */
@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color? = null,
    titleWeight: FontWeight = FontWeight.Normal,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LocalIosColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.iosHighlightClickable(onLongClick = onLongClick, onClick = onClick) else Modifier)
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = IosType.body.copy(fontWeight = titleWeight),
                color = titleColor ?: colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = IosType.footnote, color = colors.secondaryLabel, maxLines = 2)
            }
        }
        if (value != null) {
            Text(value, style = IosType.body.copy(fontFeatureSettings = "tnum"), color = colors.secondaryLabel, maxLines = 1)
        }
        trailing?.invoke(this)
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Text("›", fontSize = 24.sp, color = colors.tertiaryLabel, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------------------
// 按钮
// ---------------------------------------------------------------------------

/** 纯文字按钮（系统蓝）。 */
@Composable
fun IosTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    enabled: Boolean = true,
    bold: Boolean = false,
    style: TextStyle = IosType.body,
) {
    val colors = LocalIosColors.current
    Text(
        text,
        style = style.copy(fontWeight = if (bold) FontWeight.SemiBold else style.fontWeight),
        color = if (enabled) color ?: colors.blue else colors.tertiaryLabel,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .iosClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** 实心圆角大按钮。 */
@Composable
fun IosFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    enabled: Boolean = true,
) {
    val colors = LocalIosColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color ?: colors.blue else colors.fill)
            .iosClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Text(text, style = IosType.headline, color = if (enabled) Color.White else colors.tertiaryLabel)
    }
}

/** 浅色底 + 彩色字的胶囊按钮（Tinted）。 */
@Composable
fun IosTintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    style: TextStyle = IosType.subheadline.copy(fontWeight = FontWeight.SemiBold),
) {
    val colors = LocalIosColors.current
    val c = color ?: colors.blue
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = if (colors.isDark) 0.24f else 0.13f))
            .iosClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, style = style, color = c, maxLines = 1)
    }
}

/** 时钟 App 秒表风格的圆形按钮：浅色圆底 + 内圈细环 + 彩色文字。 */
@Composable
fun IosCircleButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    val colors = LocalIosColors.current
    val fillColor by animateColorAsState(color.copy(alpha = if (colors.isDark) 0.28f else 0.16f), tween(260), label = "circleFill")
    val textColor by animateColorAsState(color, tween(260), label = "circleText")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .iosClickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(size - 8.dp)
                .border(1.5.dp, colors.background.copy(alpha = 0.9f), CircleShape),
        )
        Text(text, style = IosType.headline, color = textColor)
    }
}

// ---------------------------------------------------------------------------
// 控件
// ---------------------------------------------------------------------------

/** iOS 开关：绿色滑轨 + 白色圆钮，带弹簧动画。 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalIosColors.current
    val track by animateColorAsState(if (checked) colors.green else colors.fill, tween(220), label = "switchTrack")
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "switchThumb",
    )
    Canvas(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        val h = size.height
        drawRoundRect(color = track, cornerRadius = CornerRadius(h / 2, h / 2))
        val r = h / 2 - 2.dp.toPx()
        val cx = h / 2 + (size.width - h) * position
        drawCircle(Color.Black.copy(alpha = 0.10f), radius = r + 0.5.dp.toPx(), center = Offset(cx, h / 2 + 1.5.dp.toPx()))
        drawCircle(Color.White, radius = r, center = Offset(cx, h / 2))
    }
}

/** 分段控件：选中块带弹簧滑动动画。 */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tags: List<String>? = null,
) {
    val colors = LocalIosColors.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.secondaryFill),
    ) {
        val count = options.size.coerceAtLeast(1)
        val segment = maxWidth / count
        val index = selectedIndex.coerceIn(0, count - 1)
        val offset by animateDpAsState(
            targetValue = segment * index,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
            label = "segmentOffset",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .padding(2.dp)
                .shadow(2.dp, RoundedCornerShape(7.dp), clip = false)
                .background(colors.segmentThumb, RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val selected = i == index
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { if (!selected) onSelect(i) },
                        )
                        .then(if (tags != null && i < tags.size) Modifier.testTag(tags[i]) else Modifier),
                ) {
                    Text(
                        label,
                        style = IosType.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = colors.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 步进器：− | + 。 */
@Composable
fun IosStepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    decrementTag: String? = null,
    incrementTag: String? = null,
) {
    val colors = LocalIosColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .size(width = 96.dp, height = 34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.secondaryFill),
    ) {
        StepperHalf("−", decrementEnabled, decrementTag, onDecrement)
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(colors.separator),
        )
        StepperHalf("+", incrementEnabled, incrementTag, onIncrement)
    }
}

@Composable
private fun RowScope.StepperHalf(symbol: String, enabled: Boolean, tag: String?, onClick: () -> Unit) {
    val colors = LocalIosColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .iosClickable(enabled = enabled, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Text(symbol, fontSize = 22.sp, color = if (enabled) colors.label else colors.tertiaryLabel)
    }
}

/** iOS 滑块：细滑轨 + 白色圆钮。支持点击跳转、拖动和无障碍调节。 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val colors = LocalIosColors.current
    val onChange by rememberUpdatedState(onValueChange)
    val range by rememberUpdatedState(valueRange)
    val stepCount by rememberUpdatedState(steps)

    fun snap(raw: Float): Float {
        val r = range
        val v = raw.coerceIn(r.start, r.endInclusive)
        if (stepCount <= 0 || r.endInclusive <= r.start) return v
        val stepSize = (r.endInclusive - r.start) / (stepCount + 1)
        return (r.start + ((v - r.start) / stepSize).roundToInt() * stepSize).coerceIn(r.start, r.endInclusive)
    }

    val thumbRadius = 14.dp
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange.start, valueRange.endInclusive), valueRange, steps)
                setProgress { target ->
                    onChange(snap(target))
                    true
                }
            }
            .pointerInput(Unit) {
                val r = thumbRadius.toPx()
                detectTapGestures { pos ->
                    val usable = (size.width - 2 * r).coerceAtLeast(1f)
                    val f = ((pos.x - r) / usable).coerceIn(0f, 1f)
                    onChange(snap(range.start + f * (range.endInclusive - range.start)))
                }
            }
            .pointerInput(Unit) {
                val r = thumbRadius.toPx()
                fun update(x: Float) {
                    val usable = (size.width - 2 * r).coerceAtLeast(1f)
                    val f = ((x - r) / usable).coerceIn(0f, 1f)
                    onChange(snap(range.start + f * (range.endInclusive - range.start)))
                }
                detectHorizontalDragGestures(
                    onDragStart = { update(it.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        update(change.position.x)
                    },
                )
            },
    ) {
        val r = thumbRadius.toPx()
        val span = valueRange.endInclusive - valueRange.start
        val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val cy = size.height / 2
        val trackH = 4.dp.toPx()
        val startX = r
        val endX = size.width - r
        val thumbX = startX + (endX - startX) * fraction
        drawRoundRect(
            color = colors.fill,
            topLeft = Offset(startX, cy - trackH / 2),
            size = Size(endX - startX, trackH),
            cornerRadius = CornerRadius(trackH / 2, trackH / 2),
        )
        drawRoundRect(
            color = colors.blue,
            topLeft = Offset(startX, cy - trackH / 2),
            size = Size(thumbX - startX, trackH),
            cornerRadius = CornerRadius(trackH / 2, trackH / 2),
        )
        drawCircle(Color.Black.copy(alpha = 0.12f), radius = r + 0.5.dp.toPx(), center = Offset(thumbX, cy + 1.5.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.05f), radius = r + 0.5.dp.toPx(), center = Offset(thumbX, cy))
        drawCircle(Color.White, radius = r, center = Offset(thumbX, cy))
    }
}

/** 单行文本输入框（用在分组卡片里）。 */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIosColors.current
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = IosType.body, color = colors.tertiaryLabel)
        }
        // modifier 作用在输入框本身（便于测试与无障碍定位）
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = IosType.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.blue),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// 弹窗与标签栏
// ---------------------------------------------------------------------------

data class IosAlertAction(
    val text: String,
    val style: Style = Style.Default,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
) {
    enum class Style { Default, Cancel, Destructive }
}

/** iOS 风格的提示框：居中标题与说明，底部按钮以细线分隔；出现时轻微缩放淡入。 */
@Composable
fun IosAlert(
    onDismiss: () -> Unit,
    title: String,
    actions: List<IosAlertAction>,
    message: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalIosColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
        Column(
            modifier = Modifier
                .width(296.dp)
                .graphicsLayer {
                    val p = appear.value
                    val s = 1.1f - 0.1f * p
                    scaleX = s
                    scaleY = s
                    alpha = p.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(16.dp))
                .background(if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F7)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            ) {
                Text(title, style = IosType.headline, color = colors.label, textAlign = TextAlign.Center)
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = IosType.footnote, color = colors.label, textAlign = TextAlign.Center)
                }
                content?.invoke(this)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
            if (actions.size == 2) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    AlertButton(actions[0], Modifier.weight(1f))
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(colors.separator))
                    AlertButton(actions[1], Modifier.weight(1f))
                }
            } else {
                actions.forEachIndexed { i, action ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                    AlertButton(action, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AlertButton(action: IosAlertAction, modifier: Modifier) {
    val colors = LocalIosColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 46.dp)
            .iosHighlightClickable(enabled = action.enabled, onClick = action.onClick),
    ) {
        Text(
            action.text,
            style = IosType.body.copy(fontWeight = if (action.style == IosAlertAction.Style.Cancel) FontWeight.SemiBold else FontWeight.Normal),
            color = when {
                !action.enabled -> colors.tertiaryLabel
                action.style == IosAlertAction.Style.Destructive -> colors.red
                else -> colors.blue
            },
        )
    }
}

data class IosTabItem(val label: String, val icon: Int, val tag: String)

/** iOS 底部标签栏：半透明底色 + 顶部细线；选中项为系统蓝，切换时图标轻微弹跳。 */
@Composable
fun IosTabBar(items: List<IosTabItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalIosColors.current
    Column(modifier.fillMaxWidth().background(colors.tabBar)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == selectedIndex
                val tint by animateColorAsState(if (selected) colors.blue else colors.tabInactive, tween(200), label = "tabTint")
                val bounce = remember { Animatable(1f) }
                var initialized by remember { mutableStateOf(false) }
                LaunchedEffect(selected) {
                    if (initialized && selected) {
                        bounce.snapTo(0.82f)
                        bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f))
                    }
                    initialized = true
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(i) },
                        )
                        .testTag(item.tag),
                ) {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = bounce.value
                                scaleY = bounce.value
                            },
                    )
                    Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tint)
                }
            }
        }
    }
}
