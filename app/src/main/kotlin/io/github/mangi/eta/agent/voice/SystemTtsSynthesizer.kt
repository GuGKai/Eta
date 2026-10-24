package io.github.mangi.eta.agent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.Closeable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 系统引擎逐句合成：先把一句话合成到本地 WAV，再读出 PCM 交给播放方播放。
 *
 * 和 [SystemTtsSpeaker] 的区别在于谁来放音：这里引擎只负责合成，播放交给我们自己的音轨，
 * 所以暂停、继续都作用在自己的缓冲上，能从任意位置停住再接着放，不必依赖引擎回报朗读位置。
 */
internal class SystemTtsSynthesizer(context: Context, engine: String?) : Closeable {
    private val appContext = context.applicationContext
    private val enginePackage = engine?.trim()?.takeIf { it.isNotEmpty() }
    private var instance: TextToSpeech? = null
    private var ready: Boolean? = null
    private var initWaiter: CancellableContinuation<Unit>? = null
    private var clipWaiter: CancellableContinuation<Unit>? = null
    private var settledId: String? = null
    private var counter = 0
    private var closed = false

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = settle(utteranceId, null)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = settle(utteranceId, playbackFailure())

        override fun onError(utteranceId: String?, errorCode: Int) = settle(utteranceId, playbackFailure())

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            settle(utteranceId, CancellationException("System TTS synthesis interrupted"))
    }

    /**
     * 合成一句话。返回的 [SystemTtsClip.sampleRate] 是引擎实际输出，播放方必须按它建音轨。
     * 引擎不支持 synthesizeToFile、或输出不是单声道 16 位时抛出异常，由调用方回退。
     */
    suspend fun synthesize(text: String, voice: String?): SystemTtsClip {
        val engine = engine()
        applyVoice(engine, voice)
        val file = File.createTempFile("eta-tts-", ".wav", appContext.cacheDir)
        val utteranceId = "eta-tts-clip-" + (++counter)
        try {
            settledId = null
            if (engine.synthesizeToFile(text, null, file, utteranceId) != TextToSpeech.SUCCESS) {
                throw SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎无法合成这段文字")
            }
            // 引擎可能同步回调 onDone，先到达的结果记在 settledId 里，不会丢。
            if (settledId != utteranceId) {
                withTimeout(SYNTH_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        clipWaiter = continuation
                        continuation.invokeOnCancellation {
                            clipWaiter = null
                            runCatching { instance?.stop() }
                        }
                    }
                }
            }
            val parsed = withContext(Dispatchers.IO) { SystemTtsWav.parse(file.readBytes()) }
                ?: throw SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎输出的音频格式不受支持")
            return parsed.copy(pcm = shapeTailSilence(parsed.pcm, parsed.sampleRate))
        } finally {
            clipWaiter = null
            settledId = null
            runCatching { file.delete() }
        }
    }

    override fun close() {
        closed = true
        initWaiter?.let { waiter -> initWaiter = null; runCatching { waiter.cancel() } }
        clipWaiter?.let { waiter ->
            clipWaiter = null
            runCatching { waiter.resumeWithException(CancellationException("Speech playback stopped")) }
        }
        val engine = instance
        instance = null
        ready = null
        engine?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
    }

    private suspend fun engine(): TextToSpeech {
        val existing = instance
        if (existing != null) {
            if (ready == true) return existing
            if (ready == false) throw playbackFailure()
        } else {
            if (closed) throw SpeechFailure(SpeechErrorCode.AUDIO, "语音播报已停止")
            // 先登记实例再等回调：onInit 可能同步返回，构造后才创建挂起点会丢掉结果。
            instance = TextToSpeech(appContext, { status -> onInit(status) }, enginePackage)
        }
        if (ready == null) {
            try {
                withTimeout(INIT_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        initWaiter = continuation
                        continuation.invokeOnCancellation { initWaiter = null }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                throw SpeechFailure(SpeechErrorCode.TIMEOUT, "系统语音引擎初始化超时，请检查系统的文字转语音设置")
            }
        }
        val engine = instance ?: throw playbackFailure()
        if (ready != true) throw playbackFailure()
        engine.setOnUtteranceProgressListener(progress)
        return engine
    }

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        val waiter = initWaiter
        initWaiter = null
        waiter?.let { continuation ->
            if (ready == true) continuation.resume(Unit) else continuation.resumeWithException(playbackFailure())
        }
    }

    private fun applyVoice(engine: TextToSpeech, voice: String?) {
        val wanted = voice?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val match = engine.voices?.firstOrNull { it.name == wanted } ?: return
        runCatching { engine.voice = match }
    }

    private fun settle(utteranceId: String?, failure: Throwable?) {
        val waiter = clipWaiter
        if (waiter == null) {
            settledId = utteranceId
            return
        }
        clipWaiter = null
        if (failure == null) waiter.resume(Unit) else waiter.resumeWithException(failure)
    }

    private fun playbackFailure(): SpeechFailure {
        val target = enginePackage?.let { "（" + it + "）" }.orEmpty()
        return SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎" + target + "合成失败，请检查该引擎的音色设置")
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 8_000L
        const val SYNTH_TIMEOUT_MS = 20_000L
    }
}
