package io.github.mangi.eta.agent.voice

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

internal data class SpeechPlaybackState(
    val messageId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 暂停中：已合成的音频与播放位置都保留，继续后从断点接着读。 */
    val paused: Boolean = false,
)

internal class SpeechPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val beforePlayback: () -> Unit = {},
    private val interruptOnScreenOff: Boolean = true,
    private val afterPlayback: () -> Unit = {},
    /** 暂停/继续后同步宿主（朗读卡片的按钮文案）。 */
    private val onPauseChanged: (Boolean) -> Unit = {},
    private val synthesize: suspend (SpeechSettings, SpeechCredentials, String, (ByteArray) -> Unit) -> Unit = ::synthesizeSpeech,
    /** 整篇单会话流式合成；为 null 时只用逐段请求。 */
    private val streamSynthesize: (suspend (SpeechSettings, SpeechCredentials, List<String>, (ByteArray) -> Unit) -> Unit)? = ::streamSpeech,
    private val createOutput: () -> SpeechAudioOutput = ::SpeechPcmOutput,
    private val createSystemSpeaker: (SpeechSettings) -> SystemTtsSpeaker = { SystemTtsSpeaker(context, it.systemTtsEngine) },
) {
    private val mutableState = MutableStateFlow(SpeechPlaybackState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var output: SpeechAudioOutput? = null
    private var systemSpeaker: SystemTtsSpeaker? = null
    private var lease: SpeechAudioLease? = null
    private var playbackActive = false

    fun speak(messageId: String, text: String, settings: SpeechSettings? = null, credentials: SpeechCredentials? = null) {
        stop()
        val session = generation
        mutableState.value = SpeechPlaybackState(messageId, loading = true)
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                lease = SpeechAudioLease(context, interruptOnScreenOff) { stop() }.also { it.acquire(playback = false) }
                val config = settings ?: SpeechSettingsRepository.settings()
                val secrets = credentials ?: SpeechSettingsRepository.credentials(context)
                validateSpeechSettings(config, secrets, synthesis = true)
                val readable = withContext(Dispatchers.Default) { SpeechText.readable(text) }
                if (readable.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "这条消息没有可朗读的正文")
                if (readable.length > 30_000) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "正文过长，请选择较短的回答朗读")
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
        output?.resume()
        onPauseChanged(false)
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

    /** 系统 TTS 引擎自己合成并播放，这里只负责分段喂文本并等待每段结束。 */
    private suspend fun speakWithSystemEngine(
        session: Long,
        messageId: String,
        readable: String,
        config: SpeechSettings,
    ) {
        val speaker = createSystemSpeaker(config)
        systemSpeaker = speaker
        val chunks = SpeechText.chunks(readable)
        val announced = java.util.concurrent.atomic.AtomicBoolean(false)
        var index = 0
        // 暂停打断当前段时，引擎最后回报的朗读位置；继续后只补读这一段剩下的字。
        var offset = 0
        try {
            while (index < chunks.size) {
                awaitResume()
                if (session != generation) throw CancellationException("Speech playback replaced")
                val chunk = chunks[index]
                if (offset >= chunk.length) {
                    index++
                    offset = 0
                    continue
                }
                speaker.speak(chunk.substring(offset), config.systemTtsVoice) {
                    if (announced.compareAndSet(false, true)) scope.launch(Dispatchers.Main.immediate) {
                        markPlaying(session, messageId)
                    }
                }
                if (session != generation) throw CancellationException("Speech playback replaced")
                if (mutableState.value.paused) {
                    // 被暂停打断：不前进到下一段，恢复后从这段的断点续读。
                    offset = (offset + speaker.spokenOffset).coerceAtMost(chunk.length)
                    continue
                }
                index++
                offset = 0
            }
        } finally {
            speaker.close()
            if (systemSpeaker === speaker) systemSpeaker = null
        }
    }

    /** 暂停期间挂起播放循环，继续后返回；停止朗读会随 job 取消一起退出。 */
    private suspend fun awaitResume() {
        mutableState.first { !it.paused }
    }

    /** 等音轨把已写入的音频放完；暂停的时间不计入超时，暂停多久都能接着读。 */
    private suspend fun awaitDrained(session: Long, player: SpeechAudioOutput) {
        var waited = 0L
        while (!player.drained()) {
            if (session != generation) throw CancellationException("Speech playback replaced")
            if (mutableState.value.paused) waited = 0 else waited += DRAIN_POLL_MS
            if (waited > DRAIN_TIMEOUT_MS) throw SpeechFailure(SpeechErrorCode.TIMEOUT, "朗读播放超时，请重试")
            delay(DRAIN_POLL_MS)
        }
    }

    private fun markPlaying(session: Long, messageId: String) {
        if (session != generation) return
        mutableState.value = mutableState.value.copy(messageId = messageId, loading = false)
    }

    fun stop() {
        generation++
        output?.close(); output = null
        systemSpeaker?.close(); systemSpeaker = null
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
    fun finish()
    fun drained(): Boolean

    /** 就地暂停音轨，已缓冲的音频与播放位置都保留，继续时从断点接着放。 */
    fun pause() {}

    fun resume() {}
}

private const val DRAIN_POLL_MS = 20L
private const val DRAIN_TIMEOUT_MS = 180_000L
