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
import io.github.mangi.eta.agent.device.RootAccess
import io.github.mangi.eta.agent.device.SystemVolumeControl
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.Closeable
import kotlinx.coroutines.CancellationException

/** 只协调本进程的录音和播放；所有入口共用一个租约，资源仍由入口生命周期持有。 */
internal class SpeechAudioLease(private val context: Context, private val interrupt: () -> Unit) : Closeable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val systemVolume = SystemVolumeControl(context)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes())
        .setOnAudioFocusChangeListener({ change -> if (change < 0 && current === this) interrupt() }, handler)
        .build()

    /**
     * 聆听用的瞬态焦点：只用来请别的播放器暂停／让音。丢焦点不打断聆听——这是我们自己申请的。
     */
    private val captureFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ }, handler)
        .build()
    private var registered = false
    private var focused = false
    private var captureFocused = false

    /** 非 null 表示这次聆听是我们把媒体音量压掉了，值就是原本的音量。 */
    private var mutedMediaVolume: Int? = null
    private var mediaWatch: Runnable? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { if (current === this@SpeechAudioLease) interrupt() }
    }

    /**
     * [muteMedia] 用于语音入口的聆听阶段：麦克风会把正在播放的音乐、视频一起收进去，
     * 识别会明显变差，所以聆听期间请别的播放器暂停、并把媒体音量压到 0，
     * 租约关闭（识别结束、取消或出错）时还原。
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

    /**
     * 聆听期间压掉别的播放。
     *
     * 两步走：先申请瞬态焦点请播放器自己暂停（平台正规做法），再把媒体音量压到 0；
     * 之后每 [MEDIA_WATCH_INTERVAL_MS] 复检一次——实测有播放器会把自己的音量重新写回来。
     *
     * 音量这一步现在由 [SystemVolumeControl] 负责：Android 15 起普通应用改不动媒体流音量
     * （public volume API hardening 会静默忽略），它会读回确认并退回 Root 通道。
     * 日志一律用 info：release 构建会剥掉 debug 日志。
     */
    private fun muteMediaForCapture() {
        val audio = manager ?: return
        requestCaptureFocus(audio)
        muteMediaNow()
        startMediaWatch(audio)
    }

    /** 发一次"把媒体音量压到 0"的请求；原值只记第一次看到的那个，供结束还原。 */
    private fun muteMediaNow() {
        val audio = manager ?: return
        val before = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
        if (before == null) {
            AndroidAgentLogger.info("Eta speech listening media mute skipped: volume unavailable")
            return
        }
        if (before <= 0) return
        if (mutedMediaVolume == null) mutedMediaVolume = before
        val observed = systemVolume.apply(AudioManager.STREAM_MUSIC, 0)
        AndroidAgentLogger.info(
            "Eta speech listening media mute requested=$before observed=${observed ?: -1} " +
                "root=${RootAccess.isGranted}",
        )
    }

    /** 每隔一小段时间复检媒体音量：被播放器改回来就再压一次，并记住要还原的值。 */
    private fun startMediaWatch(audio: AudioManager) {
        if (mediaWatch != null) return
        val step = object : Runnable {
            override fun run() {
                mediaWatch = null
                if (current !== this@SpeechAudioLease) return
                val volume = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull()
                if (volume != null && volume > 0) {
                    AndroidAgentLogger.info("Eta speech listening media volume returned=$volume, muting again")
                    muteMediaNow()
                }
                mediaWatch = this
                handler.postDelayed(this, MEDIA_WATCH_INTERVAL_MS)
            }
        }
        mediaWatch = step
        handler.postDelayed(step, MEDIA_WATCH_INTERVAL_MS)
    }

    private fun stopMediaWatch() {
        mediaWatch?.let(handler::removeCallbacks)
        mediaWatch = null
    }

    private fun requestCaptureFocus(audio: AudioManager) {
        if (captureFocused) return
        captureFocused = runCatching {
            audio.requestAudioFocus(captureFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        AndroidAgentLogger.info("Eta speech listening audio focus granted=$captureFocused")
    }

    private fun restoreMediaAfterCapture() {
        stopMediaWatch()
        val volume = mutedMediaVolume ?: return
        mutedMediaVolume = null
        val observed = systemVolume.apply(AudioManager.STREAM_MUSIC, volume)
        AndroidAgentLogger.info(
            "Eta speech listening media restore requested=$volume observed=${observed ?: -1}",
        )
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
        if (captureFocused) runCatching { manager.abandonAudioFocusRequest(captureFocus) }
        captureFocused = false
        restoreMediaAfterCapture()
        // 关闭只停止接收新的 Root 请求，已经在排队的还原命令要跑完。
        systemVolume.close()
        if (current === this) current = null
    }

    private companion object {
        var current: SpeechAudioLease? = null

        /** 复检间隔：太小会频繁写音量，太大则播放器改回来后又响起来。 */
        const val MEDIA_WATCH_INTERVAL_MS = 400L

        fun speechAttributes(): AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    }
}
