package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchSmootherTest {

    @Test
    fun singleOctaveOutlier_isIgnored() {
        val s = PitchSmoother()
        repeat(5) { s.add(82.41) }
        val out = s.add(164.82) // 偶发的二次泛音误判
        assertEquals(82.41, out, 0.01)
        assertEquals(82.41, s.add(82.41), 0.01)
    }

    @Test
    fun jitter_isSmoothedByMedian() {
        val s = PitchSmoother()
        val inputs = listOf(110.0, 110.3, 109.6, 110.1, 125.0, 109.9, 110.2)
        var last = 0.0
        inputs.forEach { last = s.add(it) }
        assertEquals(110.0, last, 0.25)
    }

    @Test
    fun sustainedNoteChange_isFollowed() {
        val s = PitchSmoother()
        repeat(5) { s.add(82.41) }
        var out = 0.0
        repeat(3) { out = s.add(110.0) }
        assertEquals(110.0, out, 0.01)
    }

    @Test
    fun reset_clearsHistory() {
        val s = PitchSmoother()
        repeat(5) { s.add(82.41) }
        s.reset()
        assertEquals(196.0, s.add(196.0), 1e-9)
    }
}
