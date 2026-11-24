package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationPaneUiState(
    val conversations: List<ConversationSummaryUi>,
    val selectedConversationId: String?,
    /** 已生效的搜索词：只有按键盘搜索键才会改它，输入框里的草稿不写这里。 */
    val searchQuery: String,
    /**
     * 全库正文扫描（搜索第二阶段）是否正在跑。
     *
     * 第一阶段只做标题与预览的内存过滤，通常几十毫秒就有结果，不值得提示；
     * 第二阶段要读十几万行正文与分块，才需要把侧栏标题切成「正在搜索」。
     * 收起或取消后延迟复位，见 AgentAppState.scheduleSearchStatusReset。
     */
    val isSearchRunning: Boolean = false,
    /**
     * 工作区（会话文件夹）：按创建顺序排列，显示在置顶与日期分组之上。
     * 已归入工作区的会话不会再出现在下方分组里，避免同一条会话出现两次。
     */
    val workspaces: List<ConversationWorkspaceUi> = emptyList(),
)

/**
 * 侧栏里的一个工作区。语义上就是一个文件夹：可以重命名、删除，点击标题行展开或折叠。
 */
@Immutable
data class ConversationWorkspaceUi(
    val id: String,
    val name: String,
    val collapsed: Boolean = false,
    val conversations: List<ConversationSummaryUi> = emptyList(),
)

@Immutable
data class ConversationSummaryUi(
    val id: String,
    val title: String,
    val preview: String,
    val timeLabel: String,
    val updatedAtMillis: Long = 0L,
    val mode: ConversationModeUi,
    val isPinned: Boolean = false,
    val isActiveRun: Boolean = false,
    val runStatus: String? = null,
    val characterName: String? = null,
    val characterAvatarPath: String? = null,
    /** 所属工作区 id；为空表示未归入任何工作区。 */
    val workspaceId: String? = null,
)

@Immutable
enum class ConversationModeUi {
    Chat,
    PhoneAgent,
    Terminal,
    Automation,
}
