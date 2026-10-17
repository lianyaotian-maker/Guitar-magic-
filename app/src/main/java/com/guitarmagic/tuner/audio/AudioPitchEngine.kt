package com.guitarmagic.tuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.guitarmagic.tuner.pitch.DetectionRange
import com.guitarmagic.tuner.pitch.PitchStream
import com.guitarmagic.tuner.pitch.PitchTracker
import com.guitarmagic.tuner.pitch.Sensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 用 AudioRecord 采集麦克风（44100Hz、单声道、16 位 PCM），
 * 每 HOP_SIZE 个采样做一次检测（帧长 FRAME_SIZE，约 93ms，50% 重叠）。
 */
class AudioPitchEngine(private val context: Context) {

    sealed interface Event {
        /** @param suppressed 本帧被屏蔽（正在播放参考音或节拍器运行中），output 不含音高 */
        data class Pitch(val output: PitchTracker.Output, val suppressed: Boolean = false) : Event
        data class Error(val message: String) : Event
    }

    private var job: Job? = null

    /** 在此时间点（SystemClock.elapsedRealtime）之前屏蔽识别，用于播放参考音时。 */
    @Volatile
    private var suppressUntil = 0L

    /** 短暂暂停（例如播放调准提示音时）：期间丢弃麦克风数据，但保留识别状态，之后继续跟踪。 */
    @Volatile
    private var pauseUntil = 0L

    @Volatile
    private var resetRequested = false

    fun pauseFor(millis: Long) {
        pauseUntil = maxOf(pauseUntil, SystemClock.elapsedRealtime() + millis)
    }

    /** 清空当前识别状态（例如切换调弦预设时，旧读数不再有意义）。 */
    fun resetRecognition() {
        resetRequested = true
    }

    /** 持续屏蔽识别（例如节拍器正在运行）。 */
    @Volatile
    var externallySuppressed = false

    /** 允许识别的音域（随调弦、锁定弦与识别模式变化）。 */
    @Volatile
    var detectionRange: ClosedFloatingPointRange<Double> = DetectionRange.FULL_MIN_HZ..DetectionRange.FULL_MAX_HZ

    @Volatile
    var sensitivity: Sensitivity = Sensitivity.MEDIUM

    /** 从现在起屏蔽识别 [millis] 毫秒（不会缩短已有的屏蔽时间）。 */
    fun suppressFor(millis: Long) {
        suppressUntil = maxOf(suppressUntil, SystemClock.elapsedRealtime() + millis)
    }

    /** 把屏蔽时间改为从现在起 [millis] 毫秒（可缩短，例如提前停止参考音后只留余音时间）。 */
    fun setSuppressionFor(millis: Long) {
        suppressUntil = SystemClock.elapsedRealtime() + millis
    }

    val isRunning: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope, onEvent: (Event) -> Unit) {
        if (isRunning) return
        job = scope.launch(Dispatchers.IO) { runLoop(onEvent) }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    @SuppressLint("MissingPermission") // 调用方保证已获得 RECORD_AUDIO 权限
    private fun createRecorder(): AudioRecord? {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBytes <= 0) return null
        val bufferBytes = maxOf(minBytes, FRAME_SIZE * 2 * 2)
        for (source in audioSources()) {
            try {
                val recorder = AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferBytes)
                if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
                recorder.release()
            } catch (e: Exception) {
                Log.w(TAG, "音频源 $source 不可用", e)
            }
        }
        return null
    }

    /** 优先使用未经处理的原始音频（无自动增益/降噪），否则用语音识别源，最后退回普通麦克风。 */
    private fun audioSources(): List<Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
    }

    private suspend fun runLoop(onEvent: (Event) -> Unit) {
        val recorder = createRecorder()
        if (recorder == null) {
            onEvent(Event.Error("无法打开麦克风，可能正被其他应用占用"))
            return
        }
        val stream = PitchStream(sampleRate = SAMPLE_RATE, frameSize = FRAME_SIZE, hopSize = HOP_SIZE)
        val hop = ShortArray(HOP_SIZE)
        val hopFloats = FloatArray(HOP_SIZE)
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onEvent(Event.Error("无法开始录音，可能正被其他应用占用"))
                return
            }
            val loopContext = currentCoroutineContext()
            while (loopContext.isActive) {
                var read = 0
                while (read < HOP_SIZE && loopContext.isActive) {
                    val n = recorder.read(hop, read, HOP_SIZE - read)
                    if (n < 0) {
                        onEvent(Event.Error("读取麦克风数据失败（错误码 $n）"))
                        return
                    }
                    read += n
                }
                if (!loopContext.isActive) break
                val now = SystemClock.elapsedRealtime()
                if (resetRequested) {
                    resetRequested = false
                    stream.reset()
                }
                if (now < pauseUntil) {
                    stream.clearWindow()
                    continue
                }
                if (externallySuppressed || now < suppressUntil) {
                    // 屏蔽期间丢弃麦克风数据并清空识别状态，参考音/滴答声不会残留到之后的读数里
                    stream.reset()
                    onEvent(Event.Pitch(PitchTracker.Output(null, false, -120.0, stream.noiseFloorDb), suppressed = true))
                    continue
                }
                for (i in 0 until HOP_SIZE) hopFloats[i] = hop[i] / 32768f
                stream.sensitivity = sensitivity
                val range = detectionRange
                val output = stream.processHop(hopFloats, now, range.start, range.endInclusive) ?: continue
                onEvent(Event.Pitch(output))
            }
        } finally {
            try {
                recorder.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "停止录音失败", e)
            }
            recorder.release()
        }
    }

    companion object {
        private const val TAG = "AudioPitchEngine"
        const val SAMPLE_RATE = 44100
        const val FRAME_SIZE = 4096
        const val HOP_SIZE = 2048
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
