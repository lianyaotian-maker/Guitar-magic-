package com.guitarmagic.tuner.core

import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StringAssignerTest {

    private val a4 = 440.0
    private fun f(midi: Int) = MusicTheory.midiToFrequency(midi, a4)
    private val openC = Tuning("custom_c", "Open C", listOf(36, 43, 48, 55, 60, 64), isCustom = true)

    @Test
    fun standardTuning_freshStart_picksNearestString() {
        val a = StringAssigner()
        Tuning.STANDARD.strings.forEachIndexed { i, m ->
            assertEquals(i, a.assign(f(m) * 1.01, noteId = i.toLong(), tuning = Tuning.STANDARD, a4 = a4))
        }
    }

    @Test
    fun switchingToOpenC_doesNotConfuseStrings() {
        // 吉他仍在标准调弦；切换到 Open C 后逐根拨弦，每根都应认作对应的弦
        val a = StringAssigner()
        a.onTuningChanged(previous = Tuning.STANDARD, a4 = a4)
        Tuning.STANDARD.strings.forEachIndexed { i, m ->
            assertEquals("${6 - i}弦", i, a.assign(f(m), noteId = 100L + i, tuning = openC, a4 = a4))
        }
    }

    @Test
    fun switchingToHalfStepDown_andDropD_keepStringIdentity() {
        for (target in listOf(Tuning.HALF_STEP_DOWN, Tuning.DROP_D, Tuning.DADGAD, Tuning.OPEN_G)) {
            val a = StringAssigner()
            a.onTuningChanged(previous = Tuning.STANDARD, a4 = a4)
            Tuning.STANDARD.strings.forEachIndexed { i, m ->
                assertEquals("${target.displayName} ${6 - i}弦", i, a.assign(f(m), 200L + i, target, a4))
            }
        }
    }

    @Test
    fun tuningDownGradually_staysOnSameString_acrossPlucks() {
        // 6 弦从 E2 一步步调到 C2（每次重新拨弦），途中不能被认成别的弦
        val a = StringAssigner()
        a.onTuningChanged(previous = Tuning.STANDARD, a4 = a4)
        var note = 0L
        var cents = 0.0
        while (cents >= -400.0) {
            val freq = f(40) * Math.pow(2.0, cents / 1200)
            assertEquals("cents=$cents", 0, a.assign(freq, note++, openC, a4))
            cents -= 50.0
        }
        assertTrue(abs(MusicTheory.cents(a.remembered(0)!!, f(36))) < 1.0)
    }

    @Test
    fun withinOneNote_assignmentIsSticky() {
        val a = StringAssigner()
        assertEquals(1, a.assign(f(45), noteId = 7, tuning = Tuning.STANDARD, a4 = a4))
        // 同一次发声中音高被拧到靠近 D3，也不改变
        assertEquals(1, a.assign(f(49), noteId = 7, tuning = Tuning.STANDARD, a4 = a4))
        // 新的拨弦才重新判断
        assertEquals(2, a.assign(f(50), noteId = 8, tuning = Tuning.STANDARD, a4 = a4))
    }

    @Test
    fun lockedObservation_updatesMemory_andReset_clears() {
        val a = StringAssigner()
        a.observeLocked(3, 190.0, noteId = 1)
        assertEquals(190.0, a.remembered(3)!!, 1e-9)
        a.reset()
        assertNull(a.remembered(3))
    }
}

class TuneProgressExtrasTest {
    @Test
    fun toleranceParameter_andNextUntuned() {
        val p = TuneProgress(requiredFrames = 1)
        assertNull(p.update(0, 4.0, tolerance = 2.0))
        assertEquals(0, p.update(0, 1.5, tolerance = 2.0))
        assertEquals(1, p.nextUntuned(0))
        p.update(1, 0.0)
        assertEquals(2, p.nextUntuned(1))
        for (i in 2..5) p.update(i, 0.0)
        assertNull(p.nextUntuned(0))
        assertEquals(9.9, TuneTolerance.RELAXED.cents - 0.1, 1e-9)
    }
}

class ChimeTest {
    @Test
    fun chime_isShortBoundedAndFades() {
        val c = ToneSynth.chime(44100)
        assertTrue(c.size in 10000..20000)
        assertTrue(c.all { abs(it) <= 0.46f })
        assertEquals(0f, c.last(), 1e-3f)
        assertTrue(c.maxOf { abs(it) } > 0.3f)
    }
}
