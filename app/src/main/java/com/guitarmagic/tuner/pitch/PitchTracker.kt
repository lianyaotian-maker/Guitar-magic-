package com.guitarmagic.tuner.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** 识别灵敏度：调整“拨弦触发”所需的最小音量。 */
enum class Sensitivity(val offsetDb: Double, val label: String) {
    LOW(6.0, "低"),
    MEDIUM(0.0, "中"),
    HIGH(-6.0, "高"),
}

/** 识别模式。 */
enum class DetectionMode(val label: String) {
    /** 只识别当前调弦各弦附近的音（推荐，抗干扰最强） */
    GUITAR("吉他琴弦"),
    /** 识别 55–1500 Hz 内的任意音 */
    CHROMATIC("半音阶"),
}

/**
 * 拨弦触发式的音高跟踪器（纯 Kotlin，可单元测试）。
 *
 * 工作方式与专业调音 App 一致：
 * 1. **待命**：不显示任何读数，持续估计环境噪声；
 * 2. **拨弦起音**：音量在短时间内明显升高、且高于“噪声 + 余量”和绝对下限，才打开识别窗口；
 * 3. **确认**：窗口内连续 [CONFIRM_FRAMES] 帧都是高置信度的周期信号、音高彼此相差不超过
 *    [CONFIRM_CENTS] 音分、且落在允许的音域内，才认定为一个琴弦音并开始显示；
 *    说话、滑音、敲击、杂音、持续的背景声都无法同时满足这些条件；
 * 4. **跟随**：琴弦持续发声时，跟随音高的缓慢变化（拧弦钮）；
 *    新的拨弦且音高明显不同（换弦）时重新确认并切换；
 * 5. **释放**：声音衰减到持续门限以下或不再稳定，进入保持状态，读数保留 [holdMillis] 后消失。
 */
