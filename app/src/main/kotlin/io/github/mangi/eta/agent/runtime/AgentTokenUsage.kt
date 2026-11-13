package io.github.mangi.eta.agent.runtime

internal data class AgentTokenUsage(
    val contextTokens: Int? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val cachedTokens: Int? = null,
    val requestDurationMs: Long? = null,
    val stepCount: Int? = null,
    val llmMs: Long? = null,
    val toolMs: Long? = null,
    val ttftMs: Long? = null,
    val ttftSteps: Int? = null,
    val decodeMs: Long? = null,
    val decodeTokens: Int? = null,
) {
    fun merge(other: AgentTokenUsage): AgentTokenUsage = AgentTokenUsage(
        other.contextTokens ?: contextTokens, other.inputTokens ?: inputTokens,
        other.outputTokens ?: outputTokens, other.reasoningTokens ?: reasoningTokens,
        other.cachedTokens ?: cachedTokens, other.requestDurationMs ?: requestDurationMs,
    )

    val isEmpty: Boolean
        get() = contextTokens == null &&
            inputTokens == null &&
            outputTokens == null &&
            reasoningTokens == null &&
            cachedTokens == null &&
            stepCount == null &&
            llmMs == null &&
            toolMs == null &&
            ttftMs == null &&
            ttftSteps == null &&
            decodeMs == null &&
            decodeTokens == null
}
