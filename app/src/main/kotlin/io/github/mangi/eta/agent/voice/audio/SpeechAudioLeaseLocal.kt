package io.github.mangi.eta.agent.voice.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import io.github.mangi.eta.agent.device.RootAccess
import io.github.mangi.eta.agent.device.SystemVolumeControl
import io.github.mangi.eta.core.AndroidAgentLogger

/*
 * fork 本地：AudioFocusRequest 的构建与状态管理 + 聆听期间"压掉别的播放"。
 *
 * 抽离自 SpeechAudioLease.kt（挂载点化，原文件只留调用）。SpeechAudioLease 上游只有一个
 * "抢到焦点就播、丢焦点就打断"的粗粒度策略；本地要的是：
 *  1. 提示音只让我们让一让（duck）时继续朗读，别把整段掐掉；
 *  2. 焦点被真正抢走时只暂停（由 focusLost 回调决定，朗读入口传暂停、录音传取消）；
 *  3. 聆听时请别的播放器暂停、把媒体音量压到 0，结束后还原。
 */

/** 朗读与聆听都用媒体通道的属性：跟随媒体音量，和系统 TTS 引擎、其他媒体应用一致。 */
internal fun speechAttributes(): AudioAttributes =
    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

/**
 * 播放用的瞬态焦点，以及"当前是否持有焦点／是否被要求让一让"的状态。
 *
 * [isCurrent] 用来判断这个租约是否仍是当前持有者（换人之后旧租约不再响应焦点变化）；
 * [onLost] 在焦点被真正抢走时回调，permanent 表示 AUDIOFOCUS_LOSS（不可自动恢复）。
 */
internal class SpeechPlaybackFocus(
    handler: Handler,
    private val isCurrent: () -> Boolean,
    private val onLost: (permanent: Boolean) -> Unit,
) {
    /** 当前是否处于"被要求让一让"（CAN_DUCK）状态；朗读继续，只是被短音盖住。 */
    var ducked = false
        private set

    private var focused = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes())
        // 显式关掉"duck 时自动暂停"：提示音只该让我们让一让，不该把整段朗读掐掉。
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener({ change ->
            if (isCurrent()) {
                AndroidAgentLogger.info("Eta speech audio focus change=$change")
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        focused = false
                        ducked = false
                        onLost(true)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        focused = false
                        ducked = false
                        onLost(false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        // 消息铃声、通知提示音这类短音只会要求我们"让一让"：
                        // 继续朗读即可（一两秒被盖过去比整段被掐掉体验好得多），
                        // 只有真正的抢占（来电、别的朗读）才走上面的暂停分支。
                        ducked = true
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        focused = true
                        ducked = false
                    }
                }
            }
        }, handler)
        .build()

    /** 申请焦点；返回是否拿到。 */
    fun requestFocus(audio: AudioManager): Boolean {
        focused = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focused
    }

    /**
     * 焦点被别的应用抢走后，用户点「继续」时再申请一次。
     * 拿不到也照常放音（音轨不需要焦点），只是无法请对方让开。
     */
    fun reacquire(audio: AudioManager): Boolean {
        focused = runCatching {
            audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        return focused
    }

    fun abandon(audio: AudioManager) {
        if (focused) audio.abandonAudioFocusRequest(focusRequest)
        focused = false
    }
}

/**
 * 聆听期间压掉别的播放。
 *
 * 麦克风会把正在播放的音乐、视频一起收进去，识别会明显变差，所以聆听期间先申请瞬态焦点
 * 请播放器自己暂停（平台正规做法），再把媒体音量压到 0；之后每 [MEDIA_WATCH_INTERVAL_MS]
 * 复检一次——实测有播放器会把自己的音量重新写回来。租约关闭（识别结束、取消或出错）时还原。
 *
 * 音量这一步现在由 [SystemVolumeControl] 负责：Android 15 起普通应用改不动媒体流音量
 * （public volume API hardening 会静默忽略），它会读回确认并退回 Root 通道。
 * 日志一律用 info：release 构建会剥掉 debug 日志。
 */
internal class SpeechCaptureMediaMute(
    context: Context,
    private val handler: Handler,
    private val manager: AudioManager?,
    private val isCurrent: () -> Boolean,
) {
    /**
     * 聆听用的瞬态焦点：只用来请别的播放器暂停／让音。丢焦点不打断聆听——这是我们自己申请的。
     */
    private val captureFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ }, handler)
        .build()

    private val systemVolume = SystemVolumeControl(context)
    private var captureFocused = false

    /** 非 null 表示这次聆听是我们把媒体音量压掉了，值就是原本的音量。 */
    private var mutedMediaVolume: Int? = null
    private var mediaWatch: Runnable? = null

    fun begin() {
        val audio = manager ?: return
        requestCaptureFocus(audio)
        muteMediaNow()
        startMediaWatch(audio)
    }

    /** 结束聆听：放掉聆听焦点、还原媒体音量，并关掉音量通道。 */
    fun end() {
        if (captureFocused) runCatching { manager?.abandonAudioFocusRequest(captureFocus) }
        captureFocused = false
        restoreMediaAfterCapture()
        // 关闭只停止接收新的 Root 请求，已经在排队的还原命令要跑完。
        systemVolume.close()
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
                if (!isCurrent()) return
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

    private companion object {
        /** 复检间隔：太小会频繁写音量，太大则播放器改回来后又响起来。 */
        const val MEDIA_WATCH_INTERVAL_MS = 400L
    }
}
