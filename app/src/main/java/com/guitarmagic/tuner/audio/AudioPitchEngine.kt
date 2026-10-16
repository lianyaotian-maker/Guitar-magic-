package com.guitarmagic.tuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.guitarmagic.tuner.pitch.PitchTracker
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
        data class Pitch(val output: PitchTracker.Output) : Event
        data class Error(val message: String) : Event
    }

    private var job: Job? = null

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
        val tracker = PitchTracker(sampleRate = SAMPLE_RATE)
        val window = FloatArray(FRAME_SIZE)
        val hop = ShortArray(HOP_SIZE)
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onEvent(Event.Error("无法开始录音，可能正被其他应用占用"))
                return
            }
            var filled = 0
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
                // 滑动窗口：丢掉最旧的 HOP_SIZE 个采样，追加新采样
                System.arraycopy(window, HOP_SIZE, window, 0, FRAME_SIZE - HOP_SIZE)
                val offset = FRAME_SIZE - HOP_SIZE
                for (i in 0 until HOP_SIZE) window[offset + i] = hop[i] / 32768f
                filled = minOf(FRAME_SIZE, filled + HOP_SIZE)
                if (filled < FRAME_SIZE) continue
                onEvent(Event.Pitch(tracker.process(window, SystemClock.elapsedRealtime())))
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
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
