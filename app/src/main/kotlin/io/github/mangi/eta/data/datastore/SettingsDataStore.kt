package io.github.mangi.eta.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.DEFAULT_CHAT_TEXT_SCALE
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.AssistantOverlaySettings
import io.github.mangi.eta.data.model.ConversationTitleSettings
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.SubagentSettings
import io.github.mangi.eta.data.model.WebToolsSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.mangi.eta.data.model.Settings
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 一个工作区（会话文件夹）的持久化记录。
 *
 * 侧栏里工作区就是文件夹：可重命名、可删除，可展开/折叠；折叠状态一并持久化，
 * 重启后保持用户上次的展开状态。
 */
@Serializable
data class ConversationWorkspaceRecord(
    val id: String,
    val name: String,
    val collapsed: Boolean = false,
)

/** 工作区列表与「会话 → 工作区」归属；一条会话最多属于一个工作区。 */
@Serializable
data class ConversationWorkspaceState(
    val workspaces: List<ConversationWorkspaceRecord> = emptyList(),
    val assignments: Map<String, String> = emptyMap(),
)

internal object SettingsDataStore {
    private val SPEECH_SETTINGS = stringPreferencesKey("speech_settings_v1")
    private val WEB_TOOLS_SETTINGS = stringPreferencesKey("web_tools_settings_v1")
    private val SUBAGENT_SETTINGS = stringPreferencesKey("subagent_settings_v1")
    private val CONVERSATION_TITLE_SETTINGS = stringPreferencesKey("conversation_title_settings_v1")
    private val ASSISTANT_OVERLAY_SETTINGS = stringPreferencesKey("assistant_overlay_settings_v1")
    private val speechJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun speechSettingsFlow(): Flow<SpeechSettings> {
        ensureInitialized()
        return dataStore.data.map { prefs ->
            prefs[SPEECH_SETTINGS]?.let { speechJson.decodeFromString<SpeechSettings>(it) }
                ?: SpeechSettings()
        }
    }

    suspend fun speechSettings() = speechSettingsFlow().first()

    suspend fun setSpeechSettings(settings: SpeechSettings) {
        ensureInitialized()
        dataStore.edit { it[SPEECH_SETTINGS] = speechJson.encodeToString(settings) }
    }

    fun webToolsSettingsFlow(): Flow<WebToolsSettings> {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .map { prefs ->
                prefs[WEB_TOOLS_SETTINGS]?.let { raw ->
                    runCatching { speechJson.decodeFromString<WebToolsSettings>(raw) }.getOrNull()
                } ?: WebToolsSettings()
            }
    }

    suspend fun webToolsSettings(): WebToolsSettings = webToolsSettingsFlow().first()

    suspend fun setWebToolsSettings(settings: WebToolsSettings) {
        ensureInitialized()
        dataStore.edit { it[WEB_TOOLS_SETTINGS] = speechJson.encodeToString(settings) }
    }

    fun subagentSettingsFlow(): Flow<SubagentSettings> {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .map { prefs ->
                prefs[SUBAGENT_SETTINGS]?.let { raw ->
                    runCatching { speechJson.decodeFromString<SubagentSettings>(raw) }.getOrNull()
                } ?: SubagentSettings()
            }
    }

    suspend fun subagentSettings(): SubagentSettings = subagentSettingsFlow().first()

    suspend fun setSubagentSettings(settings: SubagentSettings) {
        ensureInitialized()
        dataStore.edit { it[SUBAGENT_SETTINGS] = speechJson.encodeToString(settings) }
    }

    private val CONVERSATION_WORKSPACES = stringPreferencesKey("conversation_workspaces_v1")

    private val workspaceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 侧栏工作区（会话文件夹）与「会话 → 工作区」归属。 */
    fun conversationWorkspacesFlow(): Flow<ConversationWorkspaceState> {
        ensureInitialized()
        return dataStore.data.map { prefs ->
            prefs[CONVERSATION_WORKSPACES]?.let { raw ->
                runCatching { workspaceJson.decodeFromString<ConversationWorkspaceState>(raw) }.getOrNull()
            } ?: ConversationWorkspaceState()
        }
    }

    suspend fun conversationWorkspaces(): ConversationWorkspaceState = conversationWorkspacesFlow().first()

    suspend fun setConversationWorkspaces(state: ConversationWorkspaceState) {
        ensureInitialized()
        dataStore.edit { it[CONVERSATION_WORKSPACES] = workspaceJson.encodeToString(state) }
    }

    fun conversationTitleSettingsFlow(): Flow<ConversationTitleSettings> {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .map { prefs ->
                prefs[CONVERSATION_TITLE_SETTINGS]?.let { raw ->
                    runCatching { speechJson.decodeFromString<ConversationTitleSettings>(raw) }.getOrNull()
                } ?: ConversationTitleSettings()
            }
    }

