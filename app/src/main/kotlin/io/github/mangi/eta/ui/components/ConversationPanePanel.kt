package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ImportContacts
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.ConversationWorkspaceUi
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowListPopup

private object ConversationPanelMetrics {
    val PaneHorizontalPadding = 16.dp
    val TopInset = 6.dp
    val AfterActionBar = 8.dp
    val BottomInset = 2.dp
    val ActionIconSize = 20.dp
    val SectionTopPadding = 8.dp
    val SectionBottomPadding = 10.dp
    val SectionIconSize = 14.dp
    val SectionIconGap = 8.dp
    val SectionCountGap = 12.dp
    val RowMinHeight = 48.dp
    val RowGap = 4.dp
    val RowCornerRadius = 12.dp
    val RowHorizontalPadding = 12.dp
    val RowVerticalPadding = 12.dp
    val ActiveDotSize = 6.dp
    val ActiveDotGap = 10.dp
    val EmptyVerticalPadding = 28.dp
    val DockTopGap = 2.dp
    val DockEntryCornerRadius = 12.dp
    val DockEntryIconSize = 20.dp
    /** 工作区里的会话比外层列表缩进一档，用来体现「装在文件夹里」。 */
    val WorkspaceIndent = 16.dp
}

/**
 * 侧栏与外层推移容器共用的背景色。
 *
 * 深色下聊天区保持 surface（纯黑/近黑），侧栏沿用同一颜色会在展开后与聊天区融为一体；
 * 抬高一档到 surfaceContainer，保证两侧始终可分。浅色维持 surface，避免改变现有观感。
 */
