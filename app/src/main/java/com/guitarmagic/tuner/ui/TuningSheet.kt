package com.guitarmagic.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.core.CustomTunings
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import com.guitarmagic.tuner.ui.ios.IosAlert
import com.guitarmagic.tuner.ui.ios.IosAlertAction
import com.guitarmagic.tuner.ui.ios.IosDivider
import com.guitarmagic.tuner.ui.ios.IosNavBar
import com.guitarmagic.tuner.ui.ios.IosRow
import com.guitarmagic.tuner.ui.ios.IosSection
import com.guitarmagic.tuner.ui.ios.IosStepper
import com.guitarmagic.tuner.ui.ios.IosTextButton
import com.guitarmagic.tuner.ui.ios.IosTextField
import com.guitarmagic.tuner.ui.ios.IosTintedButton
import com.guitarmagic.tuner.ui.ios.IosType
import com.guitarmagic.tuner.ui.ios.IosType.tabular
import com.guitarmagic.tuner.ui.ios.LocalIosColors
import com.guitarmagic.tuner.ui.ios.iosClickable
import java.util.Locale

/** 面板内的页面：列表或编辑器。editing 为 null 表示新建。 */
private sealed interface SheetPage {
    data object Presets : SheetPage
    data class Edit(val editing: Tuning?) : SheetPage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningSheet(
    state: TunerUiState,
    onDismiss: () -> Unit,
    onSelect: (Tuning) -> Unit,
    onSave: (id: String?, name: String, strings: List<Int>) -> Boolean,
    onDelete: (String) -> Unit,
    onPreview: (Int) -> Unit,
    defaultName: () -> String,
) {
    val colors = LocalIosColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 6.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.tertiaryLabel),
            )
        },
    ) {
        TuningSheetContent(
            state = state,
            onSelect = onSelect,
            onSave = onSave,
            onDelete = onDelete,
            onPreview = onPreview,
            defaultName = defaultName,
            onDone = onDismiss,
        )
    }
}

/** 面板内容（不含 BottomSheet 外壳），便于测试。 */
@Composable
fun TuningSheetContent(
    state: TunerUiState,
    onSelect: (Tuning) -> Unit,
    onSave: (id: String?, name: String, strings: List<Int>) -> Boolean,
    onDelete: (String) -> Unit,
    onPreview: (Int) -> Unit,
    defaultName: () -> String,
    onDone: () -> Unit = {},
) {
    var page by remember { mutableStateOf<SheetPage>(SheetPage.Presets) }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // iOS 导航：进入编辑页从右侧推入，返回时从左侧滑回
            val forward = targetState is SheetPage.Edit
            val enter = fadeIn(Motion.standard()) +
                slideInHorizontally(Motion.standard()) { if (forward) it / 2 else -it / 3 }
            val exit = fadeOut(Motion.standard(Motion.SHORT)) +
                slideOutHorizontally(Motion.standard()) { if (forward) -it / 3 else it / 2 }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "sheetPage",
    ) { current ->
        when (current) {
            SheetPage.Presets -> PresetList(
                state = state,
                onSelect = onSelect,
                onEdit = { page = SheetPage.Edit(it) },
                onCreate = { page = SheetPage.Edit(null) },
                onDone = onDone,
            )
            is SheetPage.Edit -> PresetEditor(
                editing = current.editing,
                template = state.tuning,
                useFlats = state.useFlats,
                a4 = state.a4,
                defaultName = defaultName,
                onPreview = onPreview,
                onCancel = { page = SheetPage.Presets },
                onSave = { name, strings -> onSave(current.editing?.id, name, strings) },
                onDelete = { id ->
                    onDelete(id)
                    page = SheetPage.Presets
                },
            )
        }
    }
}

@Composable
private fun PresetList(
    state: TunerUiState,
    onSelect: (Tuning) -> Unit,
    onEdit: (Tuning) -> Unit,
    onCreate: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalIosColors.current
    Column(Modifier.fillMaxWidth()) {
        IosNavBar(
            title = "选择调弦",
            trailing = { IosTextButton("完成", onDone, bold = true) },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            IosSection(header = "内置") {
                Tuning.BUILT_IN.forEachIndexed { i, t ->
                    if (i > 0) IosDivider()
                    TuningRow(t, selected = t.id == state.tuning.id, useFlats = state.useFlats, onClick = { onSelect(t) })
                }
            }
            val canCreate = state.customTunings.size < CustomTunings.MAX_PRESETS
            IosSection(
                header = "我的预设",
                footer = if (state.customTunings.isEmpty()) {
                    "还没有自定义预设。新建预设后，可以为每根弦选择任意音高。"
                } else {
                    "轻点“编辑”可修改音高、重命名或删除预设。"
                },
            ) {
                state.customTunings.forEach { t ->
                    TuningRow(
                        t,
                        selected = t.id == state.tuning.id,
                        useFlats = state.useFlats,
                        onClick = { onSelect(t) },
                        onEdit = { onEdit(t) },
                    )
                    IosDivider()
                }
                IosRow(
                    title = "＋ 新建预设",
                    titleColor = if (canCreate) colors.blue else colors.tertiaryLabel,
                    onClick = if (canCreate) onCreate else null,
                    modifier = Modifier.testTag("createPreset"),
                )
            }
        }
    }
}

