package io.github.mangi.eta.ui.markdown


/**
 * 把「正在读第几句」落到 Markdown 渲染块里的字符区间。
 *
 * 朗读按清洗后的纯文本切句，界面显示的是渲染后的块文本。渲染端会给行内代码、链接两侧补窄空格，
 * 排版空白也与朗读文本不一样，直接拿整句去 indexOf 会失败，退化成「按句首近似」又会把末尾一两个字
 * 和标点漏在区间外。所以定位统一在**去掉所有空白**的紧凑文本上做，命中后再映射回原文区间。
 *
 * 句序号通常单调前进，走过的位置缓存在 [MarkdownSpeechHighlightCache] 里，
 * 换句时只往前找一句；只有回退（重新朗读、跳读）才从头重算。
 */
internal class MarkdownSpeechHighlightCache {
    private var blocks: List<MarkdownBlock>? = null
    private var sentences: List<String>? = null
    private var flat: FlattenedBlocks? = null
    /** 已经定位到第几句；-1 表示还没开始。 */
    private var scanned = -1
    /** 紧凑文本里下一句从哪里往后找，保证重复句不会被定位到前面那一处。 */
    private var cursor = 0
    /** 每句在紧凑文本里的区间；高亮只盖这一段，读到哪高亮到哪。 */
    private val spans = HashMap<Int, Span>()
    private var current: MarkdownSpeechHighlight? = null
    private var currentIndex = -1

    fun highlight(
        blocks: List<MarkdownBlock>,
        sentences: List<String>,
        index: Int,
    ): MarkdownSpeechHighlight? {
        if (index < 0 || index >= sentences.size) return null
        if (this.sentences !== sentences || this.blocks !== blocks) {
            this.blocks = blocks
            this.sentences = sentences
            flat = null
            scanned = -1
            cursor = 0
            spans.clear()
            current = null
            currentIndex = -1
        }
        if (index == currentIndex && current != null) return current
        if (index < scanned) {
            scanned = -1
            cursor = 0
            spans.clear()
            current = null
            currentIndex = -1
        }
        val flat = flat ?: FlattenedBlocks(blocks).also { flat = it }
        while (scanned < index) {
            scanned++
            locate(scanned, flat)
        }
        val span = spans[index] ?: return current
        val range = flat.flatRange(span.start, span.end) ?: return current
        current = flat.highlightSpan(range.first, range.last + 1)
            ?: flat.highlightSpan(range.first, flat.text.length)
        currentIndex = index
        return current
    }

    /** 定位第 [i] 句在紧凑文本里的区间并推进 [cursor]；找不到时按游标处近似落点，绝不放弃。 */
    private fun locate(i: Int, flat: FlattenedBlocks) {
        val needle = compactOf(sentences?.getOrNull(i).orEmpty())
        if (needle.isEmpty()) return
        val at = flat.compact.indexOf(needle, cursor)
        if (at >= 0) {
            spans[i] = Span(at, at + needle.length)
            cursor = at + needle.length
            return
        }
        // 整句对不上时按句首逐级兜底：长度逐级缩短，短句同样能兜底。
        val fallback = FALLBACK_HEAD_LENGTHS
            .asSequence()
            .filter { needle.length >= it }
            .mapNotNull { size -> flat.compact.indexOf(needle.take(size), cursor).takeIf { it >= 0 } }
            .firstOrNull()
        if (fallback != null) {
            val end = minOf(fallback + needle.length, flat.compact.length)
            spans[i] = Span(fallback, end)
            cursor = end
            return
        }
        // 仍然对不上（例如这句只存在于渲染时被省略的标记里）：记下游标位置继续推进。
        val startAt = cursor.coerceIn(0, (flat.compact.length - 1).coerceAtLeast(0))
        val endAt = minOf(startAt + needle.length.coerceAtLeast(1), flat.compact.length)
        spans[i] = Span(startAt, endAt)
        cursor = endAt
    }

    /** 一句在紧凑文本里的区间。 */
    private data class Span(val start: Int, val end: Int)
}

/** 整句匹配失败时按这些长度依次用句首兜底：先长后短，既避免撞车，也保证短句有得兜。 */
private val FALLBACK_HEAD_LENGTHS = intArrayOf(10, 6, 4, 2)

