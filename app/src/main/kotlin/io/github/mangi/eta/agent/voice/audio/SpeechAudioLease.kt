package io.github.mangi.eta.agent.voice.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AppStrings
import java.io.Closeable
import kotlinx.coroutines.CancellationException

/** 只协调本进程的录音和播放；所有入口共用一个租约，资源仍由入口生命周期持有。 */
/**
 * [interruptOnScreenOff] 为 false 时锁屏不打断：聊天朗读需要在锁屏和后台继续，
 * 录音与助手浮窗仍随熄屏结束。
 */
internal class SpeechAudioLease(
    private val context: Context,
    private val interruptOnScreenOff: Boolean = true,
    private val interrupt: () -> Unit,
    /** 焦点被别的应用抢走：默认按打断处理，朗读入口传入暂停，保留音频缓冲与通知。 */
    private val focusLost: (permanent: Boolean) -> Unit = { interrupt() },
) : Closeable {
    private val manager = context.getSystemService(AudioManager::class.java)

    private val handler = Handler(Looper.getMainLooper())

    /** fork 本地：播放焦点与"让一让"状态，实现见 SpeechAudioLeaseLocal.kt。 */
    private val playbackFocus = SpeechPlaybackFocus(handler, { current === this }) { permanent ->
        focusLost(permanent)
    }

    /** fork 本地：聆听期间请别的播放器让音并压掉媒体音量，实现见 SpeechAudioLeaseLocal.kt。 */
    private val captureMute = SpeechCaptureMediaMute(context, handler, manager) { current === this }

    private var registered = false
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
                if (interruptOnScreenOff) addAction(Intent.ACTION_SCREEN_OFF)
            }, Context.RECEIVER_NOT_EXPORTED)
            registered = true
            if (playback) requestPlaybackFocus(beforeFocus) else if (muteMedia) captureMute.begin()
        } catch (error: RuntimeException) {
            close()
            throw error
        }
    }

    fun requestPlaybackFocus(beforeFocus: () -> Unit = {}) {
        if (current !== this) throw CancellationException("Speech owner replaced")
        beforeFocus()
        if (!playbackFocus.requestFocus(manager)) {
            throw SpeechFailure(
                SpeechErrorCode.AUDIO,
                AppStrings.get(R.string.voice_i18n_audio_focus_unavailable, "当前无法获得音频焦点，请稍后重试"),
            )
        }
    }

    /** 焦点被别的应用抢走后，用户点「继续」时再申请一次，实现见 SpeechAudioLeaseLocal.kt。 */
    fun reacquireFocus(): Boolean = manager?.let { playbackFocus.reacquire(it) } ?: false

    override fun close() {
        if (registered) context.unregisterReceiver(receiver)
        registered = false
        playbackFocus.abandon(manager)
        captureMute.end()
        if (current === this) current = null
    }

    private companion object { var current: SpeechAudioLease? = null }
}
