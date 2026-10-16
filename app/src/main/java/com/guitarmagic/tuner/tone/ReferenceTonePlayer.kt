package com.guitarmagic.tuner.tone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.guitarmagic.tuner.core.ToneSynth
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 播放某根弦的标准参考音（合成的拨弦声）。同一时间只播放一个音。 */
class ReferenceTonePlayer {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "reference-tone").apply { isDaemon = true }
    }
    private var track: AudioTrack? = null // 只在 executor 线程访问

    @Volatile
    private var released = false

    fun play(frequency: Double) {
        if (released || frequency <= 0) return
        executor.execute {
            if (released) return@execute
            releaseTrack()
            try {
                val pcm = ToneSynth.pluck(frequency, SAMPLE_RATE)
                val shorts = ShortArray(pcm.size) { (pcm[it] * 32767f).toInt().toShort() }
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(shorts.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                if (t.state == AudioTrack.STATE_NO_STATIC_DATA || t.state == AudioTrack.STATE_INITIALIZED) {
                    t.write(shorts, 0, shorts.size)
                    t.play()
                    track = t
                } else {
                    t.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "参考音播放失败", e)
            }
        }
    }

    fun stop() {
        if (released) return
        executor.execute { releaseTrack() }
    }

    fun release() {
        if (released) return
        released = true
        executor.execute { releaseTrack() }
        executor.shutdown()
    }

    private fun releaseTrack() {
        val t = track ?: return
        track = null
        try {
            t.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "停止失败", e)
        }
        t.release()
    }

    private companion object {
        const val TAG = "ReferenceTonePlayer"
        const val SAMPLE_RATE = 44100
    }
}
