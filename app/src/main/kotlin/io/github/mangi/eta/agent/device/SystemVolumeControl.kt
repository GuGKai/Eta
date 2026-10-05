package io.github.mangi.eta.agent.device

import android.content.Context
import android.media.AudioManager
import io.github.mangi.eta.core.AgentLogger
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 修改系统流音量，并在公开音量 API 被系统硬化拦掉时退回 Root。
 *
 * Android 15 起对普通应用收紧了公开音量 API（public volume API hardening）：非系统应用调用
 * `setStreamVolume`／`adjustStreamVolume` 会被**静默忽略**——不抛异常，读回也不变。
 * 本机实测（Android 16，`android.media.audio.autoPublicVolumeApiHardening=true`）：
 * 以 Eta 的 UID 把 `STREAM_MUSIC` 从 39 改成 0 或 20 都毫无效果（事件日志里也看不到 Eta），
 * 同一个命令在 Root 下立刻生效。语音聆听时"把媒体音量压到 0"正是这样失效的。
 *
 * 因此这里统一处理：先走公开 API 并读回确认；没生效且 Root 已授权时，把同一个请求交给
 * `cmd media_session volume` 在后台线程执行。Root 不可用时保持原样，只留一条 info 日志，
 * 不做任何越权尝试（也不主动弹 Root 授权）。
 */
internal class SystemVolumeControl(
    context: Context,
    private val logger: AgentLogger = AndroidAgentLogger,
) : Closeable {

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val rootExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EtaSystemVolume")
    }
    private val closed = AtomicBoolean(false)

    val available: Boolean get() = audio != null

    fun currentVolume(stream: Int): Int? = audio?.let { manager ->
        runCatching { manager.getStreamVolume(stream) }.getOrNull()
    }

    /**
     * 尽力把 [stream] 调到 [index]，返回这一刻读回的音量。
     *
     * 公开 API 生效时是同步的；被硬化拦下时 Root 通道异步执行，所以返回的仍是旧值，
     * 调用方按自己的节奏复检即可（Root 通道的成败都写 info 日志）。
     */
    fun apply(stream: Int, index: Int): Int? {
        val manager = audio ?: return null
        runCatching { manager.setStreamVolume(stream, index, 0) }
        val observed = currentVolume(stream)
        if (observed != index) requestRootVolume(stream, index)
        return observed
    }

    /** 只走 Root 通道；未授权、已关闭或线程池拒绝时返回 false。 */
    fun requestRootVolume(stream: Int, index: Int): Boolean {
        if (closed.get()) return false
        if (!RootAccess.isGranted) {
            logger.info("Eta system volume root fallback skipped: unauthorized stream=$stream index=$index")
            return false
        }
        return runCatching {
            rootExecutor.execute { runRootVolumeCommand(stream, index) }
            true
        }.getOrDefault(false)
    }

    private fun runRootVolumeCommand(stream: Int, index: Int) {
        val executor = BoundedRootCommandExecutor(logger, rootAvailable = { true })
        try {
            val result = executor.execute(
                command = "cmd media_session volume --stream $stream --set $index",
                timeoutMillis = ROOT_TIMEOUT_MS,
                maxOutputBytes = 1024,
            )
            if (result.ok) {
                logger.info("Eta system volume set via root stream=$stream index=$index")
            } else {
                logger.info(
                    "Eta system volume root command failed: code=${result.errorCode} " +
                        "exit=${result.exitCode} timeout=${result.timedOut}",
                )
            }
        } finally {
            executor.close()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // 已排队的还原请求必须跑完（shutdownNow 会把它丢掉），所以只停止接收新任务。
        runCatching { rootExecutor.shutdown() }
    }

    private companion object {
        const val ROOT_TIMEOUT_MS = 6_000L
    }
}
