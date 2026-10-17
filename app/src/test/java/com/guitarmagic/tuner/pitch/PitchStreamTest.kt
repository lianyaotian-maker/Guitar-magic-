package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 用连续音频流验证“拨弦触发”识别机制：
 * 正常/轻柔拨弦能稳定识别；说话般的滑音、缓慢变响的背景声、敲击、噪声、音域外的声音都不触发。
 */
class PitchStreamTest {

    private val sr = 44100
    private val hop = 2048
    private val guitarRange = DetectionRange.of(Tuning.STANDARD, 440.0, null, DetectionMode.GUITAR)

    /** 音频片段描述：频率随时间变化 f(t)，音量包络 env(t)（线性幅度，峰值约 1）。 */
    private fun render(
        seconds: Double,
        seed: Int,
        noiseDb: Double = -80.0,
        levelDb: Double = -40.0,
        amps: DoubleArray = TestSignals.WEAK_FUNDAMENTAL,
        frequency: (Double) -> Double,
        envelope: (Double) -> Double,
    ): FloatArray {
        val r = Random(seed)
        val n = (seconds * sr).toInt()
        val phases = DoubleArray(amps.size) { r.nextDouble(0.0, 2 * PI) }
        val phase = DoubleArray(amps.size) { phases[it] }
        // 归一化：使包络为 1 时的 RMS 等于 levelDb
        var norm = 0.0
        for (a in amps) norm += a * a / 2
        val gain = 10.0.pow(levelDb / 20) / sqrt(norm)
        val noiseAmp = 10.0.pow(noiseDb / 20) * sqrt(3.0)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val f = frequency(t)
            val e = envelope(t)
            var v = 0.0
            for (k in amps.indices) {
                val fk = f * (k + 1)
                phase[k] += 2 * PI * fk / sr
                if (fk < sr / 2) v += amps[k] * sin(phase[k])
            }
            out[i] = (v * gain * e + noiseAmp * (r.nextDouble() * 2 - 1)).toFloat()
        }
        return out
    }

    /** 在 [start] 秒拨弦、指数衰减的音。 */
    private fun pluck(f: Double, start: Double, decay: Double = 0.8): (Double) -> Double = { t ->
        if (t < start) 0.0 else exp(-decay * (t - start))
    }

    private class Frame(val time: Double, val out: PitchTracker.Output?)

    private fun run(
        x: FloatArray,
        range: ClosedFloatingPointRange<Double> = guitarRange,
        sensitivity: Sensitivity = Sensitivity.MEDIUM,
    ): List<Frame> {
        val s = PitchStream()
        s.sensitivity = sensitivity
        val frames = ArrayList<Frame>()
        var i = 0
        while (i + hop <= x.size) {
            val t = (i + hop).toDouble() / sr
            val out = s.processHop(x.copyOfRange(i, i + hop), (t * 1000).toLong(), range.start, range.endInclusive)
            frames += Frame(t, out)
            i += hop
        }
        return frames
    }

    private fun List<Frame>.fresh(from: Double, to: Double) =
        filter { it.time in from..to && it.out?.frequency != null && it.out.holding == false }

    private fun List<Frame>.framesIn(from: Double, to: Double) = count { it.time in from..to }

    private fun List<Frame>.anyPitch() = any { it.out?.frequency != null }

    @Test
    fun pluckedOpenStrings_areDetectedQuicklyAndAccurately() {
        for (a4 in listOf(440.0, 432.0)) {
            val range = DetectionRange.of(Tuning.STANDARD, a4, null, DetectionMode.GUITAR)
            for ((index, midi) in Tuning.STANDARD.strings.withIndex()) {
                val f = MusicTheory.midiToFrequency(midi, a4)
                val frames = run(render(3.0, seed = index, frequency = { f }, envelope = pluck(f, 0.5)), range)
                val first = frames.firstOrNull { it.out?.frequency != null }
                assertTrue("A4=$a4 ${MusicTheory.label(midi)} 未识别", first != null)
                assertTrue("A4=$a4 ${MusicTheory.label(midi)} 识别太慢：${first!!.time}", first.time < 0.5 + 0.30)
                val fresh = frames.fresh(0.9, 2.0)
                assertTrue("A4=$a4 ${MusicTheory.label(midi)} 命中率低", fresh.size >= frames.framesIn(0.9, 2.0) * 0.9)
                val maxErr = fresh.maxOf { abs(MusicTheory.cents(it.out!!.frequency!!, f)) }
                assertTrue("A4=$a4 ${MusicTheory.label(midi)} 误差 $maxErr 音分", maxErr < 1.5)
            }
        }
    }

    @Test
    fun softPluck_isDetected() {
        for ((index, midi) in Tuning.STANDARD.strings.withIndex()) {
            val f = MusicTheory.midiToFrequency(midi)
            // 轻弹：约 -56 dBFS，环境噪声 -75 dBFS
            val frames = run(render(2.5, seed = 10 + index, noiseDb = -75.0, levelDb = -56.0, frequency = { f }, envelope = pluck(f, 0.5)))
            val fresh = frames.fresh(0.5, 1.3)
            assertTrue("${MusicTheory.label(midi)} hits=${fresh.size}", fresh.size >= 8)
            val maxErr = fresh.drop(2).maxOf { abs(MusicTheory.cents(it.out!!.frequency!!, f)) }
            assertTrue("${MusicTheory.label(midi)} 误差 $maxErr", maxErr < 4.0)
        }
    }

    @Test
    fun veryQuietSounds_areIgnored() {
        val f = MusicTheory.midiToFrequency(45)
        val frames = run(render(2.5, seed = 3, noiseDb = -85.0, levelDb = -72.0, frequency = { f }, envelope = pluck(f, 0.5)))
        assertTrue(!frames.anyPitch())
    }

    @Test
    fun sensitivityLevels_changeTriggerThreshold() {
        val f = MusicTheory.midiToFrequency(50)
        val x = render(2.5, seed = 4, noiseDb = -85.0, levelDb = -61.0, frequency = { f }, envelope = pluck(f, 0.5))
        assertTrue("高灵敏度应能识别", run(x, sensitivity = Sensitivity.HIGH).anyPitch())
        assertTrue("中灵敏度应能识别", run(x, sensitivity = Sensitivity.MEDIUM).anyPitch())
        assertTrue("低灵敏度不应识别", !run(x, sensitivity = Sensitivity.LOW).anyPitch())
    }

    @Test
    fun noiseOnly_neverReportsPitch() {
        for (noiseDb in listOf(-85.0, -70.0, -55.0, -40.0, -25.0)) {
            for (seed in 0 until 3) {
                val x = render(5.0, seed = seed, noiseDb = noiseDb, levelDb = -200.0, frequency = { 100.0 }, envelope = { 0.0 })
                assertTrue("noise=$noiseDb seed=$seed", !run(x).anyPitch())
            }
        }
    }

    @Test
    fun tapsAndKnocks_areIgnored() {
        // 每 0.3 秒一次很短的宽频敲击声（碰到手机、桌面）
        val r = Random(9)
        val x = FloatArray(sr * 4) { i ->
            val inBurst = (i % (sr * 3 / 10)) < sr / 200
            val burst = if (inBurst) 0.3 * (r.nextDouble() * 2 - 1) * exp(-(i % (sr * 3 / 10)).toDouble() / 60) else 0.0
            (burst + 0.0001 * (r.nextDouble() * 2 - 1)).toFloat()
        }
        assertTrue(!run(x).anyPitch())
    }

    @Test
    fun slowlySwellingBackgroundTone_withoutPluck_isIgnored() {
        // 背景音乐/电器声：音量 4 秒内从 -90 dB 缓慢升到 -40 dB，没有拨弦起音
        val f = 196.0
        val frames = run(render(4.5, seed = 5, levelDb = -40.0, frequency = { f }, envelope = { t -> 10.0.pow(-50.0 * (1 - minOf(t / 4.0, 1.0)) / 20) }))
        assertTrue(!frames.anyPitch())
    }

    @Test
    fun glidingVoiceLikePitch_isIgnored() {
        // 像说话一样音高不断滑动（0.5 秒内上滑 7 个半音），即便有起音也不应被当成琴弦音
        val frames = run(
            render(
                2.5,
                seed = 6,
                levelDb = -35.0,
                frequency = { t -> 150.0 * 2.0.pow(((t * 14) % 7.0) / 12.0) },
                envelope = { t -> if (t < 0.5) 0.0 else 1.0 },
            ),
        )
        assertTrue(!frames.anyPitch())
    }

    @Test
    fun outOfGuitarRange_isIgnored_butChromaticModeAccepts() {
        val f = 1000.0 // 远高于标准调弦 1 弦的音域
        val x = render(2.0, seed = 7, frequency = { f }, envelope = pluck(f, 0.5))
        assertTrue(!run(x, guitarRange).anyPitch())
        val chromatic = DetectionRange.of(Tuning.STANDARD, 440.0, null, DetectionMode.CHROMATIC)
        val frames = run(x, chromatic)
        val last = frames.last { it.out?.frequency != null }.out!!.frequency!!
        assertEquals(f, last, 1.0)
    }

    @Test
    fun turningThePeg_isFollowedContinuously() {
        // A2 偏低 40 音分拨弦，随后 1.5 秒内拧到准（持续发声，不衰减）
        val target = 110.0
        val start = target * 2.0.pow(-40.0 / 1200)
        val freq: (Double) -> Double = { t -> if (t < 1.0) start else start * 2.0.pow(minOf((t - 1.0) / 1.5, 1.0) * 40.0 / 1200) }
        val frames = run(render(3.5, seed = 8, frequency = freq, envelope = { t -> if (t < 0.5) 0.0 else 1.0 }))
        val fresh = frames.fresh(0.9, 3.4)
        assertTrue("跟踪中断：${fresh.size}/${frames.framesIn(0.9, 3.4)}", fresh.size >= frames.framesIn(0.9, 3.4) * 0.95)
        val final = frames.last().out!!.frequency!!
        assertTrue("最终 ${MusicTheory.cents(final, target)} 音分", abs(MusicTheory.cents(final, target)) < 3.0)
    }

    @Test
    fun pluckingAnotherString_switchesQuickly() {
        val e2 = MusicTheory.midiToFrequency(40)
        val a2 = MusicTheory.midiToFrequency(45)
        val frames = run(
            render(
                3.5,
                seed = 11,
                frequency = { t -> if (t < 1.5) e2 else a2 },
                envelope = { t ->
                    when {
                        t < 0.5 -> 0.0
                        t < 1.5 -> exp(-1.5 * (t - 0.5)) // E2 衰减到约 -13 dB
                        else -> exp(-0.8 * (t - 1.5))   // 拨 A2
                    }
                },
            ),
        )
        val switched = frames.first { it.time > 1.5 && it.out?.frequency?.let { f -> abs(MusicTheory.cents(f, a2)) < 20 } == true }
        assertTrue("切换太慢：${switched.time}", switched.time < 1.5 + 0.35)
        assertTrue(abs(MusicTheory.cents(frames.last { it.out?.frequency != null }.out!!.frequency!!, a2)) < 2.0)
    }

    @Test
    fun afterNoteEnds_readingIsHeldThenCleared() {
        val f = MusicTheory.midiToFrequency(55)
        val frames = run(render(4.0, seed = 12, frequency = { f }, envelope = { t -> if (t in 0.5..1.5) 1.0 else 0.0 }))
        val at = { t: Double -> frames.minBy { abs(it.time - t) }.out }
        assertTrue(at(1.2)?.frequency != null && at(1.2)?.holding == false)
        val held = at(2.2)
        assertTrue("应保留读数", held?.frequency != null && held.holding)
        assertNull("应已清除", at(3.6)?.frequency)
    }

    @Test
    fun lowFrequencyRumble_doesNotBlockDetection() {
        val f = 110.0
        val base = render(2.5, seed = 13, noiseDb = -75.0, levelDb = -48.0, frequency = { f }, envelope = pluck(f, 0.5))
        val x = FloatArray(base.size) { base[it] + (0.003 * sin(2 * PI * 20 * it / sr)).toFloat() }
        val fresh = run(x).fresh(0.5, 1.5)
        assertTrue("hits=${fresh.size}", fresh.size >= 12)
    }

    @Test
    fun detectionRange_coversStringsWithMargin() {
        val r = DetectionRange.of(Tuning.STANDARD, 440.0, null, DetectionMode.GUITAR)
        assertTrue(r.start < 82.41 / 1.3 && r.start > 55.0)
        assertTrue(r.endInclusive > 329.63 * 1.3 && r.endInclusive < 500.0)
        val locked = DetectionRange.of(Tuning.STANDARD, 440.0, 1, DetectionMode.GUITAR)
        assertTrue(110.0 in locked && 70.0 !in locked && 160.0 !in locked)
        assertEquals(DetectionRange.FULL_MAX_HZ, DetectionRange.of(Tuning.STANDARD, 440.0, 1, DetectionMode.CHROMATIC).endInclusive, 0.0)
    }
}