    suspend fun conversationTitleSettings(): ConversationTitleSettings = conversationTitleSettingsFlow().first()

    suspend fun setConversationTitleSettings(settings: ConversationTitleSettings) {
        ensureInitialized()
        dataStore.edit { it[CONVERSATION_TITLE_SETTINGS] = speechJson.encodeToString(settings) }
    }

    fun assistantOverlaySettingsFlow(): Flow<AssistantOverlaySettings> {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .map { prefs ->
                prefs[ASSISTANT_OVERLAY_SETTINGS]?.let { raw ->
                    runCatching { speechJson.decodeFromString<AssistantOverlaySettings>(raw) }.getOrNull()
                } ?: AssistantOverlaySettings()
            }
    }

    suspend fun assistantOverlaySettings(): AssistantOverlaySettings = assistantOverlaySettingsFlow().first()

    suspend fun setAssistantOverlaySettings(settings: AssistantOverlaySettings) {
        ensureInitialized()
        dataStore.edit { it[ASSISTANT_OVERLAY_SETTINGS] = speechJson.encodeToString(settings) }
    }

    private const val STORE_NAME = "eta_settings"

    private val SELECTED_PROVIDER_ID = stringPreferencesKey("selected_provider_id")
    private val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
    private val DEEPSEEK_BALANCE = stringPreferencesKey("deepseek_balance_snapshot_v1")
    private val DEFAULT_ASSISTANT_SYSTEM_PROMPT = stringPreferencesKey("default_assistant_system_prompt")
    private val OFFICIAL_MODEL_CATALOG_REVISION = intPreferencesKey("official_model_catalog_revision")
    private val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
    private val AUTO_MEMORY_ENABLED = booleanPreferencesKey("auto_memory_enabled")
    private val AUTO_SKILLS_ENABLED = booleanPreferencesKey("auto_skills_enabled")
    private val LINUX_DISTRIBUTION = stringPreferencesKey("linux_distribution")
    private val APPEARANCE_UI_STYLE = stringPreferencesKey("appearance_ui_style")
    private val APPEARANCE_THEME_MODE = stringPreferencesKey("appearance_theme_mode")
    private val APPEARANCE_MONET_ENABLED = booleanPreferencesKey("appearance_monet_enabled")
    private val APPEARANCE_PALETTE_STYLE = stringPreferencesKey("appearance_palette_style")
    private val APPEARANCE_ACCENT_COLOR = stringPreferencesKey("appearance_accent_color")
    private val APPEARANCE_PURE_BLACK_ENABLED = booleanPreferencesKey("appearance_pure_black_enabled")
    private val APPEARANCE_BLUR_ENABLED = booleanPreferencesKey("appearance_blur_enabled")
    private val APPEARANCE_PROGRESSIVE_BLUR_ENABLED =
        booleanPreferencesKey("appearance_progressive_blur_enabled")
    private val APPEARANCE_TOP_BAR_BLUR_STYLE = stringPreferencesKey("appearance_top_bar_blur_style")
    private val APPEARANCE_SWIPE_DISMISS_ENABLED =
        booleanPreferencesKey("appearance_swipe_dismiss_enabled")
    private val APPEARANCE_PREDICTIVE_BACK_ENABLED =
        booleanPreferencesKey("appearance_predictive_back_enabled")
    private val APPEARANCE_INTERFACE_SCALE = floatPreferencesKey("appearance_interface_scale")
    private val APPEARANCE_CHAT_TEXT_SCALE = floatPreferencesKey("appearance_chat_text_scale")
    private const val SELECTED_MODEL_BY_PROVIDER_PREFIX = "selected_model_id_by_provider."

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

    @Volatile
    private lateinit var dataStore: DataStore<Preferences>

    /** 进程内最近一次读到的设置：界面首帧直接用它，避免先闪「未配置 / 不支持」。 */
    @Volatile
    private var settingsCache: Settings? = null

    private var warmUpJob: Job? = null

    fun init(context: Context, prewarm: Boolean = true) {
        if (!::dataStore.isInitialized) {
            dataStore = context.applicationContext.dataStore
        }
        if (prewarm) warmUp()
    }

    fun cachedSettings(): Settings? = settingsCache

