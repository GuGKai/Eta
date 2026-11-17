package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** Projects batch admission from the final, permission-filtered tool catalog. */
internal object AgentBatchToolCatalog {
    const val NAME = "batch"
    const val MAX_CALLS = 8

    fun project(tools: JSONArray, enabled: Boolean = true): JSONArray {
        val result = JSONArray()
        val admittedNames = linkedSetOf<String>()
        for (index in 0 until tools.length()) {
            val schema = tools.getJSONObject(index)
            val name = schema.optJSONObject("function")?.optString("name")
            if (name == NAME) continue
            result.put(schema)
            // The final catalog has already applied capability, permission and restriction
            // filtering.  Expose every remaining tool for explicit sequential batches;
            // auto mode is checked separately by AgentLoop and remains read-only.
            if (!name.isNullOrBlank()) admittedNames += name
        }
        if (enabled && admittedNames.isNotEmpty()) {
            val autoNames = admittedNames.filter { it in AgentToolBatchPlanner.batchTools }
            result.put(AgentToolSchema.function(
                name = NAME,
                description = "批量执行当前已开启的工具，减少模型往返。每项使用对应工具原有参数，按输入顺序返回独立结果；最多 8 项。auto 模式只接受互不依赖的只读查询（本轮允许：${autoNames.joinToString().ifEmpty { "无，请使用 sequential" }}），并行上限 4 项；sequential 模式按顺序执行任意已开启工具，包括界面操作、写入、终端和 MCP，但不能嵌套 batch。顺序模式默认在首项失败后停止，可设置 on_error=continue 继续独立步骤；auto 默认继续收集所有查询结果。不能引用本批次其他项的输出；需要上一步结果时先读取结果再发起下一批。异步工具返回代表任务已提交，不代表后台任务完成；暂停或取消总会停止后续项，中断后先核实未知状态，不要重放已完成动作。",
                parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("calls", JSONObject()
                            .put("type", "array")
                            .put("minItems", 1)
                            .put("maxItems", MAX_CALLS)
                            .put("items", JSONObject()
                                .put("type", "object")
                                .put("properties", JSONObject()
                                    .put("tool", JSONObject().put("type", "string").put("enum", JSONArray(admittedNames.toList())))
                                    .put("arguments", JSONObject().put("type", "object")
                                        .put("description", "该工具原有 JSON 参数，执行前逐项校验当前工具 Schema。")))
                                .put("required", JSONArray(listOf("tool", "arguments")))
                                .put("additionalProperties", false)))
                        .put("mode", JSONObject().put("type", "string")
                            .put("enum", JSONArray(listOf("auto", "sequential"))).put("default", "auto"))
                        .put("on_error", JSONObject().put("type", "string")
                            .put("enum", JSONArray(listOf("stop", "continue")))
                            .put("description", "普通失败的处理方式，默认 sequential=stop、auto=continue。stop 保证逐项执行，失败后剩余项返回跳过结果；暂停和取消始终停止后续项。")))
                    .put("required", JSONArray().put("calls"))
                    .put("additionalProperties", false),
            ))
        }
        return result
    }

    /**
     * wrapper 自身参数是否合法；返回 null 表示结构可用。
     * 这些检查必须在任何子调用执行前完成，否则模型给错参数时会先执行一部分再失败。
     */
    fun problem(argumentsJson: String): String? {
        val arguments = runCatching { JSONObject(argumentsJson) }.getOrNull()
            ?: return "arguments 不是合法的 JSON 对象"
        val calls = arguments.optJSONArray("calls") ?: return "缺少 calls 数组"
        if (calls.length() == 0) return "calls 至少需要 1 项"
        if (calls.length() > MAX_CALLS) return "calls 最多 $MAX_CALLS 项，当前 ${calls.length()} 项"
        for (index in 0 until calls.length()) {
            val call = calls.optJSONObject(index) ?: return "calls[$index]: 每一项都必须是对象"
            if (call.optString("tool").isBlank()) return "calls[$index]: 缺少 tool"
            if (call.optJSONObject("arguments") == null) return "calls[$index]: 缺少 arguments 对象"
        }
        val mode = arguments.optString("mode", "auto")
        if (mode != "auto" && mode != "sequential") return "mode 只支持 auto 或 sequential"
        val onError = arguments.optString("on_error", "")
        if (onError.isNotEmpty() && onError != "stop" && onError != "continue") {
            return "on_error 只支持 stop 或 continue"
        }
        return null
    }

    fun calls(parent: AgentModelClient.ToolCall): List<AgentModelClient.ToolCall> {
        val calls = JSONObject(parent.argumentsJson).getJSONArray("calls")
        return (0 until calls.length()).map { index ->
            val call = calls.getJSONObject(index)
            AgentModelClient.ToolCall(
                id = "${parent.id}-batch-$index",
                name = call.getString("tool"),
                argumentsJson = call.getJSONObject("arguments").toString(),
            )
        }
    }
}
