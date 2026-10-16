package com.guitarmagic.tuner.pitch

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** 最近标准音的信息。 */
data class NoteInfo(
    /** MIDI 编号，A4 = 69，C4 = 60 */
    val midi: Int,
    /** 音名，例如 "A"、"C♯" */
    val name: String,
    /** 科学音高记号中的八度，C4 为中央 C */
    val octave: Int,
    /** 实际频率相对该标准音的偏差（音分），范围约 -50..+50 */
    val cents: Double,
    /** 该标准音的理论频率（Hz） */
    val frequency: Double,
) {
    /** 科学音高记号，例如 "E2"、"A4" */
    val label: String get() = "$name$octave"
}

object MusicTheory {
    const val DEFAULT_A4 = 440.0
    const val MIN_A4 = 430.0
    const val MAX_A4 = 450.0

    private val NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    fun noteName(midi: Int): String = NAMES[Math.floorMod(midi, 12)]

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun label(midi: Int): String = "${noteName(midi)}${octave(midi)}"

    fun midiToFrequency(midi: Int, a4: Double = DEFAULT_A4): Double =
        a4 * 2.0.pow((midi - 69) / 12.0)

    /** 连续的 MIDI 值（带小数），用于计算音分。 */
    fun frequencyToMidi(frequency: Double, a4: Double = DEFAULT_A4): Double =
        69.0 + 12.0 * ln(frequency / a4) / ln(2.0)

    /** frequency 相对 reference 的音分差。 */
    fun cents(frequency: Double, reference: Double): Double =
        1200.0 * ln(frequency / reference) / ln(2.0)

    fun nearestNote(frequency: Double, a4: Double = DEFAULT_A4): NoteInfo {
        val midiFloat = frequencyToMidi(frequency, a4)
        val midi = midiFloat.roundToInt()
        val target = midiToFrequency(midi, a4)
        return NoteInfo(
            midi = midi,
            name = noteName(midi),
            octave = octave(midi),
            cents = cents(frequency, target),
            frequency = target,
        )
    }
}

/** 一种调弦方式，strings 按 6 弦（最粗）到 1 弦（最细）排列，值为 MIDI 编号。 */
data class Tuning(val id: String, val displayName: String, val strings: List<Int>) {
    init {
        require(strings.size == 6)
    }

    fun frequencies(a4: Double): List<Double> = strings.map { MusicTheory.midiToFrequency(it, a4) }

    /** 找出与频率最接近的弦（按音分距离），返回下标 0..5（0 为 6 弦）。 */
    fun closestString(frequency: Double, a4: Double): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        strings.forEachIndexed { index, midi ->
            val distance = kotlin.math.abs(
                MusicTheory.cents(frequency, MusicTheory.midiToFrequency(midi, a4))
            )
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    companion object {
        // MIDI: E2=40 A2=45 D3=50 G3=55 B3=59 E4=64
        val STANDARD = Tuning("standard", "标准调弦", listOf(40, 45, 50, 55, 59, 64))
        val DROP_D = Tuning("drop_d", "Drop D", listOf(38, 45, 50, 55, 59, 64))
        val HALF_STEP_DOWN = Tuning("half_down", "降半音", listOf(39, 44, 49, 54, 58, 63))
        val OPEN_G = Tuning("open_g", "Open G", listOf(38, 43, 50, 55, 59, 62))
        val DADGAD = Tuning("dadgad", "DADGAD", listOf(38, 45, 50, 55, 57, 62))

        val ALL = listOf(STANDARD, DROP_D, HALF_STEP_DOWN, OPEN_G, DADGAD)

        fun byId(id: String?): Tuning = ALL.firstOrNull { it.id == id } ?: STANDARD
    }
}
