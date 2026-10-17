package com.guitarmagic.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarmagic.tuner.BuildConfig
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.TunerViewModel
import com.guitarmagic.tuner.core.TuneTolerance
import com.guitarmagic.tuner.pitch.DetectionMode
import com.guitarmagic.tuner.pitch.Sensitivity
import com.guitarmagic.tuner.ui.ios.IosDivider
import com.guitarmagic.tuner.ui.ios.IosLargeTitle
import com.guitarmagic.tuner.ui.ios.IosRow
import com.guitarmagic.tuner.ui.ios.IosSection
import com.guitarmagic.tuner.ui.ios.IosSegmentedControl
import com.guitarmagic.tuner.ui.ios.IosSwitch
import com.guitarmagic.tuner.ui.ios.IosType
import com.guitarmagic.tuner.ui.ios.LocalIosColors

@Composable
fun SettingsTab(viewModel: TunerViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onSetA4 = viewModel::setA4,
        onSetUseFlats = viewModel::setUseFlats,
        onSetHaptics = viewModel::setHapticsEnabled,
        onSetSensitivity = viewModel::setSensitivity,
        onSetDetectionMode = viewModel::setDetectionMode,
        onSetChime = viewModel::setChimeEnabled,
        onSetTolerance = viewModel::setTolerance,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    state: TunerUiState,
    onSetA4: (Double) -> Unit,
    onSetUseFlats: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSetSensitivity: (Sensitivity) -> Unit = {},
    onSetDetectionMode: (DetectionMode) -> Unit = {},
    onSetChime: (Boolean) -> Unit = {},
    onSetTolerance: (TuneTolerance) -> Unit = {},
) {
    val colors = LocalIosColors.current
    // 平板、横屏时内容限制在合适宽度并居中
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
      Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
        IosLargeTitle("设置")
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            IosSection(
                header = "识别",
                footer = "灵敏度：环境嘈杂时选“低”，安静环境里轻轻拨弦选“高”。\n" +
                    "吉他琴弦模式只识别当前调弦附近的音，最不容易被说话声和杂音干扰；半音阶模式可识别任意音。",
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("灵敏度", style = IosType.body, color = colors.label)
                    Spacer(Modifier.padding(top = 8.dp))
                    IosSegmentedControl(
                        options = Sensitivity.entries.map { it.label },
                        selectedIndex = Sensitivity.entries.indexOf(state.sensitivity),
                        onSelect = { onSetSensitivity(Sensitivity.entries[it]) },
                        tags = Sensitivity.entries.map { "sensitivity_${it.name}" },
                    )
                }
                IosDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("识别模式", style = IosType.body, color = colors.label)
                    Spacer(Modifier.padding(top = 8.dp))
                    IosSegmentedControl(
                        options = DetectionMode.entries.map { it.label },
                        selectedIndex = DetectionMode.entries.indexOf(state.detectionMode),
                        onSelect = { onSetDetectionMode(DetectionMode.entries[it]) },
                        tags = DetectionMode.entries.map { "mode_${it.name}" },
                    )
                }
            }

            IosSection(header = "基准音 A4", footer = "范围 430–450 Hz。大多数乐器使用 440 Hz。") {
                A4Editor(
                    value = state.a4.toFloat(),
                    onChange = { onSetA4(it.toDouble()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            IosSection(header = "音名显示", footer = "黑键音名使用升号（如 C♯）还是降号（如 D♭）。") {
                Column(Modifier.padding(16.dp)) {
                    IosSegmentedControl(
                        options = listOf("升号 C♯ F♯", "降号 D♭ G♭"),
                        selectedIndex = if (state.useFlats) 1 else 0,
                        onSelect = { onSetUseFlats(it == 1) },
                        tags = listOf("sharps", "flats"),
                    )
                }
            }

            IosSection(
                header = "调准精度",
                footer = "偏差在所选范围内即显示“已调准”。±2 适合录音、演出前精调；±10 适合快速粗调。",
            ) {
                Column(Modifier.padding(16.dp)) {
                    IosSegmentedControl(
                        options = TuneTolerance.entries.map { it.label },
                        selectedIndex = TuneTolerance.entries.indexOf(state.tolerance),
                        onSelect = { onSetTolerance(TuneTolerance.entries[it]) },
                        tags = TuneTolerance.entries.map { "tolerance_${it.name}" },
                    )
                }
            }

            IosSection(header = "调准反馈") {
                IosRow(
                    title = "调准时提示音",
                    subtitle = "某根弦调准时发出清脆的“叮”",
                    trailing = {
                        IosSwitch(
                            checked = state.chimeEnabled,
                            onCheckedChange = onSetChime,
                            modifier = Modifier.testTag("chimeSwitch"),
                        )
                    },
                )
                IosDivider()
                IosRow(
                    title = "调准时振动",
                    subtitle = "某根弦调准时手机轻振一下",
                    trailing = {
                        IosSwitch(
                            checked = state.hapticsEnabled,
                            onCheckedChange = onSetHaptics,
                            modifier = Modifier.testTag("hapticsSwitch"),
                        )
                    },
                )
            }

            IosSection(header = "使用小贴士") {
                Tip("调音页右上角的“引导”可以从 6 弦开始逐根调音，调准一根自动跳到下一根。")
                IosDivider()
                Tip("把手机放在离吉他 20–50 厘米的地方，每次清晰地拨一根弦。")
                IosDivider()
                Tip("每次只拨一根弦，让它自然延音，指针稳定后再拧弦钮。")
                IosDivider()
                Tip("从低于目标音的位置往上调，音更稳定。")
                IosDivider()
                Tip("长按琴弦可试听参考音，试听期间会暂停识别。")
                IosDivider()
                Tip("在“更改调弦”里可以新建预设，为每根弦选择任意音高。")
            }

            IosSection(header = "关于") {
                IosRow(title = "Guitar Magic", value = "版本 ${BuildConfig.VERSION_NAME}")
                IosDivider()
                IosRow(title = "音高识别", value = "YIN 算法 · 离线")
                IosDivider()
                IosRow(title = "隐私", value = "不录音、不上传")
            }
        }
      }
    }
}

@Composable
private fun Tip(text: String) {
    val colors = LocalIosColors.current
    Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text("•", style = IosType.subheadline.copy(fontWeight = FontWeight.Bold), color = colors.blue)
        Spacer(Modifier.width(10.dp))
        Text(text, style = IosType.subheadline, color = colors.label)
    }
}