@Composable
internal fun conversationPaneContainerColor(): Color {
    val colors = MiuixTheme.colorScheme
    return if (colors.background.luminance() > 0.5f) colors.surface else colors.surfaceContainer
}

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState,
    width: androidx.compose.ui.unit.Dp,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSearchChange: (String) -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationPinnedChange: (ConversationSummaryUi, Boolean) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onCreateWorkspace: (String) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onWorkspaceCollapsedChange: (String, Boolean) -> Unit,
    onConversationMoveToWorkspace: (String, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // state.conversations 已由 AgentAppState 按标题、预览与消息内容过滤。
    val query = state.searchQuery.trim()
    val groups = remember(state.conversations) { state.conversations.groupForDrawer() }
    // 置顶分组可折叠：点击置顶分组的标题行收起/展开，只影响置顶区，日期分组照旧。
    // 搜索期间强制展开，避免命中项被折叠藏起来。
    var pinnedCollapsed by rememberSaveable { mutableStateOf(false) }
    val pinnedSectionCollapsed = pinnedCollapsed && query.isEmpty()
    // 工作区的命名、删除与「移动到工作区」共用一个宿主状态。
    var workspaceNameTarget by remember { mutableStateOf<WorkspaceNameTarget?>(null) }
    var workspaceDeleteTarget by remember { mutableStateOf<ConversationWorkspaceUi?>(null) }
    var workspaceMoveTarget by remember { mutableStateOf<ConversationSummaryUi?>(null) }
    val hasWorkspaceConversations = state.workspaces.any { it.conversations.isNotEmpty() }
    // stringResource 只能在组合期求值，先把默认名算好再交给点击回调。
    val defaultWorkspaceName = stringResource(R.string.workspace_default_name, state.workspaces.size + 1)

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .width(width)
                .fillMaxHeight(),
            color = conversationPaneContainerColor(),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ) {
            // 搜索与工具条占据独立布局空间，列表只在中间视口内滚动和回弹。
            Column(modifier = Modifier.fillMaxSize()) {
                PaneFixedRegion {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            )
                            .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
                    ) {
                        Spacer(Modifier.height(ConversationPanelMetrics.TopInset))
                        PaneActionBar(query = state.searchQuery, onSearchChange = onSearchChange)
                        Spacer(Modifier.height(ConversationPanelMetrics.AfterActionBar))
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding)
                        .scrollEndHaptic()
                        .overScrollVertical(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(ConversationPanelMetrics.RowGap),
                    overscrollEffect = null,
                ) {
                    if (query.isBlank()) {
                        item(key = "workspaces") {
                            WorkspacesSection(
                                workspaces = state.workspaces,
                                selectedConversationId = state.selectedConversationId,
                                onCreateWorkspace = {
                                    workspaceNameTarget = WorkspaceNameTarget.Create(
                                        defaultName = defaultWorkspaceName,
                                    )
                                },
                                onToggle = { workspace ->
                                    onWorkspaceCollapsedChange(workspace.id, !workspace.collapsed)
                                },
                                onRenameRequest = { workspace ->
                                    workspaceNameTarget = WorkspaceNameTarget.Rename(
                                        id = workspace.id,
                                        currentName = workspace.name,
                                    )
                                },
                                onDeleteRequest = { workspace -> workspaceDeleteTarget = workspace },
                                onConversationSelected = onConversationSelected,
                                onConversationRename = onConversationRename,
                                onConversationPinnedChange = onConversationPinnedChange,
                                onConversationExport = onConversationExport,
                                onConversationDelete = onConversationDelete,
                                onConversationMove = { conversation -> workspaceMoveTarget = conversation },
                            )
                        }
                    }
                    if (state.conversations.isEmpty() && !hasWorkspaceConversations) {
                        item {
                            EmptyConversations(isSearching = query.isNotBlank())
                        }
                    } else {
                        groups.forEachIndexed { index, group ->
                            val collapsible = group.section == ConversationDrawerSection.Pinned
                            val collapsed = collapsible && pinnedSectionCollapsed
                            item(key = "section-${group.section}") {
                                ConversationSectionHeader(
                                    group = group,
                                    collapsible = collapsible,
                                    collapsed = collapsed,
                                    onToggleCollapse = { pinnedCollapsed = !pinnedCollapsed },
                                )
                            }
                            if (!collapsed) {
                                items(
                                    items = group.items,
                                    key = { it.id },
                                ) { conversation ->
                                    ConversationTextRow(
                                        conversation = conversation,
                                        selected = conversation.id == state.selectedConversationId,
                                        onClick = { onConversationSelected(conversation.id) },
                                        onRename = { onConversationRename(conversation) },
                                        onTogglePinned = {
                                            onConversationPinnedChange(conversation, !conversation.isPinned)
                                        },
                                        onMoveToWorkspace = { workspaceMoveTarget = conversation },
                                        onExport = { onConversationExport(conversation) },
                                        onDelete = { onConversationDelete(conversation) },
                                    )
                                }
                            }
                            // 置顶区与日期区之间补一条分割线，分组标题样式保持原有日期分隔方式。
                            // 置顶区收起时不再补线，避免标题下直接跟一条悬空的分割线。
                            if (collapsible && index < groups.lastIndex && !collapsed) {
                                item(key = "section-divider-pinned") {
                                    ConversationSectionDivider()
                                }
                            }
                        }
                    }
                }
                PaneFixedRegion {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                            )
                            .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
                    ) {
                        Spacer(Modifier.height(ConversationPanelMetrics.DockTopGap))
                        PaneDock(
                            onOpenSettings = onOpenSettings,
                            onOpenModelProviders = onOpenModelProviders,
                            onOpenTools = onOpenTools,
                            onOpenSkills = onOpenSkills,
                            onOpenCharacters = onOpenCharacters,
                            onOpenPermissions = onOpenPermissions,
                        )
                        Spacer(Modifier.height(ConversationPanelMetrics.BottomInset))
                    }
                }
            }
        }

        when (val target = workspaceNameTarget) {
            null -> Unit
            is WorkspaceNameTarget.Create -> WorkspaceNameDialog(
                title = stringResource(R.string.workspace_create_title),
                initialName = target.defaultName,
                onDismiss = { workspaceNameTarget = null },
                onConfirm = { name ->
                    onCreateWorkspace(name)
                    workspaceNameTarget = null
                },
            )

            is WorkspaceNameTarget.Rename -> WorkspaceNameDialog(
                title = stringResource(R.string.action_rename_workspace),
                initialName = target.currentName,
                onDismiss = { workspaceNameTarget = null },
                onConfirm = { name ->
                    onRenameWorkspace(target.id, name)
                    workspaceNameTarget = null
                },
            )
        }

        workspaceDeleteTarget?.let { workspace ->
            EtaWindowDialog(
                show = true,
                title = stringResource(R.string.workspace_delete_title),
                summary = stringResource(R.string.workspace_delete_message, workspace.name),
                onDismissRequest = { workspaceDeleteTarget = null },
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.action_delete),
                    destructive = true,
                    onCancel = { workspaceDeleteTarget = null },
                    onConfirm = {
                        onDeleteWorkspace(workspace.id)
                        workspaceDeleteTarget = null
                    },
                )
            }
        }

        workspaceMoveTarget?.let { conversation ->
            EtaWindowDialog(
                show = true,
                title = stringResource(R.string.workspace_move_title),
                summary = conversation.title.ifBlank { conversation.preview }.take(48),
                onDismissRequest = { workspaceMoveTarget = null },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.workspaces.isEmpty()) {
                        Text(
                            text = stringResource(R.string.workspace_move_empty),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (conversation.workspaceId != null) {
                        EtaTextButton(
                            text = stringResource(R.string.action_remove_from_workspace),
                            onClick = {
                                onConversationMoveToWorkspace(conversation.id, null)
                                workspaceMoveTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.workspaces
                        .filterNot { it.id == conversation.workspaceId }
                        .forEach { workspace ->
                            EtaTextButton(
                                text = workspace.name,
                                onClick = {
                                    onConversationMoveToWorkspace(conversation.id, workspace.id)
                                    workspaceMoveTarget = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    EtaTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { workspaceMoveTarget = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 命名弹窗的两种入口：新建工作区、重命名工作区。 */
private sealed interface WorkspaceNameTarget {
    data class Create(val defaultName: String) : WorkspaceNameTarget

    data class Rename(val id: String, val currentName: String) : WorkspaceNameTarget
}

@Composable
private fun WorkspaceNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember(initialName) { mutableStateOf(initialName) }
    EtaWindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.workspace_name_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MiuixDialogActions(
                confirmText = stringResource(R.string.action_save),
                confirmEnabled = input.isNotBlank(),
                onCancel = onDismiss,
                onConfirm = { onConfirm(input.trim()) },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * 置顶分组上方的「工作区」栏：标题行右侧的加号新建工作区，下面的每一行就是一个文件夹，
 * 点击标题行展开或折叠，长按弹菜单可重命名或删除。
 */
@Composable
private fun WorkspacesSection(
    workspaces: List<ConversationWorkspaceUi>,
    selectedConversationId: String?,
    onCreateWorkspace: () -> Unit,
    onToggle: (ConversationWorkspaceUi) -> Unit,
    onRenameRequest: (ConversationWorkspaceUi) -> Unit,
    onDeleteRequest: (ConversationWorkspaceUi) -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationPinnedChange: (ConversationSummaryUi, Boolean) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onConversationMove: (ConversationSummaryUi) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ConversationPanelMetrics.RowGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = ConversationPanelMetrics.SectionTopPadding,
                    bottom = ConversationPanelMetrics.SectionBottomPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(ConversationPanelMetrics.SectionIconSize),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionIconGap))
            Text(
                text = stringResource(R.string.workspace_section_title),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionCountGap))
            Text(
                text = workspaces.size.toString(),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onCreateWorkspace,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_create_workspace),
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
        if (workspaces.isEmpty()) {
            Text(
                text = stringResource(R.string.workspace_empty_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(
                    horizontal = ConversationPanelMetrics.RowHorizontalPadding,
                    vertical = 6.dp,
                ),
            )
        }
        workspaces.forEach { workspace ->
            WorkspaceRow(workspace = workspace, onToggle = { onToggle(workspace) }, onRenameRequest = onRenameRequest, onDeleteRequest = onDeleteRequest)
            if (!workspace.collapsed) {
                workspace.conversations.forEach { conversation ->
                    ConversationTextRow(
                        conversation = conversation,
                        selected = conversation.id == selectedConversationId,
                        onClick = { onConversationSelected(conversation.id) },
                        onRename = { onConversationRename(conversation) },
                        onTogglePinned = { onConversationPinnedChange(conversation, !conversation.isPinned) },
                        onMoveToWorkspace = { onConversationMove(conversation) },
                        onExport = { onConversationExport(conversation) },
                        onDelete = { onConversationDelete(conversation) },
                        modifier = Modifier.padding(start = ConversationPanelMetrics.WorkspaceIndent),
                    )
                }
            }
        }
        Spacer(Modifier.height(ConversationPanelMetrics.SectionBottomPadding))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceRow(
    workspace: ConversationWorkspaceUi,
    onToggle: () -> Unit,
    onRenameRequest: (ConversationWorkspaceUi) -> Unit,
    onDeleteRequest: (ConversationWorkspaceUi) -> Unit,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    // 折叠时箭头朝下、展开时朝上；旋转而不是换图标，和置顶分组的折叠观感一致。
    val chevronRotation by animateFloatAsState(
        targetValue = if (workspace.collapsed) 0f else 180f,
        animationSpec = tween(durationMillis = 180),
        label = "workspaceChevron",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ConversationPanelMetrics.RowMinHeight)
                .clip(RoundedCornerShape(ConversationPanelMetrics.RowCornerRadius))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showActionMenu = true
                    },
                )
                .padding(
                    horizontal = ConversationPanelMetrics.RowHorizontalPadding,
                    vertical = ConversationPanelMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = workspace.name,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = workspace.conversations.size.toString(),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(ConversationPanelMetrics.SectionIconGap))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (workspace.collapsed) {
                        R.string.workspace_expand
                    } else {
                        R.string.workspace_collapse
                    },
                ),
                modifier = Modifier
                    .size(ConversationPanelMetrics.SectionIconSize)
                    .rotate(chevronRotation),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }

        WindowListPopup(
            show = showActionMenu,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.BottomEnd,
            onDismissRequest = { showActionMenu = false },
        ) {
            val renameText = stringResource(R.string.action_rename_workspace)
            val deleteText = stringResource(R.string.action_delete_workspace)
            val renameItem = remember(renameText) {
                DropdownItem(
                    text = renameText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val deleteItem = remember(deleteText) {
                DropdownItem(
                    text = deleteText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    },
                )
            }
            val deleteColors = DropdownDefaults.dropdownColors(
                contentColor = MiuixTheme.colorScheme.error,
                selectedContentColor = MiuixTheme.colorScheme.error,
                selectedIndicatorColor = MiuixTheme.colorScheme.error,
            )
            ListPopupColumn {
                DropdownImpl(
                    item = renameItem,
                    optionSize = 2,
                    isSelected = false,
                    index = 0,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onRenameRequest(workspace)
                    },
                )
                DropdownImpl(
                    item = deleteItem,
                    optionSize = 2,
                    isSelected = false,
                    index = 1,
                    dropdownColors = deleteColors,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onDeleteRequest(workspace)
                    },
                )
            }
        }
    }
}

@Composable
private fun PaneFixedRegion(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(conversationPaneContainerColor()),
    ) {
        content()
    }
}

@Composable
private fun PaneActionBar(
    query: String,
    onSearchChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBar(
            modifier = Modifier.weight(1f),
            insideMargin = DpSize.Zero,
            expanded = false,
            onExpandedChange = {},
            inputField = {
                InputField(
                    query = query,
                    onQueryChange = onSearchChange,
                    onSearch = onSearchChange,
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.conversation_search_hint),
                    // 深色下侧栏已抬高到 surfaceContainer，搜索框需再高一档才能显出轮廓；
                    // 浅色的 surfaceContainerHigh 与 surfaceContainerHighest 相同，观感不变。
                    color = MiuixTheme.colorScheme.surfaceContainerHighest,
                )
            },
            content = {},
        )
    }
}

@Composable
private fun ConversationSectionHeader(
    group: ConversationDrawerGroup,
    collapsible: Boolean = false,
    collapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
) {
    // 折叠时箭头朝下、展开时朝上；旋转而不是换图标，切换更连贯。
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 180f,
        animationSpec = tween(durationMillis = 180),
        label = "conversationSectionChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ConversationPanelMetrics.RowCornerRadius))
            .then(if (collapsible) Modifier.clickable(onClick = onToggleCollapse) else Modifier)
            .padding(
                top = ConversationPanelMetrics.SectionTopPadding,
                bottom = ConversationPanelMetrics.SectionBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (group.section == ConversationDrawerSection.Pinned) {
                Icons.Rounded.PushPin
            } else {
                Icons.Rounded.Schedule
            },
            contentDescription = null,
            modifier = Modifier.size(ConversationPanelMetrics.SectionIconSize),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionIconGap))
        Text(
            text = group.localizedLabel(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionCountGap))
        Text(
            text = group.items.size.toString(),
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
        )
        if (collapsible) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (collapsed) {
                        R.string.conversation_section_expand
                    } else {
                        R.string.conversation_section_collapse
                    },
                ),
                modifier = Modifier
                    .size(ConversationPanelMetrics.SectionIconSize)
                    .rotate(chevronRotation),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun ConversationSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        thickness = 0.33.dp,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationTextRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePinned: () -> Unit,
    onMoveToWorkspace: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ConversationPanelMetrics.RowMinHeight)
                .clip(RoundedCornerShape(ConversationPanelMetrics.RowCornerRadius))
                .background(
                    when {
                        // 与侧栏背景保持一档亮度差；浅色下与 surfaceContainerHigh 相同。
                        selected -> MiuixTheme.colorScheme.surfaceContainerHighest
                        // 置顶行比侧栏底色深一档，未选中时也能与普通会话区分。
                        conversation.isPinned -> MiuixTheme.colorScheme.surfaceContainerHigh
                        else -> Color.Transparent
                    },
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showActionMenu = true
                    },
                )
                .padding(
                    horizontal = ConversationPanelMetrics.RowHorizontalPadding,
                    vertical = ConversationPanelMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            conversation.characterName?.let { name ->
                CharacterAvatar(
                    name = name,
                    avatarPath = conversation.characterAvatarPath,
                    size = 36.dp,
                    shape = CircleShape,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
            val title = conversation.title.ifBlank { conversation.preview }
            Text(
                text = title,
                color = if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 标题与角色名相同（如未改名的角色会话）时不再重复第二行。
            conversation.characterName?.takeIf { it != title }?.let { name ->
                Text(name, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            conversation.runStatus?.let { status ->
                Text(
                    text = status,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            if (conversation.isActiveRun) {
                Box(
                    modifier = Modifier
                        .padding(start = ConversationPanelMetrics.ActiveDotGap)
                        .size(ConversationPanelMetrics.ActiveDotSize)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }

        WindowListPopup(
            show = showActionMenu,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.BottomEnd,
            onDismissRequest = { showActionMenu = false },
        ) {
            val pinText = stringResource(
                if (conversation.isPinned) R.string.action_unpin else R.string.action_pin,
            )
            val moveText = stringResource(R.string.action_move_to_workspace)
            val renameText = stringResource(R.string.action_rename)
            val exportText = stringResource(R.string.action_export)
            val deleteText = stringResource(R.string.action_delete)
            val pinItem = remember(pinText) {
                DropdownItem(
                    text = pinText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val moveItem = remember(moveText) {
                DropdownItem(
                    text = moveText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val renameItem = remember(renameText) {
                DropdownItem(
                    text = renameText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val exportItem = remember(exportText) {
                DropdownItem(
                    text = exportText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                        )
                    },
                )
            }
            val deleteItem = remember(deleteText) {
                DropdownItem(
                    text = deleteText,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = modifier.size(ConversationPanelMetrics.ActionIconSize),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    },
                )
            }
            val deleteColors = DropdownDefaults.dropdownColors(
                contentColor = MiuixTheme.colorScheme.error,
                selectedContentColor = MiuixTheme.colorScheme.error,
                selectedIndicatorColor = MiuixTheme.colorScheme.error,
            )
            ListPopupColumn {
                DropdownImpl(
                    item = pinItem,
                    optionSize = 5,
                    isSelected = false,
                    index = 0,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onTogglePinned()
                    },
                )
                DropdownImpl(
                    item = moveItem,
                    optionSize = 5,
                    isSelected = false,
                    index = 1,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onMoveToWorkspace()
                    },
                )
                DropdownImpl(
                    item = renameItem,
                    optionSize = 5,
                    isSelected = false,
                    index = 2,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onRename()
                    },
                )
                DropdownImpl(
                    item = exportItem,
                    optionSize = 5,
                    isSelected = false,
                    index = 3,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onExport()
                    },
                )
                DropdownImpl(
                    item = deleteItem,
                    optionSize = 5,
                    isSelected = false,
                    index = 4,
                    dropdownColors = deleteColors,
                    onSelectedIndexChange = {
                        showActionMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyConversations(isSearching: Boolean) {
    Text(
        text = stringResource(
            if (isSearching) R.string.conversation_no_results else R.string.conversation_empty,
        ),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(
            horizontal = ConversationPanelMetrics.RowHorizontalPadding,
            vertical = ConversationPanelMetrics.EmptyVerticalPadding,
        ),
    )
}

@Composable
private fun PaneDock(
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DockEntry(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.sidebar_settings),
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Rounded.Cloud,
            label = stringResource(R.string.sidebar_models),
            onClick = onOpenModelProviders,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Rounded.Dashboard,
            label = stringResource(R.string.ui_tool_ability_9f0f80),
            onClick = onOpenTools,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Rounded.ImportContacts,
            label = stringResource(R.string.sidebar_skills),
            onClick = onOpenSkills,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.sidebar_permissions),
            onClick = onOpenPermissions,
            modifier = Modifier.weight(1f),
        )
        DockEntry(
            icon = Icons.Rounded.SportsBar,
            label = stringResource(R.string.sidebar_characters),
            onClick = onOpenCharacters,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DockEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ConversationPanelMetrics.DockEntryCornerRadius))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(ConversationPanelMetrics.DockEntryIconSize),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
)

private sealed interface ConversationDrawerSection {
    data object Pinned : ConversationDrawerSection
    data object Today : ConversationDrawerSection
    data class Dated(val label: String) : ConversationDrawerSection
}

@Composable
private fun ConversationDrawerGroup.localizedLabel(): String = when (val value = section) {
    ConversationDrawerSection.Pinned -> stringResource(R.string.conversation_section_pinned)
    ConversationDrawerSection.Today -> stringResource(R.string.conversation_section_today)
    is ConversationDrawerSection.Dated -> value.label
}

private fun List<ConversationSummaryUi>.groupForDrawer(): List<ConversationDrawerGroup> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<ConversationDrawerGroup>()
    for (conversation in this) {
        val section = conversation.drawerSection()
        val last = groups.lastOrNull()
        if (last?.section == section) {
            groups[groups.lastIndex] = last.copy(items = last.items + conversation)
        } else {
            groups += ConversationDrawerGroup(section = section, items = listOf(conversation))
        }
    }
    return groups
}

private fun ConversationSummaryUi.drawerSection(): ConversationDrawerSection = when {
    isPinned -> ConversationDrawerSection.Pinned
    isActiveRun || isUpdatedToday(updatedAtMillis) -> ConversationDrawerSection.Today
    else -> ConversationDrawerSection.Dated(timeLabel)
}

private fun isUpdatedToday(timestampMillis: Long): Boolean {
    if (timestampMillis <= 0L) return true
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    return now.get(java.util.Calendar.ERA) == target.get(java.util.Calendar.ERA) &&
        now.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)
}