@Composable
private fun TuningRow(
    tuning: Tuning,
    selected: Boolean,
    useFlats: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val colors = LocalIosColors.current
    IosRow(
        title = tuning.displayName,
        subtitle = tuning.notesSummary(useFlats),
        titleWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        onClick = onClick,
        trailing = {
            if (selected) {
                Text("✓", color = colors.blue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            if (onEdit != null) {
                IosTextButton("编辑", onEdit, style = IosType.subheadline)
            }
        },
    )
}

@Composable
private fun PresetEditor(
    editing: Tuning?,
    template: Tuning,
    useFlats: Boolean,
    a4: Double,
    defaultName: () -> String,
    onPreview: (Int) -> Unit,
    onCancel: () -> Unit,
    onSave: (name: String, strings: List<Int>) -> Boolean,
    onDelete: (String) -> Unit,
) {
    val colors = LocalIosColors.current
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.displayName ?: defaultName()) }
    val strings = remember(editing?.id) {
        (editing?.strings ?: template.strings)
            .map { it.coerceIn(Tuning.MIN_STRING_MIDI, Tuning.MAX_STRING_MIDI) }
            .toMutableStateList()
    }
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameValid = CustomTunings.sanitizeName(name).isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        IosNavBar(
            title = if (editing == null) "新建预设" else "编辑预设",
            leading = { IosTextButton("取消", onCancel) },
            trailing = {
                IosTextButton(
                    "保存",
                    { onSave(name, strings.toList()) },
                    enabled = nameValid,
                    bold = true,
                    modifier = Modifier.testTag("savePreset"),
                )
            },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            IosSection(header = "名称", footer = if (nameValid) null else "请输入名称") {
                IosTextField(
                    value = name,
                    onValueChange = { if (it.length <= CustomTunings.MAX_NAME_LENGTH) name = it },
                    placeholder = "例如：Open D",
                    modifier = Modifier.testTag("presetName"),
                )
            }
            IosSection(header = "从现有调弦开始") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Tuning.BUILT_IN.forEach { t ->
                        IosTintedButton(t.displayName, onClick = {
                            t.strings.forEachIndexed { i, m -> strings[i] = m }
                        })
                    }
                }
            }
            IosSection(
                header = "琴弦",
                footer = "轻点音名可直接选择音高，可选范围 ${MusicTheory.label(Tuning.MIN_STRING_MIDI, useFlats)}–" +
                    "${MusicTheory.label(Tuning.MAX_STRING_MIDI, useFlats)}。▶ 试听。",
            ) {
                strings.forEachIndexed { index, midi ->
                    if (index > 0) IosDivider()
                    StringEditorRow(
                        index = index,
                        midi = midi,
                        useFlats = useFlats,
                        a4 = a4,
                        onChange = { strings[index] = it },
                        onPick = { pickerFor = index },
                        onPreview = { onPreview(midi) },
                    )
                }
            }
            if (editing != null) {
                IosSection {
                    IosRow(
                        title = "删除预设",
                        titleColor = colors.red,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("deletePreset"),
                    )
                }
            }
        }
    }

    pickerFor?.let { index ->
        NotePickerDialog(
            stringNumber = Tuning.STRING_COUNT - index,
            initial = strings[index],
            useFlats = useFlats,
            a4 = a4,
            onConfirm = {
                strings[index] = it
                pickerFor = null
            },
            onDismiss = { pickerFor = null },
        )
    }

    if (confirmDelete && editing != null) {
        IosAlert(
            onDismiss = { confirmDelete = false },
            title = "删除预设",
            message = "确定删除“${editing.displayName}”吗？此操作无法撤销。",
            actions = listOf(
                IosAlertAction("取消", IosAlertAction.Style.Cancel) { confirmDelete = false },
                IosAlertAction("删除", IosAlertAction.Style.Destructive) {
                    confirmDelete = false
                    onDelete(editing.id)
                },
            ),
        )
    }
}

