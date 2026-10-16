package com.guitarmagic.tuner.core

/**
 * 记录本次调音中哪些弦已经调准：某根弦连续 [requiredFrames] 帧都在 ±[toleranceCents] 以内，
 * 就标记为已调准（打勾）。换调弦方式时调用 [reset]。
 */
class TuneProgress(
    private val stringCount: Int = 6,
    private val requiredFrames: Int = 8,
    private val toleranceCents: Double = 5.0,
) {
    private val tuned = BooleanArray(stringCount)
    private var streakString = -1
    private var streak = 0

    val tunedStrings: Set<Int> get() = (0 until stringCount).filterTo(LinkedHashSet()) { tuned[it] }

    val allTuned: Boolean get() = tuned.all { it }

    fun reset() {
        tuned.fill(false)
        streakString = -1
        streak = 0
    }

    /**
     * 输入一帧：当前对应的弦及其偏差；无信号时传 null。
     * @return 若这一帧让某根弦“新”变为已调准，返回该弦下标，否则返回 null
     */
    fun update(stringIndex: Int?, cents: Double?): Int? {
        if (stringIndex == null || cents == null || stringIndex !in 0 until stringCount ||
            kotlin.math.abs(cents) > toleranceCents
        ) {
            streakString = -1
            streak = 0
            return null
        }
        if (stringIndex != streakString) {
            streakString = stringIndex
            streak = 0
        }
        streak++
        if (streak >= requiredFrames && !tuned[stringIndex]) {
            tuned[stringIndex] = true
            return stringIndex
        }
        return null
    }
}
