package io.github.mangi.eta.agent.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechTextTest {
    @Test fun spokenTextReadsCodeKeepsLinkLabelAndInlineIdentifiers() {
        val text = SpeechText.readable("""
            # 操作说明

            请查看 **设置** 与 [帮助](https://example.invalid)，参数是 `user_name`。

            ```kotlin
            secretCommand()
            ```

                anotherSecretCommand()

            完成。
        """.trimIndent())
        assertTrue(text.contains("操作说明"))
        assertTrue(text.contains("设置"))
        assertTrue(text.contains("帮助"))
        assertTrue(text.contains("user_name"))
        assertFalse(text.contains("https://"))
        // 代码块与缩进代码都照读，只丢掉围栏与缩进标记。
        assertTrue(text.contains("secretCommand()"))
        assertTrue(text.contains("anotherSecretCommand()"))
        assertFalse(text.contains("**"))
        assertTrue(text.endsWith("完成。"))
    }

    @Test fun sentenceChunkingPreservesContentAndSurrogatePairs() {
        val text = "第一句话。第二句话。" + "你好😀".repeat(30)
        val chunks = SpeechText.chunks(text, 17)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 17 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
    }

    @Test fun codeOnlyAnswerReadsTheCodeBody() {
        // 只有代码块的回答也要读：只跳过围栏标记，代码内容照读。
        assertEquals("hello", SpeechText.readable("```\nhello\n```"))
    }
}
