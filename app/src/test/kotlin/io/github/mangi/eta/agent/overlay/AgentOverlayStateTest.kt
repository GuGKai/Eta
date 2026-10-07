package io.github.mangi.eta.agent.overlay

import io.github.mangi.eta.agent.runtime.AgentEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentOverlayStateTest {
    @Test
    fun internalStepsCollapseIntoThinkingAndDoNotFlickerDuringTools() {
        var state = AgentOverlayState.Initial.applyEvent(AgentEvent.RunStarted(0, 0, 10, false))
        val internalSteps = listOf(
            AgentEvent.RoundStarted(1, 2),
            AgentEvent.ProviderRequestStarted(1),
            AgentEvent.ProviderResponseStarted(1, 200),
            AgentEvent.AssistantBlockStart(1, AgentEvent.AssistantBlockKind.TOOL_CALL, 0, name = "tap"),
            AgentEvent.AssistantReceived(1, 0, "", listOf("tap")),
        )
        internalSteps.forEach { state = state.applyEvent(it) }
        assertEquals(AgentOverlayStatus.Reasoning, state.status)

        state = state.applyEvent(AgentEvent.ToolStarted(1, "call-1", "tap", "{}"))
        assertEquals(AgentOverlayStatus.RunningTool("tap"), state.status)
        // 工具执行中到达的截图、重试、压缩事件不改写当前文字。
        listOf(
            AgentEvent.ToolImagesAttached(1, "tap", 1, 10),
            AgentEvent.ModelRetryScheduled(1, 1, 3, 1_000, "MODEL_TIMEOUT"),
            AgentEvent.ProviderRequestStarted(2),
        ).forEach { state = state.applyEvent(it) }
        assertEquals(AgentOverlayStatus.RunningTool("tap"), state.status)

        state = state.applyEvent(AgentEvent.ToolFinished(1, "call-1", "tap", "ok", 0, 0))
        assertEquals(AgentOverlayStatus.Reasoning, state.status)
    }

    @Test
    fun pausedStateSurvivesInFlightEventsUntilRunEnds() {
        val paused = AgentOverlayState(phase = AgentOverlayPhase.PAUSED, status = AgentOverlayStatus.Paused)
        listOf(
            AgentEvent.ToolFinished(1, "call-1", "tap", "ok", 0, 0),
            AgentEvent.ToolStarted(2, "call-2", "swipe", "{}"),
            AgentEvent.ProviderRequestStarted(2),
        ).forEach { assertEquals(paused, paused.applyEvent(it)) }
        assertEquals(AgentOverlayPhase.FAILED, paused.applyEvent(AgentEvent.RunFailed("已停止")).phase)
    }
}