/** 朗读文本与渲染文本共同的清洗规则：空白（含渲染端补的窄空格）一律不参与匹配。 */
private fun isIgnorableSpace(char: Char): Boolean =
    char.isWhitespace() || char == '\u200B' || char == '\uFEFF' || char == '\u00A0'

private fun compactOf(text: CharSequence): String = buildString {
    for (char in text) if (!isIgnorableSpace(char)) append(char)
}

/**
 * 把渲染块按顺序拼成一条可搜索的文本，同时记住每个字符属于哪一块、在块内第几位；
 * 另外维护一条去掉空白的紧凑文本，以及紧凑字符 → 原文下标的映射。
 *
 * 空白按朗读文本的清洗规则压缩，块之间用换行分隔，分隔符用 [NO_POSITION] 标记。
 */
private class FlattenedBlocks(blocks: List<MarkdownBlock>) {
    val text = StringBuilder()
    private val owners = ArrayList<Int>()
    private val positions = ArrayList<Int>()
    private val compactChars = StringBuilder()
    private val compactFlat = ArrayList<Int>()
    /** 去掉所有空白后的文本，定位就在这里做。 */
    val compact: String
    /** 紧凑文本第 i 个字符在 [text] 里的下标。 */
    private val compactIndex: IntArray

    init {
        collect(blocks)
        compact = compactChars.toString()
        compactIndex = compactFlat.toIntArray()
    }

    /** 紧凑文本区间 → 原文区间（首字符下标 … 末字符下标），保留句中的窄空格与缩进。 */
    fun flatRange(startCompact: Int, endCompact: Int): IntRange? {
        if (startCompact < 0 || endCompact <= startCompact || endCompact > compactIndex.size) return null
        return compactIndex[startCompact]..compactIndex[endCompact - 1]
    }

    /** 原文区间 → 高亮；一句若横跨多个块（排版把一句话拆进相邻块）就按块拆成多段，整句都涂到。 */
    fun highlightSpan(start: Int, end: Int): MarkdownSpeechHighlight? {
        if (start < 0 || end <= start || end > text.length) return null
        val ranges = ArrayList<MarkdownSpeechHighlight.Range>()
        var index = start
        while (index < end) {
            val owner = owners[index]
            if (positions[index] == NO_POSITION) {
                index++
                continue
            }
            var scan = index
            var last = index
            while (scan < end && owners[scan] == owner) {
                if (positions[scan] != NO_POSITION) last = scan
                scan++
            }
            ranges += MarkdownSpeechHighlight.Range(owner, positions[index], positions[last] + 1)
            index = scan
        }
        return if (ranges.isEmpty()) null else MarkdownSpeechHighlight(ranges)
    }

    private fun collect(list: List<MarkdownBlock>) {
        for (block in list) when (block) {
            is MarkdownTextBlock -> append(block)
            is MarkdownQuote -> collect(block.blocks)
            is MarkdownAlert -> collect(block.blocks)
            is MarkdownList -> block.items.forEach { collect(it.blocks) }
            is MarkdownTable -> {
                block.header.forEach(::append)
                block.rows.forEach { row -> row.forEach(::append) }
            }
            else -> Unit
        }
    }

    private fun append(block: MarkdownTextBlock) {
        val source = block.text.text
        if (source.isBlank()) return
        if (text.isNotEmpty()) push('\n', block.offset, NO_POSITION)
        var index = 0
        while (index < source.length) {
            val char = source[index]
            // 渲染端会给行内代码、链接补窄空格，朗读文本那边没有，一并压成一个空格。
            if (char != '\n' && isIgnorableSpace(char)) {
                push(' ', block.offset, index)
                while (index < source.length && source[index] != '\n' && isIgnorableSpace(source[index])) index++
                continue
            }
            push(char, block.offset, index)
            index++
        }
    }

    private fun push(char: Char, owner: Int, position: Int) {
        text.append(char)
        owners.add(owner)
        positions.add(position)
        if (!isIgnorableSpace(char)) {
            compactChars.append(char)
            compactFlat.add(text.length - 1)
        }
    }

    private companion object {
        const val NO_POSITION = -1
    }
}
