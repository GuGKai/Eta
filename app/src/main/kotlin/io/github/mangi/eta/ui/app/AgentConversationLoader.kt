package io.github.mangi.eta.ui.app

import android.content.Context
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 进程级会话预加载：在 Activity.onCreate 就开跑，而不是等到首帧组合时才加载。
 *
 * 这样启动画面可以在放行第一帧之前等到数据就绪，避免先画一帧"没有任何会话"的空页面、
 * 再闪回真正要看的会话。
 */
internal object AgentConversationLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Deferred<AgentConversationStore.Snapshot>? = null
    private var cached: AgentConversationStore.Snapshot? = null

    /**
     * 幂等：同一进程内只加载一次，结果由 [takeForState] 或 [takeIfReady] 取走。
     * 传 [assistantConversationKey] 时，该浮窗会话会成为这次加载的选中项，
     * 首帧就直接落到目标会话上。
     */
    fun start(context: Context, assistantConversationKey: String? = null) {
        val preferredId = assistantConversationKey
            ?.takeIf(String::isNotBlank)
            ?.let { archiveConversationId(AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE, it) }
        synchronized(lock) {
            if (job != null || cached != null) return
            val appContext = context.applicationContext
            job = scope.async {
                val snapshot = runCatching {
                    AgentConversationStore.loadFromDisk(appContext, preferredId)
                }.getOrElse { failure ->
                    AndroidAgentLogger.warnThrottled("agent_conversation_preload_failed") {
                        "Agent conversation preload failed: type=${failure.safeLogType()}"
                    }
                    emptySnapshot()
                }
                synchronized(lock) {
                    cached = snapshot
                    job = null
                }
                snapshot
            }
        }
    }

    fun isReady(): Boolean = synchronized(lock) { cached != null }

    /** 等加载完成但不取走结果；供启动画面放行第一帧之前使用。 */
    suspend fun await(context: Context) {
        start(context)
        synchronized(lock) { job }?.await()
    }

    /** 组合线程同步取走已就绪的结果。 */
    fun takeIfReady(): AgentConversationStore.Snapshot? = synchronized(lock) {
        cached?.also { cached = null }
    }

    /** 取走结果；尚未就绪时先等加载完成。 */
    suspend fun takeForState(context: Context): AgentConversationStore.Snapshot {
        start(context)
        synchronized(lock) { job }?.await()
        return synchronized(lock) { cached?.also { cached = null } } ?: emptySnapshot()
    }

    private fun emptySnapshot(): AgentConversationStore.Snapshot = AgentConversationStore.Snapshot(
        selectedConversationId = null,
        conversationsById = emptyMap(),
        titles = emptyMap(),
        updatedAt = emptyMap(),
        pinned = emptySet(),
    )
}
