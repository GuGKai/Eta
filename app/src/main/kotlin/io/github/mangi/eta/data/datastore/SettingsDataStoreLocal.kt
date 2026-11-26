package io.github.mangi.eta.data.datastore

// fork 本地功能抽离，避免与上游冲突：SettingsDataStore.kt 只保留同名一行转发。
// 本文件承载 fork 新增的持久化项（侧栏工作区、DeepSeek 余额快照）与其读写逻辑。

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

private val CONVERSATION_WORKSPACES = stringPreferencesKey("conversation_workspaces_v1")

private val workspaceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 侧栏工作区（会话文件夹）与「会话 → 工作区」归属。 */
internal fun DataStore<Preferences>.conversationWorkspacesFlow(): Flow<ConversationWorkspaceState> =
    data.map { prefs ->
        prefs[CONVERSATION_WORKSPACES]?.let { raw ->
            runCatching { workspaceJson.decodeFromString<ConversationWorkspaceState>(raw) }.getOrNull()
        } ?: ConversationWorkspaceState()
    }

internal suspend fun DataStore<Preferences>.writeConversationWorkspaces(state: ConversationWorkspaceState) {
    edit { it[CONVERSATION_WORKSPACES] = workspaceJson.encodeToString(state) }
}

private val DEEPSEEK_BALANCE = stringPreferencesKey("deepseek_balance_snapshot_v1")

/** 顶栏 DeepSeek 余额胶囊的快照（余额 + 基线 + 抓取时间）。 */
internal suspend fun DataStore<Preferences>.readDeepSeekBalanceJson(): String? =
    data.first()[DEEPSEEK_BALANCE]

internal suspend fun DataStore<Preferences>.writeDeepSeekBalanceJson(value: String) {
    edit { preferences ->
        preferences[DEEPSEEK_BALANCE] = value
    }
}
