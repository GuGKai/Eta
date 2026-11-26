package io.github.mangi.eta.agent.voice.tts

import android.content.Context
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.AppStrings
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.github.mangi.eta.agent.voice.audio.SpeechAudioLease
import io.github.mangi.eta.agent.voice.audio.SpeechErrorCode
import io.github.mangi.eta.agent.voice.audio.SpeechFailure
import io.github.mangi.eta.agent.voice.audio.SpeechPcmOutput
import io.github.mangi.eta.agent.voice.audio.SpeechSegmentFade
import io.github.mangi.eta.agent.voice.audio.speechFailure
import io.github.mangi.eta.agent.voice.audio.validateSpeechSettings
import io.github.mangi.eta.agent.voice.SystemTtsSpeaker
import io.github.mangi.eta.agent.voice.SystemTtsSynthesizer

internal data class SpeechPlaybackState(
    val messageId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 暂停中：已合成的音频与播放位置都保留，继续后从断点接着读。 */
    val paused: Boolean = false,
    /**
     * 正在读第几句（按 [SpeechText.sentences] 的顺序）；界面据此高亮当前句，
     * 拿不到句级进度（例如云端流式合成的分段）时为 -1。
     */
    val sentenceIndex: Int = -1,
)

internal class SpeechPlaybackController(
    private val context: Context,
    internal val scope: CoroutineScope,
    private val beforePlayback: () -> Unit = {},
    private val interruptOnScreenOff: Boolean = true,
    private val afterPlayback: () -> Unit = {},
    /** 暂停/继续后同步宿主（朗读卡片的按钮文案）。 */
    private val onPauseChanged: (Boolean) -> Unit = {},
    private val synthesize: suspend (SpeechSettings, SpeechCredentials, String, (ByteArray) -> Unit) -> Unit = ::synthesizeSpeech,
    /** 整篇单会话流式合成；为 null 时只用逐段请求。 */
    private val streamSynthesize: (suspend (SpeechSettings, SpeechCredentials, List<String>, (ByteArray) -> Unit) -> Unit)? = ::streamSpeech,
    private val createOutput: () -> SpeechAudioOutput = { SpeechPcmOutput() },
    internal val createSystemSpeaker: (SpeechSettings) -> SystemTtsSpeaker = { SystemTtsSpeaker(context, it.systemTtsEngine) },
    internal val createSystemSynthesizer: (SpeechSettings) -> SystemTtsSynthesizer =
        { SystemTtsSynthesizer(context, it.systemTtsEngine) },
) {
    internal val mutableState = MutableStateFlow(SpeechPlaybackState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    internal var generation = 0L
    internal var output: SpeechAudioOutput? = null
    internal var systemSpeaker: SystemTtsSpeaker? = null
    internal var systemSynthesizer: SystemTtsSynthesizer? = null
    internal var systemClipPlayed = false
    private var lease: SpeechAudioLease? = null
    private var playbackActive = false

    fun speak(messageId: String, text: String, settings: SpeechSettings? = null, credentials: SpeechCredentials? = null) {
        stop()
        systemClipPlayed = false
        val session = generation
        mutableState.value = SpeechPlaybackState(messageId, loading = true)
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                lease = SpeechAudioLease(
                    context = context,
                    interruptOnScreenOff = interruptOnScreenOff,
                    interrupt = ::stop,
                    focusLost = { pauseForFocusLoss() },
                ).also { it.acquire(playback = false) }
                val rawConfig = settings ?: SpeechSettingsRepository.settings()
                val rawSecrets = credentials ?: SpeechSettingsRepository.credentials(context)
                val (config, secrets) = SpeechSettingsRepository.resolveDeepSeekFallback(rawConfig, rawSecrets)
                validateSpeechSettings(config, secrets, synthesis = true)
                val readable = withContext(Dispatchers.Default) { SpeechText.readable(text) }
                if (readable.isBlank()) {
                    throw SpeechFailure(
                        SpeechErrorCode.NO_SPEECH,
                        AppStrings.get(R.string.voice_i18n_playback_no_readable_text, "这条消息没有可朗读的正文"),
                    )
                }
                if (readable.length > 30_000) {
                    throw SpeechFailure(
                        SpeechErrorCode.CONFIGURATION,
                        AppStrings.get(R.string.voice_i18n_playback_text_too_long, "正文过长，请选择较短的回答朗读"),
                    )
                }
                lease?.requestPlaybackFocus { beforePlayback(); playbackActive = true }
                if (config.tts == TtsProvider.SYSTEM) {
                    speakWithSystemEngine(session, messageId, readable, config)
                } else {
                    val player = createOutput()
                    output = player
                    playChunks(config, secrets, SpeechText.chunks(readable), player) {
                        markPlaying(session, messageId)
                    }
                    player.finish()
                    awaitDrained(session, player)
                }
                if (session == generation) stop()
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                if (session == generation) {
                    val failure = error.speechFailure()
                    AndroidAgentLogger.warn("Eta speech playback failed: code=${failure.code} type=${error.javaClass.simpleName}")
                    stop()
                    mutableState.value = SpeechPlaybackState(error = failure.userMessage)
                }
            } finally {
                if (session == generation) stop()
            }
        }
    }

    /** 暂停：不关音轨、不丢已合成的音频，继续后从断点接着播。 */
    fun pause() {
        val current = mutableState.value
        if (current.messageId == null || current.paused) return
        mutableState.value = current.copy(paused = true)
        output?.pause()
        systemSpeaker?.interrupt()
        onPauseChanged(true)
    }

    fun resume() {
        val current = mutableState.value
        if (current.messageId == null || !current.paused) return
        mutableState.value = current.copy(paused = false)
        // 焦点可能还在对方手里，点继续时再申请一次；拿不到也照常放音，只是请不动对方让开。
        lease?.reacquireFocus()
        output?.resume()
        onPauseChanged(false)
    }

    /**
     * 别的应用抢走音频焦点（例如切去刷视频、听歌）时只暂停，不终止：
     * 音频缓冲、播放位置、前台服务与实时活动通知都保留，点「继续」从断点接着读。
     */
    private fun pauseForFocusLoss() {
        val current = mutableState.value
        if (current.messageId == null || current.paused) return
        AndroidAgentLogger.info("Eta speech paused by audio focus loss")
        pause()
    }

    /** 朗读卡片上的暂停与继续共用一枚按钮；返回切换后的暂停状态。 */
    fun togglePause(): Boolean {
        if (mutableState.value.paused) {
            resume()
            return false
        }
        pause()
        return true
    }

    /**
     * 合成按网络速度下载，播放按实时速度写入音轨，两者经队列解耦。若在合成回调里直接写音轨，
     * 读流会被播放节奏拖住，服务端也会长时间等待客户端读取。
     *
     * 优先把整篇放进一个流式合成会话，音频是一条连续的流，整体只在首尾淡入淡出。
     * 流式会话在出声前失败（例如账号未开通实时模型、自定义地址不支持 WebSocket）时，
     * 回落到逐段请求：最多领先播放 [PREFETCH_CHUNKS] 段，接缝处由 [SpeechSegmentFade] 淡入淡出。
     * 已经出声后再失败则直接报错，不从头重读。
     */
    private suspend fun playChunks(
        config: SpeechSettings,
        secrets: SpeechCredentials,
        chunks: List<String>,
        player: SpeechAudioOutput,
        onFirstAudio: () -> Unit,
    ) = coroutineScope {
        val queue = Channel<SpeechQueueItem>(Channel.UNLIMITED)
        val ahead = Semaphore(PREFETCH_CHUNKS)
        launch {
            try {
                var streamed = false
                val stream = streamSynthesize
                if (stream != null && supportsSpeechSession(config.tts)) {
                    try {
                        ahead.acquire()
                        stream(config, secrets, chunks) { bytes ->
                            streamed = true
                            queue.trySend(SpeechQueueItem.Audio(bytes))
                        }
                        queue.send(SpeechQueueItem.ChunkEnd)
                        streamed = true
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                        if (streamed) throw error
                        AndroidAgentLogger.warn("Eta speech stream unavailable, using segments: code=${error.speechFailure().code}")
                        ahead.release()
                    }
                }
                if (!streamed) for (chunk in chunks) {
                    ahead.acquire()
                    synthesize(config, secrets, chunk) { bytes -> queue.trySend(SpeechQueueItem.Audio(bytes)) }
                    queue.send(SpeechQueueItem.ChunkEnd)
                }
                queue.close()
            } catch (error: Throwable) {
                // 合成超时是 CancellationException，子协程以它结束不会取消父作用域，必须经队列交给播放端。
                queue.close(error)
            }
        }
        withContext(Dispatchers.IO) {
            var announced = false
            val fade = SpeechSegmentFade()
            // 音轨写满时 write 会休眠重试；可中断才能让宿主取消立即结束这里，随后关闭音轨。
            // 暂停期间音轨不再消费，write 自己等待，不在这里重复轮询，恢复后写到哪就放到哪。
            suspend fun play(bytes: ByteArray) {
                if (bytes.isNotEmpty()) runInterruptible { player.write(bytes) }
            }
            for (item in queue) when (item) {
                is SpeechQueueItem.Audio -> {
                    if (!announced) { announced = true; onFirstAudio() }
                    play(fade.write(item.bytes))
                }
                SpeechQueueItem.ChunkEnd -> {
                    play(fade.end())
                    ahead.release()
                }
            }
        }
    }

    // fork 本地：系统 TTS 引擎链路与暂停/收尾挂起都抽到 SpeechPlaybackControllerLocal.kt，
    // 这里只剩调用（speakWithSystemEngine / awaitDrained / markPlaying 等扩展函数）。

    fun stop() {
        generation++
        output?.close(); output = null
        systemSpeaker?.close(); systemSpeaker = null
        systemSynthesizer?.close(); systemSynthesizer = null
        job?.cancel(); job = null
        lease?.close(); lease = null
        if (playbackActive) afterPlayback()
        playbackActive = false
        mutableState.value = SpeechPlaybackState()
    }
}

private sealed interface SpeechQueueItem {
    class Audio(val bytes: ByteArray) : SpeechQueueItem
    data object ChunkEnd : SpeechQueueItem
}

private const val PREFETCH_CHUNKS = 2

internal interface SpeechAudioOutput : Closeable {
    fun write(bytes: ByteArray)

    /** 已播出的帧数；拿不到播放头（例如引擎自己放音）时返回 null。 */
    fun playedFrames(): Long? = null
    fun finish()
    fun drained(): Boolean

    /** 就地暂停音轨，已缓冲的音频与播放位置都保留，继续时从断点接着放。 */
    fun pause() {}

    fun resume() {}
}

