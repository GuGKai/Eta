package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.subagent.AgentSubagentToolCatalog
import org.json.JSONObject

/** Independent reads may overlap; all other calls form an execution barrier. */
internal object AgentToolBatchPlanner {
    const val MAX_CONCURRENT_CALLS = 4

    data class Segment(
        val parallel: Boolean,
        val calls: List<AgentModelClient.ToolCall>,
    )

    private val parallelSafeTools = setOf(
        "search_apps", "web_search", "tasks_triggers", "tasks_list", "tasks_history",
    )
    private val fileReadTools = setOf(
        "stat_file", "list_directory", "glob_files", "grep_files", "read_file",
    )

    // fetch_url retains a small, ordered document cache. Its calls remain sequential.
    val batchTools: Set<String> = parallelSafeTools + fileReadTools + "fetch_url"

    fun isParallelSafe(call: AgentModelClient.ToolCall): Boolean {
        val args = runCatching { JSONObject(call.argumentsJson) }.getOrNull() ?: return false
        if (call.name in parallelSafeTools) return true
        if (call.name !in fileReadTools) return false
        // Linux/root file access uses a shared shell environment and privilege state.
        return args.optString("environment", "android").equals("android", ignoreCase = true) &&
            args.optString("identity", "user").equals("user", ignoreCase = true)
    }

    fun plan(calls: List<AgentModelClient.ToolCall>): List<Segment> {
        val segments = mutableListOf<Segment>()
        val parallelRun = mutableListOf<AgentModelClient.ToolCall>()

        fun flushParallel() {
            if (parallelRun.isNotEmpty()) {
                segments += Segment(parallel = parallelRun.size > 1, calls = parallelRun.toList())
                parallelRun.clear()
            }
        }

        var index = 0
        while (index < calls.size) {
            val call = calls[index]
            // 同一轮里连续的 subagent 调用互不抢占屏幕与记忆锁，按上游语义整段并发派发；
            // 与只读查询不同，它们不是单条调用成段，必须聚成一批才能真并行。
            if (call.name == AgentSubagentToolCatalog.TOOL_NAME) {
                val run = mutableListOf<AgentModelClient.ToolCall>()
                while (index < calls.size && calls[index].name == AgentSubagentToolCatalog.TOOL_NAME) {
                    run += calls[index]
                    index += 1
                }
                flushParallel()
                segments += Segment(parallel = run.size > 1, calls = run.toList())
                continue
            }
            if (isParallelSafe(call)) {
                parallelRun += call
            } else {
                flushParallel()
                segments += Segment(parallel = false, calls = listOf(call))
            }
            index += 1
        }
        flushParallel()
        return segments
    }
}
