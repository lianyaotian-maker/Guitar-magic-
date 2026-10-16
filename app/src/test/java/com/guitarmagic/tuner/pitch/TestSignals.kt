package com.guitarmagic.tuner.pitch

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 测试用的合成信号。 */
object TestSignals {
    const val SAMPLE_RATE = 44100
    const val FRAME = 4096

    fun sine(frequency: Double, amplitude: Double = 0.5, length: Int = FRAME): FloatArray =
        harmonic(frequency, doubleArrayOf(1.0), amplitude = amplitude, length = length)

    /**
     * 多泛音信号：amplitudes[k] 是第 k+1 次谐波的相对幅度。
     * 各谐波使用随机初相位；可选指数衰减（模拟拨弦）和白噪声。
     */
    fun harmonic(
        frequency: Double,
        amplitudes: DoubleArray,
        amplitude: Double = 0.5,
        length: Int = FRAME,
        decayPerSecond: Double = 0.0,
        noise: Double = 0.0,
        seed: Int = 42,
    ): FloatArray {
        val random = Random(seed)
        val phases = DoubleArray(amplitudes.size) { random.nextDouble(0.0, 2 * PI) }
        val norm = amplitudes.sum()
        val out = FloatArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / SAMPLE_RATE
            var v = 0.0
            for (k in amplitudes.indices) {
                val f = frequency * (k + 1)
                if (f >= SAMPLE_RATE / 2.0) break
                v += amplitudes[k] * sin(2 * PI * f * t + phases[k])
            }
            v = v / norm * amplitude * exp(-decayPerSecond * t)
            if (noise > 0) v += noise * (random.nextDouble() * 2 - 1)
            out[i] = v.toFloat()
        }
        return out
    }

    fun whiteNoise(amplitude: Double, length: Int = FRAME, seed: Int = 7): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { (amplitude * (random.nextDouble() * 2 - 1)).toFloat() }
    }

    /** 接近真实吉他的泛音分布：基频最强，高次泛音逐渐变弱。 */
    val GUITAR_BRIGHT = doubleArrayOf(1.0, 0.8, 0.6, 0.45, 0.3, 0.2, 0.15, 0.1)

    /** 低音弦常见情况：二次泛音明显强于基频。 */
    val WEAK_FUNDAMENTAL = doubleArrayOf(0.35, 1.0, 0.7, 0.5, 0.3, 0.2, 0.12, 0.08)

    /** 更极端：基频只有二次泛音的 1/4，三次泛音也很强。 */
    val VERY_WEAK_FUNDAMENTAL = doubleArrayOf(0.25, 1.0, 0.9, 0.6, 0.4, 0.3, 0.2, 0.1)
}
