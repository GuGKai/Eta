package io.github.mangi.eta.agent.runtime

/** 恢复日志只保留 UI 可见事件；工具参数增量仍只存在于当前模型回合。 */
internal fun AgentEvent.recoveryProjection(): AgentEvent? = when (this) {
    is AgentEvent.AssistantBlockDelta ->
        takeUnless { kind == AgentEvent.AssistantBlockKind.TOOL_CALL }
    else -> this
}

/** 网页建议不落盘，GUI 所有权只在当前进程有效；二者保留活动 run 的重连回放。 */
internal fun AgentEvent.recoveryProjection(): AgentEvent? = when (this) {
    is AgentEvent.SearchSuggestions, is AgentEvent.GuiControlChanged -> null
    else -> replayProjection()
}
