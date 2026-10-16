package com.guitarmagic.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.core.CustomTunings
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import java.util.Locale

/** 面板内的页面：列表或编辑器。editing 为 null 表示新建。 */
private sealed interface SheetPage {
    data object List : SheetPage
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TuningSheetContent(
            state = state,
            onSelect = onSelect,
            onSave = onSave,
            onDelete = onDelete,
            onPreview = onPreview,
            defaultName = defaultName,
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
) {
    var page by remember { mutableStateOf<SheetPage>(SheetPage.List) }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState is SheetPage.Edit
            val enter = fadeIn(Motion.standard()) +
                slideInHorizontally(Motion.standard()) { if (forward) it / 3 else -it / 3 }
            val exit = fadeOut(Motion.standard(Motion.SHORT)) +
                slideOutHorizontally(Motion.standard()) { if (forward) -it / 3 else it / 3 }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "sheetPage",
    ) { current ->
        when (current) {
            SheetPage.List -> TuningList(
                state = state,
                onSelect = onSelect,
                onEdit = { page = SheetPage.Edit(it) },
                onCreate = { page = SheetPage.Edit(null) },
                onDelete = onDelete,
            )
            is SheetPage.Edit -> TuningEditor(
                editing = current.editing,
                template = state.tuning,
                useFlats = state.useFlats,
                a4 = state.a4,
                defaultName = defaultName,
                onPreview = onPreview,
                onCancel = { page = SheetPage.List },
                onSave = { name, strings -> onSave(current.editing?.id, name, strings) },
            )
        }
    }
}

