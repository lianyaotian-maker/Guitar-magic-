package com.guitarmagic.tuner.pitch

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 一帧音频的检测结果。
 *
 * @param frequency 基频（Hz）
 * @param confidence 周期性置信度，0..1，越接近 1 越像一个干净的周期信号
 * @param rmsDb 该帧的音量（dBFS，满幅正弦约为 -3 dB）
 */
data class PitchResult(
    val frequency: Double,
    val confidence: Double,
    val rmsDb: Double,
)

/**
 * 基于 YIN 算法（de Cheveigné & Kawahara, 2002）的基频检测器，纯 Kotlin 实现，
 * 不依赖 Android API，便于在 JVM 上做单元测试。
 *
 * 关键点：
 * 1. 差分函数 + 累积均值归一化（CMNDF），对泛音强于基频的信号依然能找到真正的周期；
 * 2. 绝对阈值取“第一个”足够深的谷，避免倍周期（低八度）误判；
 * 3. 额外的倍周期校验：若 2τ / 3τ 处的谷明显更深，说明当前 τ 只是泛音的周期
 *    （例如吉他低音 E 弦二次泛音很强），改用更长的周期；
 * 4. 在原始差分函数上做抛物线插值，获得亚采样精度。
 */
class YinPitchDetector(
    val sampleRate: Int = 44100,
    val minFrequency: Double = 55.0,
    val maxFrequency: Double = 1500.0,
    private val threshold: Double = 0.15,
    /** 找不到低于阈值的谷时，全局最小值仍需低于此值才认为有音高。 */
    private val fallbackThreshold: Double = 0.35,
) {
    val tauMin: Int = floor(sampleRate / maxFrequency).toInt().coerceAtLeast(2)
    val tauMax: Int = ceil(sampleRate / minFrequency).toInt()

    /** 建议的最小帧长：积分窗口至少与最大周期一样长。 */
    val minBufferSize: Int = tauMax * 2 + 2

    private var diff = DoubleArray(0)
    private var cmnd = DoubleArray(0)
    private var work = DoubleArray(0)

    /** 计算一帧的 RMS 音量（dBFS）。样本范围为 -1..1。 */
    fun rmsDb(samples: FloatArray, length: Int = samples.size): Double {
        if (length == 0) return -120.0
        var mean = 0.0
        for (i in 0 until length) mean += samples[i]
        mean /= length
        var sum = 0.0
        for (i in 0 until length) {
            val v = samples[i] - mean
            sum += v * v
        }
        val rms = sqrt(sum / length)
        return if (rms <= 1e-9) -120.0 else 20.0 * log10(rms)
    }

    /**
     * 检测一帧的基频。返回 null 表示没有可靠的音高（噪声、无声或非周期信号）。
     */
    fun detect(samples: FloatArray, length: Int = samples.size): PitchResult? {
        require(length >= minBufferSize) { "帧长 $length 太短，至少需要 $minBufferSize" }
        val tauLimit = tauMax + 1 // 多算一个点用于插值
        val window = length - tauLimit
        ensureCapacity(length, tauLimit + 1)

        // 去直流
        var mean = 0.0
        for (i in 0 until length) mean += samples[i]
        mean /= length
        val x = work
        for (i in 0 until length) x[i] = samples[i] - mean

        var energy = 0.0
        for (i in 0 until length) energy += x[i] * x[i]
        val rms = sqrt(energy / length)
        val db = if (rms <= 1e-9) -120.0 else 20.0 * log10(rms)
        if (rms <= 1e-7) return null

        // 步骤 1-2：差分函数 d(τ) = Σ (x[j] - x[j+τ])²
        val d = diff
        d[0] = 0.0
        for (tau in 1..tauLimit) {
            var sum = 0.0
            var j = 0
            while (j < window) {
                val delta = x[j] - x[j + tau]
                sum += delta * delta
                j++
            }
            d[tau] = sum
        }

        // 步骤 3：累积均值归一化 d'(τ)
        val c = cmnd
        c[0] = 1.0
        var running = 0.0
        for (tau in 1..tauLimit) {
            running += d[tau]
            c[tau] = if (running <= 0.0) 1.0 else d[tau] * tau / running
        }

        // 步骤 4：绝对阈值，取第一个低于阈值的谷
        var tau = -1
        var t = tauMin
        while (t <= tauMax) {
            if (c[t] < threshold) {
                while (t + 1 <= tauMax && c[t + 1] < c[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            // 没有足够深的谷：取全局最小，但要求它也相对较深
            var best = tauMin
            for (i in tauMin..tauMax) if (c[i] < c[best]) best = i
            if (c[best] >= fallbackThreshold) return null
            tau = best
            // 全局最小可能落在 2T、3T 上，回退检查其约数
            tau = preferSubmultiple(tau)
        }

        // 倍周期校验：当前 τ 可能只是强泛音的周期
        tau = preferMultiple(tau)

        // 步骤 5：在原始差分函数上做抛物线插值
        val refined = parabolicMinimum(d, tau)
        val frequency = sampleRate / refined
        if (frequency < minFrequency * 0.97 || frequency > maxFrequency * 1.03) return null
        val confidence = (1.0 - c[tau]).coerceIn(0.0, 1.0)
        return PitchResult(frequency, confidence, db)
    }

    /**
     * 如果 k·τ（k=2,3）处存在明显更深的谷，说明 τ 是泛音周期，取更长的那个。
     * 比较的是插值后的谷底值：高音区周期不是整数个采样点，整数位置的值会偏大。
     */
    private fun preferMultiple(tau: Int): Int {
        val base = interpolatedValue(cmnd, tau)
        if (base < 0.02) return tau // 已经非常干净，不可能是泛音误判
        var result = tau
        var resultValue = base
        for (k in 2..3) {
            val center = tau * k
            if (center > tauMax) break
            val m = localMinimumNear(center)
            val value = interpolatedValue(cmnd, m)
            if (value < resultValue * 0.5 && value < threshold) {
                result = m
                resultValue = value
            }
        }
        return result
    }

    /** 回退路径下全局最小可能在 2T/3T/4T，检查 τ/k 处是否也有接近的谷。 */
    private fun preferSubmultiple(tau: Int): Int {
        val c = cmnd
        val value = c[tau]
        for (k in 4 downTo 2) {
            val center = Math.round(tau.toDouble() / k).toInt()
            if (center < tauMin) continue
            val m = localMinimumNear(center)
            if (c[m] < value * 1.2 + 0.03) return m
        }
        return tau
    }

    /** 在 center 附近 ±3% 的范围内找局部最小。 */
    private fun localMinimumNear(center: Int): Int {
        val radius = maxOf(2, (center * 0.03).toInt())
        val from = maxOf(tauMin, center - radius)
        val to = minOf(tauMax, center + radius)
        var best = from
        for (i in from..to) if (cmnd[i] < cmnd[best]) best = i
        return best
    }

    /** 抛物线插值得到的谷底值。 */
    private fun interpolatedValue(values: DoubleArray, tau: Int): Double {
        if (tau < 1 || tau + 1 >= values.size) return values[tau]
        val y0 = values[tau - 1]
        val y1 = values[tau]
        val y2 = values[tau + 1]
        val denom = y0 - 2 * y1 + y2
        if (denom <= 0.0) return y1
        val offset = (0.5 * (y0 - y2) / denom).coerceIn(-1.0, 1.0)
        return (y1 - 0.25 * (y0 - y2) * offset).coerceAtLeast(0.0)
    }

    private fun parabolicMinimum(values: DoubleArray, tau: Int): Double {
        if (tau < 1 || tau + 1 >= values.size) return tau.toDouble()
        val y0 = values[tau - 1]
        val y1 = values[tau]
        val y2 = values[tau + 1]
        val denom = y0 - 2 * y1 + y2
        if (denom <= 0.0) return tau.toDouble()
        val offset = 0.5 * (y0 - y2) / denom
        return tau + offset.coerceIn(-1.0, 1.0)
    }

    private fun ensureCapacity(length: Int, tauSize: Int) {
        if (work.size < length) work = DoubleArray(length)
        if (diff.size < tauSize) {
            diff = DoubleArray(tauSize)
            cmnd = DoubleArray(tauSize)
        }
    }
}
