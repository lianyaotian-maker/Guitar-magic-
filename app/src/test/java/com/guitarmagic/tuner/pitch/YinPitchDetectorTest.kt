package com.guitarmagic.tuner.pitch

import com.guitarmagic.tuner.pitch.TestSignals.FRAME
import com.guitarmagic.tuner.pitch.TestSignals.GUITAR_BRIGHT
import com.guitarmagic.tuner.pitch.TestSignals.SAMPLE_RATE
import com.guitarmagic.tuner.pitch.TestSignals.VERY_WEAK_FUNDAMENTAL
import com.guitarmagic.tuner.pitch.TestSignals.WEAK_FUNDAMENTAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class YinPitchDetectorTest {

    private val detector = YinPitchDetector(sampleRate = SAMPLE_RATE)
    private val maxCentsError = 1.0

    private val signalShapes = mapOf(
        "正弦波" to doubleArrayOf(1.0),
        "明亮吉他音色" to GUITAR_BRIGHT,
        "基频弱于二次泛音" to WEAK_FUNDAMENTAL,
        "基频远弱于二次泛音" to VERY_WEAK_FUNDAMENTAL,
    )

    private fun assertDetects(expected: Double, samples: FloatArray, description: String) {
        val result = detector.detect(samples)
        assertNotNull("$description：未检测到音高", result)
        val cents = MusicTheory.cents(result!!.frequency, expected)
        assertTrue(
            "$description：期望 %.3f Hz，实际 %.3f Hz，误差 %.3f 音分".format(expected, result.frequency, cents),
            abs(cents) <= maxCentsError,
        )
    }

    /** 6 根空弦 × 两种基准音 × 多种音色，误差必须在 ±1 音分以内。 */
    @Test
    fun openStrings_allShapes_bothReferences_withinOneCent() {
        for (a4 in listOf(440.0, 432.0)) {
            val frequencies = Tuning.STANDARD.frequencies(a4)
            for ((shapeName, shape) in signalShapes) {
                frequencies.forEachIndexed { index, f ->
                    val label = MusicTheory.label(Tuning.STANDARD.strings[index])
                    for (seed in 1..3) {
                        val samples = TestSignals.harmonic(f, shape, seed = seed)
                        assertDetects(f, samples, "A4=$a4 $label $shapeName seed=$seed")
                    }
                }
            }
        }
    }

    /** 识别结果映射回正确的弦与音名，偏差接近 0 音分。 */
    @Test
    fun openStrings_mapToCorrectNote_bothReferences() {
        for (a4 in listOf(440.0, 432.0)) {
            Tuning.STANDARD.strings.forEach { midi ->
                val f = MusicTheory.midiToFrequency(midi, a4)
                val samples = TestSignals.harmonic(f, WEAK_FUNDAMENTAL)
                val result = detector.detect(samples)!!
                val note = MusicTheory.nearestNote(result.frequency, a4)
                assertEquals("A4=$a4", MusicTheory.label(midi), note.label)
                assertTrue("A4=$a4 ${note.label} cents=${note.cents}", abs(note.cents) <= maxCentsError)
                assertEquals(f, note.frequency, 1e-9)
            }
        }
    }

    @Test
    fun a4_440_and_432() {
        for (a4 in listOf(440.0, 432.0)) {
            for ((shapeName, shape) in signalShapes) {
                assertDetects(a4, TestSignals.harmonic(a4, shape), "A4=$a4 $shapeName")
            }
        }
    }

    /** 低音 E 弦带衰减和噪声，模拟真实拨弦。 */
    @Test
    fun lowE_decayingWithNoise_notOctaveError() {
        for (a4 in listOf(440.0, 432.0)) {
            val f = MusicTheory.midiToFrequency(40, a4)
            for (shape in listOf(GUITAR_BRIGHT, WEAK_FUNDAMENTAL, VERY_WEAK_FUNDAMENTAL)) {
                for (seed in 1..5) {
                    val samples = TestSignals.harmonic(
                        f, shape, amplitude = 0.3, decayPerSecond = 3.0, noise = 0.005, seed = seed,
                    )
                    assertDetects(f, samples, "A4=$a4 E2 衰减+噪声 seed=$seed")
                }
            }
        }
    }

    /** 覆盖 60Hz–1400Hz 全范围（按半音步进，并带 ±30 音分偏移）。 */
    @Test
    fun fullRange_60_to_1400Hz() {
        var f = 60.0
        val step = 2.0.pow(1.0 / 12.0)
        while (f <= 1400.0) {
            for (detune in listOf(-30.0, 0.0, 17.0)) {
                val freq = f * 2.0.pow(detune / 1200.0)
                if (freq < 60.0 || freq > 1400.0) continue
                assertDetects(freq, TestSignals.sine(freq), "正弦 %.2f Hz".format(freq))
                assertDetects(freq, TestSignals.harmonic(freq, GUITAR_BRIGHT), "泛音 %.2f Hz".format(freq))
                assertDetects(freq, TestSignals.harmonic(freq, WEAK_FUNDAMENTAL), "弱基频 %.2f Hz".format(freq))
            }
            f *= step
        }
        assertDetects(1400.0, TestSignals.sine(1400.0), "正弦 1400 Hz")
        assertDetects(60.0, TestSignals.harmonic(60.0, WEAK_FUNDAMENTAL), "弱基频 60 Hz")
    }

    /** 低于 / 高于标准音若干音分时，偏差计算正确。 */
    @Test
    fun detunedStrings_reportCorrectCents() {
        val a4 = 440.0
        for (midi in Tuning.STANDARD.strings) {
            for (detune in listOf(-40.0, -12.0, -4.0, 3.0, 9.0, 25.0, 45.0)) {
                val target = MusicTheory.midiToFrequency(midi, a4)
                val f = target * 2.0.pow(detune / 1200.0)
                val result = detector.detect(TestSignals.harmonic(f, WEAK_FUNDAMENTAL))!!
                val note = MusicTheory.nearestNote(result.frequency, a4)
                assertEquals(midi, note.midi)
                assertEquals("${note.label} detune=$detune", detune, note.cents, maxCentsError)
            }
        }
    }

    @Test
    fun silence_returnsNull() {
        assertNull(detector.detect(FloatArray(FRAME)))
    }

    @Test
    fun whiteNoise_returnsNull() {
        for (seed in 1..5) {
            assertNull("seed=$seed", detector.detect(TestSignals.whiteNoise(0.3, seed = seed)))
        }
    }

    @Test
    fun rmsDb_ofFullScaleSine_isAboutMinus3() {
        val db = detector.rmsDb(TestSignals.sine(440.0, amplitude = 1.0))
        assertEquals(-3.01, db, 0.1)
    }
}
