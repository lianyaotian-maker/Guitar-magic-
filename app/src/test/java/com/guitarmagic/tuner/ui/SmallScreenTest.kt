package com.guitarmagic.tuner.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.guitarmagic.tuner.TunerUiState
import com.guitarmagic.tuner.pitch.MusicTheory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 小屏手机上调音页的所有琴弦都必须能看到、能点到（不会被截断）。 */
@RunWith(RobolectricTestRunner::class)
class SmallScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(onToggle: (Int) -> Unit) {
        rule.setContent {
            TunerTheme {
                TunerContent(
                    state = TunerUiState(listening = true, frequency = MusicTheory.midiToFrequency(40) * 0.97, playingToneMidi = null),
                    onOpenTunings = {}, onSetA4 = {}, onToggleString = onToggle, onPlayString = {},
                    onRetry = {}, onResetProgress = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun tinyScreen_allStringsReachable() {
        var toggled = -1
        render { toggled = it }
        rule.onNodeWithTag("string5").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(5, toggled)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun compactScreen_allStringsVisible() {
        var toggled = -1
        render { toggled = it }
        rule.onNodeWithTag("string0").assertIsDisplayed()
        rule.onNodeWithTag("string5").assertIsDisplayed().performClick()
        assertEquals(5, toggled)
        rule.onNodeWithTag("tuningButton").assertIsDisplayed()
    }
}
