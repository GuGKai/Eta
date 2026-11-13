package io.github.mangi.eta.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
internal fun SpeechVoiceSection(store: SpeechSettingsStore) {
    val settings = store.settings
    val voice = when (settings.tts) {
        TtsProvider.NONE -> return
        TtsProvider.QWEN -> settings.qwenVoice
        TtsProvider.DOUBAO -> settings.doubaoVoice
        TtsProvider.GEMINI -> settings.geminiTts.voice
        TtsProvider.MINIMAX -> settings.minimaxTts.voice
        TtsProvider.SYSTEM -> settings.systemTtsVoice
    }
    val presets = SpeechVoicePresets.forProvider(settings.tts)
    val presetIndex = presets.indexOfFirst { it.id == voice }
    var editingCustom by rememberSaveable(settings.tts) { mutableStateOf(false) }
    val custom = editingCustom || presetIndex < 0
    val change: (String) -> Unit = {
        store.edit(when (settings.tts) {
            TtsProvider.NONE -> settings
            TtsProvider.QWEN -> settings.copy(qwenVoice = it)
            TtsProvider.DOUBAO -> settings.copy(doubaoVoice = it)
            TtsProvider.GEMINI -> settings.copy(geminiTts = settings.geminiTts.copy(voice = it))
            TtsProvider.MINIMAX -> settings.copy(minimaxTts = settings.minimaxTts.copy(voice = it))
            TtsProvider.SYSTEM -> settings.copy(systemTtsVoice = it)
        })
    }
    // 选中预设项就切过去；选中末尾的「自定义」则只打开输入框，不动已有音色。
    val onSelectIndex: (Int) -> Unit = { index ->
        val preset = presets.getOrNull(index)
        if (preset == null) {
            editingCustom = true
        } else {
            editingCustom = false
            change(preset.id)
        }
    }

    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_voice)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    val labels = presets.map { preset ->
                        "${stringResource(preset.name)} · ${stringResource(preset.summary)}"
                    } + stringResource(R.string.speech_voice_custom)
                    CouiDropdownPreference(
                        title = stringResource(R.string.speech_voice),
                        items = labels,
                        selectedIndex = if (custom) presets.size else presetIndex,
                        onSelectedIndexChange = onSelectIndex,
                    )
                }
                AppearanceUiStyle.MIUIX -> {
                    OverlaySpinnerPreference(
                        title = stringResource(R.string.speech_voice),
                        items = presets.map { preset ->
                            DropdownItem(text = stringResource(preset.name), summary = stringResource(preset.summary))
                        } + DropdownItem(text = stringResource(R.string.speech_voice_custom)),
                        selectedIndex = if (custom) presets.size else presetIndex,
                        onSelectedIndexChange = onSelectIndex,
                    )
                }
            }
            AnimatedVisibility(visible = custom) {
                SpeechFieldColumn {
                    SpeechField(
                        label = stringResource(R.string.speech_voice_id),
                        value = voice,
                        onChange = { change(it.trim()) },
                    )
                }
            }
            SpeechDeliveryFields(store)
        }
        SpeechNote(
            stringResource(
                when (settings.tts) {
                    TtsProvider.QWEN -> R.string.speech_qwen_voice_note
                    TtsProvider.DOUBAO -> R.string.speech_doubao_voice_note
                    TtsProvider.GEMINI -> R.string.speech_gemini_voice_note
                    TtsProvider.MINIMAX -> R.string.speech_minimax_voice_note
                    // 系统语音的音色来自设备引擎，界面里另有选择器与说明。
                    TtsProvider.SYSTEM -> R.string.speech_system_tts_note
                    TtsProvider.NONE -> error("No synthesis provider")
                },
            ),
        )
    }
}
