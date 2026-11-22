package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 结构化提问工具的 schema。
 *
 * 参数形状对齐 spring-ai-agent-utils 的 `AskUserQuestionTool`：一次 1–4 题，
 * 每题 2–4 个具体选项、可选行说明、可多选。卡片永远自带自由输入，所以这里
 * 不要求模型自己列「其他」。
 */
internal object AgentAskQuestionToolCatalog {
    const val TOOL_NAME = "ask_user_question"

    private const val DESCRIPTION =
        "向用户提一个或几个必须由本人决定的问题，界面会把它们画成可点的选项卡。" +
            "用在方向不明、要在几种做法里挑一种、或需要用户给具体信息的时候；" +
            "自己查得到的事情不要问，能合并的问题合到一题。" +
            "每题给 2–4 个具体、可执行的选项，并写一行说明；不要写「是/否」这类空选项。" +
            "调用本工具后这一轮就结束，用户点完答案会作为下一条消息发回来，你再继续。"

    fun appendTo(tools: JSONArray) {
        tools.put(
            AgentToolSchema.function(
                name = TOOL_NAME,
                description = DESCRIPTION,
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject().put(
                            "questions",
                            JSONObject()
                                .put("type", "array")
                                .put("minItems", 1)
                                .put("maxItems", 4)
                                .put("description", "要问的问题，一题一个对象，最多 4 题。")
                                .put(
                                    "items",
                                    JSONObject()
                                        .put("type", "object")
                                        .put(
                                            "properties",
                                            JSONObject()
                                                .put(
                                                    "question",
                                                    JSONObject()
                                                        .put("type", "string")
                                                        .put("description", "完整问句，要能单独看懂。"),
                                                )
                                                .put(
                                                    "header",
                                                    JSONObject()
                                                        .put("type", "string")
                                                        .put("description", "选项卡上的短标签，2–4 个字，越短越好，例如「方案」「范围」；最多 6 个字。"),
                                                )
                                                .put(
                                                    "multiSelect",
                                                    JSONObject()
                                                        .put("type", "boolean")
                                                        .put("description", "是否允许多选，默认 false。"),
                                                )
                                                .put(
                                                    "options",
                                                    JSONObject()
                                                        .put("type", "array")
                                                        .put("minItems", 2)
                                                        .put("maxItems", 4)
                                                        .put("description", "2–4 个具体选项；用户还能自己输入。")
                                                        .put(
                                                            "items",
                                                            JSONObject()
                                                                .put("type", "object")
                                                                .put(
                                                                    "properties",
                                                                    JSONObject()
                                                                        .put(
                                                                            "label",
                                                                            JSONObject()
                                                                                .put("type", "string")
                                                                                .put("description", "选项正文，点它就是这句答案。"),
                                                                        )
                                                                        .put(
                                                                            "description",
                                                                            JSONObject()
                                                                                .put("type", "string")
                                                                                .put("description", "选项下方的一行说明，讲清差别或代价。"),
                                                                        ),
                                                                )
                                                                .put("required", JSONArray().put("label")),
                                                        ),
                                                ),
                                        )
                                        .put("required", JSONArray().put("question").put("options")),
                                ),
                        ),
                    )
                    .put("required", JSONArray().put("questions")),
            ),
        )
    }
}
