package com.guitarmagic.tuner.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * 把逐帧检测串起来：自适应噪声门 → YIN 检测 → 一致性校验 → 中值平滑 → 短暂保持。
 * 不依赖 Android，便于单元测试。
 *
 * 噪声门不再使用固定音量，而是持续估计环境噪声（噪声底），信号比噪声底高出
 * [openMarginDb] 就开始识别。这样无论手机麦克风灵敏度高低、房间安静与否，
 * 轻轻拨弦也能识别；真正的噪声则由 YIN 的置信度与连续帧一致性校验挡掉。
 */
class PitchTracker(
    sampleRate: Int = 44100,
    /** 比噪声底高出多少 dB 开始识别 */
    private val openMarginDb: Double = 6.0,
    /** 识别过程中降到噪声底以上多少 dB 才停止（滞回） */
    private val closeMarginDb: Double = 3.0,
    /** 绝对下限：再安静的环境，低于此音量也不识别（接近 16 位量化噪声） */
    private val absoluteMinDb: Double = -85.0,
    /** 置信度达到此值，单帧即可采信 */
    private val strongConfidence: Double = 0.85,
    /** 置信度达到此值，需要与上一帧或当前音高一致才采信（用于小音量） */
    private val weakConfidence: Double = 0.65,
    /** 信号消失后保留最后读数的时间 */
    private val holdMillis: Long = 1200,
    initialNoiseFloorDb: Double = -80.0,
) {
    data class Output(
        /** 平滑后的频率；null 表示当前没有可显示的音高 */
        val frequency: Double?,
        /** 当前帧是否没有新的有效检测（显示的是保留的旧值） */
        val holding: Boolean,
        /** 当前帧音量（dBFS） */
        val levelDb: Double,
        /** 当前估计的环境噪声底（dBFS） */
        val noiseFloorDb: Double = -120.0,
    )

    private val detector = YinPitchDetector(sampleRate = sampleRate)
    private val smoother = PitchSmoother()
    private val initialFloor = initialNoiseFloorDb
    private var gateOpen = false
    private var current: Double? = null
    private var lastCandidate: Double? = null
    private var lastValidAt = Long.MIN_VALUE

    /** 当前估计的噪声底（dBFS）。 */
    var noiseFloorDb: Double = initialNoiseFloorDb
        private set

    val minFrameSize: Int get() = detector.minBufferSize

    fun reset() {
        smoother.reset()
        gateOpen = false
        current = null
        lastCandidate = null
        lastValidAt = Long.MIN_VALUE
    }

    /** 连噪声底一起重置（例如重新打开麦克风时）。 */
    fun resetAll() {
        reset()
        noiseFloorDb = initialFloor
    }

    fun process(frame: FloatArray, nowMillis: Long): Output {
        val db = detector.rmsDb(frame)
        val openDb = max(noiseFloorDb + openMarginDb, absoluteMinDb)
        val closeDb = max(noiseFloorDb + closeMarginDb, absoluteMinDb - 3.0)
        gateOpen = if (gateOpen) db >= closeDb else db >= openDb

        var fresh: Double? = null
        var candidate: Double? = null
        if (gateOpen) {
            val result = detector.detect(frame)
            if (result != null && result.confidence >= weakConfidence) {
                candidate = result.frequency
                val consistentWithPrevious = lastCandidate?.let { semitones(result.frequency, it) < 0.6 } == true
                val consistentWithCurrent = current?.let { semitones(result.frequency, it) < 0.6 } == true
                if (result.confidence >= strongConfidence || consistentWithPrevious || consistentWithCurrent) {
                    fresh = smoother.add(result.frequency)
                    lastValidAt = nowMillis
                }
            }
        }
        lastCandidate = candidate
        updateNoiseFloor(db, signalPresent = fresh != null)

        if (fresh != null) {
            current = fresh
        } else if (current != null && nowMillis - lastValidAt > holdMillis) {
            current = null
            smoother.reset()
        }
        return Output(current, holding = fresh == null && current != null, levelDb = db, noiseFloorDb = noiseFloorDb)
    }

    /**
     * 噪声底估计：比当前估计更安静的帧让它快速下降；没有识别到音高的帧
     * （即噪声）让它较快上升（约 9 dB/秒）；正在发音时几乎不动。
     */
    private fun updateNoiseFloor(db: Double, signalPresent: Boolean) {
        val floor = noiseFloorDb
        noiseFloorDb = when {
            db < floor -> floor + 0.3 * (db - floor)
            !signalPresent -> floor + min(db - floor, 0.4)
            else -> floor + 0.01
        }.coerceIn(MIN_FLOOR_DB, MAX_FLOOR_DB)
    }

    private fun semitones(a: Double, b: Double): Double = abs(12.0 * ln(a / b) / ln(2.0))

    private companion object {
        const val MIN_FLOOR_DB = -100.0
        const val MAX_FLOOR_DB = -40.0
    }
}
