package io.github.mangi.eta.agent.runtime

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 挂起中的提问登记簿。
 *
 * Runtime 与界面同进程，答案不需要跨进程传递：工具线程阻塞在 [PendingAsk] 上，
 * 界面拿到用户选择后按消息 ID 投递答案把它唤醒，本轮对话就带着真实答案继续，
 * 不必先结束这一轮再新起一轮。
 */
internal object PendingAskRegistry {
    private val entries = ConcurrentHashMap<String, PendingAsk>()

    /** 登记一条挂起中的提问；同一消息 ID 只允许一条，重复登记返回 null。 */
    fun register(
        messageId: String,
        runId: String,
        toolCallId: String,
        conversationId: String?,
    ): PendingAsk? {
        val pending = PendingAsk(messageId, runId, toolCallId, conversationId)
        return if (entries.putIfAbsent(messageId, pending) == null) pending else null
    }

    fun forget(pending: PendingAsk) {
        entries.remove(pending.messageId, pending)
    }

    /**
     * 界面投递答案。没有对应挂起项时返回 false，调用方继续走老的「另发一条用户消息」路径。
     */
    fun submit(messageId: String, answer: String): Boolean {
        val pending = entries[messageId] ?: return false
        if (!pending.deliver(answer)) return false
        entries.remove(messageId, pending)
        return true
    }

    val pendingCount: Int
        get() = entries.size
}

/** 一条挂起中的提问：等待期间只有一个线程在 [await] 上阻塞。 */
internal class PendingAsk(
    val messageId: String,
    val runId: String,
    val toolCallId: String,
    val conversationId: String?,
) {
    private val latch = CountDownLatch(1)
    private val settled = AtomicBoolean(false)

    @Volatile
    private var answer: String? = null

    /** 投递答案并唤醒等待者；已经结算过的（先答后取消、或重复提交）返回 false。 */
    fun deliver(text: String): Boolean {
        if (!settled.compareAndSet(false, true)) return false
        answer = text
        latch.countDown()
        return true
    }

    /** 运行被取消、或提问提前作废：唤醒等待者按取消处理。 */
    fun release() {
        if (!settled.compareAndSet(false, true)) return
        latch.countDown()
    }

    fun await(timeoutMs: Long): AskOutcome {
        val finished = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return AskOutcome.Cancelled
        }
        answer?.let { return AskOutcome.Answered(it) }
        return if (finished) AskOutcome.Cancelled else AskOutcome.TimedOut
    }
}

internal sealed interface AskOutcome {
    data class Answered(val text: String) : AskOutcome

    data object Cancelled : AskOutcome

    data object TimedOut : AskOutcome
}

/**
 * 用户眼前是不是正开着这个会话页。
 *
 * Runtime 用它决定提问挂起时要不要发通知：只要不是「Eta 在前台且正显示这个会话」，
 * 也就是切到了别的应用、或停在 Eta 内的其它页面，都算看不到。
 */
internal object AskPresence {
    @Volatile
    private var visibleConversationId: String? = null

    fun enter(conversationId: String?) {
        visibleConversationId = conversationId
    }

    fun leave(conversationId: String?) {
        if (visibleConversationId == conversationId) visibleConversationId = null
    }

    val currentConversationId: String?
        get() = visibleConversationId

    fun isShowingConversation(conversationId: String?): Boolean {
        if (conversationId.isNullOrBlank()) return false
        if (visibleConversationId != conversationId) return false
        return io.github.mangi.eta.ui.MainActivity.isInForeground
    }
}
