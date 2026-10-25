package io.github.mangi.eta.ui.markdown


/**
 * 把「正在读第几句」落到 Markdown 渲染块里的字符区间。
 *
 * 朗读按清洗后的纯文本切句，界面显示的是渲染后的块文本；两者去掉标记后字序一致，
 * 所以按句顺序在这些块拼起来的文本里推进游标就能命中。句子跨块（例如一句话里夹了
 * 换行或表格单元格）时只标到起始块末尾，宁可少标一点，也不要标错位置。
 *
 * 句序号通常单调前进，走过的位置缓存在 [MarkdownSpeechHighlightCache] 里，
 * 换句时只往前找一句；只有回退（重新朗读、跳读）才从头重算。
 */
internal class MarkdownSpeechHighlightCache {
    private var blocks: List<MarkdownBlock>? = null
    private var sentences: List<String>? = null
    private var flat: FlattenedBlocks? = null
    /** 已经定位到的句序号；-1 表示还没开始。 */
    private var scanned = -1
    /** 下一句从这里往后找，保证重复句不会被定位到前面那一处。 */
    private var cursor = 0
    private var current: MarkdownSpeechHighlight? = null

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
            current = null
        }
        if (index == scanned) return current
        if (index < scanned) {
            scanned = -1
            cursor = 0
            current = null
        }
        val flat = flat ?: FlattenedBlocks(blocks).also { flat = it }
        while (scanned < index) {
            scanned++
            val needle = sentences[scanned].trim()
            if (needle.isEmpty()) {
                current = null
                continue
            }
            val at = flat.text.indexOf(needle, cursor)
            if (at >= 0) {
                cursor = at + needle.length
                current = flat.highlightAt(at, cursor)
                continue
            }
            // 整句对不上时退一步按句首定位：句中夹了排版空白或代码块也能落到正确的开头。
            val head = needle.take(MATCH_HEAD_CHARS)
            val fallback = if (head.length >= MATCH_HEAD_CHARS) flat.text.indexOf(head, cursor) else -1
            if (fallback < 0) {
                // 这句在渲染文本里确实找不到，游标不动，继续试下一句。
                current = null
                continue
            }
            cursor = fallback + head.length
            current = flat.highlightAt(fallback, minOf(fallback + needle.length, flat.text.length))
        }
        return current
    }
}

/** 整句匹配失败时用句首这么多字兜底定位，够长到不撞车，又短到能容忍句中差异。 */
private const val MATCH_HEAD_CHARS = 10

/**
 * 把渲染块按顺序拼成一条可搜索的文本，同时记住每个字符属于哪一块、在块内第几位。
 *
 * 空白按朗读文本的清洗规则压缩，块之间用换行分隔，分隔符用 [NO_POSITION] 标记。
 */
private class FlattenedBlocks(blocks: List<MarkdownBlock>) {
    val text = StringBuilder()
    private val owners = ArrayList<Int>()
    private val positions = ArrayList<Int>()

    init {
        collect(blocks)
    }

    fun highlightAt(start: Int, end: Int): MarkdownSpeechHighlight? {
        if (start < 0 || end <= start || end > text.length) return null
        var from = start
        while (from < end && positions[from] == NO_POSITION) from++
        if (from >= end) return null
        val offset = owners[from]
        var last = end - 1
        while (last > from && (owners[last] != offset || positions[last] == NO_POSITION)) last--
        if (owners[last] != offset || positions[last] == NO_POSITION) return null
        return MarkdownSpeechHighlight(
            blockOffset = offset,
            start = positions[from],
            end = positions[last] + 1,
        )
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
            if (char != '\n' && isSpace(char)) {
                push(' ', block.offset, index)
                while (index < source.length && source[index] != '\n' && isSpace(source[index])) index++
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
    }

    private fun isSpace(char: Char): Boolean =
        char.isWhitespace() || char == '\u200B' || char == '\uFEFF'

    private companion object {
        const val NO_POSITION = -1
    }
}
