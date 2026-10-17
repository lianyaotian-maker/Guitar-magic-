package com.guitarmagic.tuner.pitch

import kotlin.math.pow

/** 计算允许识别的音域，用来过滤与调弦无关的声音（说话、音乐、啸叫等）。 */
object DetectionRange {
    const val FULL_MIN_HZ = 55.0
    const val FULL_MAX_HZ = 1500.0

    /** 自动模式下，最低弦以下、最高弦以上额外允许的半音数（弦松得很厉害时也能识别） */
    const val AUTO_MARGIN_SEMITONES = 5.0

    /** 锁定某根弦时，该弦上下允许的半音数 */
    const val LOCKED_MARGIN_SEMITONES = 6.0

    fun of(tuning: Tuning, a4: Double, lockedString: Int?, mode: DetectionMode): ClosedFloatingPointRange<Double> {
        if (mode == DetectionMode.CHROMATIC) return FULL_MIN_HZ..FULL_MAX_HZ
        val (low, high, margin) = if (lockedString != null && lockedString in tuning.strings.indices) {
            val midi = tuning.strings[lockedString]
            Triple(midi, midi, LOCKED_MARGIN_SEMITONES)
        } else {
            Triple(tuning.strings.min(), tuning.strings.max(), AUTO_MARGIN_SEMITONES)
        }
        val factor = 2.0.pow(margin / 12.0)
        val min = (MusicTheory.midiToFrequency(low, a4) / factor).coerceAtLeast(FULL_MIN_HZ)
        val max = (MusicTheory.midiToFrequency(high, a4) * factor).coerceAtMost(FULL_MAX_HZ)
        return min..max
    }
}
