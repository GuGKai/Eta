package io.github.mangi.eta.agent.overlay

import androidx.compose.runtime.Immutable
import io.github.mangi.eta.agent.runtime.AgentEvent

/** Agent 浮窗所处的阶段。 */
internal enum class AgentOverlayPhase { RUNNING, PAUSED, FINISHED, FAILED }

/**
 * Agent 浮窗的渲染状态。由 [AgentEvent] 流累积而来，[AgentOverlayBubble] 直接消费。
 */
@Immutable
internal data class AgentOverlayState(
    val phase: AgentOverlayPhase = AgentOverlayPhase.RUNNING,
    val round: Int = 0,
    val status: AgentOverlayStatus = AgentOverlayStatus.Reasoning,
    val detailText: String = "",
) {
    companion object {
        val Initial = AgentOverlayState(status = AgentOverlayStatus.Reasoning)
    }
}

/**
 * 将一个 [AgentEvent] 折叠进当前渲染状态。
 *
 * 胶囊与通知只展示用户关心的少数几个阶段：思考、操作某个工具、整理回答。
 * 请求模型、模型已响应、生成工具参数、轮次、读取截图等内部步骤不单独露出：
 * 它们每轮都会出现、持续不到一秒，频繁切换只会干扰用户。
 * 同一工具的完成与下一次调用之间也不切到"完成"，保持在操作态，避免文字来回跳。
 */
internal fun AgentOverlayState.applyEvent(event: AgentEvent): AgentOverlayState {
    // 暂停在下一个检查点生效，之前已在途的工具结束等事件不能把文字切回运行态。
    if (phase == AgentOverlayPhase.PAUSED && event !is AgentEvent.RunFinished && event !is AgentEvent.RunFailed) {
        return this
    }
    return projectEvent(event)
}

private fun AgentOverlayState.projectEvent(event: AgentEvent): AgentOverlayState = when (event) {
    is AgentEvent.RunStarted -> copy(phase = AgentOverlayPhase.RUNNING, status = AgentOverlayStatus.Reasoning, detailText = "")
    is AgentEvent.RoundStarted -> copy(round = event.round).thinkingUnlessWorking()
    is AgentEvent.ProviderRequestStarted -> copy(round = event.round).thinkingUnlessWorking()
    is AgentEvent.ModelRetryScheduled -> copy(round = event.round, detailText = event.displayMessage).thinkingUnlessWorking()
    is AgentEvent.ContextCompaction -> copy(detailText = event.displayMessage).thinkingUnlessWorking()

    is AgentEvent.AssistantBlockDelta -> when (event.kind) {
        AgentEvent.AssistantBlockKind.TEXT -> appendStreamingText(event)
        else -> this
    }

    is AgentEvent.ToolStarted -> running(AgentOverlayStatus.RunningTool(event.name), event.round)
    is AgentEvent.HostedToolStarted -> running(AgentOverlayStatus.HostedToolRunning(event.name), event.round)

    // 工具结束后模型马上开始下一轮思考；直接回到思考态，由下一次工具调用切换文字。
    is AgentEvent.ToolFinished -> running(AgentOverlayStatus.Reasoning, event.round)
    is AgentEvent.HostedToolFinished -> running(AgentOverlayStatus.Reasoning, event.round)

    is AgentEvent.UserSupplementReceived -> copy(
        phase = AgentOverlayPhase.RUNNING,
        status = AgentOverlayStatus.SupplementReceived,
        detailText = "",
    )

    is AgentEvent.RunFinished -> copy(
        phase = AgentOverlayPhase.FINISHED,
        round = event.round,
        status = AgentOverlayStatus.ResultReady,
    )

    is AgentEvent.RunFailed -> copy(
        phase = AgentOverlayPhase.FAILED,
        status = AgentOverlayStatus.RunFailed,
        detailText = event.reason,
    )

    is AgentEvent.AssistantBlockStart,
    is AgentEvent.AssistantBlockEnd,
    is AgentEvent.AssistantReceived,
    is AgentEvent.ProviderResponseStarted,
    is AgentEvent.UsageReceived,
    is AgentEvent.ToolImagesAttached,
    -> this
}

private fun AgentOverlayState.running(status: AgentOverlayStatus, round: Int) =
    copy(phase = AgentOverlayPhase.RUNNING, round = round, status = status)

/** 正在执行工具或输出回答时，后台的请求、重试与压缩事件不打断当前文字。 */
private fun AgentOverlayState.thinkingUnlessWorking(): AgentOverlayState = when (status) {
    is AgentOverlayStatus.RunningTool,
    is AgentOverlayStatus.HostedToolRunning,
    AgentOverlayStatus.GeneratingAnswer,
    AgentOverlayStatus.Paused,
    AgentOverlayStatus.Stopping,
    -> this
    else -> copy(phase = AgentOverlayPhase.RUNNING, status = AgentOverlayStatus.Reasoning)
}

private const val MaxStreamingPreviewChars = 320

private fun AgentOverlayState.appendStreamingText(event: AgentEvent.AssistantBlockDelta): AgentOverlayState {
    val nextPreview = (detailText + event.delta)
        .trimStart()
        .take(MaxStreamingPreviewChars)
    return copy(
        phase = AgentOverlayPhase.RUNNING,
        round = event.round,
        status = AgentOverlayStatus.GeneratingAnswer,
        detailText = nextPreview,
    )
}
