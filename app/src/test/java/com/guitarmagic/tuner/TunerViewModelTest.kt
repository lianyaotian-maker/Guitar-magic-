package com.guitarmagic.tuner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.guitarmagic.tuner.pitch.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class TunerViewModelTest {

    private fun newViewModel() = TunerViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun playingTone_pausesRecognition_untilStopped() {
        val vm = newViewModel()
        vm.playTone(40)
        assertEquals(40, vm.state.value.playingToneMidi)
        assertTrue(vm.state.value.paused)
        assertNull(vm.state.value.frequency)
        vm.stopTone()
        assertNull(vm.state.value.playingToneMidi)
        assertFalse(vm.state.value.paused)
    }

    @Test
    fun playingTone_endsAutomatically() {
        val vm = newViewModel()
        vm.playTone(45)
        ShadowLooper.idleMainLooper(5, java.util.concurrent.TimeUnit.SECONDS)
        assertNull(vm.state.value.playingToneMidi)
    }

    @Test
    fun metronome_pausesRecognition() {
        val vm = newViewModel()
        vm.setMetronomeRunning(true)
        assertTrue(vm.state.value.paused)
        vm.setMetronomeRunning(false)
        assertFalse(vm.state.value.paused)
    }

    @Test
    fun customTuning_saveSelectEditDelete_persists() {
        val vm = newViewModel()
        val saved = vm.saveCustomTuning(null, "Open D", listOf(38, 45, 50, 54, 57, 62))
        assertNotNull(saved)
        assertEquals(saved, vm.state.value.tuning)
        // 新的 ViewModel（模拟重启 App）能读到保存的预设与选择
        val reloaded = newViewModel()
        assertEquals(listOf(saved), reloaded.state.value.customTunings)
        assertEquals(saved!!.id, reloaded.state.value.tuning.id)

        val edited = vm.saveCustomTuning(saved.id, "Open D2", listOf(38, 45, 50, 54, 57, 64))
        assertEquals("Open D2", vm.state.value.tuning.displayName)
        assertEquals(edited, vm.state.value.customTunings.single())

        vm.deleteCustomTuning(saved.id)
        assertTrue(vm.state.value.customTunings.isEmpty())
        assertEquals(Tuning.STANDARD, vm.state.value.tuning)
    }

    @Test
    fun invalidCustomTuning_isRejected() {
        val vm = newViewModel()
        assertNull(vm.saveCustomTuning(null, "  ", listOf(38, 45, 50, 54, 57, 62)))
        assertNull(vm.saveCustomTuning(null, "x", listOf(1, 45, 50, 54, 57, 62)))
        assertTrue(vm.state.value.customTunings.isEmpty())
    }
}
