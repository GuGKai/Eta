package io.github.mangi.eta.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.AssistantRoleStatus
import io.github.mangi.eta.agent.voice.DefaultAssistantOwner
import io.github.mangi.eta.ui.screens.settings.SettingsSystemStatus

// fork 本地新增：设置页系统状态的内存缓存与助手角色状态文案。

@Volatile
internal var cachedSettingsSystemStatus: SettingsSystemStatus? = null

@Composable
internal fun AssistantRoleStatus.displayValue(): String = when (owner) {
    DefaultAssistantOwner.ETA -> "Eta"
    DefaultAssistantOwner.GEMINI -> "Gemini"
    DefaultAssistantOwner.OTHER -> ownerLabel ?: stringResource(R.string.assistant_role_status_other)
    DefaultAssistantOwner.NONE -> stringResource(R.string.assistant_role_status_unset)
}
