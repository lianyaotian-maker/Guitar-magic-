package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTrackerTest {

    @Test
    fun quietSignal_isGated() {
        val tracker = PitchTracker()
        // 约 -66 dBFS 的 A4，低于噪声门
        val out = tracker.process(TestSignals.sine(440.0, amplitude = 0.0007), 0)
        assertNull(out.frequency)
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