@Composable
private fun StringEditorRow(
    index: Int,
    midi: Int,
    useFlats: Boolean,
    a4: Double,
    onChange: (Int) -> Unit,
    onPick: () -> Unit,
    onPreview: () -> Unit,
) {
    val colors = LocalIosColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            "${Tuning.STRING_COUNT - index}弦",
            style = IosType.subheadline,
            color = colors.secondaryLabel,
            modifier = Modifier.width(40.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .iosClickable(onClick = onPick)
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .testTag("editString$index"),
        ) {
            AnimatedNoteLabel(midi, useFlats)
            Text(
                String.format(Locale.US, "%.2f Hz", MusicTheory.midiToFrequency(midi, a4)),
                style = IosType.caption1.tabular(),
                color = colors.secondaryLabel,
            )
        }
        IosStepper(
            onDecrement = { onChange(midi - 1) },
            onIncrement = { onChange(midi + 1) },
            decrementEnabled = midi > Tuning.MIN_STRING_MIDI,
            incrementEnabled = midi < Tuning.MAX_STRING_MIDI,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.blue.copy(alpha = if (colors.isDark) 0.24f else 0.12f))
                .iosClickable(onClick = onPreview),
        ) {
            Text("▶", color = colors.blue, fontSize = 13.sp)
        }
    }
}

/** 音名变化时上下滚动（升高向上，降低向下）。 */
@Composable
private fun AnimatedNoteLabel(midi: Int, useFlats: Boolean) {
    val colors = LocalIosColors.current
    AnimatedContent(
        targetState = midi,
        transitionSpec = {
            val up = targetState > initialState
            (fadeIn(Motion.standard(Motion.SHORT)) + slideInVertically(Motion.standard()) { if (up) it / 2 else -it / 2 }) togetherWith
                (fadeOut(Motion.standard(Motion.SHORT)) + slideOutVertically(Motion.standard()) { if (up) -it / 2 else it / 2 })
        },
        label = "editNote",
    ) { m ->
        Text(MusicTheory.label(m, useFlats), style = IosType.headline, color = colors.label)
    }
}

/** 选择音高：12 个音名 + 八度。超出可识别范围的组合会被禁用。 */
@Composable
fun NotePickerDialog(
    stringNumber: Int,
    initial: Int,
    useFlats: Boolean,
    a4: Double,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalIosColors.current
    val minOctave = MusicTheory.octave(Tuning.MIN_STRING_MIDI)
    val maxOctave = MusicTheory.octave(Tuning.MAX_STRING_MIDI)
    var octave by remember { mutableIntStateOf(MusicTheory.octave(initial)) }
    var pitchClass by remember { mutableIntStateOf(Math.floorMod(initial, 12)) }
    val selected = MusicTheory.midiOf(pitchClass, octave)
    val valid = CustomTunings.isValidMidi(selected)
    val names = MusicTheory.pitchClassNames(useFlats)

    IosAlert(
        onDismiss = onDismiss,
        title = "${stringNumber}弦音高",
        actions = listOf(
            IosAlertAction("取消", IosAlertAction.Style.Cancel, onClick = onDismiss),
            IosAlertAction("确定", enabled = valid) { onConfirm(selected) },
        ),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (valid) MusicTheory.label(selected, useFlats) else "超出范围",
            style = IosType.title1,
            color = if (valid) colors.label else colors.red,
        )
        Text(
            if (valid) {
                String.format(Locale.US, "%.2f Hz", MusicTheory.midiToFrequency(selected, a4))
            } else {
                "可选范围 ${MusicTheory.label(Tuning.MIN_STRING_MIDI, useFlats)}–${MusicTheory.label(Tuning.MAX_STRING_MIDI, useFlats)}"
            },
            style = IosType.footnote.tabular(),
            color = colors.secondaryLabel,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("第 $octave 八度", style = IosType.subheadline, color = colors.label, modifier = Modifier.width(96.dp))
            IosStepper(
                onDecrement = { octave-- },
                onIncrement = { octave++ },
                decrementEnabled = octave > minOctave,
                incrementEnabled = octave < maxOctave,
            )
        }
        Spacer(Modifier.height(10.dp))
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 4) {
                    val pc = row * 4 + col
                    val enabled = CustomTunings.isValidMidi(MusicTheory.midiOf(pc, octave))
                    val isSelected = pc == pitchClass
                    val bg by animateColorAsState(
                        when {
                            isSelected && enabled -> colors.blue
                            isSelected -> colors.red.copy(alpha = 0.25f)
                            else -> colors.secondaryFill
                        },
                        Motion.standard(Motion.SHORT),
                        label = "pcBg",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .size(width = 56.dp, height = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .iosClickable(enabled = enabled) { pitchClass = pc },
                    ) {
                        Text(
                            names[pc],
                            color = when {
                                isSelected && enabled -> Color.White
                                enabled -> colors.label
                                else -> colors.tertiaryLabel
                            },
                            style = IosType.callout.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}
