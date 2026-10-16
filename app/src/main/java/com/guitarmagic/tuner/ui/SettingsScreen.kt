package com.guitarmagic.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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

@Composable
fun SettingsTab(viewModel: TunerViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onSetA4 = viewModel::setA4,
        onSetUseFlats = viewModel::setUseFlats,
        onSetHaptics = viewModel::setHapticsEnabled,
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SettingsCard {
            CardTitle("基准音 A4")
            A4Editor(
                value = state.a4.toFloat(),
                onChange = { onSetA4(it.toDouble()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsCard {
            CardTitle("音名显示")
            Text(
                "黑键音名使用升号还是降号，例如 C♯ 或 D♭",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Pill("升号  C♯ F♯", selected = !state.useFlats, onClick = { onSetUseFlats(false) }, modifier = Modifier.weight(1f).testTag("sharps"))
                Pill("降号  D♭ G♭", selected = state.useFlats, onClick = { onSetUseFlats(true) }, modifier = Modifier.weight(1f).testTag("flats"))
            }
        }

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardTitle("调准时振动")
                    Text(
                        "某根弦调准时手机轻振一下",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.hapticsEnabled, onCheckedChange = onSetHaptics)
            }
        }

        SettingsCard {
            CardTitle("使用小贴士")
            Tip("把手机放在离吉他 20–50 厘米的地方，环境尽量安静。")
            Tip("每次只拨一根弦，让它自然延音，指针稳定后再拧弦钮。")
            Tip("从低于目标音的位置往上调（先调低再调紧），音更稳定。")
            Tip("长按下方的琴弦可以试听标准参考音。")
            Tip("在“调弦”菜单里可以新建自己的预设，为每根弦选择任意音高。")
        }

        SettingsCard {
            CardTitle("关于")
            Text(
                "吉他调音器 v${BuildConfig.VERSION_NAME}\n" +
                    "完全离线运行，音高识别使用自研 YIN 算法，不收集任何数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Tip(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•  ", color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
