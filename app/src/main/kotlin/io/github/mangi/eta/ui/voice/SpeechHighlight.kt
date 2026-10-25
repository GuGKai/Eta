package io.github.mangi.eta.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.mangi.eta.agent.voice.SpeechText

/**
 * 与朗读端完全一致的切句结果。
 *
 * 朗读把清洗后的正文按 [SpeechText.sentences] 切成句，界面要标出正在读的那一句，
 * 就必须用同一份文本、同一套规则算一遍，否则句序号对不上。
 */
@Composable
internal fun rememberSpeechSentences(content: String): List<String> =
    remember(content) { SpeechText.sentences(SpeechText.readable(content)) }
