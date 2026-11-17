package io.github.mangi.eta.agent.model

import org.json.JSONArray

/**
 * 回答末尾的下一步建议块。
 *
 * 模型在定稿时以
 * ```
 * <suggestions>["再看一眼前面的改动","换成表格给我"]</suggestions>
 * ```
 * 形式给出，界面上渲染成可点的 chip；点击等价于把该条文本作为下一条用户消息发出。
 *
 * 三条硬约束（都是踩过坑的）：
 * 1. 无论内部 JSON 能否解析，标签块都从正文剥离——否则界面会漏出原始标签；
 * 2. 只采用**最后一个**标签块，模型有时在思考或示例里多写一遍；
 * 3. 落在 markdown 代码围栏内的标签块一律忽略，避免把示例代码吃掉。
 */
internal object PromptSuggestions {
    /** 单次最多采用的条数；超出部分丢弃，不用来引导用户做长菜单。 */
    const val MAX_ITEMS: Int = 4

    /** 单条建议的最大字符数，过长说明模型写成了整段话，不适合做 chip。 */
    const val MAX_ITEM_CHARS: Int = 120

    private val BLOCK = Regex("(?s)<suggestions>(.*?)</suggestions>")

    private const val OPEN_TAG = "<suggestions>"
    private const val CLOSE_TAG = "</suggestions>"

    /**
     * 从定稿正文中剥离建议块。
     *
     * @return 剥离后的正文与解析出的建议；没有标签或标签全在代码围栏内时原样返回。
     */
    fun extract(text: String): Pair<String, List<String>> {
        if (!text.contains(OPEN_TAG)) return text to emptyList()
        val match = lastBlockOutsideCodeFence(text) ?: return text to emptyList()
        val cleaned = text.removeRange(match.range).trimEnd()
        return cleaned to parse(match.groupValues[1])
    }

    /**
     * 流式阶段的显示裁剪：标签已开始但还没闭合时，先把尾巴藏起来，
     * 免得标签和半截 JSON 一路滚过去。已闭合的部分交给 [extract] 处理。
     */
    fun visibleDuringStreaming(text: String): String {
        val open = text.lastIndexOf(OPEN_TAG)
        if (open < 0) return text
        val close = text.indexOf(CLOSE_TAG, open)
        if (close >= 0) return text
        if (isInsideCodeFence(text, open)) return text
        return text.substring(0, open).trimEnd()
    }

    private fun lastBlockOutsideCodeFence(text: String): MatchResult? {
        var result: MatchResult? = null
        for (match in BLOCK.findAll(text)) {
            if (!isInsideCodeFence(text, match.range.first)) result = match
        }
        return result
    }

    /** 判定 [index] 所在位置是否处于未闭合的 ``` 围栏内（按行统计开合）。 */
    private fun isInsideCodeFence(text: String, index: Int): Boolean {
        var fenceOpen = false
        var lineStart = 0
        for (line in text.lineSequence()) {
            if (lineStart >= index) break
            if (line.trimStart().startsWith("```")) fenceOpen = !fenceOpen
            lineStart += line.length + 1
        }
        return fenceOpen
    }

    private fun parse(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val fromJson = runCatching {
            val array = JSONArray(trimmed)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index, "").takeIf(String::isNotBlank)
            }
        }.getOrNull()
        val items = fromJson ?: trimmed.lineSequence().map { line ->
            line.trim().removePrefix("-").removePrefix("*").trim()
        }.filter { it.isNotBlank() }.toList()
        return items
            .map { it.replace(Whitespace, " ").trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_ITEM_CHARS }
            .distinct()
            .take(MAX_ITEMS)
    }

    private val Whitespace = Regex("\\s+")
}
