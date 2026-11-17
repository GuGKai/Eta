package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val defaultAssistantSystemPrompt: String = "",
    val memoryEnabled: Boolean = true,
    val autoMemoryEnabled: Boolean = true,
    val autoSkillsEnabled: Boolean = true,
    val appearance: AppearanceSettings = AppearanceSettings(),
)
