package com.guitarmagic.tuner.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * 节拍器音频引擎：独立高优先级线程持续向 AudioTrack 写入 [ClickScheduler] 生成的音频。
 * 音频按采样计数精确排布，不依赖系统定时器，因此不会忽快忽慢。
 * 界面通过 [visual] 读取“当前正在播放到哪一拍”，用于动画与声音同步。
 */
class MetronomeEngine {

    /** 界面动画所需的播放状态。 */
    data class Visual(
        /** 小节内第几拍（0 起） */
        val beat: Int,
        /** 拍内第几个细分 */
        val sub: Int,
        /** 从开始累计的拍数，用于摆锤左右交替 */
        val beatSerial: Long,
        /** 距离上一拍已经过去的比例（0..1） */
        val beatProgress: Float,
        /** 距离上一个滴答（含细分）已经过去的秒数 */
        val secondsSinceTick: Float,
    )

    private val lock = Any()
    private var track: AudioTrack? = null
    private var trackGeneration = -1
    private var scheduler: ClickScheduler? = null
    private var sampleRate = DEFAULT_SAMPLE_RATE
    private val recentTicks = ArrayDeque<Tick>()

    @Volatile
    private var generation = 0

    @Volatile
    private var latestConfig = MetronomeConfig()

    /** 是否正在播放（已经停止但仍在释放资源的旧线程不算）。 */
    val isRunning: Boolean get() = synchronized(lock) { isRunningLocked() }

    private fun isRunningLocked() = track != null && trackGeneration == generation

    fun start(config: MetronomeConfig) {
        latestConfig = config.normalized()
        val myGeneration: Int
        synchronized(lock) {
            if (isRunningLocked()) return
            myGeneration = ++generation
        }
        Thread({ runLoop(myGeneration) }, "metronome-audio").start()
    }

    fun stop() {
        synchronized(lock) {
            generation++
            try {
                // 立即静音；写线程会在 write 返回后退出并释放资源
                track?.pause()
                track?.flush()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "暂停失败", e)
            }
        }
    }

    fun updateConfig(config: MetronomeConfig) {
        latestConfig = config.normalized()
        synchronized(lock) { scheduler?.updateConfig(latestConfig) }
    }

    fun visual(): Visual? = synchronized(lock) {
        if (!isRunningLocked()) return null
        val t = track ?: return null
        val s = scheduler ?: return null
        val head = try {
            t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        } catch (e: IllegalStateException) {
            return null
        }
        var lastTick: Tick? = null
        var lastBeat: Tick? = null
        for (i in recentTicks.indices.reversed()) {
            val tick = recentTicks[i]
            if (tick.frame > head) continue
            if (lastTick == null) lastTick = tick
            if (tick.sub == 0) {
                lastBeat = tick
                break
            }
        }
        if (lastTick == null || lastBeat == null) return null
        val beatFrames = s.beatFrames()
        Visual(
            beat = lastTick.beat,
            sub = lastTick.sub,
            beatSerial = lastBeat.beatSerial,
            beatProgress = ((head - lastBeat.frame) / beatFrames).toFloat().coerceIn(0f, 1f),
            secondsSinceTick = ((head - lastTick.frame).toFloat() / sampleRate).coerceAtLeast(0f),
        )
    }

    private fun runLoop(myGeneration: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val rate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            .takeIf { it in 8000..192000 } ?: DEFAULT_SAMPLE_RATE
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBytes, rate / 20 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "无法创建 AudioTrack", e)
            return
        }
        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            return
        }
        val newScheduler = ClickScheduler(rate, latestConfig)
        synchronized(lock) {
            if (generation != myGeneration) {
                newTrack.release()
                return
            }
            track = newTrack
            trackGeneration = myGeneration
            scheduler = newScheduler
            sampleRate = rate
            recentTicks.clear()
        }
        val floats = FloatArray(CHUNK)
        val shorts = ShortArray(CHUNK)
        try {
            newTrack.play()
            while (generation == myGeneration) {
                synchronized(lock) {
                    newScheduler.render(floats, CHUNK) { tick ->
                        recentTicks.addLast(tick)
                        while (recentTicks.size > MAX_RECENT_TICKS) recentTicks.removeFirst()
                    }
                }
                for (i in 0 until CHUNK) {
                    shorts[i] = (floats[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                var written = 0
                while (written < CHUNK && generation == myGeneration) {
                    val n = newTrack.write(shorts, written, CHUNK - written)
                    if (n < 0) {
                        Log.w(TAG, "写入音频失败：$n")
                        return
                    }
                    if (n == 0) break // 被暂停
                    written += n
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "节拍器播放中断", e)
        } finally {
            synchronized(lock) {
                if (track === newTrack) {
                    track = null
                    trackGeneration = -1
                    scheduler = null
                    recentTicks.clear()
                }
            }
            try {
                newTrack.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "停止失败", e)
            }
            newTrack.release()
        }
    }

    private companion object {
        const val TAG = "MetronomeEngine"
        const val DEFAULT_SAMPLE_RATE = 48000
        const val CHUNK = 256
        const val MAX_RECENT_TICKS = 64
    }
}
