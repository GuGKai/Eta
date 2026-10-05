package io.github.mangi.eta.agent.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.Closeable
import kotlinx.coroutines.CancellationException

/** 只协调本进程的录音和播放；所有入口共用一个租约，资源仍由入口生命周期持有。 */
internal class SpeechAudioLease(private val context: Context, private val interrupt: () -> Unit) : Closeable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener({ change -> if (change < 0 && current === this) interrupt() }, Handler(Looper.getMainLooper()))
        .build()
    private var registered = false
    private var focused = false

    /** 非 null 表示这次聆听是我们把媒体音量压到了 0，值就是原本的音量。 */
    private var mutedMediaVolume: Int? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { if (current === this@SpeechAudioLease) interrupt() }
    }

    /**
     * [muteMedia] 用于语音入口的聆听阶段：麦克风会把正在播放的音乐、视频一起收进去，
     * 识别会明显变差，所以聆听期间把媒体音量压到 0，租约关闭（识别结束、取消或出错）时还原。
     */
    fun acquire(playback: Boolean, muteMedia: Boolean = false, beforeFocus: () -> Unit = {}) {
        current?.interrupt?.invoke()
        current = this
        try {
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_SCREEN_OFF)
            }, Context.RECEIVER_NOT_EXPORTED)
            registered = true
            if (playback) requestPlaybackFocus(beforeFocus) else if (muteMedia) muteMediaForCapture()
        } catch (error: RuntimeException) {
            close()
            throw error
        }
    }

    /** 只在真的有媒体在播时压音量，避免没有播放时把音量条改到 0 让用户以为音量丢了。 */
    private fun muteMediaForCapture() {
        val audio = manager ?: return
        val volume = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: return
        if (volume <= 0) return
        if (!runCatching { audio.isMusicActive }.getOrDefault(false)) return
        if (!runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }.isSuccess) return
        mutedMediaVolume = volume
        AndroidAgentLogger.debug { "Eta speech listening muted media volume=$volume" }
    }

    private fun restoreMediaAfterCapture() {
        val volume = mutedMediaVolume ?: return
        mutedMediaVolume = null
        val audio = manager ?: return
        // 用户在聆听期间自己调过音量就尊重用户的调整，不再还原。
        val currentVolume = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: return
        if (currentVolume > 0) return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
        AndroidAgentLogger.debug { "Eta speech listening restored media volume=$volume" }
    }

    fun requestPlaybackFocus(beforeFocus: () -> Unit = {}) {
        if (current !== this) throw CancellationException("Speech owner replaced")
        beforeFocus()
        focused = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focused) throw SpeechFailure(SpeechErrorCode.AUDIO, "当前无法获得音频焦点，请稍后重试")
    }

    override fun close() {
        if (registered) context.unregisterReceiver(receiver)
        registered = false
        if (focused) manager.abandonAudioFocusRequest(focus)
        focused = false
        restoreMediaAfterCapture()
        if (current === this) current = null
    }

    private companion object { var current: SpeechAudioLease? = null }
}
