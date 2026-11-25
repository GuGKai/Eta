package io.github.mangi.eta.agent.tool

import android.content.Context
import io.github.mangi.eta.agent.model.catalog.AgentAskQuestionToolCatalog
import io.github.mangi.eta.agent.model.provider.AgentModelClient
import io.github.mangi.eta.agent.model.AskCardMessageId
import io.github.mangi.eta.agent.model.AskQuestionAnswer
import io.github.mangi.eta.agent.model.AskQuestionCard
import io.github.mangi.eta.agent.runtime.AgentRunCancelledException
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.runtime.AskNotificationManager
import io.github.mangi.eta.agent.runtime.AskOutcome
import io.github.mangi.eta.agent.runtime.AskPresence
import io.github.mangi.eta.agent.runtime.PendingAskRegistry
import io.github.mangi.eta.core.AndroidAgentLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * 会话页发起的运行里，`ask_user_question` 改成「真挂起」。
 *
 * 工具线程在这里阻塞等答案，用户点完卡片，答案随工具结果回到模型手里，
 * 同一轮对话接着往下做；运行侧取消（停止、插话打断整轮）会立刻把等待叫醒按取消处理。
 *
 * 定时任务、语音面板、子智能体不走这条路径：它们拿到的仍是原来的「提问即收尾」行为。
 */
internal class AskQuestionSuspender(
    private val context: Context,
    private val runId: String,
    private val conversationId: String?,
    private val runController: AgentRunController,
    /** 回退实现：参数不成立、或同一条问题已经在挂起时交给它收尾。 */
    private val fallback: AgentModelClient.ToolExecutor,
    private val maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
) : AgentModelClient.ToolExecutor {

    override fun execute(toolCall: AgentModelClient.ToolCall): AgentModelClient.ToolResult {
        if (toolCall.name != AgentAskQuestionToolCatalog.TOOL_NAME) return fallback.execute(toolCall)
        val card = runCatching { JSONObject(toolCall.argumentsJson.ifBlank { "{}" }) }
            .getOrNull()
            ?.let(AskQuestionCard::from)
            ?: AskQuestionCard.Empty
        // 参数不成立时交回原实现报错，校验只写一遍。
        if (card.isEmpty) return fallback.execute(toolCall)

        val messageId = AskCardMessageId.of(runId, toolCall.id)
        val pending = PendingAskRegistry.register(
            messageId = messageId,
            runId = runId,
            toolCallId = toolCall.id,
            conversationId = conversationId,
        ) ?: return fallback.execute(toolCall)

        val binding = runController.register(pending::release)
        try {
            // 卡片已经画在会话里了；只有用户看不到那个会话页时才需要额外通知。
            if (!AskPresence.isShowingConversation(conversationId)) {
                AskNotificationManager.notifyPendingAsk(context, conversationId, card, messageId)
            }
            AndroidAgentLogger.info(
                "Ask suspend: run=$runId tool=${toolCall.id} conversation=${conversationId.orEmpty()} " +
                    "questions=${card.questions.size}",
            )
            return when (val outcome = pending.await(maxWaitMs)) {
                is AskOutcome.Answered -> answeredResult(card, outcome.text)
                AskOutcome.Cancelled -> throw AgentRunCancelledException()
                AskOutcome.TimedOut -> timedOutResult()
            }
        } finally {
            binding.close()
            PendingAskRegistry.forget(pending)
            AskNotificationManager.dismiss(context, conversationId, messageId)
        }
    }

    private fun answeredResult(
        card: AskQuestionCard,
        answerText: String,
    ): AgentModelClient.ToolResult {
        val perQuestion = AskQuestionAnswer.perQuestion(answerText, card.questions.size)
        val content = JSONObject()
            .put("ok", true)
            .put("tool", AgentAskQuestionToolCatalog.TOOL_NAME)
            .put("status", "answered")
            .put("question_count", card.questions.size)
            .put("answers", JSONArray().also { array -> perQuestion.forEach { array.put(it) } })
            .put("answer_text", answerText.trim())
            .put(
                "message",
                "用户已经在提问卡片上选好并提交了答案：answers 按题序排列，每题一项，" +
                    "多选用「、」连接，自由输入的那一格会直接是用户的原话。" +
                    "请直接据此继续完成原来的任务，不要再重复提问、也不要再等用户确认。",
            )
            .toString()
        return AgentModelClient.ToolResult(content)
    }

    private fun timedOutResult(): AgentModelClient.ToolResult {
        val minutes = (maxWaitMs / 60_000L).coerceAtLeast(1L)
        val content = JSONObject()
            .put("ok", false)
            .put("tool", AgentAskQuestionToolCatalog.TOOL_NAME)
            .put("code", "USER_NO_ANSWER")
            .put(
                "message",
                "用户 $minutes 分钟内没有作答，本次提问视为未答。不要重复提问；" +
                    "按现有信息能做的先做完，并在回答里说明还缺哪些信息。",
            )
            .toString()
        return AgentModelClient.ToolResult(content)
    }

    private companion object {
        /** 挂起上限：超过就当用户不打算答，本轮按未答收尾，别让线程一直挂着。 */
        const val DEFAULT_MAX_WAIT_MS = 30L * 60L * 1000L
    }
}
