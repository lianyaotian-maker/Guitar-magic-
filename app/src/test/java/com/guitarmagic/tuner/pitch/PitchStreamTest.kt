package com.guitarmagic.tuner.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 用连续音频流验证完整的识别管线：小音量灵敏度、噪声中不误报、滤波后精度不变。 */
class PitchStreamTest {

    private val sr = 44100
    private val hop = 2048

    /**
     * 合成音频流：[silenceSeconds] 秒只有环境噪声，然后是一个拨弦音（可选衰减）。
     * @param levelDb 拨弦音前 0.5 秒的 RMS 音量（dBFS）
     * @param noiseDb 白噪声 RMS 音量（dBFS）
     */
    private fun stream(
        f: Double,
        levelDb: Double,
        noiseDb: Double,
        seed: Int,
        silenceSeconds: Double = 1.0,
        noteSeconds: Double = 2.0,
        decay: Double = 0.6,
        amps: DoubleArray = TestSignals.WEAK_FUNDAMENTAL,
    ): FloatArray {
        val r = Random(seed)
        val silence = (silenceSeconds * sr).toInt()
        val n = silence + (noteSeconds * sr).toInt()
        val phases = DoubleArray(amps.size) { r.nextDouble(0.0, 2 * PI) }
        val note = DoubleArray(n - silence) { i ->
            val t = i.toDouble() / sr
            var v = 0.0
            for (k in amps.indices) {
                val fk = f * (k + 1)
                if (fk < sr / 2) v += amps[k] * sin(2 * PI * fk * t + phases[k])
            }
            v * exp(-decay * t)
        }
        val head = minOf(note.size, sr / 2)
        val rms = sqrt((0 until head).sumOf { note[it] * note[it] } / head)
        val gain = if (levelDb < -150) 0.0 else 10.0.pow(levelDb / 20) / rms
        val noiseAmp = 10.0.pow(noiseDb / 20) * sqrt(3.0)
        return FloatArray(n) { i ->
            val s = if (i >= silence) note[i - silence] * gain else 0.0
            (s + noiseAmp * (r.nextDouble() * 2 - 1)).toFloat()
        }
    }

    private class Result(val frames: Int, val hits: Int, val errors: List<Double>) {
        val hitRate get() = if (frames == 0) 0.0 else hits.toDouble() / frames
        fun percentile(p: Double): Double =
            if (errors.isEmpty()) 0.0 else errors.sorted()[((errors.size - 1) * p).toInt()]
    }

    /** 统计信号段（跳过起音最初几帧）的命中率与误差。 */
    private fun analyze(x: FloatArray, f: Double, signalStart: Int, skipFrames: Int = 6): Result {
        val s = PitchStream()
        var i = 0
        var t = 0L
        var frames = 0
        var hits = 0
        val errors = ArrayList<Double>()
        while (i + hop <= x.size) {
            val out = s.processHop(x.copyOfRange(i, i + hop), t)
            i += hop
            t += 46
            if (out == null || i <= signalStart) continue
            frames++
            val freq = out.frequency
            if (freq != null && !out.holding) {
                hits++
                if (i > signalStart + skipFrames * hop) errors += abs(MusicTheory.cents(freq, f))
            }
        }
        return Result(frames, hits, errors)
    }

    @Test
    fun softPlaying_isDetected_onAllStrings() {
        for ((index, midi) in Tuning.STANDARD.strings.withIndex()) {
            val f = MusicTheory.midiToFrequency(midi)
            // 轻弹：约 -55 dBFS，环境噪声 -70 dBFS（旧版固定门限下几乎完全识别不到）
            val r = analyze(stream(f, -55.0, -70.0, seed = index), f, sr)
            assertTrue("${MusicTheory.label(midi)} hit=${r.hitRate}", r.hitRate > 0.85)
            assertTrue("${MusicTheory.label(midi)} p95=${r.percentile(0.95)}", r.percentile(0.95) < 4.0)
        }
    }

    @Test
    fun verySoftPlaying_isMostlyDetected() {
        var total = 0.0
        for ((index, midi) in Tuning.STANDARD.strings.withIndex()) {
            val f = MusicTheory.midiToFrequency(midi)
            total += analyze(stream(f, -62.0, -80.0, seed = 20 + index), f, sr).hitRate
        }
        assertTrue("avg hit=${total / 6}", total / 6 > 0.6)
    }

    @Test
    fun normalPlaying_isAccurate() {
        for (a4 in listOf(440.0, 432.0)) {
            for ((index, midi) in Tuning.STANDARD.strings.withIndex()) {
                val f = MusicTheory.midiToFrequency(midi, a4)
                // 稳定的音（不衰减），经过滤波后精度仍应在 ±1 音分以内
                val r = analyze(stream(f, -35.0, -75.0, seed = 40 + index, decay = 0.0), f, sr)
                assertTrue("A4=$a4 ${MusicTheory.label(midi)} hit=${r.hitRate}", r.hitRate > 0.95)
                assertTrue("A4=$a4 ${MusicTheory.label(midi)} max=${r.percentile(1.0)}", r.percentile(1.0) < 1.0)
            }
        }
    }

    @Test
    fun noiseOnly_neverReportsPitch() {
        for (noiseDb in listOf(-85.0, -70.0, -55.0, -40.0, -25.0)) {
            for (seed in 0 until 4) {
                val x = stream(100.0, -200.0, noiseDb, seed = seed, silenceSeconds = 0.0, noteSeconds = 6.0)
                val r = analyze(x, 100.0, signalStart = 0, skipFrames = 0)
                assertEquals("noise=$noiseDb seed=$seed", 0, r.hits)
            }
        }
    }

    @Test
    fun lowFrequencyRumble_isFilteredOut() {
        // 20 Hz 的强烈低频振动（如手碰桌面）叠加在轻弹的 A2 上
        val f = 110.0
        val base = stream(f, -55.0, -75.0, seed = 7)
        val x = FloatArray(base.size) { base[it] + (0.01 * sin(2 * PI * 20 * it / sr)).toFloat() }
        val r = analyze(x, f, sr)
        assertTrue("hit=${r.hitRate}", r.hitRate > 0.85)
        assertTrue("p95=${r.percentile(0.95)}", r.percentile(0.95) < 4.0)
    }

    @Test
    fun reset_clearsState() {
        val s = PitchStream()
        val x = stream(196.0, -40.0, -80.0, seed = 3, silenceSeconds = 0.0, noteSeconds = 1.0)
        var last: PitchTracker.Output? = null
        var i = 0
        while (i + hop <= x.size) {
            last = s.processHop(x.copyOfRange(i, i + hop), i.toLong()) ?: last
            i += hop
        }
        assertTrue(last?.frequency != null)
        s.reset()
        // 重置后窗口为空，下一次送入不会立即输出
        assertEquals(null, s.processHop(FloatArray(hop), 0))
    }
}
