package io.github.mangi.eta.ui.markdown

// fork 本地功能抽离，避免与上游冲突：MarkdownContent.kt 只保留一处调用。
// 本文件承载 fork 新增的朗读进度高亮（数据模型 + CompositionLocal + 上色渲染）。

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 朗读进度高亮：把正在朗读的那一句涂上主题色背景。
 *
 * 一句理论上可能跨多块（排版把一句话拆进相邻块时），所以按块拆成多段 [Range]，
 * 每个块只涂属于自己那一段，其余块读取到的仍是原文。
 */
@Immutable
internal data class MarkdownSpeechHighlight(
    val ranges: List<Range>,
) {
    @Immutable
    data class Range(
        val blockOffset: Int,
        val start: Int,
        val end: Int,
    )

    /** 这个块需要涂的部分；没有就是空。 */
    fun rangesFor(blockOffset: Int): List<Range> = ranges.filter { it.blockOffset == blockOffset }
}

internal val LocalMarkdownSpeechHighlight = compositionLocalOf<MarkdownSpeechHighlight?> { null }

/** 背景取主题高亮色，透明度压低到不遮住正文。 */
private const val SPEECH_HIGHLIGHT_ALPHA = 0.4f

/**
 * 正在朗读的那一句涂上主题高亮色背景。
 *
 * 只对命中的块加一层 background span，其余块原样返回，不参与重组。
 */
@Composable
internal fun markdownSpeechHighlighted(text: AnnotatedString, blockOffset: Int): AnnotatedString {
    val highlight = LocalMarkdownSpeechHighlight.current ?: return text
    val ranges = highlight.rangesFor(blockOffset)
    if (ranges.isEmpty()) return text
    val background = MiuixTheme.colorScheme.primary.copy(alpha = SPEECH_HIGHLIGHT_ALPHA)
    return remember(text, ranges, background) {
        AnnotatedString.Builder(text)
            .apply {
                for (range in ranges) {
                    val start = range.start.coerceIn(0, text.length)
                    val end = range.end.coerceIn(start, text.length)
                    if (end > start) addStyle(SpanStyle(background = background), start, end)
                }
            }
            .toAnnotatedString()
    }
}
