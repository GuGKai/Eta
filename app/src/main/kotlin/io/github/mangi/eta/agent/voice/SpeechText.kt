package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.R
import io.github.mangi.eta.core.AppStrings
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

internal object SpeechText {
    fun readable(markdown: CharSequence): String {
        val root = MarkdownParser(
            flavour = GFMFlavourDescriptor(),
            cancellationToken = CancellationToken.NonCancellable,
        ).buildMarkdownTreeFromString(markdown)
        fun StringBuilder.appendNode(node: ASTNode) {
            when (node.type) {
                MarkdownElementTypes.LINK_DEFINITION,
                MarkdownElementTypes.HTML_BLOCK,
                MarkdownTokenTypes.HTML_TAG,
                MarkdownTokenTypes.HORIZONTAL_RULE,
                MarkdownTokenTypes.ATX_HEADER,
                MarkdownTokenTypes.SETEXT_1,
                MarkdownTokenTypes.SETEXT_2,
                MarkdownTokenTypes.LIST_BULLET,
                MarkdownTokenTypes.LIST_NUMBER,
                MarkdownTokenTypes.BLOCK_QUOTE,
                MarkdownTokenTypes.BACKTICK,
                MarkdownTokenTypes.EMPH,
                GFMTokenTypes.TILDE,
                GFMTokenTypes.TABLE_SEPARATOR -> Unit
                // 代码块照读：跳过围栏标记，只把内容读出来；行与行之间保留换行，自然断句。
                MarkdownElementTypes.CODE_FENCE -> {
                    append('\n')
                    var inBody = false
                    for (child in node.children) {
                        when (child.type) {
                            MarkdownTokenTypes.CODE_FENCE_END -> break
                            MarkdownTokenTypes.EOL -> if (inBody) append('\n') else inBody = true
                            MarkdownTokenTypes.CODE_FENCE_CONTENT ->
                                if (inBody) append(markdown, child.startOffset, child.endOffset)
                        }
                    }
                    append('\n')
                }
                MarkdownElementTypes.CODE_BLOCK -> {
                    append('\n')
                    for (child in node.children) {
                        when (child.type) {
                            MarkdownTokenTypes.CODE_LINE -> append(markdown, child.startOffset, child.endOffset)
                            MarkdownTokenTypes.EOL -> append('\n')
                        }
                    }
                    append('\n')
                }
                MarkdownElementTypes.ORDERED_LIST -> {
                    val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
                    val marker = items.firstOrNull()?.children?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
                    val start = marker?.let { markdown.substring(it.startOffset, it.endOffset).trim().dropLast(1).toIntOrNull() } ?: 1
                    for ((index, item) in items.withIndex()) {
                        append('\n')
                        append(
                            AppStrings.get(
                                R.string.voice_i18n_speech_text_ordered_item,
                                "第 %1\$d 项：",
                                start + index,
                            ),
                        )
                        appendNode(item)
                    }
                    append('\n')
                }
                GFMTokenTypes.CHECK_BOX -> {
                    val marker = markdown.substring(node.startOffset, node.endOffset)
                    append(
                        if ('x' in marker || 'X' in marker) {
                            AppStrings.get(R.string.voice_i18n_speech_text_task_done, "已完成：")
                        } else {
                            AppStrings.get(R.string.voice_i18n_speech_text_task_todo, "未完成：")
                        },
                    )
                }
                // 表格单元格之间按换行断开：每格单独成句，朗读在这里停顿、高亮也只盖这一格，
                // 与「读什么就高亮什么」一致；表头与分隔线本身不出声。
                GFMTokenTypes.CELL -> {
                    node.children.forEach { appendNode(it) }
                    append('\n')
                }
                MarkdownElementTypes.IMAGE -> append(' ')
                MarkdownElementTypes.INLINE_LINK,
                MarkdownElementTypes.FULL_REFERENCE_LINK,
                MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                    val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                        ?: node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                    val text = buildString {
                        label?.children?.filterNot { it.type == MarkdownTokenTypes.LBRACKET ||
                            it.type == MarkdownTokenTypes.RBRACKET }?.forEach { appendNode(it) }
                    }
                    // 先处理链接内部，避免去掉 Markdown 边界后网址吞并相邻正文。
                    append(withoutUrls(text).ifBlank { " " })
                }
                MarkdownElementTypes.AUTOLINK -> append(withoutUrls(
                    markdown.substring(node.startOffset, node.endOffset).removeSurrounding("<", ">"),
                ))
                GFMTokenTypes.GFM_AUTOLINK -> append(withoutUrls(markdown.substring(node.startOffset, node.endOffset)))
                else -> if (node.children.isEmpty()) append(markdown, node.startOffset, node.endOffset)
                    else node.children.forEach { appendNode(it) }
            }
        }
        // 普通文本和行内代码也可能含有未被识别为链接的网址。
        return withoutUrls(buildString { appendNode(root) })
            .lineSequence().map { it.replace(HORIZONTAL_SPACE, " ").trim() }.joinToString("\n")
            .replace(BLANK_LINES, "\n\n").trim()
    }

    // 自动链接节点可能包含网址后的中文正文，只过滤到明确的文本分隔处。
    private fun withoutUrls(text: String): String = URL.replace(text) { match ->
        " " + match.value.takeLastWhile { it in ".,!?;:" }
    }

    private val URL = Regex(
        """(?<![A-Za-z0-9_])(?:https?://|www\.)(?:(?![,;]\p{IsHan})[^\s<>"'`，。！？；：、（）【】《》“”‘’])+""",
        RegexOption.IGNORE_CASE,
    )
    private val HORIZONTAL_SPACE = Regex("[ \\t]+")
    private val BLANK_LINES = Regex("\\n{3,}")

    fun chunks(text: String, limit: Int = 500): List<String> {
        require(limit >= 2)
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            var end = minOf(limit, remaining.length)
            if (end < remaining.length) {
                val boundary = remaining.take(end).indexOfLast { it in "。！？.!?；;\n" }
                if (boundary >= end / 2) end = boundary + 1
                if (remaining[end - 1].isHighSurrogate()) end--
            }
            result += remaining.substring(0, end)
            remaining = remaining.substring(end).trimStart()
        }
        return result
    }

    /**
     * 按句切分，供逐句合成使用：中文句末标点与换行是句边界，英文句号不参与切分，
     * 避免把 `8.8%`、`app/build.gradle.kts` 这类片段读断；超长句再按次级标点细分。
     *
     * 合并短残句只在同一行内做：换行意味着换块（标题、列表项、代码行），跨行合并会把
     * 「📦 产物」这种短标题粘到上一段末尾，读起来错位，高亮也跟着落到别的块上。
     */
    fun sentences(
        text: String,
        limit: Int = SENTENCE_LIMIT,
        minLength: Int = MIN_SENTENCE_LENGTH,
    ): List<String> {
        require(limit >= 16 && minLength >= 0)
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        return clean.split('\n').flatMap { line ->
            if (line.isBlank()) emptyList() else mergeShortPieces(splitLine(line, limit), minLength)
        }
    }

    /** 把一行切成句子：连续句末标点与收尾引号归入本句，超长句再按次级标点细分。 */
    private fun splitLine(line: String, limit: Int): List<String> {
        val raw = mutableListOf<String>()
        val buffer = StringBuilder()
        var index = 0
        while (index < line.length) {
            val char = line[index]
            buffer.append(char)
            index++
            if (char !in SENTENCE_END) continue
            while (index < line.length && line[index] in SENTENCE_TAIL) buffer.append(line[index++])
            raw += buffer.toString()
            buffer.setLength(0)
        }
        if (buffer.isNotBlank()) raw += buffer.toString()
        return raw.flatMap { splitLong(it.trim(), limit) }
    }

    private fun splitLong(sentence: String, limit: Int): List<String> {
        if (sentence.length <= limit) return if (sentence.isEmpty()) emptyList() else listOf(sentence)
        val pieces = mutableListOf<String>()
        var rest = sentence
        while (rest.length > limit) {
            val window = rest.take(limit)
            val cut = window.indexOfLast { it in SECONDARY_END }
            var end = if (cut >= limit / 3) cut + 1 else limit
            if (end > 1 && rest[end - 1].isHighSurrogate()) end--
            val piece = rest.substring(0, end).trim()
            if (piece.isNotEmpty()) pieces += piece
            rest = rest.substring(end).trimStart()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    /** 只把硬切后留下的零星尾巴并回前句；以标点结尾的正常短句保持独立成句。 */
    private fun mergeShortPieces(pieces: List<String>, minLength: Int): List<String> {
        val merged = mutableListOf<String>()
        for (piece in pieces) {
            if (piece.isBlank()) continue
            // 只剩标点的碎片（代码块里的分隔线、装饰符号）不单独成句，读出来也只有噪音。
            if (piece.none { it.isLetterOrDigit() }) continue
            val previous = merged.lastOrNull()
            if (previous != null && isDanglingTail(piece, minLength)) {
                merged[merged.lastIndex] = previous + piece
                continue
            }
            merged += piece
        }
        if (merged.size >= 2 && isDanglingTail(merged[0], minLength)) {
            merged[1] = merged[0] + merged[1]
            merged.removeAt(0)
        }
        return merged
    }

    /** 没有以标点收尾、又特别短的片段，是硬切留下的零头，单独成句会出现突兀停顿。 */
    private fun isDanglingTail(piece: String, minLength: Int): Boolean =
        piece.length < minLength && piece.last() !in PIECE_END

    private const val SENTENCE_LIMIT = 80
    private const val MIN_SENTENCE_LENGTH = 8
    private val SENTENCE_END = setOf('。', '！', '？', '…', '；', ';', '!', '?')
    private val SENTENCE_TAIL = SENTENCE_END + setOf('”', '』', '」', '）', ')')
    private val SECONDARY_END = setOf('，', '、', '：', ':', ',', ' ')
    private val PIECE_END = SENTENCE_TAIL + SECONDARY_END
}
