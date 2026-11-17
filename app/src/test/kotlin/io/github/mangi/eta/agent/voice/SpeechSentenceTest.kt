package io.github.mangi.eta.agent.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 逐句合成的切分规则：中文句末标点与换行切句，英文句号不参与切分，短碎片并回相邻句。 */
class SpeechSentenceTest {
    private fun sentences(text: String) = SpeechText.sentences(text)

    @Test
    fun `英文句号不参与切分`() {
        val result = sentences("跑的是 Android 16.然后下一句我还是用中文写的。偏差大概 8.8%，听起来像快进。")
        result.forEach { println("[句] $it") }
        assertEquals(2, result.size)
        assertEquals("跑的是 Android 16.然后下一句我还是用中文写的。", result[0])
        assertEquals("偏差大概 8.8%，听起来像快进。", result[1])
    }

    @Test
    fun `点号密集的标识符保持完整`() {
        val result = sentences("改 app/build.gradle.kts 这个文件就行，别动 SpeechPcmOutput.kt。")
        result.forEach { println("[句] $it") }
        assertEquals(1, result.size)
        assertTrue(result[0].contains("app/build.gradle.kts"))
        assertTrue(result[0].contains("SpeechPcmOutput.kt"))
    }

    @Test
    fun `连续标点与收尾引号不被拆开`() {
        val result = sentences("他说“先停一下……”。然后就没有然后了。")
        result.forEach { println("[句] $it") }
        assertEquals(2, result.size)
        assertEquals("他说“先停一下……”。", result[0])
        assertEquals("然后就没有然后了。", result[1])
    }

    @Test
    fun `超长句按逗号细分且不超过上限`() {
        val result = sentences("这是很长的一句话，".repeat(30) + "结束。")
        result.forEach { println("[分片 ${it.length}字] …${it.takeLast(14)}") }
        assertTrue(result.size > 1)
        assertTrue(result.all { it.length <= 80 })
    }

    @Test
    fun `短句保持独立成句`() {
        assertEquals(listOf("按下暂停。", "好。"), sentences("按下暂停。好。"))
        assertEquals(
            listOf("好。", "这是一句足够长的句子，超过八个字。"),
            sentences("好。这是一句足够长的句子，超过八个字。"),
        )
    }

    @Test
    fun `硬切留下的零头并回前句`() {
        assertEquals(
            listOf("一二三四五六七八九十甲乙丙丁，短尾"),
            SpeechText.sentences("一二三四五六七八九十甲乙丙丁，短尾", limit = 16),
        )
    }

    @Test
    fun `换行也算句边界`() {
        assertEquals(listOf("第一行内容。", "第二行内容。"), sentences("第一行内容。\n第二行内容。"))
    }

    @Test
    fun `markdown 回答走完 readable 再切句`() {
        val markdown = """
            ## 🎚️ 采样率是什么

            AudioTrack 是 Android 里把 PCM 送到扬声器的通道。建它要声明采样率、声道、位深。

            - 采样率就是每秒采多少点。
            - 22 050 和 24 000 差别很小，遇到时按实际值来。

            ```kotlin
            val x = 1
            ```

            最后一句结论。
        """.trimIndent()
        val readable = SpeechText.readable(markdown)
        val result = sentences(readable)
        result.forEachIndexed { index, sentence -> println("[${index + 1}] $sentence") }
        // 代码块照读：围栏标记不出现，代码内容会出现。
        assertTrue(readable.contains("val x = 1"))
        assertFalse(readable.contains("##"))
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.length <= 80 })
        assertEquals("最后一句结论。", result.last())
    }
}
