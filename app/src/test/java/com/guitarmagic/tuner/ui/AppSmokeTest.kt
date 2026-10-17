package com.guitarmagic.tuner.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.guitarmagic.tuner.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 启动真实的 MainActivity（真实 ViewModel），在各个页面间切换并操作，确保整体不崩溃。 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun withPermission_navigateAllTabsAndUseFeatures() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithTag("tuningButton").assertIsDisplayed()

            // 打开调弦面板 → 新建预设 → 保存
            rule.onNodeWithTag("tuningButton").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("选择调弦").assertIsDisplayed()
            rule.onNodeWithTag("createPreset").performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("savePreset").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("我的调弦 1").assertIsDisplayed()

            // 节拍器：开始、停止
            rule.onNodeWithTag("tab_METRONOME").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("startStop").performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("bpmPlus").performScrollTo().performClick()
            rule.onNodeWithTag("startStop").performScrollTo().performClick()
            rule.waitForIdle()

            // 设置：切换降号
            rule.onNodeWithTag("tab_SETTINGS").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("flats").performScrollTo().performClick()
            rule.waitForIdle()

            // 回到调音页
            rule.onNodeWithTag("tab_TUNER").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("string0").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("已锁定 6 弦", substring = true).assertIsDisplayed()

            // 长按试听：显示“暂停识别”提示，可以提前停止
            rule.onNodeWithTag("string1").performTouchInput { longClick() }
            rule.waitForIdle()
            rule.onNodeWithText("正在播放参考音", substring = true).assertIsDisplayed()
            rule.onNodeWithText("停止").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("请弹奏一根琴弦").assertIsDisplayed()
        }
    }

    @Test
    fun withoutPermission_showsGuide_otherTabsStillWork() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForIdle()
            rule.onNodeWithText("需要使用麦克风").assertIsDisplayed()
            rule.onNodeWithTag("tab_METRONOME").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("startStop").performScrollTo().assertIsDisplayed()
        }
    }
}
