package com.guitarmagic.tuner.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** 每拍细分。 */
enum class Subdivision(val ticksPerBeat: Int, val label: String) {
    NONE(1, "不细分"),
    EIGHTH(2, "八分"),
    TRIPLET(3, "三连音"),
    SIXTEENTH(4, "十六分"),
}

data class MetronomeConfig(
    val bpm: Int = 100,
    val beatsPerBar: Int = 4,
    val subdivision: Subdivision = Subdivision.NONE,
    val accentFirst: Boolean = true,
) {
    fun normalized(): MetronomeConfig = copy(
        bpm = bpm.coerceIn(MIN_BPM, MAX_BPM),
        beatsPerBar = beatsPerBar.coerceIn(MIN_BEATS, MAX_BEATS),
    )

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 250
        const val MIN_BEATS = 1
        const val MAX_BEATS = 12
    }
}

enum class ClickType { ACCENT, BEAT, SUB }

/**
 * 一次“滴答”。
 * @param frame 在输出音频流中的采样位置
 * @param beat 小节内第几拍（0 起）
 * @param sub 拍内第几个细分（0 = 正拍）
 * @param beatSerial 从开始到现在的第几拍（只在正拍上递增），用于摆锤左右交替
 */
data class Tick(
    val frame: Long,
    val beat: Int,
    val sub: Int,
    val type: ClickType,
    val beatSerial: Long,
)

/** 三种滴答声：重拍、普通拍、细分拍。 */
class ClickSounds(sampleRate: Int) {
    val accent: FloatArray = click(sampleRate, 1760.0, 0.95)
    val beat: FloatArray = click(sampleRate, 1180.0, 0.75)
    val sub: FloatArray = click(sampleRate, 900.0, 0.38)

    fun forType(type: ClickType): FloatArray = when (type) {
        ClickType.ACCENT -> accent
        ClickType.BEAT -> beat
        ClickType.SUB -> sub
    }

    private fun click(sampleRate: Int, frequency: Double, gain: Double): FloatArray {
        val length = (0.035 * sampleRate).toInt()
        val attack = (0.0008 * sampleRate).coerceAtLeast(1.0)
        return FloatArray(length) { i ->
            val t = i.toDouble() / sampleRate
            val env = minOf(1.0, i / attack) * exp(-t / 0.007)
            val tone = sin(2 * PI * frequency * t) + 0.35 * sin(2 * PI * frequency * 2.01 * t)
            (gain * env * tone / 1.35).toFloat()
        }
    }
}

/**
 * 节拍器调度与混音：按采样计数生成滴答声，长时间运行也不会漂移。
 * 纯 Kotlin，不依赖 Android，可单元测试。线程安全：[updateConfig] 可在任意线程调用，
 * 新设置在下一次 [render] 开始时生效。
 */
