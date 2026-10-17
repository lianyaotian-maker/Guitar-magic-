package com.guitarmagic.tuner.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 合成类似拨弦的参考音：精确的整数倍泛音叠加，高次泛音衰减更快。
 * 频率完全由公式决定，不存在延迟线类算法的音高误差。
 */
object ToneSynth {
    private val HARMONICS = doubleArrayOf(1.0, 0.55, 0.35, 0.22, 0.14, 0.09, 0.05)

    fun pluck(
        frequency: Double,
        sampleRate: Int,
        durationSeconds: Double = 2.5,
        peak: Double = 0.6,
    ): FloatArray {
        require(frequency > 0 && sampleRate > 0)
        val length = (durationSeconds * sampleRate).toInt()
        val out = FloatArray(length)
        val attack = (0.004 * sampleRate).toInt().coerceAtLeast(1)
        val release = (0.08 * sampleRate).toInt().coerceAtLeast(1)
        var maxAbs = 0.0
        val buffer = DoubleArray(length)
        for (k in HARMONICS.indices) {
            val f = frequency * (k + 1)
            if (f >= sampleRate * 0.45) break
            val amp = HARMONICS[k]
            val decay = 1.3 * (1.0 + 0.7 * k) // 每秒衰减系数
            val w = 2 * PI * f / sampleRate
            for (i in 0 until length) {
                buffer[i] += amp * exp(-decay * i / sampleRate) * sin(w * i)
            }
        }
        for (i in 0 until length) {
            val env = min(1.0, i.toDouble() / attack) * min(1.0, (length - 1 - i).toDouble() / release)
            buffer[i] *= max(0.0, env)
            maxAbs = max(maxAbs, kotlin.math.abs(buffer[i]))
        }
        val gain = if (maxAbs > 0) peak / maxAbs else 0.0
        for (i in 0 until length) out[i] = (buffer[i] * gain).toFloat()
        return out
    }

    /**
     * 调准提示音：清脆的“叮”（钟声般的非整数倍泛音，快速衰减），约 0.3 秒。
     * 音高高于吉他识别音域，不会被当成琴声。
     */
    fun chime(sampleRate: Int, peak: Double = 0.45): FloatArray {
        val length = (0.32 * sampleRate).toInt()
        val partials = doubleArrayOf(1.0, 2.76, 5.40)
        val amps = doubleArrayOf(1.0, 0.35, 0.12)
        val decays = doubleArrayOf(14.0, 22.0, 34.0)
        val base = 1975.5 // B6
        val attack = (0.002 * sampleRate).toInt().coerceAtLeast(1)
        val buffer = DoubleArray(length)
        var maxAbs = 0.0
        for (i in 0 until length) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (k in partials.indices) {
                val f = base * partials[k]
                if (f >= sampleRate * 0.45) continue
                v += amps[k] * exp(-decays[k] * t) * sin(2 * PI * f * t)
            }
            val env = min(1.0, i.toDouble() / attack) * min(1.0, (length - 1 - i) / (0.02 * sampleRate))
            buffer[i] = v * env
            maxAbs = max(maxAbs, kotlin.math.abs(buffer[i]))
        }
        val gain = if (maxAbs > 0) peak / maxAbs else 0.0
        return FloatArray(length) { (buffer[it] * gain).toFloat() }
    }
}
