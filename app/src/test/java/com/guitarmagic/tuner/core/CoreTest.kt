package com.guitarmagic.tuner.core

import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import com.guitarmagic.tuner.pitch.YinPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CustomTuningsTest {

    private fun custom(id: String, name: String, vararg midi: Int) = Tuning(id, name, midi.toList(), isCustom = true)

    @Test
    fun roundTrip() {
        val list = listOf(
            custom("custom_1", "Open D", 38, 45, 50, 54, 57, 62),
            custom("custom_2", "七弦低音 B", 35, 40, 45, 50, 55, 59),
        )
        assertEquals(list, CustomTunings.decode(CustomTunings.encode(list)))
    }

    @Test
    fun builtInTunings_areNotStored() {
        assertEquals("", CustomTunings.encode(listOf(Tuning.STANDARD)))
    }

    @Test
    fun namesWithTabsAndNewlines_areSanitized() {
        val t = custom("custom_9", "a\tb\nc  d", 40, 45, 50, 55, 59, 64)
        val decoded = CustomTunings.decode(CustomTunings.encode(listOf(t)))
        assertEquals(1, decoded.size)
        assertEquals("a b c d", decoded[0].displayName)
        assertEquals(16, CustomTunings.sanitizeName("一二三四五六七八九十一二三四五六七八").length)
    }

    @Test
    fun corruptData_isSkippedWithoutCrash() {
        val text = listOf(
            "custom_1\tOK\t40,45,50,55,59,64",
            "garbage",
            "custom_2\tbad count\t40,45",
            "custom_3\tout of range\t10,45,50,55,59,64",
            "custom_4\tnot number\tx,45,50,55,59,64",
            "standard\twrong prefix\t40,45,50,55,59,64",
            "custom_1\tduplicate\t40,45,50,55,59,64",
            "",
        ).joinToString("\n")
        val decoded = CustomTunings.decode(text)
        assertEquals(listOf("custom_1"), decoded.map { it.id })
        assertTrue(CustomTunings.decode(null).isEmpty())
        assertTrue(CustomTunings.decode("   ").isEmpty())
    }

    @Test
    fun build_validates() {
        assertNull(CustomTunings.build("custom_1", "   ", listOf(40, 45, 50, 55, 59, 64)))
        assertNull(CustomTunings.build("custom_1", "x", listOf(40, 45, 50)))
        assertNull(CustomTunings.build("custom_1", "x", listOf(20, 45, 50, 55, 59, 64)))
        val ok = CustomTunings.build("custom_1", "  我的 ", listOf(40, 45, 50, 55, 59, 64))
        assertNotNull(ok)
        assertEquals("我的", ok!!.displayName)
        assertTrue(ok.isCustom)
    }

    @Test
    fun defaultNameAndId_areUnique() {
        val existing = listOf(custom("custom_100", "我的调弦 1", 40, 45, 50, 55, 59, 64))
        assertEquals("我的调弦 2", CustomTunings.defaultName(existing))
        assertEquals("custom_101", CustomTunings.newId(existing, 100))
        assertEquals("custom_5", CustomTunings.newId(existing, 5))
    }

    @Test
    fun byId_findsCustom_orFallsBack() {
        val c = custom("custom_7", "x", 40, 45, 50, 55, 59, 64)
        assertEquals(c, Tuning.byId("custom_7", listOf(c)))
        assertEquals(Tuning.STANDARD, Tuning.byId("custom_missing", listOf(c)))
        assertEquals(Tuning.DADGAD, Tuning.byId("dadgad"))
    }

    @Test
    fun stringRange_isDetectable() {
        val detector = YinPitchDetector()
        val low = MusicTheory.midiToFrequency(Tuning.MIN_STRING_MIDI, 430.0)
        val high = MusicTheory.midiToFrequency(Tuning.MAX_STRING_MIDI, 450.0)
        assertTrue(low >= detector.minFrequency)
        assertTrue(high <= detector.maxFrequency)
    }
}

class TuneProgressTest {

    @Test
    fun stringBecomesTuned_afterSustainedFrames() {
        val p = TuneProgress(requiredFrames = 3)
        assertNull(p.update(0, 1.0))
        assertNull(p.update(0, -2.0))
        assertEquals(0, p.update(0, 4.9))
        assertNull(p.update(0, 0.0)) // 已经标记过，不重复返回
        assertEquals(setOf(0), p.tunedStrings)
        assertFalse(p.allTuned)
    }

    @Test
    fun interruptions_resetStreak() {
        val p = TuneProgress(requiredFrames = 3)
        p.update(1, 0.0)
        p.update(1, 0.0)
        p.update(1, 12.0) // 偏了
        p.update(1, 0.0)
        p.update(null, null)
        p.update(1, 0.0)
        p.update(2, 0.0) // 换弦
        assertTrue(p.tunedStrings.isEmpty())
    }

    @Test
    fun allTuned_andReset() {
        val p = TuneProgress(requiredFrames = 1)
        for (i in 0 until 6) p.update(i, 0.0)
        assertTrue(p.allTuned)
        p.reset()
        assertTrue(p.tunedStrings.isEmpty())
        assertNull(p.update(7, 0.0))
    }
}

class ToneSynthTest {

    @Test
    fun referenceTone_hasExactPitch() {
        val detector = YinPitchDetector()
        for (a4 in listOf(440.0, 432.0)) {
            for (midi in Tuning.STANDARD.strings + listOf(Tuning.MIN_STRING_MIDI, Tuning.MAX_STRING_MIDI)) {
                val f = MusicTheory.midiToFrequency(midi, a4)
                val tone = ToneSynth.pluck(f, 44100)
                val frame = tone.copyOfRange(4410, 4410 + 4096)
                val result = detector.detect(frame)!!
                val cents = MusicTheory.cents(result.frequency, f)
                assertTrue("midi=$midi cents=$cents", abs(cents) < 0.5)
            }
        }
    }

    @Test
    fun referenceTone_isBoundedAndFadesOut() {
        val tone = ToneSynth.pluck(82.41, 44100, durationSeconds = 1.0)
        assertEquals(44100, tone.size)
        assertTrue(tone.all { abs(it) <= 0.61f })
        assertEquals(0f, tone.last(), 1e-4f)
        assertEquals(0f, tone.first(), 1e-4f)
    }
}
