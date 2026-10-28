package io.github.mangi.eta.agent.subagent

import io.github.mangi.eta.agent.model.AgentLoop
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentProviderClient
import io.github.mangi.eta.agent.model.AgentTraceFormatter
import io.github.mangi.eta.agent.model.EndpointKind
import io.github.mangi.eta.agent.model.ProviderCapabilities
import io.github.mangi.eta.agent.model.ProviderEvent
import io.github.mangi.eta.agent.model.ProviderRequest
import io.github.mangi.eta.agent.model.ProviderResponse
import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentRunController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Subagent 同轮并发派发的最小验证。
 *
 * 放在无 Robolectric 的测试类里：Robolectric 在本机 aarch64 起不来，
 * 上游 `SubagentTest` 的同名用例在本机永远是环境失败，并发行为等于没有覆盖。
 * 这里只用纯 JVM 依赖复现同一条路径，确保「同轮多个 subagent 调用真的同时进入执行」。
 */
class AgentSubagentConcurrencyTest {
    @Test
    fun multipleSubagentCallsInOneTurnRunConcurrentlyAndKeepOrder() {
        val startedLatch = CountDownLatch(2)
        val releaseLatch = CountDownLatch(2)
        val events = mutableListOf<AgentEvent>()

        val provider = object : AgentProviderClient {
            override val id: String = "fake-provider"
            override val capabilities = ProviderCapabilities(
                endpoint = EndpointKind.CHAT_COMPLETIONS,
                streamingText = true,
                streamingToolCalls = true,
                imageInput = false,
                toolResultImages = false,
                strictTools = false,
                parallelToolCalls = true,
            )
            private var callCount = 0

            override fun complete(
                request: ProviderRequest,
                runController: AgentRunController,
                onEvent: (ProviderEvent) -> Unit,
            ): ProviderResponse {
                callCount += 1
                if (callCount == 1) {
                    fun subagent(id: String, type: String, description: String): JSONObject =
                        JSONObject()
                            .put("id", id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", "subagent").put(
                                    "arguments",
                                    JSONObject()
                                        .put("subagent_type", type)
                                        .put("description", description)
                                        .put("prompt", "分析 $description")
                                        .toString(),
                                ),
                            )

                    val toolCalls = JSONArray()
                        .put(subagent("call_1", "explore", "检索日历"))
                        .put(subagent("call_2", "coder", "检查日志"))
                    return ProviderResponse(
                        assistantMessage = JSONObject()
                            .put("role", "assistant")
                            .put("content", "")
                            .put("finish_reason", "tool_calls")
                            .put("tool_calls", toolCalls),
                    )
                }
                return ProviderResponse(
                    assistantMessage = JSONObject()
                        .put("role", "assistant")
                        .put("content", "汇总完成")
                        .put("finish_reason", "stop"),
                )
            }
        }

        val tools = JSONArray().put(AgentSubagentToolCatalog.schema())
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", "开始"))
        val loop = AgentLoop(
            config = AgentModelClient.ModelConfig(
                baseUrl = "https://example.com/v1",
                apiKey = "test-key",
                model = "test-model",
                systemPrompt = "",
            ),
            messages = messages,
            tools = tools,
            provider = provider,
            toolExecutor = AgentModelClient.ToolExecutor { call ->
                startedLatch.countDown()
                assertTrue(
                    "同一轮的多个 subagent 调用必须并发进入执行",
                    startedLatch.await(3, TimeUnit.SECONDS),
                )
                releaseLatch.countDown()
                val snapshot = SubagentActivitySnapshot(
                    subagentId = "sub_${call.id}",
                    subagentType = "explore",
                    description = call.id,
                    status = SubagentActivitySnapshot.STATUS_COMPLETED,
                    output = "result-${call.id}",
                ).encode()
                AgentModelClient.ToolResult(
                    content = JSONObject()
                        .put("ok", true)
                        .put("tool_calls", 2)
                        .put("summary", "result-${call.id}")
                        .toString(),
                    command = snapshot,
                )
            },
            runController = AgentRunController(),
            traceFormatter = AgentTraceFormatter(),
            onEvent = { event -> synchronized(events) { events += event } },
        )

        val result = loop.run()
        assertEquals("汇总完成", result.content)
        val toolMessages = (0 until messages.length())
            .map { messages.getJSONObject(it) }
            .filter { it.optString("role") == "tool" }
        assertEquals(listOf("call_1", "call_2"), toolMessages.map { it.optString("tool_call_id") })

        val finishedEvents = synchronized(events) { events.toList() }.filterIsInstance<AgentEvent.ToolFinished>()
        assertEquals(2, finishedEvents.size)
        finishedEvents.forEach { finished ->
            assertNotNull(SubagentActivitySnapshot.decode(finished.command))
        }
    }

    /** 单个 subagent 调用不应被并发路径特殊对待，也不能重复执行。 */
    @Test
    fun singleSubagentCallRunsOnce() {
        val executed = mutableListOf<String>()
        val provider = object : AgentProviderClient {
            override val id: String = "fake-provider"
            override val capabilities = ProviderCapabilities(
                endpoint = EndpointKind.CHAT_COMPLETIONS,
                streamingText = true,
                streamingToolCalls = true,
                imageInput = false,
                toolResultImages = false,
                strictTools = false,
                parallelToolCalls = true,
            )
            private var callCount = 0

            override fun complete(
                request: ProviderRequest,
                runController: AgentRunController,
                onEvent: (ProviderEvent) -> Unit,
            ): ProviderResponse {
                callCount += 1
                if (callCount == 1) {
                    val toolCalls = JSONArray().put(
                        JSONObject()
                            .put("id", "only")
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", "subagent").put(
                                    "arguments",
                                    JSONObject()
                                        .put("subagent_type", "explore")
                                        .put("description", "单次")
                                        .put("prompt", "单次任务")
                                        .toString(),
                                ),
                            ),
                    )
                    return ProviderResponse(
                        assistantMessage = JSONObject()
                            .put("role", "assistant")
                            .put("content", "")
                            .put("finish_reason", "tool_calls")
                            .put("tool_calls", toolCalls),
                    )
                }
                return ProviderResponse(
                    assistantMessage = JSONObject()
                        .put("role", "assistant")
                        .put("content", "完成")
                        .put("finish_reason", "stop"),
                )
            }
        }

        val loop = AgentLoop(
            config = AgentModelClient.ModelConfig(
                baseUrl = "https://example.com/v1",
                apiKey = "test-key",
                model = "test-model",
                systemPrompt = "",
            ),
            messages = JSONArray().put(JSONObject().put("role", "user").put("content", "开始")),
            tools = JSONArray().put(AgentSubagentToolCatalog.schema()),
            provider = provider,
            toolExecutor = AgentModelClient.ToolExecutor { call ->
                executed += call.id
                AgentModelClient.ToolResult(JSONObject().put("ok", true).toString())
            },
            runController = AgentRunController(),
            traceFormatter = AgentTraceFormatter(),
            onEvent = {},
        )

        assertEquals("完成", loop.run().content)
        assertEquals(listOf("only"), executed)
    }
}
