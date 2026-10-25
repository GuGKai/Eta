package io.github.mangi.eta.agent.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException

/** 按 [sampleRate] 建流式音轨；采样率必须与写入的 PCM 一致，否则会变调变速。 */
internal class SpeechPcmOutput(private val sampleRate: Int = DEFAULT_SAMPLE_RATE) : SpeechAudioOutput {
    private var track: AudioTrack? = AudioTrack.Builder()
        // 用媒体通道：朗读跟随媒体音量，与系统 TTS 引擎和其他媒体应用一致。
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(maxOf(sampleRate, AudioTrack.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private var frames = 0L
    private var started = false
    private var finished = false

    @Volatile private var paused = false

    override fun write(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val written = synchronized(this) {
                val audio = track ?: throw CancellationException("Speech playback stopped")
                check(!finished) { "Speech audio input already finished" }
                val remaining = bytes.size - offset
                val count = audio.write(bytes, offset, remaining, AudioTrack.WRITE_NON_BLOCKING)
                if (count < 0) throw SpeechFailure(SpeechErrorCode.AUDIO, "音频播放失败")
                frames += count / BYTES_PER_FRAME
                // 空音轨提前启动会在等待云端首包时超时停用；写满后再启动，短写也表示已达到可写上限。
                if (!started && frames > 0 && (frames >= audio.bufferSizeInFrames || count < remaining)) {
                    audio.play()
                    started = true
                }
                count
            }
            offset += written
            // 暂停时音轨不再消费，缓冲很快写满；放慢空转，恢复后继续写入。
            if (written == 0) Thread.sleep(if (paused) PAUSED_POLL_MS else 5)
        }
    }

    @Synchronized override fun finish() {
        val audio = track ?: throw CancellationException("Speech playback stopped")
        if (finished) return
        // 短音频可能永远填不满启动阈值，结束输入时按实际帧数启动，不补静音或截掉正文。
        if (!started && frames > 0) {
            audio.setStartThresholdInFrames(frames.toInt())
            audio.play()
            started = true
        }
        finished = true
    }

    @Synchronized override fun drained(): Boolean = track?.let {
        finished && (it.playbackHeadPosition.toLong() and 0xffffffffL) >= frames
    } ?: true

    /** 只停在这里，不清缓冲、不释放音轨：继续时从这一帧接着放。 */
    @Synchronized override fun pause() {
        paused = true
        val audio = track ?: return
        if (started) runCatching { audio.pause() }
    }

    @Synchronized override fun resume() {
        paused = false
        val audio = track ?: return
        if (started) runCatching { audio.play() }
    }

    @Synchronized override fun close() {
        track?.let { it.pause(); it.flush(); it.release() }
        track = null
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 24_000
        const val BYTES_PER_FRAME = 2
        const val PAUSED_POLL_MS = 50L
    }
}
