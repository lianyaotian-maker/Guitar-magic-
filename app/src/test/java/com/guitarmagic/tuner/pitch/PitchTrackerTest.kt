package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchTrackerTest {

    private val e2 = MusicTheory.midiToFrequency(40)

    @Test
    fun belowAbsoluteMinimum_isGated() {
        val tracker = PitchTracker()
        repeat(5) {
            assertNull(tracker.process(TestSignals.sine(440.0, amplitude = 0.00005), it * 46L).frequency)
        }
    }

    @Test
    fun veryQuietTone_isIgnored() {
        // 约 -66 dBFS：低于中等灵敏度的触发下限（-64 dBFS）
        val tracker = PitchTracker()
        repeat(10) {
            assertNull(tracker.process(TestSignals.sine(440.0, amplitude = 0.0007), it * 46L).frequency)
        }
    }

    @Test
    fun pluck_isConfirmedAfterThreeStableFrames() {
        val tracker = PitchTracker()
        val frame = TestSignals.harmonic(e2, TestSignals.WEAK_FUNDAMENTAL, amplitude = 0.2)
        assertNull(tracker.process(frame, 0).frequency)
        assertNull(tracker.process(frame, 46).frequency)
        val out = tracker.process(frame, 92)
        assertNotNull(out.frequency)
        assertTrue(out.tracking)
        assertTrue(abs(MusicTheory.cents(out.frequency!!, e2)) < 1.0)
    }

    @Test
    fun reading_isHeldAfterSilence_thenCleared() {
        val tracker = PitchTracker(holdMillis = 1000)
        val frame = TestSignals.harmonic(e2, TestSignals.WEAK_FUNDAMENTAL, amplitude = 0.2)
        var t = 0L
        repeat(6) {
            tracker.process(frame, t)
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
        for (i in 0 until 30) {
            assertNull(tracker.process(TestSignals.whiteNoise(0.1, seed = i), i * 46L).frequency)
        }
    }

    @Test
    fun outOfRange_isIgnored() {
        val tracker = PitchTracker()
        val frame = TestSignals.harmonic(e2, TestSignals.WEAK_FUNDAMENTAL, amplitude = 0.2)
        repeat(6) {
            assertNull(tracker.process(frame, it * 46L, minHz = 100.0, maxHz = 500.0).frequency)
        }
    }

    @Test
    fun noiseFloor_adaptsUpAndDown() {
        val tracker = PitchTracker()
        var t = 0L
        repeat(300) { tracker.process(TestSignals.whiteNoise(0.01, seed = it), t); t += 46 } // 约 -45 dBFS 的噪声
        assertTrue("floor=${tracker.noiseFloorDb}", tracker.noiseFloorDb > -50)
        repeat(30) { tracker.process(TestSignals.whiteNoise(0.0001, seed = it), t); t += 46 } // 安静下来
        assertTrue("floor=${tracker.noiseFloorDb}", tracker.noiseFloorDb < -80)
    }
}
