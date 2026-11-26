package io.github.mangi.eta.ui.markdown

import io.github.mangi.eta.agent.voice.tts.SpeechText
import java.util.concurrent.atomic.AtomicBoolean
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
    fun fencedCodeIsSkippedWithNotice() {
        val markdown = "改这两行：\n\n```kotlin\nval a = 1\nval b = 2\n```\n\n就这么多。"
        val sentences = sentencesOf(markdown)
        // 代码内容不读，每块只念一句占位提示；占位句不给高亮，不在代码框里涂近似区间。
        assertEquals(listOf("改这两行：", "代码块，略。", "就这么多。"), sentences)
        assertNull(highlighted(markdown, 1))
        assertEquals("就这么多。", highlighted(markdown, 2))
    }

    @Test
    fun indentedCodeIsSkippedWithNotice() {
        val markdown = "示例：\n\n    val c = 3\n\n结束。"
        assertEquals(listOf("示例：", "代码块，略。", "结束。"), sentencesOf(markdown))
        assertNull(highlighted(markdown, 1))
    }

    @Test
    fun skipNoticeDoesNotJumpToSameTextLaterInDocument() {
        // 文末说明里原样出现「代码块，略。」：占位句不能搜过去，否则高亮跳到不相干段落。
        val markdown = "先看一下。\n\n```kotlin\nval a = 1\n```\n\n后文提到「代码块，略。」这句话。"
        assertEquals(
            listOf("先看一下。", "代码块，略。", "后文提到「代码块，略。」", "这句话。"),
            sentencesOf(markdown),
        )
        assertNull(highlighted(markdown, 1))
        assertEquals(condensed("后文提到「代码块，略。」"), highlighted(markdown, 2))
        assertEquals("这句话。", highlighted(markdown, 3))
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
    fun orderedListItemsIgnoreTheSpokenLeadAndHighlightTheBody() {
        // 朗读会先念「第 1 项：」，界面上序号是灰色单独画的，块里只有正文；高亮必须落在正文上。
        val markdown = """
            开头一句说明。

            1. 就地替换那一步，其余步骤原样保留
            2. 我重出一份完整计划，你再看一遍
            3. 两步都要：先替换，需要时再重出
            4. 其他（直接说）

            收尾一句。
        """.trimIndent()
        val sentences = sentencesOf(markdown)
        assertEquals(6, sentences.size)
        assertTrue(sentences[1].startsWith("第 1 项"))
        assertEquals("就地替换那一步，其余步骤原样保留", highlighted(markdown, 1))
        assertEquals("我重出一份完整计划，你再看一遍", highlighted(markdown, 2))
        // 正文自己带冒号也不能被当成引导语剥掉。
        assertEquals("两步都要：先替换，需要时再重出", highlighted(markdown, 3))
        assertEquals("其他（直接说）", highlighted(markdown, 4))
        assertEquals("收尾一句。", highlighted(markdown, 5))
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

    /** 单测没有 RaTeX 原生库：预置不可用标记走「保留带定界符原文」路径，块文本字符与设备上
     *  appendInlineContent(alternateText = 原始源码) 完全一致（都带 $ 定界符）。 */
    private fun disableNativeMathEngine() {
        val field = RaTeXMathLayoutEngine::class.java.getDeclaredField("nativeUnavailable")
        field.isAccessible = true
        (field.get(RaTeXMathLayoutEngine) as AtomicBoolean).set(true)
    }

    @Test
    fun inlineMathSentencesHighlightWholeSentenceIncludingFormula() {
        disableNativeMathEngine()
        // 块文本里的公式是带 $ 定界符的原始源码，朗读文本剥掉了 $：定位时必须容忍这个差异，
        // 否则整句对不上、句首兜底区间偏短，游标漂移还会吃掉下一句的开头。
        val markdown = "半径 \$r\$ 的圆面积是 \$\\pi r^2\$，这个式子小学就背过。\n\n" +
            "周长 2πr 的推导更直观：把圆剪成很多细扇形拼起来，拼出的长方形一边是 \$r\$，另一边是周长的一半 \$\\pi r\$。\n\n" +
            "所以两式互为表里，只是 \$r\$ 的幂次差一次。"
        assertEquals(
            condensed("半径 \$r\$ 的圆面积是 \$\\pi r^2\$，这个式子小学就背过。"),
            highlighted(markdown, 0),
        )
        assertEquals(
            condensed("周长 2πr 的推导更直观：把圆剪成很多细扇形拼起来，拼出的长方形一边是 \$r\$，另一边是周长的一半 \$\\pi r\$。"),
            highlighted(markdown, 1),
        )
        assertEquals(
            condensed("所以两式互为表里，只是 \$r\$ 的幂次差一次。"),
            highlighted(markdown, 2),
        )
    }

    @Test
    fun sentenceStartingWithInlineMathHighlightsFromFormula() {
        disableNativeMathEngine()
        // 句首就是公式：候选落点也要从定界符 '$' 开始试，区间盖住整个公式区域。
        val markdown = "\$r\$ 是半径，别和直径搞混。\n\n下一句收尾。"
        assertEquals(
            condensed("\$r\$ 是半径，别和直径搞混。"),
            highlighted(markdown, 0),
        )
        assertEquals("下一句收尾。", highlighted(markdown, 1))
    }

    @Test
    fun plainDollarTextStillMatchesLiterally() {
        // 普通文本里的 '$' 不是公式定界符，朗读与块文本都有它，精确匹配优先，不受影响。
        val markdown = "这杯咖啡要 \$30，太贵了。"
        assertEquals(condensed("这杯咖啡要 \$30，太贵了。"), highlighted(markdown, 0))
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