    /** 应用启动即订阅一次，让缓存提前就绪；失败不影响正常读取。 */
    private fun warmUp() {
        if (warmUpJob != null || !::dataStore.isInitialized) return
        warmUpJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { settingsFlow().collect { } }
        }
    }

    fun settingsFlow(): Flow<Settings> {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw cause
                }
            }
            .map { preferences ->
                preferences.toSettings().also { settingsCache = it }
            }
    }

    suspend fun settings(): Settings = settingsFlow().first()

    suspend fun officialModelCatalogRevision(): Int {
        ensureInitialized()
        return dataStore.data.first()[OFFICIAL_MODEL_CATALOG_REVISION] ?: 0
    }

    suspend fun setOfficialModelCatalogRevision(revision: Int) {
        ensureInitialized()
        dataStore.edit { preferences ->
            preferences[OFFICIAL_MODEL_CATALOG_REVISION] = revision
        }
    }

    /** 顶栏 DeepSeek 余额胶囊的快照（余额 + 基线 + 抓取时间）。 */
    suspend fun deepSeekBalanceJson(): String? {
        ensureInitialized()
        return dataStore.data.first()[DEEPSEEK_BALANCE]
    }

    suspend fun setDeepSeekBalanceJson(value: String) {
        ensureInitialized()
        dataStore.edit { preferences ->
            preferences[DEEPSEEK_BALANCE] = value
        }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        ensureInitialized()
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            val updated = transform(current)
            require(updated.defaultAssistantSystemPrompt.length <= 32_000 && '\u0000' !in updated.defaultAssistantSystemPrompt)
            prefs.putOrRemove(DEFAULT_ASSISTANT_SYSTEM_PROMPT, updated.defaultAssistantSystemPrompt)
            prefs.putOrRemove(SELECTED_PROVIDER_ID, updated.selectedProviderId)
            prefs.putOrRemove(SELECTED_MODEL_ID, updated.selectedModelId)
            prefs[MEMORY_ENABLED] = updated.memoryEnabled
            prefs[AUTO_MEMORY_ENABLED] = updated.autoMemoryEnabled
            prefs[AUTO_SKILLS_ENABLED] = updated.autoSkillsEnabled
            prefs.putAppearance(updated.appearance.normalized())
        }
    }

    fun selectedProviderIdFlow(): Flow<String?> =
        settingsFlow().map { it.selectedProviderId }

    fun selectedModelIdFlow(): Flow<String?> =
        settingsFlow().map { it.selectedModelId }

    suspend fun selectedModelIdForProvider(providerId: String): String? {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw cause
                }
            }
            .map { prefs -> prefs[selectedModelByProviderKey(providerId)] }
            .first()
    }

    fun memoryEnabledFlow(): Flow<Boolean> =
        settingsFlow().map { it.memoryEnabled }

    fun linuxDistributionFlow(): Flow<String?> {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .map { preferences -> preferences[LINUX_DISTRIBUTION] }
    }

    fun linuxBackendFlow(distribution: String): Flow<String?> {
        ensureInitialized()
        return dataStore.data.catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }.map { it[stringPreferencesKey("linux_backend.$distribution")] }
    }

    suspend fun setLinuxBackend(distribution: String, backend: String?) {
        ensureInitialized()
        dataStore.edit { preferences ->
            val key = stringPreferencesKey("linux_backend.$distribution")
            if (backend == null) preferences.remove(key) else preferences[key] = backend
        }
    }

    fun appearanceSettingsFlow(): Flow<AppearanceSettings> =
        settingsFlow().map { it.appearance }

    suspend fun setSelectedProviderId(id: String?) {
        updateSettings { it.copy(selectedProviderId = id) }
    }

    suspend fun setSelectedModelId(id: String?) {
        updateSettings { it.copy(selectedModelId = id) }
    }

    suspend fun setSelection(providerId: String?, modelId: String?) {
        ensureInitialized()
        dataStore.edit { prefs ->
            val previousProviderId = prefs[SELECTED_PROVIDER_ID]
            val previousModelId = prefs[SELECTED_MODEL_ID]
            if (previousProviderId != null && previousModelId != null) {
                prefs[selectedModelByProviderKey(previousProviderId)] = previousModelId
            }

            prefs.putOrRemove(SELECTED_PROVIDER_ID, providerId)
            prefs.putOrRemove(SELECTED_MODEL_ID, modelId)
            if (providerId != null && modelId != null) {
                prefs[selectedModelByProviderKey(providerId)] = modelId
            }
        }
    }

    suspend fun clearSelectedModelIdForProvider(providerId: String) {
        ensureInitialized()
        dataStore.edit { prefs ->
            prefs.remove(selectedModelByProviderKey(providerId))
        }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        updateSettings { it.copy(memoryEnabled = enabled) }
    }

    suspend fun setLinuxDistribution(value: String?) {
        ensureInitialized()
        dataStore.edit { preferences -> preferences.putOrRemove(LINUX_DISTRIBUTION, value) }
    }

    suspend fun setAppearanceSettings(settings: AppearanceSettings) {
        updateSettings { it.copy(appearance = settings.normalized()) }
    }

    suspend fun updateAppearanceSettings(transform: (AppearanceSettings) -> AppearanceSettings) {
        updateSettings { settings ->
            settings.copy(appearance = transform(settings.appearance).normalized())
        }
    }

    suspend fun selectUiStyle(style: AppearanceUiStyle) {
        ensureInitialized()
        dataStore.edit { prefs ->
            prefs[APPEARANCE_UI_STYLE] = style.persistedValue
        }
    }

    suspend fun hasSavedUiStyle(): Boolean {
        ensureInitialized()
        return dataStore.data
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }
            .first()
            .contains(APPEARANCE_UI_STYLE)
    }

    private fun ensureInitialized() {
        check(::dataStore.isInitialized) {
            "SettingsDataStore.init(context) must be called in Application.onCreate()"
        }
    }

    private fun selectedModelByProviderKey(providerId: String): Preferences.Key<String> =
        stringPreferencesKey("$SELECTED_MODEL_BY_PROVIDER_PREFIX$providerId")

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value.isNullOrBlank()) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        selectedProviderId = this[SELECTED_PROVIDER_ID],
        selectedModelId = this[SELECTED_MODEL_ID],
        defaultAssistantSystemPrompt = this[DEFAULT_ASSISTANT_SYSTEM_PROMPT].orEmpty(),
        memoryEnabled = this[MEMORY_ENABLED] ?: true,
        autoMemoryEnabled = this[AUTO_MEMORY_ENABLED] ?: true,
        autoSkillsEnabled = this[AUTO_SKILLS_ENABLED] ?: true,
        appearance = AppearanceSettings(
            uiStyle = AppearanceUiStyle.fromPersistedValue(this[APPEARANCE_UI_STYLE]),
            themeMode = AppearanceThemeMode.fromPersistedValue(this[APPEARANCE_THEME_MODE]),
            monetEnabled = this[APPEARANCE_MONET_ENABLED] ?: false,
            paletteStyle = AppearancePaletteStyle.fromPersistedValue(this[APPEARANCE_PALETTE_STYLE]),
            accentColor = AppearanceAccentColor.fromPersistedValue(this[APPEARANCE_ACCENT_COLOR]),
            pureBlackEnabled = this[APPEARANCE_PURE_BLACK_ENABLED] ?: false,
            blurEnabled = this[APPEARANCE_BLUR_ENABLED] ?: true,
            progressiveBlurEnabled = this[APPEARANCE_PROGRESSIVE_BLUR_ENABLED] ?: true,
            topBarBlurStyle = AppearanceTopBarBlurStyle.fromPersistedValue(
                this[APPEARANCE_TOP_BAR_BLUR_STYLE],
            ),
            swipeDismissEnabled = this[APPEARANCE_SWIPE_DISMISS_ENABLED] ?: true,
            predictiveBackEnabled = this[APPEARANCE_PREDICTIVE_BACK_ENABLED] ?: true,
            interfaceScale = this[APPEARANCE_INTERFACE_SCALE] ?: 1f,
            chatTextScale = this[APPEARANCE_CHAT_TEXT_SCALE] ?: DEFAULT_CHAT_TEXT_SCALE,
        ).normalized(),
    )

    private fun MutablePreferences.putAppearance(settings: AppearanceSettings) {
        val currentStyle = AppearanceUiStyle.fromPersistedValue(this[APPEARANCE_UI_STYLE])
        if (contains(APPEARANCE_UI_STYLE) || settings.uiStyle != currentStyle) {
            this[APPEARANCE_UI_STYLE] = settings.uiStyle.persistedValue
        }
        this[APPEARANCE_THEME_MODE] = settings.themeMode.persistedValue
        this[APPEARANCE_MONET_ENABLED] = settings.monetEnabled
        this[APPEARANCE_PALETTE_STYLE] = settings.paletteStyle.persistedValue
        this[APPEARANCE_ACCENT_COLOR] = settings.accentColor.persistedValue
        this[APPEARANCE_PURE_BLACK_ENABLED] = settings.pureBlackEnabled
        this[APPEARANCE_BLUR_ENABLED] = settings.blurEnabled
        this[APPEARANCE_PROGRESSIVE_BLUR_ENABLED] = settings.progressiveBlurEnabled
        this[APPEARANCE_TOP_BAR_BLUR_STYLE] = settings.topBarBlurStyle.persistedValue
        this[APPEARANCE_SWIPE_DISMISS_ENABLED] = settings.swipeDismissEnabled
        this[APPEARANCE_PREDICTIVE_BACK_ENABLED] = settings.predictiveBackEnabled
        this[APPEARANCE_INTERFACE_SCALE] = settings.interfaceScale
        this[APPEARANCE_CHAT_TEXT_SCALE] = settings.chatTextScale
    }
}
