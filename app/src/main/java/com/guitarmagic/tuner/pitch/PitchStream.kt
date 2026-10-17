package com.guitarmagic.tuner.pitch

/**
 * 连续音频流的处理管线：带通滤波 → 滑动窗口（帧长 [frameSize]，每 [hopSize] 个采样出一帧）
 * → [PitchTracker]。纯 Kotlin，可用合成音频流做单元测试。
 */
class PitchStream(
    private val sampleRate: Int = 44100,
    val frameSize: Int = 4096,
    val hopSize: Int = 2048,
    private val tracker: PitchTracker = PitchTracker(sampleRate = sampleRate),
) {
    init {
        require(hopSize in 1..frameSize)
        require(frameSize >= tracker.minFrameSize)
    }

    // 50 Hz 四阶巴特沃斯高通（两级级联）去掉低频轰鸣与手持振动，2.2 kHz 低通去掉高频嘶声；
    // 吉他基频 60–1400 Hz 基本不受影响（最低的 B1 约衰减 0.7 dB）
    private val highPass1 = Biquad.highPass(sampleRate, 50.0, q = 0.5411961)
    private val highPass2 = Biquad.highPass(sampleRate, 50.0, q = 1.3065630)
    private val lowPass = Biquad.lowPass(sampleRate, 2200.0)
    private val window = FloatArray(frameSize)
    private var filled = 0

    val noiseFloorDb: Double get() = tracker.noiseFloorDb

    /** 清空窗口与识别状态（保留噪声底估计）。 */
    fun reset() {
        window.fill(0f)
        filled = 0
        highPass1.reset()
        highPass2.reset()
        lowPass.reset()
        tracker.reset()
    }

    /**
     * 送入恰好 [hopSize] 个采样（-1..1）。窗口填满之前返回 null。
     */
    fun processHop(hop: FloatArray, nowMillis: Long): PitchTracker.Output? {
        require(hop.size >= hopSize)
        System.arraycopy(window, hopSize, window, 0, frameSize - hopSize)
        val offset = frameSize - hopSize
        for (i in 0 until hopSize) {
            window[offset + i] = lowPass.process(highPass2.process(highPass1.process(hop[i].toDouble()))).toFloat()
        }
        filled = minOf(frameSize, filled + hopSize)
        if (filled < frameSize) return null
        return tracker.process(window, nowMillis)
    }
}