class PitchTracker(
    sampleRate: Int = 44100,
    var sensitivity: Sensitivity = Sensitivity.MEDIUM,
    private val holdMillis: Long = 1500,
    private val releaseMillis: Long = 300,
    initialNoiseFloorDb: Double = -75.0,
) {
    data class Output(
        /** 平滑后的频率；null 表示当前没有可显示的音高 */
        val frequency: Double?,
        /** 当前帧没有新的有效检测（显示的是保留的旧值） */
        val holding: Boolean,
        /** 当前帧音量（dBFS） */
        val levelDb: Double,
        /** 当前估计的环境噪声底（dBFS） */
        val noiseFloorDb: Double = -120.0,
        /** 是否正在跟踪一个琴弦音 */
        val tracking: Boolean = false,
        /** 拨弦编号：每确认一次新的拨弦加 1（同一次持续发声期间不变） */
        val noteId: Long = 0,
    )

    private enum class State { IDLE, TRACKING }

    private val detector = YinPitchDetector(sampleRate = sampleRate)
    private val smoother = PitchSmoother(windowSize = 7, emaAlpha = 0.3)
    private val initialFloor = initialNoiseFloorDb
    private val recentDb = ArrayDeque<Double>()
    private val candidates = ArrayList<Double>()
    private var state = State.IDLE
    private var current: Double? = null
    private var lastGoodAt = Long.MIN_VALUE
    private var frameIndex = 0L
    private var onsetUntil = -1L
    private var noteId = 0L

    /** 当前估计的噪声底（dBFS）。 */
    var noiseFloorDb: Double = initialNoiseFloorDb
        private set

    val minFrameSize: Int get() = detector.minBufferSize

    /** 清空识别状态（保留噪声底估计）。 */
    fun reset() {
        smoother.reset()
        recentDb.clear()
        candidates.clear()
        state = State.IDLE
        current = null
        lastGoodAt = Long.MIN_VALUE
        onsetUntil = -1L
    }

    /** 连噪声底一起重置。 */
    fun resetAll() {
        reset()
        noiseFloorDb = initialFloor
    }

    /**
     * 处理一帧。
     * @param minHz/maxHz 允许的音域，范围外的音高一律忽略
     */
    fun process(
        frame: FloatArray,
        nowMillis: Long,
        minHz: Double = 0.0,
        maxHz: Double = Double.MAX_VALUE,
    ): Output {
        frameIndex++
        val db = detector.rmsDb(frame)
        val offset = sensitivity.offsetDb
        val acquireDb = max(noiseFloorDb + ACQUIRE_MARGIN_DB, ACQUIRE_MIN_DB + offset)
        val sustainDb = max(noiseFloorDb + SUSTAIN_MARGIN_DB, SUSTAIN_MIN_DB + offset)

        // 起音检测：比最近一段时间的最低音量高出 ONSET_RISE_DB，且足够响
        val recentMin = recentDb.minOrNull() ?: SILENCE_DB
        if (db >= acquireDb && db - recentMin >= ONSET_RISE_DB) onsetUntil = frameIndex + ONSET_WINDOW_FRAMES
        recentDb.addLast(db)
        while (recentDb.size > ONSET_LOOKBACK_FRAMES) recentDb.removeFirst()

        val result = if (db >= sustainDb) detector.detect(frame) else null
        val frequency = result?.frequency?.takeIf { it in minHz..maxHz }
        val confidence = result?.confidence ?: 0.0
        val candidateOk = frequency != null &&
            frameIndex <= onsetUntil &&
            db >= acquireDb &&
            confidence >= ACQUIRE_CONFIDENCE

        var fresh = false
        when (state) {
            State.IDLE -> {
                if (candidateOk) {
                    candidates += frequency!!
                    if (confirmed()) {
                        startTracking(nowMillis)
                        fresh = true
                    }
                } else {
                    candidates.clear()
                }
            }
            State.TRACKING -> {
                val reference = current
                val follows = frequency != null && reference != null &&
                    confidence >= SUSTAIN_CONFIDENCE &&
                    semitones(frequency, reference) <= FOLLOW_SEMITONES
                if (follows) {
                    current = smoother.add(frequency!!)
                    lastGoodAt = nowMillis
                    fresh = true
                    candidates.clear()
                } else if (candidateOk) {
                    // 音高明显不同的新拨弦（换弦）：重新确认后切换
                    candidates += frequency!!
                    if (confirmed()) {
                        startTracking(nowMillis)
                        fresh = true
                    }
                } else {
                    candidates.clear()
                }
                if (!fresh && nowMillis - lastGoodAt > releaseMillis) state = State.IDLE
            }
        }

        if (state == State.IDLE) {
            updateNoiseFloor(db)
            if (current != null && nowMillis - lastGoodAt > holdMillis) {
                current = null
                smoother.reset()
            }
        }
        return Output(
            frequency = current,
            holding = !fresh && current != null,
            levelDb = db,
            noiseFloorDb = noiseFloorDb,
            tracking = state == State.TRACKING,
            noteId = noteId,
        )
    }

    private fun confirmed(): Boolean {
        if (candidates.size < CONFIRM_FRAMES) return false
        val last = candidates.takeLast(CONFIRM_FRAMES)
        val center = last.sorted()[last.size / 2]
        return last.all { semitones(it, center) * 100 <= CONFIRM_CENTS }
    }

    private fun startTracking(nowMillis: Long) {
        smoother.reset()
        var value = 0.0
        candidates.takeLast(CONFIRM_FRAMES).forEach { value = smoother.add(it) }
        candidates.clear()
        current = value
        state = State.TRACKING
        noteId++
        lastGoodAt = nowMillis
    }

    /**
     * 噪声底估计（只在待命时更新）：更安静的帧让它快速下降，其余帧让它缓慢上升（约 3 dB/秒）。
     * 环境变吵时触发门限随之升高，避免背景声误触发。
     */
    private fun updateNoiseFloor(db: Double) {
        val floor = noiseFloorDb
        noiseFloorDb = if (db < floor) {
            floor + 0.3 * (db - floor)
        } else {
            floor + min(db - floor, 0.15)
        }.coerceIn(MIN_FLOOR_DB, MAX_FLOOR_DB)
    }

    private fun semitones(a: Double, b: Double): Double = abs(12.0 * ln(a / b) / ln(2.0))

    companion object {
        /** 触发识别需比噪声底高出的音量 */
        const val ACQUIRE_MARGIN_DB = 14.0

        /** 触发识别的绝对最低音量（中等灵敏度） */
        const val ACQUIRE_MIN_DB = -64.0

        /** 跟踪中保持识别需比噪声底高出的音量 */
        const val SUSTAIN_MARGIN_DB = 6.0

        /** 跟踪中保持识别的绝对最低音量（中等灵敏度） */
        const val SUSTAIN_MIN_DB = -74.0

        /** 起音：比最近若干帧的最低音量高出的幅度 */
        const val ONSET_RISE_DB = 6.0
        const val ONSET_LOOKBACK_FRAMES = 8

        /** 起音后允许确认的帧数（约 0.37 秒） */
        const val ONSET_WINDOW_FRAMES = 8

        const val ACQUIRE_CONFIDENCE = 0.88
        const val SUSTAIN_CONFIDENCE = 0.75
        const val CONFIRM_FRAMES = 3
        const val CONFIRM_CENTS = 30.0

        /** 跟踪中单帧允许的音高变化（超过视为别的音） */
        const val FOLLOW_SEMITONES = 1.0

        private const val SILENCE_DB = -120.0
        private const val MIN_FLOOR_DB = -95.0
        private const val MAX_FLOOR_DB = -35.0
    }
}
