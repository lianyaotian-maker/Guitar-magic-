package com.guitarmagic.tuner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.guitarmagic.tuner.MetronomeUiState
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.metronome.MetronomeConfig
import com.guitarmagic.tuner.metronome.MetronomeEngine
import com.guitarmagic.tuner.metronome.Subdivision
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 在 JVM 上渲染各个界面并模拟操作，确保不会崩溃、交互正确。 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class UiSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private val e2 = MusicTheory.midiToFrequency(40)

    @Test
    fun tuner_showsNoteAndInTuneStatus() {
        rule.setContent {
            TunerTheme {
                TunerContent(
                    state = TunerUiState(listening = true, frequency = e2),
                    onOpenTunings = {}, onSetA4 = {}, onToggleString = {}, onPlayString = {},
                    onRetry = {}, onResetProgress = {},
                )
            }
        }
        rule.onNodeWithTag("noteLabel", useUnmergedTree = true).assertTextEquals("E2")
        rule.onNodeWithTag("status").assertIsDisplayed()
        rule.onNodeWithText("✓ 已调准").assertIsDisplayed()
        rule.onNodeWithText("82.4 Hz").assertIsDisplayed()
        rule.onNodeWithText("标准调弦").assertIsDisplayed()
    }

    @Test
    fun tuner_idleAndOffTuneStates_andStringInteractions() {
        var toggled = -1
        var played = -1
        var state by mutableStateOf(TunerUiState(listening = true))
        rule.setContent {
            TunerTheme {
                TunerContent(
                    state = state,
                    onOpenTunings = {}, onSetA4 = {},
                    onToggleString = { toggled = it }, onPlayString = { played = it },
                    onRetry = {}, onResetProgress = {},
                )
            }
        }
        rule.onNodeWithText("请弹奏一根琴弦").assertIsDisplayed()

        // 偏高 20 音分
        state = state.copy(frequency = e2 * Math.pow(2.0, 20.0 / 1200))
        rule.waitForIdle()
        rule.onNodeWithText("偏高 · 请调松").assertIsDisplayed()

        // 低了两个半音：应提示 6 弦偏低
        state = state.copy(frequency = MusicTheory.midiToFrequency(38))
        rule.waitForIdle()
        rule.onNodeWithText("6弦 E2 偏低，请调紧", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("string2").performClick()
        assertEquals(2, toggled)
        rule.onNodeWithTag("string5").performTouchInput { longClick() }
        assertEquals(5, played)

        // 锁定状态 + 全部调准横幅 + 降号显示
        state = state.copy(lockedString = 0, tunedStrings = (0..5).toSet(), useFlats = true, tuning = Tuning.HALF_STEP_DOWN)
        rule.waitForIdle()
        rule.onNodeWithText("🎉 6 根弦全部调准").assertIsDisplayed()
        rule.onNodeWithText("E♭2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tuner_pausedWhilePlayingToneOrMetronome() {
        var stoppedTone = 0
        var stoppedMetronome = 0
        var state by mutableStateOf(TunerUiState(listening = true, playingToneMidi = 40))
        rule.setContent {
            TunerTheme {
                TunerContent(
                    state = state,
                    onOpenTunings = {}, onSetA4 = {}, onToggleString = {}, onPlayString = {},
                    onRetry = {}, onResetProgress = {},
                    onStopTone = { stoppedTone++ },
                    onStopMetronome = { stoppedMetronome++ },
                )
            }
        }
        rule.onNodeWithText("正在播放参考音 E2", substring = true).assertIsDisplayed()
        rule.onNodeWithText("识别已暂停").assertIsDisplayed()
        rule.onNodeWithText("停止").performClick()
        assertEquals(1, stoppedTone)

        state = state.copy(playingToneMidi = null, metronomeRunning = true)
        rule.waitForIdle()
        rule.onNodeWithText("节拍器运行中，调音已暂停").assertIsDisplayed()
        rule.onNodeWithText("停止节拍器").performClick()
        assertEquals(1, stoppedMetronome)

        state = state.copy(metronomeRunning = false)
        rule.waitForIdle()
        rule.onNodeWithText("请弹奏一根琴弦").assertIsDisplayed()
    }

    @Test
    fun tuner_a4Dialog() {
        var a4 = 0.0
        rule.setContent {
            TunerTheme {
                TunerContent(
                    state = TunerUiState(listening = true),
                    onOpenTunings = {}, onSetA4 = { a4 = it }, onToggleString = {}, onPlayString = {},
                    onRetry = {}, onResetProgress = {},
                )
            }
        }
        rule.onNodeWithTag("a4Button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("基准音 A4").assertIsDisplayed()
        rule.onNodeWithTag("a4Plus").performClick()
        rule.onNodeWithTag("a4Plus").performClick()
        rule.onNodeWithText("确定").performClick()
        assertEquals(442.0, a4, 1e-9)
    }

    @Test
    fun tuningSheet_createPresetFlow() {
        var saved: Triple<String?, String, List<Int>>? = null
        rule.setContent {
            TunerTheme {
                TuningSheetContent(
                    state = TunerUiState(),
                    onSelect = {},
                    onSave = { id, name, strings ->
                        saved = Triple(id, name, strings)
                        true
                    },
                    onDelete = {},
                    onPreview = {},
                    defaultName = { "我的调弦 1" },
                )
            }
        }
        rule.onNodeWithText("标准调弦").assertIsDisplayed()
        rule.onNodeWithTag("createPreset").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("presetName").performTextClearance()
        rule.onNodeWithTag("savePreset").assertIsNotEnabled()
        rule.onNodeWithTag("presetName").performTextInput("Open D")
        rule.onNodeWithTag("savePreset").assertIsEnabled()

        // 打开 1 弦的音高选择器，改成 D4
        rule.onNodeWithTag("editString5").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("1弦音高").assertIsDisplayed()
        rule.onNodeWithText("D").performClick()
        rule.onNodeWithText("确定").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("savePreset").performClick()
        val result = saved!!
        assertEquals(null, result.first)
        assertEquals("Open D", result.second)
        assertEquals(listOf(40, 45, 50, 55, 59, 62), result.third)
    }

    @Test
    fun tuningSheet_editAndDeleteCustomPreset() {
        var deleted: String? = null
        val custom = Tuning("custom_1", "我的 Open D", listOf(38, 45, 50, 54, 57, 62), isCustom = true)
        rule.setContent {
            TunerTheme {
                TuningSheetContent(
                    state = TunerUiState(customTunings = listOf(custom), tuning = custom),
                    onSelect = {}, onSave = { _, _, _ -> true },
                    onDelete = { deleted = it }, onPreview = {}, defaultName = { "x" },
                )
            }
        }
        rule.onNodeWithText("我的 Open D").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("编辑").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("编辑预设").assertIsDisplayed()

        rule.onNodeWithTag("deletePreset").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("确定删除", substring = true).assertIsDisplayed()
        // 对话框里的“取消”是最后一个
        rule.onAllNodesWithText("取消").onLast().performClick()
        rule.waitForIdle()
        assertEquals(null, deleted)

        rule.onNodeWithTag("deletePreset").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("删除").performClick()
        rule.waitForIdle()
        assertEquals("custom_1", deleted)
        // 删除后回到列表
        rule.onNodeWithText("选择调弦").assertIsDisplayed()
    }

    @Test
    fun metronome_controls() {
        var toggles = 0
        var delta = 0
        var beats = 0
        var sub: Subdivision? = null
        var taps = 0
        var accent: Boolean? = null
        var state by mutableStateOf(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        val visual = MetronomeEngine.Visual(beat = 1, sub = 0, beatSerial = 5, beatProgress = 0.3f, secondsSinceTick = 0.02f)
        rule.setContent {
            TunerTheme {
                MetronomeContent(
                    state = state,
                    visualProvider = { visual },
                    onToggle = { toggles++ },
                    onChangeBpm = { delta += it },
                    onSetBpm = {},
                    onSetBeats = { beats = it },
                    onSetSubdivision = { sub = it },
                    onSetAccent = { accent = it },
                    onTap = { taps++ },
                )
            }
        }
        rule.onNodeWithText("120").assertIsDisplayed()
        rule.onNodeWithText("Allegro 快板").assertIsDisplayed()
        rule.onNodeWithTag("bpmPlus").performClick()
        rule.onNodeWithTag("bpmMinus").performClick()
        rule.onNodeWithTag("bpmPlus").performClick()
        assertEquals(1, delta)
        rule.onNodeWithTag("startStop").performScrollTo().performClick()
        assertEquals(1, toggles)
        rule.onNodeWithTag("tap").performScrollTo().performClick()
        assertEquals(1, taps)
        rule.onNodeWithText("三连音").performScrollTo().performClick()
        assertEquals(Subdivision.TRIPLET, sub)
        rule.onNodeWithTag("beatsPlus").performScrollTo().performClick()
        assertEquals(5, beats)
        rule.onNodeWithText("第一拍重音").performScrollTo()
        rule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        assertEquals(false, accent)

        // 切换到播放状态（摆锤动画、拍点）不应崩溃
        state = state.copy(running = true, config = state.config.copy(bpm = 200, beatsPerBar = 12))
        rule.waitForIdle()
        rule.onNodeWithText("停止").assertIsDisplayed()
        state = state.copy(running = false)
        rule.waitForIdle()
        rule.onNodeWithText("开始").assertIsDisplayed()
    }

    @Test
    fun settings_controls() {
        var flats: Boolean? = null
        var haptics: Boolean? = null
        var a4 = 0.0
        rule.setContent {
            TunerTheme {
                SettingsContent(
                    state = TunerUiState(a4 = 440.0),
                    onSetA4 = { a4 = it },
                    onSetUseFlats = { flats = it },
                    onSetHaptics = { haptics = it },
                )
            }
        }
        rule.onNodeWithTag("flats").performScrollTo().performClick()
        assertEquals(true, flats)
        rule.onNodeWithTag("a4Plus").performScrollTo().performClick()
        assertEquals(441.0, a4, 1e-9)
        rule.onNodeWithText("恢复默认 440 Hz").performScrollTo().performClick()
        assertEquals(440.0, a4, 1e-9)
        rule.onNode(androidx.compose.ui.test.isToggleable()).performScrollTo().performClick()
        assertEquals(false, haptics)
        assertTrue(true)
    }

    @Test
    fun darkTheme_rendersAllScreens() {
        var screen by mutableStateOf(0)
        rule.setContent {
            TunerTheme(darkTheme = true) {
                when (screen) {
                    0 -> TunerContent(
                        state = TunerUiState(listening = true, frequency = e2 * 1.01),
                        onOpenTunings = {}, onSetA4 = {}, onToggleString = {}, onPlayString = {},
                        onRetry = {}, onResetProgress = {},
                    )
                    1 -> MetronomeContent(
                        state = MetronomeUiState(), visualProvider = { null }, onToggle = {}, onChangeBpm = {},
                        onSetBpm = {}, onSetBeats = {}, onSetSubdivision = {}, onSetAccent = {}, onTap = {},
                    )
                    else -> SettingsContent(state = TunerUiState(), onSetA4 = {}, onSetUseFlats = {}, onSetHaptics = {})
                }
            }
        }
        rule.onNodeWithText("调音").assertIsDisplayed()
        screen = 1
        rule.waitForIdle()
        rule.onNodeWithText("节拍器").assertIsDisplayed()
        screen = 2
        rule.waitForIdle()
        rule.onNodeWithText("设置").assertIsDisplayed()
    }
}
