package io.github.mangi.eta.ui.markdown

import io.github.mangi.eta.agent.voice.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val blocks = document.blocks.flattenTextBlocks()
        // 一句可能跨多个块（表格同一行被拆成多个单元格），把所有命中的段拼回来比较。
        return condensed(
            highlight.ranges.mapNotNull { range ->
                blocks.firstOrNull { it.offset == range.blockOffset }
                    ?.let { it.text.text.substring(range.start, range.end) }
            }.joinToString(""),
        )
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
        assertEquals(0, first!!.ranges.first().start)
        assertEquals(3, second!!.ranges.first().start)
    }

    @Test
    fun inlineEmphasisDoesNotBreakHighlighting() {
        val markdown = "**重点**在这里，还有 `code` 与 [链接](https://example.com)。"
        // 行内代码照读：整句连成一句，代码内容进了朗读文本。
        assertEquals(listOf("重点在这里，还有 code 与 链接。"), sentencesOf(markdown))
        val text = highlighted(markdown, 0)
        assertNotNull(text)
        // 读到哪高亮到哪：整句（含行内代码）一起高亮。
        assertEquals("重点在这里，还有code与链接。", text)
    }

    @Test
    fun inlineCodeIsSpoken() {
        assertTrue(SpeechText.readable("运行 `./gradlew assembleRelease` 即可。").contains("gradlew"))
        assertTrue(SpeechText.readable("装好后的版本号是 `2026102502`。").contains("2026102502"))
    }

    @Test
    fun fencedCodeIsSpokenAndHighlightedPerLine() {
        val markdown = "改这两行：\n\n```kotlin\nval a = 1\nval b = 2\n```\n\n就这么多。"
        val sentences = sentencesOf(markdown)
        // 围栏标记不读，只读代码内容；每行单独成句。
        assertEquals(listOf("改这两行：", "val a = 1", "val b = 2", "就这么多。"), sentences)
        assertEquals(condensed("val a = 1"), highlighted(markdown, 1))
        assertEquals(condensed("val b = 2"), highlighted(markdown, 2))
        assertEquals("就这么多。", highlighted(markdown, 3))
    }

    @Test
    fun indentedCodeIsSpoken() {
        val markdown = "示例：\n\n    val c = 3\n\n结束。"
        assertEquals(listOf("示例：", "val c = 3", "结束。"), sentencesOf(markdown))
        assertEquals(condensed("val c = 3"), highlighted(markdown, 1))
    }

    @Test
    fun tableCellsAreReadAndHighlightedSeparately() {
        // 单元格之间按换行断开：每格单独成句、单独高亮，朗读在格与格之间停顿。
        val markdown = "| 现象 | 根因 |\n| --- | --- |\n| 副标题不高亮 | 只按长度合并跨行照并 |"
        val sentences = sentencesOf(markdown)
        assertEquals(listOf("现象", "根因", "副标题不高亮", "只按长度合并跨行照并"), sentences)
        assertEquals("副标题不高亮", highlighted(markdown, 2))
        assertEquals("只按长度合并跨行照并", highlighted(markdown, 3))
    }

    @Test
    fun tableCellWithInlineCodeKeepsItsOwnPunctuation() {
        // 单元格里有行内代码时同样只覆盖本格，冒号与代码都在高亮区间内。
        val markdown = "| 项 | 说明 |\n| --- | --- |\n| 甲 | 句尾紧跟 `code`：看这里 |"
        assertEquals(
            condensed("句尾紧跟 code：看这里"),
            highlighted(markdown, 3),
        )
    }

    @Test
    fun shortHeadingIsItsOwnSentenceAndHighlight() {
        // 短标题以前会被当成硬切零头并进上一段，导致标题所在块压根没有高亮。
        val markdown = "先说一句总结。\n\n## 📦 产物\n\n产物在下面。"
        assertEquals(listOf("先说一句总结。", "📦 产物", "产物在下面。"), sentencesOf(markdown))
        assertEquals(condensed("📦 产物"), highlighted(markdown, 1))
        assertEquals("产物在下面。", highlighted(markdown, 2))
    }

    @Test
    fun trailingPunctuationAfterInlineCodeIsHighlighted() {
        // 行内代码两侧渲染时会补窄空格，整句 indexOf 失败就退化成句首近似，句尾一两个字和标点会被漏掉。
        val markdown = "签名核对通过，`aapt2 dump badging` 显示版本号 2026102610。\n\n结尾一句。"
        assertEquals(
            condensed("签名核对通过，aapt2 dump badging 显示版本号 2026102610。"),
            highlighted(markdown, 0),
        )
    }

    @Test
    fun sentenceBeforeShortHeadingStillCoversItsOwnPunctuation() {
        val markdown = "改了两个地方（`feat/local`，未提交）：\n\n## 🧪 验证\n\n测试全过。"
        assertEquals(
            condensed("改了两个地方（feat/local，未提交）："),
            highlighted(markdown, 0),
        )
        assertEquals("测试全过。", highlighted(markdown, 2))
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
        val range = requireNotNull(last).ranges.first()
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
