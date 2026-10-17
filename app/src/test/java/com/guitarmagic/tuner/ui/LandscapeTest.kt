package com.guitarmagic.tuner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.guitarmagic.tuner.MetronomeUiState
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.pitch.MusicTheory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 横屏手机与平板上各页面都能正常显示和操作。 */
@RunWith(RobolectricTestRunner::class)
class LandscapeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun phoneLandscape_tunerAndMetronome() {
        var toggled = -1
        var screen by androidx.compose.runtime.mutableStateOf(0)
        rule.setContent {
            TunerTheme {
                if (screen == 0) {
                    TunerContent(
                        state = TunerUiState(listening = true, frequency = MusicTheory.midiToFrequency(45) * 1.004),
                        onOpenTunings = {}, onSetA4 = {}, onToggleString = { toggled = it }, onPlayString = {},
                        onRetry = {}, onResetProgress = {},
                    )
                } else {
                    MetronomeContent(
                        state = MetronomeUiState(), visualProvider = { null }, onToggle = {}, onChangeBpm = {},
                        onSetBpm = {}, onSetBeats = {}, onSetSubdivision = {}, onSetAccent = {}, onTap = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("tuningButton").assertIsDisplayed()
        rule.onNodeWithTag("noteLabel", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("string5").performScrollTo().performClick()
        assertEquals(5, toggled)
        screen = 1
        rule.waitForIdle()
        rule.onNodeWithTag("startStop").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("速度训练").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletPortrait_rendersCenteredContent() {
        rule.setContent {
            TunerTheme {
                TunerContent(
                    state = TunerUiState(listening = true),
                    onOpenTunings = {}, onSetA4 = {}, onToggleString = {}, onPlayString = {},
                    onRetry = {}, onResetProgress = {},
                )
            }
        }
        rule.onNodeWithTag("string0").assertIsDisplayed()
        rule.onNodeWithTag("string5").assertIsDisplayed()
        rule.onNodeWithText("调音").assertIsDisplayed()
    }
}
