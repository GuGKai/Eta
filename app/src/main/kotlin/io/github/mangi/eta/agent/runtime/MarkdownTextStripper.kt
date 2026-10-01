package io.github.mangi.eta.agent.runtime

/**
 * 通知等纯文本场景下的 Markdown 标记剥离。
 *
 * Android 通知只接受纯文本或 Spannable，不会解析 Markdown；这里把常见语法符号去掉，
 * 保留可读的文字内容，并把结果压平成单行，避免通知里出现 `**`、`##`、`[](url)` 之类的标记。
 */
internal object MarkdownTextStripper {

    private val codeFence = Regex("```[A-Za-z0-9+#-]*")
    private val image = Regex("!\\[([^\\]]*)\\]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
    private val inlineCode = Regex("`([^`]*)`")
    private val boldStar = Regex("\\*\\*(.+?)\\*\\*")
    private val boldUnderscore = Regex("__(.+?)__")
    private val strike = Regex("~~(.+?)~~")
    private val heading = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val quote = Regex("(?m)^\\s{0,3}>\\s?")
    private val bullet = Regex("(?m)^\\s*[-*+]\\s+")
    private val ordered = Regex("(?m)^\\s*\\d+[.)]\\s+")
    private val rule = Regex("(?m)^\\s*([-*_])\\1{2,}\\s*\$")
    private val tableDividerRow = Regex("(?m)^\\s*\\|?[\\s:|\\-]*\\|[\\s:|\\-]*\$")
    private val italicStar = Regex("(?<![\\w*])\\*([^*\\n]+?)\\*(?![\\w*])")
    private val italicUnderscore = Regex("(?<![\\w_])_([^_\\n]+?)_(?![\\w_])")
    private val tableDivider = Regex("\\|")
    private val horizontalSpaces = Regex("[ \\t]+")
    private val lineBreaks = Regex("\\s*\\n\\s*")

    fun strip(text: String): String {
        var s = text
        s = codeFence.replace(s, " ")
        s = image.replace(s, "\$1")
        s = link.replace(s, "\$1")
        s = inlineCode.replace(s, "\$1")
        s = boldStar.replace(s, "\$1")
        s = boldUnderscore.replace(s, "\$1")
        s = strike.replace(s, "\$1")
        s = heading.replace(s, "")
        s = quote.replace(s, "")
        s = bullet.replace(s, "· ")
        s = ordered.replace(s, "· ")
        s = rule.replace(s, " ")
        // 表格分隔行（|---|:--:|）先整行去掉，否则会残留成 "--- ---"。
        s = tableDividerRow.replace(s, " ")
        s = italicStar.replace(s, "\$1")
        s = italicUnderscore.replace(s, "\$1")
        s = tableDivider.replace(s, " ")
        s = horizontalSpaces.replace(s, " ")
        s = lineBreaks.replace(s, " ")
        return s.trim()
    }
}