@Composable
private fun TuningList(
    state: TunerUiState,
    onSelect: (Tuning) -> Unit,
    onEdit: (Tuning) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Tuning?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text("选择调弦", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        SectionLabel("内置")
        Tuning.BUILT_IN.forEach { t ->
            TuningRow(t, selected = t.id == state.tuning.id, useFlats = state.useFlats, onClick = { onSelect(t) })
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("我的预设")
        if (state.customTunings.isEmpty()) {
            Text(
                "还没有自定义预设。点击下方按钮，为每根弦选择任意音高。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        state.customTunings.forEach { t ->
            TuningRow(
                t,
                selected = t.id == state.tuning.id,
                useFlats = state.useFlats,
                onClick = { onSelect(t) },
                trailing = {
                    TextButton(onClick = { onEdit(t) }) { Text("编辑") }
                    TextButton(onClick = { pendingDelete = t }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCreate,
            enabled = state.customTunings.size < CustomTunings.MAX_PRESETS,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("createPreset"),
        ) {
            Text("＋ 新建预设")
        }
    }

    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除预设") },
            text = { Text("确定删除“${t.displayName}”吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(t.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun TuningRow(
    tuning: Tuning,
    selected: Boolean,
    useFlats: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        Motion.standard(),
        label = "rowBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (selected) "✓ " else "") + tuning.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tuning.notesSummary(useFlats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun TuningEditor(
    editing: Tuning?,
    template: Tuning,
    useFlats: Boolean,
    a4: Double,
    defaultName: () -> String,
    onPreview: (Int) -> Unit,
    onCancel: () -> Unit,
    onSave: (name: String, strings: List<Int>) -> Boolean,
) {
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.displayName ?: defaultName()) }
    val strings = remember(editing?.id) {
        (editing?.strings ?: template.strings)
            .map { it.coerceIn(Tuning.MIN_STRING_MIDI, Tuning.MAX_STRING_MIDI) }
            .toMutableStateList()
    }
    var pickerFor by remember { mutableStateOf<Int?>(null) }
    val nameValid = CustomTunings.sanitizeName(name).isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("‹ 返回") }
            Text(
                if (editing == null) "新建预设" else "编辑预设",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= CustomTunings.MAX_NAME_LENGTH) name = it },
            label = { Text("预设名称") },
            singleLine = true,
            isError = !nameValid,
            supportingText = { if (!nameValid) Text("请输入名称") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("presetName"),
        )
        Text(
            "从现有调弦开始：",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Tuning.BUILT_IN.forEach { t ->
                OutlinedButton(onClick = {
                    t.strings.forEachIndexed { i, m -> strings[i] = m }
                }) { Text(t.displayName) }
            }
        }
        Spacer(Modifier.height(12.dp))
        strings.forEachIndexed { index, midi ->
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
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = { onSave(name, strings.toList()) },
                enabled = nameValid,
                modifier = Modifier
                    .weight(1f)
                    .testTag("savePreset"),
            ) { Text("保存并使用") }
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            "${Tuning.STRING_COUNT - index}弦",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .width(40.dp)
                .padding(start = 6.dp),
        )
        TextButton(
            onClick = { onChange(midi - 1) },
            enabled = midi > Tuning.MIN_STRING_MIDI,
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 4.dp)
                .testTag("editString$index"),
        ) {
            AnimatedNumberLabel(midi, useFlats)
            Text(
                String.format(Locale.US, "%.2f Hz", MusicTheory.midiToFrequency(midi, a4)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onChange(midi + 1) },
            enabled = midi < Tuning.MAX_STRING_MIDI,
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = onPreview) { Text("▶") }
    }
}

/** 音名变化时上下滚动（升高向上，降低向下）。 */
@Composable
private fun AnimatedNumberLabel(midi: Int, useFlats: Boolean) {
    AnimatedContent(
        targetState = midi,
        transitionSpec = {
            val up = targetState > initialState
            (fadeIn(Motion.standard(Motion.SHORT)) +
                androidx.compose.animation.slideInVertically(Motion.standard()) { if (up) it / 2 else -it / 2 }) togetherWith
                (fadeOut(Motion.standard(Motion.SHORT)) +
                    androidx.compose.animation.slideOutVertically(Motion.standard()) { if (up) -it / 2 else it / 2 })
        },
        label = "editNote",
    ) { m ->
        Text(MusicTheory.label(m, useFlats), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
    val minOctave = MusicTheory.octave(Tuning.MIN_STRING_MIDI)
    val maxOctave = MusicTheory.octave(Tuning.MAX_STRING_MIDI)
    var octave by remember { mutableIntStateOf(MusicTheory.octave(initial)) }
    var pitchClass by remember { mutableIntStateOf(Math.floorMod(initial, 12)) }
    val selected = MusicTheory.midiOf(pitchClass, octave)
    val valid = CustomTunings.isValidMidi(selected)
    val names = MusicTheory.pitchClassNames(useFlats)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringNumber}弦音高") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (valid) MusicTheory.label(selected, useFlats) else "超出范围",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (valid) {
                        String.format(Locale.US, "%.2f Hz", MusicTheory.midiToFrequency(selected, a4))
                    } else {
                        "可选范围 ${MusicTheory.label(Tuning.MIN_STRING_MIDI, useFlats)}–" +
                            MusicTheory.label(Tuning.MAX_STRING_MIDI, useFlats)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { octave-- }, enabled = octave > minOctave) { Text("−") }
                    Text("第 $octave 八度", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { octave++ }, enabled = octave < maxOctave) { Text("+") }
                }
                Spacer(Modifier.height(6.dp))
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 4) {
                            val pc = row * 4 + col
                            val midi = MusicTheory.midiOf(pc, octave)
                            val enabled = CustomTunings.isValidMidi(midi)
                            val isSelected = pc == pitchClass
                            val bg by animateColorAsState(
                                when {
                                    isSelected && enabled -> MaterialTheme.colorScheme.primary
                                    isSelected -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                Motion.standard(Motion.SHORT),
                                label = "pcBg",
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .width(56.dp)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .clickable(enabled = enabled) { pitchClass = pc },
                            ) {
                                Text(
                                    names[pc],
                                    color = when {
                                        isSelected && enabled -> MaterialTheme.colorScheme.onPrimary
                                        enabled -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = valid) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
