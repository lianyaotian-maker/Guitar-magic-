package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTrackerTest {

    @Test
    fun belowAbsoluteMinimum_isGated() {
        val tracker = PitchTracker()
        // 约 -89 dBFS，低于绝对下限
        val out = tracker.process(TestSignals.sine(440.0, amplitude = 0.00005), 0)
        assertNull(out.frequency)
    }

    @Test
    fun quietSignal_inQuietRoom_isDetected() {
        val tracker = PitchTracker()
        // 约 -66 dBFS：旧版固定门限（-55 dBFS）会挡掉，新版应能识别
        var out: PitchTracker.Output? = null
        repeat(3) { out = tracker.process(TestSignals.sine(440.0, amplitude = 0.0007), it * 46L) }
        assertEquals(440.0, out!!.frequency!!, 0.3)
    }

    @Test
    fun noiseFloor_adaptsUpAndDown() {
        val tracker = PitchTracker()
        var t = 0L
        repeat(100) { tracker.process(TestSignals.whiteNoise(0.01, seed = it), t); t += 46 } // 约 -45 dBFS 的噪声
        assertTrue("floor=${tracker.noiseFloorDb}", tracker.noiseFloorDb > -50)
        repeat(20) { tracker.process(TestSignals.whiteNoise(0.0001, seed = it), t); t += 46 } // 安静下来
        assertTrue("floor=${tracker.noiseFloorDb}", tracker.noiseFloorDb < -80)
    }

    @Test
    fun loudSignal_isDetected_thenHeld_thenCleared() {
        val tracker = PitchTracker(holdMillis = 1000)
        val e2 = MusicTheory.midiToFrequency(40)
        var t = 0L
        repeat(6) {
            val out = tracker.process(TestSignals.harmonic(e2, TestSignals.WEAK_FUNDAMENTAL, amplitude = 0.2), t)
            assertNotNull(out.frequency)
            assertTrue(kotlin.math.abs(MusicTheory.cents(out.frequency!!, e2)) < 1.0)
            t += 46
        }
        val silence = FloatArray(TestSignals.FRAME)
        val held = tracker.process(silence, t + 500)
        assertTrue(held.holding)
        assertEquals(e2, held.frequency!!, 0.1)
        assertNull(tracker.process(silence, t + 1500).frequency)
    }

    @Test
    fun noise_isNotReported() {
        val tracker = PitchTracker()
        for (i in 0 until 10) {
            assertNull(tracker.process(TestSignals.whiteNoise(0.1, seed = i), i * 46L).frequency)
        }
    }
}
