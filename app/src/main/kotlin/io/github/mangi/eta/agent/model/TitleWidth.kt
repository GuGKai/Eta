package io.github.mangi.eta.agent.model

/**
 * 标题的显示宽度预算。
 *
 * 标题最终显示在侧栏、顶栏、通知等一行内，真正决定「放不放得下」的是**显示列宽**而不是字符数：
 * 一个全角汉字与一个半角字母占的水平空间差一倍。13 个全角汉字大约等于 26 个半角列，所以这里
 * 统一按列宽收口：
 *
 * - 纯中文：≤ 13 个汉字；
 * - 纯英文：≤ 26 个半角字符；
 * - 中英混排：按列宽累加，合计不超过 26 列。
 *
 * 生成侧（模型输出、首轮占位标题）都过这一道，显示侧的 `maxLines = 1 + Ellipsis` 保持不动——
 * 它按真实像素裁断，永远是最准的；这里只保证数据层不会给别的展示面（通知、文件名、侧栏）喂超长标题。
 */
internal object TitleWidth {
    /** 13 个全角汉字 ≈ 26 个半角列。 */
    const val MAX_COLUMNS = 26

    /** 单个码点占用的列宽：全角/CJK/emoji 计 2，连接符与变体选择符计 0，其余计 1。 */
    fun columnsOf(codePoint: Int): Int = when {
        codePoint == 0x200D -> 0 // ZWJ：把 emoji 序列粘在一起，本身不占位
        codePoint == 0xFE0E || codePoint == 0xFE0F -> 0 // 变体选择符（文字/emoji 呈现）
        codePoint in 0x0300..0x036F -> 0 // 组合音标等零宽组合符
        codePoint in 0x1100..0x115F -> 2 // 韩文字母
        codePoint in 0x2E80..0xA4CF -> 2 // CJK 部首、汉字、假名、注音等
        codePoint in 0xAC00..0xD7A3 -> 2 // 韩文音节
        codePoint in 0xF900..0xFAFF -> 2 // CJK 兼容表意文字
        codePoint in 0xFE30..0xFE4F -> 2 // CJK 兼容形式
        codePoint in 0xFF00..0xFF60 -> 2 // 全角 ASCII
        codePoint in 0xFFE0..0xFFE6 -> 2 // 全角符号
        codePoint in 0x2600..0x27BF -> 2 // 杂项符号与 dingbats
        codePoint in 0x1F000..0x1FAFF -> 2 // emoji
        else -> 1
    }

    /** 文本的显示列宽合计。 */
    fun columns(text: String): Int {
        var used = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            used += columnsOf(codePoint)
            index += Character.charCount(codePoint)
        }
        return used
    }

    /**
     * 按显示列宽截断。
     *
     * 用 `codePointAt` 逐码点前进，所以不会把 emoji 的代理对劈成两半；截断点落在半角单词中间时
     * 回退到最后一个空格（避免「Jetpack Compos」这种断词）；最后去掉悬空的分隔符。
     */
    fun truncateByColumns(text: String, maxColumns: Int = MAX_COLUMNS): String {
        if (text.isEmpty()) return text
        var used = 0
        var end = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = columnsOf(codePoint)
            if (used + width > maxColumns) break
            used += width
            index += Character.charCount(codePoint)
            end = index
        }
        if (end == text.length) return text
        var cut = text.substring(0, end)
        val boundary = cut.lastOrNull()
        if (boundary != null && boundary.code < 0x80 && boundary.isLetterOrDigit()) {
            val space = cut.lastIndexOf(' ')
            if (space > 0) cut = cut.substring(0, space)
        }
        return cut
            .trimEnd(' ', '·', '•', '-', '|', '、', ',', '，', ':', '：')
            .trimEnd('\u200D')
            .trim()
    }
}
