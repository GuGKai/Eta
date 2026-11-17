package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.itos.coui.layout.Divider
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.SystemTtsEngines
import io.github.mangi.eta.agent.voice.SystemTtsSpeaker
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/** 系统 TTS 播报：选择引擎（默认跟随系统）与引擎自带音色；凭据由引擎应用自己管理。 */
@Composable
internal fun SpeechSystemTtsSection(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val settings = store.settings
    val engine = settings.systemTtsEngine
    val voice = settings.systemTtsVoice
    val engines = remember { SystemTtsEngines.list(context) }
    var voices by remember { mutableStateOf(emptyList<String>()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(engine) {
        loading = true
        voices = try {
            val speaker = SystemTtsSpeaker(context, engine.ifBlank { null })
            try {
                speaker.voices()
            } finally {
                speaker.close()
            }
        } catch (_: Exception) {
            emptyList()
        }
        loading = false
    }

    val engineIndex = engines.indexOfFirst { it.packageName == engine }.let { if (it < 0) 0 else it + 1 }
    val voiceIndex = voices.indexOf(voice).let { if (it < 0) 0 else it + 1 }
    val engineItems = listOf(stringResource(R.string.speech_system_engine_default)) +
        engines.map { if (it.label == it.packageName) it.label else it.label + "（" + it.packageName + "）" }
    val voiceItems = listOf(stringResource(R.string.speech_system_voice_default)) + voices
    val engineTitle = stringResource(R.string.speech_system_engine)
    val voiceTitle = stringResource(R.string.speech_system_voice)
    val voiceSummary = if (loading) stringResource(R.string.speech_system_voice_loading) else null
    val onEngineChange: (Int) -> Unit = { index ->
        store.edit(settings.copy(systemTtsEngine = engines.getOrNull(index - 1)?.packageName.orEmpty(), systemTtsVoice = ""))
    }
    val onVoiceChange: (Int) -> Unit = { index ->
        store.edit(settings.copy(systemTtsVoice = voices.getOrNull(index - 1).orEmpty()))
    }

    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_system_tts)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    CouiDropdownPreference(
                        title = engineTitle,
                        items = engineItems,
                        selectedIndex = engineIndex,
                        onSelectedIndexChange = onEngineChange,
                    )
                    Divider()
                    CouiDropdownPreference(
                        title = voiceTitle,
                        items = voiceItems,
                        selectedIndex = voiceIndex,
                        summary = voiceSummary,
                        onSelectedIndexChange = onVoiceChange,
                    )
                }
                AppearanceUiStyle.MIUIX -> {
                    OverlayDropdownPreference(
                        title = engineTitle,
                        items = engineItems,
                        selectedIndex = engineIndex,
                        onSelectedIndexChange = onEngineChange,
                    )
                    OverlayDropdownPreference(
                        title = voiceTitle,
                        items = voiceItems,
                        selectedIndex = voiceIndex,
                        summary = voiceSummary,
                        onSelectedIndexChange = onVoiceChange,
                    )
                }
            }
        }
        SpeechNote(stringResource(R.string.speech_system_tts_note))
    }
}
