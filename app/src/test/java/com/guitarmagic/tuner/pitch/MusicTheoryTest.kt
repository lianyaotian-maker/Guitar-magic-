package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTheoryTest {

    @Test
    fun scientificPitchNotation() {
        assertEquals("A4", MusicTheory.label(69))
        assertEquals("C4", MusicTheory.label(60))
        assertEquals("E2", MusicTheory.label(40))
        assertEquals("B3", MusicTheory.label(59))
        assertEquals("C♯5", MusicTheory.label(73))
        assertEquals("B-1", MusicTheory.label(11))
    }

    @Test
    fun openStringFrequencies_at440() {
        val expected = listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)
        Tuning.STANDARD.frequencies(440.0).zip(expected).forEach { (actual, exp) ->
            assertEquals(exp, actual, 0.001)
        }
    }

    @Test
    fun openStringFrequencies_at432() {
        val f = Tuning.STANDARD.frequencies(432.0)
        assertEquals(80.9086, f[0], 0.001)
        assertEquals(108.0, f[1], 1e-9)
        assertEquals(323.6343, f[5], 0.001)
    }

    @Test
    fun nearestNote_andCents() {
        val note = MusicTheory.nearestNote(445.0, 440.0)
        assertEquals("A4", note.label)
        assertEquals(19.56, note.cents, 0.01)
        assertEquals(440.0, note.frequency, 1e-9)

        val low = MusicTheory.nearestNote(81.0, 440.0)
        assertEquals("E2", low.label)
        assertEquals(-29.8, low.cents, 0.1)

        // 恰好在两个半音中间附近时，偏差在 ±50 音分以内
        val mid = MusicTheory.nearestNote(440.0 * Math.pow(2.0, 0.49 / 12), 440.0)
        assertEquals("A4", mid.label)
        assertEquals(49.0, mid.cents, 0.01)
    }

    @Test
    fun nearestNote_respectsReference() {
        val note = MusicTheory.nearestNote(432.0, 432.0)
        assertEquals("A4", note.label)
        assertEquals(0.0, note.cents, 1e-9)
        // 440Hz 在 A4=432 基准下偏高约 31.8 音分
        assertEquals(31.77, MusicTheory.nearestNote(440.0, 432.0).cents, 0.01)
    }

    @Test
    fun tunings_haveExpectedNotes() {
        fun labels(t: Tuning) = t.strings.joinToString(" ") { MusicTheory.label(it) }
        assertEquals("E2 A2 D3 G3 B3 E4", labels(Tuning.STANDARD))
        assertEquals("D2 A2 D3 G3 B3 E4", labels(Tuning.DROP_D))
        assertEquals("D♯2 G♯2 C♯3 F♯3 A♯3 D♯4", labels(Tuning.HALF_STEP_DOWN))
        assertEquals("D2 G2 D3 G3 B3 D4", labels(Tuning.OPEN_G))
        assertEquals("D2 A2 D3 G3 A3 D4", labels(Tuning.DADGAD))
    }

    @Test
    fun closestString() {
        val t = Tuning.STANDARD
        assertEquals(0, t.closestString(80.0, 440.0))
        assertEquals(1, t.closestString(112.0, 440.0))
        assertEquals(4, t.closestString(250.0, 440.0))
        assertEquals(5, t.closestString(500.0, 440.0))
    }
}
