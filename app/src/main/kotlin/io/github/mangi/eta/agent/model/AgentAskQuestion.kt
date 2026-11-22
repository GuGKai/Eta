package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 结构化提问：模型通过 `ask_user_question` 工具向用户要一个答案。
 *
 * 数据形状参照 OpenMausBot 的 `AskQuestion.kt`（Apache-2.0）与
 * spring-ai-agent-utils 的 `AskUserQuestionTool` schema：一个问题一条，
 * 自带 2–4 个选项与一行解释，允许单选/多选，永远额外提供自由输入。
 *
 * 解析一律宽松：这些内容由模型生成，形状异常最多丢掉一张卡片，绝不能连累整轮回答。
 */
data class AskQuestionOption(
    /** 选项正文，点它就是把这句话作为答案。 */
    val label: String,
    /** 选项下方的一行说明，可为空。 */
    val detail: String? = null,
)

data class AskQuestion(
    val question: String,
    /** 选项卡上的短标签（模型自取），没有就按序号兜底。 */
    val header: String? = null,
    val multiSelect: Boolean = false,
    val options: List<AskQuestionOption> = emptyList(),
) {
    val allowsMultiple: Boolean get() = multiSelect

    fun tabLabel(position: Int): String {
        val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: "问题 $position"
        // 兜底：模型偶尔把整句塞进 header，截到 8 个字，避免切换条被一句标题撑爆。
        return if (raw.length <= 8) raw else raw.take(8) + "…"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("question", question)
        .apply {
            header?.let { put("header", it) }
            if (multiSelect) put("multiSelect", true)
            if (options.isNotEmpty()) {
                put(
                    "options",
                    JSONArray().apply {
                        options.forEach { option ->
                            put(
                                JSONObject().put("label", option.label).apply {
                                    option.detail?.let { put("description", it) }
                                },
                            )
                        }
                    },
                )
            }
        }

    companion object {
        fun from(json: JSONObject): AskQuestion? {
            val question = json.optString("question").trim()
            if (question.isEmpty()) return null
            val array = json.optJSONArray("options") ?: JSONArray()
            val options = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val label = item.optString("label").trim()
                    if (label.isEmpty()) continue
                    add(
                        AskQuestionOption(
                            label = label,
                            detail = item.optString("description").trim().takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }
            return AskQuestion(
                question = question,
                header = json.optString("header").trim().takeIf { it.isNotEmpty() },
                multiSelect = json.optBoolean("multiSelect", false),
                options = options,
            )
        }
    }
}

/** 一张卡片上可以有多个问题，按选项卡分开。 */
data class AskQuestionCard(
    val version: Int = 1,
    val questions: List<AskQuestion> = emptyList(),
) {
    val isEmpty: Boolean get() = questions.isEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("questions", JSONArray().apply { questions.forEach { put(it.toJson()) } })

    companion object {
        val Empty = AskQuestionCard()

        fun parse(raw: String?): AskQuestionCard {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return Empty
            return runCatching { JSONObject(text) }.getOrNull()?.let(::from) ?: Empty
        }

        fun from(json: JSONObject): AskQuestionCard {
            val array = json.optJSONArray("questions") ?: return Empty
            val questions = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    AskQuestion.from(item)?.let(::add)
                }
            }
            return AskQuestionCard(
                version = json.optInt("version", 1),
                questions = questions,
            )
        }
    }
}

/**
 * 提问卡片对应的消息 ID。
 *
 * 界面按它把答案回填到卡片上，运行侧按它把答案投递给正在挂起的工具调用，
 * 两边必须用同一套拼法。
 */
internal object AskCardMessageId {
    fun of(runId: String, toolCallId: String): String = "assistant-$runId-ask-$toolCallId"
}

/**
 * 答案文本：用户点完选项后，只把「选了什么」作为下一条消息发回给模型。
 *
 * 同一题多选之间用「、」连接；多题按题序各占一行，
 * 不再带「用户回答了你的问题。」前言，也不再带 Q/A 标签。
 */
internal object AskQuestionAnswer {
    /** 旧版答案文本的前言，只为兼容历史消息。 */
    const val PREAMBLE = "用户回答了你的问题。"

    fun format(questions: List<AskQuestion>, answers: List<List<String>>): String =
        questions.indices.mapNotNull { index ->
            val picked = answers.getOrElse(index) { emptyList() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (picked.isEmpty()) null else picked.joinToString("、")
        }.joinToString("\n")

    /** 卡片回显用：去掉历史消息里那句只给模型看的前言。 */
    fun withoutPreamble(answer: String): String {
        val lead = "$PREAMBLE\n\n"
        return if (answer.startsWith(lead)) answer.removePrefix(lead) else answer
    }

    /** 卡片回显用：把答案文本按题拆开，顺序与问题顺序一致。 */
    fun perQuestion(answer: String, count: Int): List<String> {
        val body = withoutPreamble(answer).trim()
        if (body.isEmpty()) return List(count) { "" }
        val legacy = body.split("\n")
            .filter { it.startsWith("A: ") }
            .map { it.removePrefix("A: ").trim() }
        val lines = if (legacy.isNotEmpty()) legacy else body.split("\n").map { it.trim() }
        return List(count) { lines.getOrElse(it) { "" } }
    }
}
