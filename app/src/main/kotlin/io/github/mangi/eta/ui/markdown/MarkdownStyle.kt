package io.github.mangi.eta.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 正文与思考共用同一套渲染器，只在字号、颜色与留白上区分层级。 */
internal enum class MarkdownTone { Answer, Thinking }

/**
 * 一次渲染所需的全部样式。按主题与语气整体记忆，块级组件只读取不再推导，
 * 避免每个段落各自拼一遍 TextStyle。
 */
@Immutable
internal class MarkdownStyle(
    val tone: MarkdownTone,
    val body: TextStyle,
    private val headings: List<TextStyle>,
    val code: TextStyle,
    val table: TextStyle,
    val textColor: Color,
    val secondaryColor: Color,
    val markerColor: Color,
    val dividerColor: Color,
    val codeBackground: Color,
    val tableHeaderBackground: Color,
    val inline: MarkdownInlineStyle,
) {
    fun heading(level: Int): TextStyle = headings[(level - 1).coerceIn(0, headings.lastIndex)]

    /** 引用块内的弱化样式：文字退到次要色，结构与字号不变。 */
    fun muted(): MarkdownStyle = MarkdownStyle(
        tone = tone,
        body = body.copy(color = secondaryColor),
        headings = headings.map { it.copy(color = secondaryColor) },
        code = code,
        table = table.copy(color = secondaryColor),
        textColor = secondaryColor,
        secondaryColor = secondaryColor,
        markerColor = markerColor,
        dividerColor = dividerColor,
        codeBackground = codeBackground,
        tableHeaderBackground = tableHeaderBackground,
        inline = inline,
    )
}

@Composable
internal fun rememberMarkdownStyle(tone: MarkdownTone): MarkdownStyle {
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    // 会话正文与我发出的消息共用同一个字号倍率；思考过程不跟随。
    val chatTextScale = LocalAppearanceSettings.current.chatTextScale
    return remember(tone, colors, textStyles, chatTextScale) {
        val answer = tone == MarkdownTone.Answer
        // 只有正文语气跟随倍率，思考语气保持原本的紧凑字号。
        val scale = if (answer) chatTextScale else 1f
        val textColor = if (answer) colors.onSurface else colors.onSurfaceVariantSummary
        val body = (if (answer) textStyles.body1 else textStyles.body2).copy(
            fontSize = if (answer) 16.sp * scale else 14.sp,
            lineHeight = if (answer) ANSWER_LINE_HEIGHT_SP.sp * scale else THINKING_LINE_HEIGHT_SP.sp,
            color = textColor,
        )
        // 聊天里的标题只是段落强调，不是页面标题：字号克制，层级主要靠字重区分。
        val headings = if (answer) {
            listOf(
                body.copy(fontSize = 20.sp * scale, lineHeight = 28.sp * scale, fontWeight = FontWeight.Bold),
                body.copy(fontSize = 18.sp * scale, lineHeight = 26.sp * scale, fontWeight = FontWeight.Bold),
                body.copy(fontSize = 17.sp * scale, lineHeight = 25.sp * scale, fontWeight = FontWeight.SemiBold),
                body.copy(fontWeight = FontWeight.SemiBold),
            )
        } else {
            listOf(body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }
        MarkdownStyle(
            tone = tone,
            body = body,
            headings = headings,
            code = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = if (answer) 13.sp * scale else 12.sp,
                lineHeight = if (answer) 20.sp * scale else 18.sp,
                color = textColor,
            ),
            table = body.copy(
                fontSize = if (answer) 14.sp * scale else 13.sp,
                lineHeight = if (answer) 21.sp * scale else 19.sp,
            ),
            textColor = textColor,
            secondaryColor = colors.onSurfaceVariantSummary,
            markerColor = if (answer) colors.onSurface.copy(alpha = 0.55f) else colors.onSurfaceVariantSummary,
            dividerColor = colors.outline.copy(alpha = 0.6f),
            codeBackground = colors.surfaceContainer,
            tableHeaderBackground = colors.surfaceContainer,
            inline = MarkdownInlineStyle(
                linkColor = colors.primary,
                codeBackground = colors.onSurface.copy(alpha = 0.07f),
            ),
        )
    }
}

/**
 * 相邻块之间的留白。源码中的空行只用于切分块，不直接占据高度；可见间距按语义分配：
 * 段落之间约半行，标题前更宽以开启新段落，标题后收紧使其贴近所属内容。
 */
internal fun markdownBlockGap(previous: MarkdownBlock?, current: MarkdownBlock, tone: MarkdownTone): TextUnit {
    if (previous == null) return 0.sp
    val gap = when {
        current is MarkdownHeading && previous is MarkdownHeading -> 8f
        current is MarkdownHeading -> if (current.level <= 2) 22f else 18f
        previous is MarkdownHeading -> 8f
        previous is MarkdownParagraph && current is MarkdownParagraph -> 12f
        else -> 14f
    }
    val scale = if (tone == MarkdownTone.Answer) 1f else THINKING_GAP_SCALE
    return (gap * scale).sp
}

/** 紧凑列表项内部（例如段落后紧跟子列表）只保留很小的间距。 */
internal fun markdownCompactGap(previous: MarkdownBlock?, tone: MarkdownTone): TextUnit =
    if (previous == null) 0.sp else if (tone == MarkdownTone.Answer) 4.sp else 3.sp

internal const val ANSWER_LINE_HEIGHT_SP = 26
internal const val THINKING_LINE_HEIGHT_SP = 22
private const val THINKING_GAP_SCALE = 0.75f

/**
 * 会话正文字号倍率：只作用于助手回答与我发出的消息（正文、标题、代码、表格），
 * 思考过程与工具过程不跟随。
 */
internal fun TextStyle.scaleChatText(factor: Float): TextStyle =
    if (factor == 1f) {
        this
    } else {
        copy(
            fontSize = fontSize.scaleIfSpecified(factor),
            lineHeight = lineHeight.scaleIfSpecified(factor),
        )
    }

/** TextUnit 未指定时不能参与算术，原样返回。 */
internal fun TextUnit.scaleIfSpecified(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor
