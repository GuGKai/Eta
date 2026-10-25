package io.github.mangi.eta.ui.markdown

import io.github.mangi.eta.agent.voice.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 朗读高亮定位：句子表与渲染块来自两条不同的管线，靠拼文本 + 顺序游标对齐。 */
class MarkdownSpeechLocatorTest {
    private val style = MarkdownInlineStyle.Default

    private fun document(markdown: String) =
        StreamingGfmParserSession().parse(markdown, isComplete = true, style = style).document

    private fun sentencesOf(markdown: String) = SpeechText.sentences(SpeechText.readable(markdown))

    /** 取高亮区间覆盖的原文，用渲染后的块文本读出来，验证标中的正是这一句。 */
    private fun highlighted(markdown: String, index: Int): String? {
        val document = document(markdown)
        val sentences = sentencesOf(markdown)
        val highlight = MarkdownSpeechHighlightCache().highlight(document.blocks, sentences, index)
            ?: return null
        val block = document.blocks.flattenTextBlocks().firstOrNull { it.offset == highlight.blockOffset }
            ?: return null
        return condensed(block.text.text.substring(highlight.start, highlight.end))
    }

    @Test
    fun eachSentenceLandsOnItsOwnText() {
        val markdown = "第一段第一句。第一段第二句。\n\n第二段只有一句。"
        assertEquals(listOf("第一段第一句。", "第一段第二句。", "第二段只有一句。"), sentencesOf(markdown))
        assertEquals("第一段第一句。", highlighted(markdown, 0))
        assertEquals("第一段第二句。", highlighted(markdown, 1))
        assertEquals("第二段只有一句。", highlighted(markdown, 2))
    }

    @Test
    fun repeatedSentencesUseTheNextOccurrence() {
        val markdown = "好的。好的。\n\n收尾。"
        assertEquals(listOf("好的。", "好的。", "收尾。"), sentencesOf(markdown))
        val document = document(markdown)
        val cache = MarkdownSpeechHighlightCache()
        val sentences = sentencesOf(markdown)
        val first = cache.highlight(document.blocks, sentences, 0)
        val second = cache.highlight(document.blocks, sentences, 1)
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(0, first!!.start)
        assertEquals(3, second!!.start)
    }

    @Test
    fun inlineEmphasisDoesNotBreakHighlighting() {
        val markdown = "**重点**在这里，还有 `code` 与 [链接](https://example.com)。"
        val sentence = sentencesOf(markdown).first()
        val text = highlighted(markdown, 0)
        assertNotNull(text)
        assertEquals(condensed(sentence), text)
    }

    @Test
    fun listItemsAreHighlightedPerItem() {
        val markdown = "步骤：\n\n- 先看第一项。\n- 再看第二项。"
        assertEquals(listOf("步骤：", "先看第一项。", "再看第二项。"), sentencesOf(markdown))
        assertEquals("先看第一项。", highlighted(markdown, 1))
        assertEquals("再看第二项。", highlighted(markdown, 2))
    }

    @Test
    fun sentencesMissingFromRenderedTextAreSkippedWithoutBreakingTheRest() {
        val markdown = "开头一句。\n\n```kotlin\nval a = 1\n```\n\n结尾一句。"
        val sentences = sentencesOf(markdown)
        val document = document(markdown)
        val cache = MarkdownSpeechHighlightCache()
        val last = cache.highlight(document.blocks, sentences, sentences.lastIndex)
        assertNotNull(last)
        val range = requireNotNull(last)
        val block = document.blocks.flattenTextBlocks().firstOrNull { it.offset == range.blockOffset }
        assertEquals("结尾一句。", block?.text?.text?.substring(range.start, range.end))
    }

    @Test
    fun outOfRangeIndexReturnsNull() {
        val document = document("只有一句。")
        assertNull(MarkdownSpeechHighlightCache().highlight(document.blocks, sentencesOf("只有一句。"), 5))
    }

    /** 渲染端会补窄空格等排版空白，比较时全部抽掉。 */
    private fun condensed(text: String) =
        text.filterNot { it.isWhitespace() || it == '\u200B' || it == '\uFEFF' }

    private fun List<MarkdownBlock>.flattenTextBlocks(): List<MarkdownTextBlock> {
        val result = ArrayList<MarkdownTextBlock>()
        fun walk(blocks: List<MarkdownBlock>) {
            for (block in blocks) when (block) {
                is MarkdownTextBlock -> result += block
                is MarkdownQuote -> walk(block.blocks)
                is MarkdownAlert -> walk(block.blocks)
                is MarkdownList -> block.items.forEach { walk(it.blocks) }
                is MarkdownTable -> {
                    result += block.header
                    block.rows.forEach { it.forEach { cell -> result += cell } }
                }
                else -> Unit
            }
        }
        walk(this)
        return result
    }
}
