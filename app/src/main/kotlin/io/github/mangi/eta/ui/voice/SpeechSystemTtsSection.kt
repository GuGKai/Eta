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
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.SystemTtsEngines
import io.github.mangi.eta.agent.voice.SystemTtsSpeaker
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle

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

    Column {
        EtaPreferenceGroupTitle(stringResource(R.string.speech_group_system_tts))
        EtaPreferenceGroup {
            EtaOverlayDropdownPreference(
                title = stringResource(R.string.speech_system_engine),
                items = engineItems,
                selectedIndex = engineIndex,
                onSelectedIndexChange = { index ->
                    store.edit(settings.copy(systemTtsEngine = engines.getOrNull(index - 1)?.packageName.orEmpty(), systemTtsVoice = ""))
                },
            )
            EtaPreferenceDivider(hasLeading = false)
            EtaOverlayDropdownPreference(
                title = stringResource(R.string.speech_system_voice),
                items = voiceItems,
                selectedIndex = voiceIndex,
                summary = if (loading) stringResource(R.string.speech_system_voice_loading) else null,
                onSelectedIndexChange = { index ->
                    store.edit(settings.copy(systemTtsVoice = voices.getOrNull(index - 1).orEmpty()))
                },
            )
        }
        SpeechNote(stringResource(R.string.speech_system_tts_note))
    }
}
