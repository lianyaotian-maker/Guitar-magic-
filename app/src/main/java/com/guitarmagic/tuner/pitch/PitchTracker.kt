package com.guitarmagic.tuner.pitch

/**
 * 把逐帧检测串起来：噪声门 → YIN 检测 → 中值平滑 → 短暂保持。
 * 不依赖 Android，便于单元测试。
 */
class PitchTracker(
    sampleRate: Int = 44100,
    /** 音量高于该值（dBFS）才开始识别 */
    private val gateOpenDb: Double = -55.0,
    /** 识别过程中音量低于该值才停止（滞回，避免余音时忽隐忽现） */
    private val gateCloseDb: Double = -62.0,
    /** 置信度低于该值的帧被丢弃 */
    private val minConfidence: Double = 0.8,
    /** 信号消失后保留最后读数的时间 */
    private val holdMillis: Long = 1200,
) {
    data class Output(
        /** 平滑后的频率；null 表示当前没有可显示的音高 */
        val frequency: Double?,
        /** 当前帧是否没有新的有效检测（显示的是保留的旧值） */
        val holding: Boolean,
        /** 当前帧音量（dBFS） */
        val levelDb: Double,
    )

    private val detector = YinPitchDetector(sampleRate = sampleRate)
    private val smoother = PitchSmoother()
    private var gateOpen = false
    private var current: Double? = null
    private var lastValidAt = Long.MIN_VALUE

    val minFrameSize: Int get() = detector.minBufferSize

    fun reset() {
        smoother.reset()
        gateOpen = false
        current = null
        lastValidAt = Long.MIN_VALUE
    }

    fun process(frame: FloatArray, nowMillis: Long): Output {
        val db = detector.rmsDb(frame)
        gateOpen = if (gateOpen) db >= gateCloseDb else db >= gateOpenDb

        var fresh: Double? = null
        if (gateOpen) {
            val result = detector.detect(frame)
            if (result != null && result.confidence >= minConfidence) {
                fresh = smoother.add(result.frequency)
                lastValidAt = nowMillis
            }
        }

        if (fresh != null) {
            current = fresh
        } else if (current != null && nowMillis - lastValidAt > holdMillis) {
            current = null
            smoother.reset()
        }
        return Output(current, holding = fresh == null && current != null, levelDb = db)
    }
}
