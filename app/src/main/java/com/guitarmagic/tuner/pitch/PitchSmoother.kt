package com.guitarmagic.tuner.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * 对连续的音高检测结果做中值滤波，让指针稳定。
 *
 * - 在对数（半音）域做中值，偶发的八度误判或噪声帧会被直接滤掉；
 * - 当新音高与当前中值相差较大时，先放入“待确认”队列，连续若干帧都一致后
 *   才认为换了音（例如换弦），从而既不乱跳又能快速跟随。
 */
class PitchSmoother(
    private val windowSize: Int = 5,
    private val jumpSemitones: Double = 0.6,
    private val confirmFrames: Int = 3,
) {
    private val history = ArrayDeque<Double>()
    private val pending = ArrayDeque<Double>()

    fun reset() {
        history.clear()
        pending.clear()
    }

    /** 加入一个新的检测频率，返回平滑后的频率。 */
    fun add(frequency: Double): Double {
        val value = toSemitones(frequency)
        if (history.isEmpty()) {
            history.addLast(value)
            return frequency
        }
        val current = median(history)
        if (abs(value - current) > jumpSemitones) {
            if (pending.isNotEmpty() && abs(value - median(pending)) > jumpSemitones) {
                pending.clear()
            }
            pending.addLast(value)
            if (pending.size >= confirmFrames) {
                history.clear()
                history.addAll(pending)
                pending.clear()
            }
            return fromSemitones(median(history))
        }
        pending.clear()
        history.addLast(value)
        while (history.size > windowSize) history.removeFirst()
        return fromSemitones(median(history))
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun toSemitones(frequency: Double) = 12.0 * ln(frequency) / ln(2.0)

    private fun fromSemitones(value: Double) = 2.0.pow(value / 12.0)
}
