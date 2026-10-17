package com.guitarmagic.tuner.core

import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning
import kotlin.math.abs

/**
 * 自动判断“正在调的是哪一根弦”。
 *
 * 只看“离哪根弦的目标音最近”在切换预设后会认错弦：例如从标准调弦切到 Open C 时，
 * 6 弦实际还是 E2，它离 5 弦的目标 G2（3 个半音）比离 6 弦的目标 C2（4 个半音）更近。
 *
 * 因此这里同时记住每根弦**最近一次实际听到的音高**（琴弦本身不会因为切换预设而改变）：
 * - 切换预设时，没有听到过的弦，假定还停在旧预设的音高；
 * - 新的一次拨弦，按“与该弦记忆音高或目标音高的较小距离”选出最接近的弦；
 * - 同一次拨弦持续发声期间不更换弦（拧弦钮时音高经过别的弦附近也不会跳走），并持续更新记忆。
 */
class StringAssigner(private val stringCount: Int = Tuning.STRING_COUNT) {
    private val memory = arrayOfNulls<Double>(stringCount)
    private var currentNote = Long.MIN_VALUE
    private var currentString: Int? = null

    /** 某根弦记住的实际音高（Hz），没有则为 null。 */
    fun remembered(index: Int): Double? = memory.getOrNull(index)

    /** 全部清空（例如换了新琴弦、重新开始）。 */
    fun reset() {
        memory.fill(null)
        currentNote = Long.MIN_VALUE
        currentString = null
    }

    /** 切换调弦预设：琴弦还停在旧预设的音高上（除非已经实际听到过）。 */
    fun onTuningChanged(previous: Tuning?, a4: Double) {
        if (previous != null) {
            for (i in 0 until stringCount) {
                if (memory[i] == null) memory[i] = MusicTheory.midiToFrequency(previous.strings[i], a4)
            }
        }
        currentNote = Long.MIN_VALUE
        currentString = null
    }

    /** 手动锁定某根弦时，同样记录它的实际音高。 */
    fun observeLocked(index: Int, frequency: Double, noteId: Long) {
        if (index !in 0 until stringCount) return
        memory[index] = frequency
        currentNote = noteId
        currentString = index
    }

    /**
     * 为一次拨弦（[noteId] 相同表示同一次持续发声）中的一帧选出对应的弦。
     */
    fun assign(frequency: Double, noteId: Long, tuning: Tuning, a4: Double): Int {
        val sticky = currentString
        if (noteId == currentNote && sticky != null && sticky in 0 until stringCount) {
            memory[sticky] = frequency
            return sticky
        }
        var best = 0
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until stringCount) {
            val target = MusicTheory.midiToFrequency(tuning.strings[i], a4)
            var d = abs(MusicTheory.cents(frequency, target))
            memory[i]?.let { d = minOf(d, abs(MusicTheory.cents(frequency, it))) }
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        currentNote = noteId
        currentString = best
        memory[best] = frequency
        return best
    }
}

/** 调准精度：偏差在 ±[cents] 以内算“已调准”。 */
enum class TuneTolerance(val cents: Double, val label: String) {
    STRICT(2.0, "±2 音分"),
    STANDARD(5.0, "±5 音分"),
    RELAXED(10.0, "±10 音分"),
}
