package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 设备上已注册的文字转语音引擎；只查询包管理器，不触发初始化或合成。 */
internal object SystemTtsEngines {
    internal data class Engine(val packageName: String, val label: String)

    fun list(context: Context): List<Engine> {
        val manager = context.packageManager
        val resolved = runCatching {
            manager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrNull().orEmpty()
        return resolved.mapNotNull { it.serviceInfo?.packageName }.distinct().map { packageName ->
            val label = runCatching {
                manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull().orEmpty()
            Engine(packageName, label.ifBlank { packageName })
        }.sortedBy { it.label }
    }
}

/**
 * 系统 TTS 朗读：把文本逐段交给设备上的文字转语音引擎（engine 为空表示系统默认引擎），
 * 每段等待 onDone/onError 后才继续，因此停止播报、音频焦点丢失都能及时中断。
 */
internal class SystemTtsSpeaker(context: Context, engine: String?) : Closeable {
    private val appContext = context.applicationContext
    private val enginePackage = engine?.trim()?.takeIf { it.isNotEmpty() }
    private var instance: TextToSpeech? = null
    private var ready: Boolean? = null
    private var initWaiter: CancellableContinuation<Unit>? = null
    private var utteranceWaiter: CancellableContinuation<Unit>? = null
    private var startListener: (() -> Unit)? = null
    private var settledBeforeWait: String? = null
    private var counter = 0
    private var closed = false

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            startListener?.invoke()
        }

        override fun onDone(utteranceId: String?) {
            settle(utteranceId, null)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            settle(utteranceId, playbackFailure())
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            settle(utteranceId, playbackFailure())
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            settle(utteranceId, null)
        }
    }

    /** 朗读一段文本，返回时表示这段已经播完或已被打断。 */
    suspend fun speak(text: String, voice: String?, onStart: () -> Unit = {}) {
        val engine = engine()
        applyVoice(engine, voice)
        val utteranceId = "eta-tts-" + (++counter)
        startListener = onStart
        try {
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                throw SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎拒绝了这次播报")
            }
            if (settledBeforeWait == utteranceId) return
            suspendCancellableCoroutine<Unit> { continuation ->
                utteranceWaiter = continuation
                continuation.invokeOnCancellation {
                    utteranceWaiter = null
                    runCatching { engine.stop() }
                }
            }
        } finally {
            startListener = null
            settledBeforeWait = null
            utteranceWaiter = null
        }
    }

    /** 读取当前引擎的音色名，供设置页展示；失败时由调用方按空列表处理。 */
    suspend fun voices(): List<String> = withContext(Dispatchers.Main.immediate) {
        engine().voices.orEmpty().map { it.name }.filter { it.isNotBlank() }.distinct().sorted()
    }

    override fun close() {
        closed = true
        initWaiter?.let { waiter -> initWaiter = null; runCatching { waiter.cancel() } }
        utteranceWaiter?.let { waiter ->
            utteranceWaiter = null
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
            if (ready == false) throw initFailure()
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
        val engine = instance ?: throw initFailure()
        if (ready != true) throw initFailure()
        engine.setOnUtteranceProgressListener(progress)
        return engine
    }

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        val waiter = initWaiter
        initWaiter = null
        waiter?.let { continuation ->
            if (ready == true) continuation.resume(Unit) else continuation.resumeWithException(initFailure())
        }
    }

    private fun applyVoice(engine: TextToSpeech, voice: String?) {
        val wanted = voice?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val match = engine.voices?.firstOrNull { it.name == wanted } ?: return
        runCatching { engine.voice = match }
    }

    private fun settle(utteranceId: String?, failure: SpeechFailure?) {
        val waiter = utteranceWaiter
        if (waiter == null) {
            settledBeforeWait = utteranceId
            return
        }
        utteranceWaiter = null
        if (failure == null) waiter.resume(Unit) else waiter.resumeWithException(failure)
    }

    private fun initFailure(): SpeechFailure {
        val target = enginePackage?.let { "（" + it + "）" }.orEmpty()
        return SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎" + target + "初始化失败，请检查文字转语音设置")
    }

    private fun playbackFailure(): SpeechFailure =
        SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎播报失败，请检查该引擎的音色设置")

    private companion object { const val INIT_TIMEOUT_MS = 5_000L }
}