class ClickScheduler(
    private val sampleRate: Int,
    initialConfig: MetronomeConfig,
    private val sounds: ClickSounds = ClickSounds(sampleRate),
) {
    private var config = initialConfig.normalized()

    @Volatile
    private var pendingConfig: MetronomeConfig? = null

    private var position = 0L // 下一次 render 的起始采样
    private var nextTickExact = 0.0 // 下一个滴答的精确位置（小数采样）
    private var lastTickExact = Double.NaN
    private var beat = 0
    private var sub = 0
    private var beatSerial = -1L

    private val voices = ArrayList<Voice>()

    private class Voice(val sound: FloatArray, var offset: Int)

    val currentConfig: MetronomeConfig get() = pendingConfig ?: config

    fun updateConfig(newConfig: MetronomeConfig) {
        pendingConfig = newConfig.normalized()
    }

    /** 每个细分之间的采样数。 */
    fun intervalFrames(c: MetronomeConfig = config): Double =
        sampleRate * 60.0 / (c.bpm * c.subdivision.ticksPerBeat)

    /** 一拍的采样数。 */
    fun beatFrames(c: MetronomeConfig = currentConfig): Double = sampleRate * 60.0 / c.bpm

    /** 生成 frames 个采样到 out（-1..1），每遇到一个滴答调用 onTick。 */
    fun render(out: FloatArray, frames: Int = out.size, onTick: (Tick) -> Unit = {}) {
        applyPendingConfig()
        var nextAt = nextTickExact.roundToLong()
        for (i in 0 until frames) {
            val frame = position + i
            while (frame >= nextAt) {
                emitTick(nextAt, onTick)
                lastTickExact = nextTickExact
                nextTickExact += intervalFrames()
                nextAt = nextTickExact.roundToLong()
            }
            var sample = 0f
            if (voices.isNotEmpty()) {
                val it = voices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    sample += v.sound[v.offset]
                    v.offset++
                    if (v.offset >= v.sound.size) it.remove()
                }
            }
            out[i] = sample
        }
        position += frames
    }

    private fun emitTick(frame: Long, onTick: (Tick) -> Unit) {
        val type = when {
            sub != 0 -> ClickType.SUB
            beat == 0 && config.accentFirst && config.beatsPerBar > 1 -> ClickType.ACCENT
            else -> ClickType.BEAT
        }
        if (sub == 0) beatSerial++
        voices += Voice(sounds.forType(type), 0)
        onTick(Tick(frame, beat, sub, type, beatSerial))
        sub++
        if (sub >= config.subdivision.ticksPerBeat) {
            sub = 0
            beat = (beat + 1) % config.beatsPerBar
        }
    }

    private fun applyPendingConfig() {
        val next = pendingConfig ?: return
        pendingConfig = null
        val old = config
        config = next
        if (next.subdivision != old.subdivision && sub != 0) {
            // 细分方式变了且正处在拍子中间：从下一拍的正拍重新开始
            sub = 0
            beat = (beat + 1) % next.beatsPerBar
        }
        if (beat >= next.beatsPerBar) {
            beat = 0
            sub = 0
        }
        if (!lastTickExact.isNaN() && (next.bpm != old.bpm || next.subdivision != old.subdivision)) {
            // 速度改变：下一个滴答按新间隔从上一个滴答算起，立即响应
            nextTickExact = maxOf(lastTickExact + intervalFrames(next), position.toDouble())
        }
    }
}

/** 敲击测速：连续点击按钮，根据最近几次的间隔计算 BPM。 */
class TapTempo(
    private val maxGapMillis: Long = 2000,
    private val maxTaps: Int = 6,
) {
    private val taps = ArrayDeque<Long>()

    fun reset() = taps.clear()

    /** 记录一次点击，有足够数据时返回 BPM（已限制在 30..250）。 */
    fun tap(nowMillis: Long): Int? {
        val last = taps.lastOrNull()
        if (last != null && (nowMillis - last > maxGapMillis || nowMillis <= last)) taps.clear()
        taps.addLast(nowMillis)
        while (taps.size > maxTaps) taps.removeFirst()
        if (taps.size < 2) return null
        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }.sorted()
        val mid = intervals.size / 2
        val median = if (intervals.size % 2 == 1) intervals[mid] else (intervals[mid - 1] + intervals[mid]) / 2
        return (60000.0 / median).roundToInt().coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM)
    }
}

/** 意大利速度术语。 */
object TempoMarkings {
    fun of(bpm: Int): String = when {
        bpm < 40 -> "Grave 庄板"
        bpm < 60 -> "Largo 广板"
        bpm < 66 -> "Larghetto 小广板"
        bpm < 76 -> "Adagio 柔板"
        bpm < 108 -> "Andante 行板"
        bpm < 120 -> "Moderato 中板"
        bpm < 156 -> "Allegro 快板"
        bpm < 176 -> "Vivace 活泼的快板"
        bpm < 200 -> "Presto 急板"
        else -> "Prestissimo 最急板"
    }
}
