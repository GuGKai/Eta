package io.github.mangi.eta.ui.components

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.ConversationWorkspaceUi
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * 会话侧边栏尺寸标尺。
 *
 * 外层水平边距（12.dp）+ 行内水平边距（16.dp）= 28.dp，与 Miuix 原生标题区默认内边距
 * 及 [InputField] 内部搜索图标起点对齐，保证搜索框、分组标题与对话标题处于同一条垂直基准线。
 */
private object ConversationPanelMetrics {
    val PaneHorizontalPadding = 12.dp
    val TopInset = 8.dp
    val AfterActionBar = 8.dp
    val BottomInset = 4.dp
    val ActionIconSize = 20.dp

    /** 第一个分组离搜索框的距离比后续分组小一档，避免顶部留白过重。 */
    val FirstSectionTopPadding = 10.dp
    val SectionTopPadding = 16.dp
    val SectionBottomPadding = 6.dp
    val SectionIconSize = 14.dp
    val SectionIconGap = 8.dp
    val SectionCountGap = 10.dp
    val SectionChevronSize = 16.dp
    val SectionChevronGap = 2.dp

    val RowMinHeight = 44.dp
    val RowGap = 2.dp
    val RowCornerRadius = 14.dp
    val RowHorizontalPadding = 16.dp
    val RowVerticalPadding = 11.dp
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
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color(0xFF18191C) else Color(0xFFF3F4F6)
    }
    val colors = MiuixTheme.colorScheme
    return if (colors.background.luminance() > 0.5f) colors.surface else colors.surfaceContainer
}

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState,
    width: Dp,
    listState: LazyListState,
    onSearchChange: (String) -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationTogglePin: (ConversationSummaryUi) -> Unit,
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
    // 已归入工作区的会话在下方分组里不再出现第二次。
    val looseConversations = remember(state.conversations) {
        state.conversations.filter { it.workspaceId == null }
    }
    val groups = remember(looseConversations) { looseConversations.groupForDrawer() }

    // 折叠状态按分组 key 记住：既跨「离开侧栏再回来」，也跨应用重启；落盘到 SharedPreferences。
    val appContext = LocalContext.current.applicationContext
    val collapsePrefs = remember(appContext) {
        appContext.getSharedPreferences(PaneCollapsePrefsName, Context.MODE_PRIVATE)
    }
    val collapsedSections = remember(collapsePrefs) {
        mutableStateMapOf<String, Boolean>().apply {
            collapsePrefs.all.forEach { (key, value) -> if (value is Boolean) put(key, value) }
        }
    }

    // 新建工作区时的默认名，例如「Workspace 3」；在 Composable 作用域解析，供后续 lambda 复用。
    val defaultWorkspaceNameFormat = stringResource(R.string.workspace_default_name)
    val createWorkspaceLabel = stringResource(R.string.action_create_workspace)

    var workspaceNameTarget by remember { mutableStateOf<WorkspaceNameTarget?>(null) }
    var workspaceDeleteTarget by remember { mutableStateOf<ConversationWorkspaceUi?>(null) }
    var workspaceMoveTarget by remember { mutableStateOf<ConversationSummaryUi?>(null) }

    fun toggleSection(key: String) {
        val next = collapsedSections[key] != true
        collapsedSections[key] = next
        collapsePrefs.edit().putBoolean(key, next).apply()
    }

    Surface(
        modifier = modifier
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
                val workspacesCollapsed = collapsedSections[WorkspacesSectionKey] == true
                val showWorkspacesSection = state.workspaces.isNotEmpty() || query.isBlank()
                if (showWorkspacesSection) {
                    item(key = "section-workspaces") {
                        PaneSectionHeader(
                            icon = Icons.Rounded.FolderOpen,
                            label = stringResource(R.string.workspace_section_title),
                            count = state.workspaces.size,
                            collapsed = workspacesCollapsed,
                            topPadding = ConversationPanelMetrics.FirstSectionTopPadding,
                            onToggle = { toggleSection(WorkspacesSectionKey) },
                            trailing = {
                                PaneHeaderAction(
                                    icon = Icons.Rounded.Add,
                                    contentDescription = createWorkspaceLabel,
                                    onClick = {
                                        workspaceNameTarget = WorkspaceNameTarget.Create(
                                            defaultName = defaultWorkspaceNameFormat.format(
                                                state.workspaces.size + 1,
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    }
                    if (!workspacesCollapsed) {
                        if (state.workspaces.isEmpty()) {
                            item(key = "section-workspaces-empty") {
                                Text(
                                    text = stringResource(R.string.workspace_empty_hint),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    style = MiuixTheme.textStyles.footnote1,
                                    modifier = Modifier.padding(
                                        start = ConversationPanelMetrics.RowHorizontalPadding,
                                        end = ConversationPanelMetrics.RowHorizontalPadding,
                                        bottom = ConversationPanelMetrics.SectionBottomPadding,
                                    ),
                                )
                            }
                        }
                        state.workspaces.forEach { workspace ->
                            item(key = "workspace-${workspace.id}") {
                                WorkspaceRow(
                                    workspace = workspace,
                                    onToggleCollapsed = {
                                        onWorkspaceCollapsedChange(workspace.id, !workspace.collapsed)
                                    },
                                    onRename = {
                                        workspaceNameTarget = WorkspaceNameTarget.Rename(
                                            id = workspace.id,
                                            currentName = workspace.name,
                                        )
                                    },
                                    onDelete = { workspaceDeleteTarget = workspace },
                                )
                            }
                            if (!workspace.collapsed) {
                                items(
                                    items = workspace.conversations,
                                    key = { "in-workspace-${it.id}" },
                                ) { conversation ->
                                    ConversationTextRow(
                                        conversation = conversation,
                                        selected = conversation.id == state.selectedConversationId,
                                        indented = true,
                                        onClick = { onConversationSelected(conversation.id) },
                                        onRename = { onConversationRename(conversation) },
                                        onExport = { onConversationExport(conversation) },
                                        onTogglePin = { onConversationTogglePin(conversation) },
                                        onMoveToWorkspace = { workspaceMoveTarget = conversation },
                                        onDelete = { onConversationDelete(conversation) },
                                    )
                                }
                            }
                        }
                    }
                }

                val hasAnyConversation = looseConversations.isNotEmpty() ||
                    state.workspaces.any { it.conversations.isNotEmpty() }
                if (!hasAnyConversation) {
                    item {
                        EmptyConversations(isSearching = query.isNotBlank())
                    }
                } else {
                    groups.forEach { group ->
                        val sectionKey = group.section.storageKey()
                        val collapsed = collapsedSections[sectionKey] == true
                        item(key = "section-$sectionKey") {
                            PaneSectionHeader(
                                icon = group.section.iconVector(),
                                label = group.localizedLabel(),
                                count = group.items.size,
                                collapsed = collapsed,
                                topPadding = if (showWorkspacesSection) {
                                    ConversationPanelMetrics.SectionTopPadding
                                } else {
                                    ConversationPanelMetrics.FirstSectionTopPadding
                                },
                                onToggle = { toggleSection(sectionKey) },
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
                                    indented = false,
                                    onClick = { onConversationSelected(conversation.id) },
                                    onRename = { onConversationRename(conversation) },
                                    onExport = { onConversationExport(conversation) },
                                    onTogglePin = { onConversationTogglePin(conversation) },
                                    onMoveToWorkspace = { workspaceMoveTarget = conversation },
                                    onDelete = { onConversationDelete(conversation) },
                                )
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

    // ── 工作区三个弹窗：新建/重命名、删除确认、移入工作区 ─────────────────────
    workspaceNameTarget?.let { target ->
        val title = when (target) {
            is WorkspaceNameTarget.Create -> stringResource(R.string.workspace_create_title)
            is WorkspaceNameTarget.Rename -> stringResource(R.string.action_rename_workspace)
        }
        val initialName = when (target) {
            is WorkspaceNameTarget.Create -> target.defaultName
            is WorkspaceNameTarget.Rename -> target.currentName
        }
        WorkspaceNameDialog(
            title = title,
            initialName = initialName,
            onDismiss = { workspaceNameTarget = null },
            onConfirm = { name ->
                when (target) {
                    is WorkspaceNameTarget.Create -> onCreateWorkspace(name)
                    is WorkspaceNameTarget.Rename -> onRenameWorkspace(target.id, name)
                }
                workspaceNameTarget = null
            },
        )
    }

    workspaceDeleteTarget?.let { workspace ->
        WorkspaceDeleteDialog(
            workspace = workspace,
            onDismiss = { workspaceDeleteTarget = null },
            onConfirm = {
                onDeleteWorkspace(workspace.id)
                workspaceDeleteTarget = null
            },
        )
    }

    workspaceMoveTarget?.let { conversation ->
        WorkspaceMoveDialog(
            conversation = conversation,
            workspaces = state.workspaces,
            onDismiss = { workspaceMoveTarget = null },
            onMove = { workspaceId ->
                onConversationMoveToWorkspace(conversation.id, workspaceId)
                workspaceMoveTarget = null
            },
        )
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
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiSearchBarBox(
                query = query,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.conversation_search_hint),
                backgroundColor = conversationPaneContainerColor(),
            )
        }
        AppearanceUiStyle.MIUIX -> {
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
                            color = MiuixTheme.colorScheme.surfaceContainerHighest,
                        )
                    },
                    content = {},
                )
            }
        }
    }
}

/** 分组标题右侧的图标按钮（目前只有工作区的「新建」）。 */
@Composable
private fun PaneHeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/**
 * 分组标题：折叠箭头 + 图标 + 名称 + 计数。
 *
 * 整块可点，点一下折起或展开这一组；计数始终显示，折叠时也能看出组里有多少条。
 */
@Composable
private fun PaneSectionHeader(
    icon: ImageVector,
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "pane-section-chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = topPadding,
                bottom = ConversationPanelMetrics.SectionBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = label, onClick = onToggle)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(ConversationPanelMetrics.SectionChevronSize)
                    .rotate(rotation),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(ConversationPanelMetrics.SectionChevronGap))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ConversationPanelMetrics.SectionIconSize),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionIconGap))
            Text(
                text = label,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionCountGap))
            Text(
                text = count.toString(),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
            )
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationTextRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    indented: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onTogglePin: () -> Unit,
    onMoveToWorkspace: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val uiStyle = LocalUiStyle.current
    val couiAnchor = rememberCouiMenuAnchor()

    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (indented) {
                Modifier.padding(start = ConversationPanelMetrics.WorkspaceIndent)
            } else {
                Modifier
            },
        )
        .heightIn(min = ConversationPanelMetrics.RowMinHeight)
        .clip(RoundedCornerShape(ConversationPanelMetrics.RowCornerRadius))
        .background(
            if (selected) {
                // 与侧栏背景保持一档亮度差；浅色下与 surfaceContainerHigh 相同。
                MiuixTheme.colorScheme.surfaceContainerHighest
            } else {
                Color.Transparent
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
        )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier.couiMenuAnchor(couiAnchor),
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
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

        when (uiStyle) {
            AppearanceUiStyle.COUI -> {
                val renameText = stringResource(R.string.action_rename)
                val exportText = stringResource(R.string.action_export)
                val pinText = stringResource(
                    if (conversation.isPinned) R.string.action_unpin_conversation else R.string.action_pin_conversation,
                )
                val moveText = stringResource(R.string.action_move_to_workspace)
                val deleteText = stringResource(R.string.action_delete)
                val items = remember(renameText, exportText, pinText, moveText, deleteText) {
                    listOf(
                        CouiMenuItem(value = 0, text = renameText, icon = Icons.Rounded.Edit),
                        CouiMenuItem(value = 1, text = exportText, icon = Icons.Rounded.Download),
                        CouiMenuItem(
                            value = 2,
                            text = pinText,
                            icon = Icons.Rounded.PushPin,
                        ),
                        CouiMenuItem(
                            value = 3,
                            text = moveText,
                            icon = Icons.Rounded.FolderOpen,
                        ),
                        CouiMenuItem(
                            value = 4,
                            text = deleteText,
                            icon = Icons.Rounded.Delete,
                            dividerBefore = true,
                        ),
                    )
                }
                Menu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                    items = items,
                    onItemClick = { item ->
                        showActionMenu = false
                        when (item.value) {
                            0 -> onRename()
                            1 -> onExport()
                            2 -> onTogglePin()
                            3 -> onMoveToWorkspace()
                            4 -> onDelete()
                        }
                    },
                )
            }

            AppearanceUiStyle.MIUIX -> WindowListPopup(
                show = showActionMenu,
                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                alignment = PopupPositionProvider.Align.BottomEnd,
                onDismissRequest = { showActionMenu = false },
            ) {
                val renameText = stringResource(R.string.action_rename)
                val exportText = stringResource(R.string.action_export)
                val pinText = stringResource(
                    if (conversation.isPinned) R.string.action_unpin_conversation else R.string.action_pin_conversation,
                )
                val moveText = stringResource(R.string.action_move_to_workspace)
                val deleteText = stringResource(R.string.action_delete)
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
                        optionSize = 5,
                        isSelected = false,
                        index = 0,
                        onSelectedIndexChange = {
                            showActionMenu = false
                            onRename()
                        },
                    )
                    DropdownImpl(
                        item = exportItem,
                        optionSize = 5,
                        isSelected = false,
                        index = 1,
                        onSelectedIndexChange = {
                            showActionMenu = false
                            onExport()
                        },
                    )
                    DropdownImpl(
                        item = pinItem,
                        optionSize = 5,
                        isSelected = false,
                        index = 2,
                        onSelectedIndexChange = {
                            showActionMenu = false
                            onTogglePin()
                        },
                    )
                    DropdownImpl(
                        item = moveItem,
                        optionSize = 5,
                        isSelected = false,
                        index = 3,
                        onSelectedIndexChange = {
                            showActionMenu = false
                            onMoveToWorkspace()
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
}

/**
 * 工作区一行：文件夹图标 + 名称 + 会话数 + 折叠箭头。
 *
 * 点击展开或折叠，长按重命名或删除；里面的会话由 [ConversationPanePanel] 紧随其后渲染。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceRow(
    workspace: ConversationWorkspaceUi,
    onToggleCollapsed: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val uiStyle = LocalUiStyle.current
    val couiAnchor = rememberCouiMenuAnchor()
    val rotation by animateFloatAsState(
        targetValue = if (workspace.collapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "workspace-chevron",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ConversationPanelMetrics.RowMinHeight)
                .clip(RoundedCornerShape(ConversationPanelMetrics.RowCornerRadius))
                .combinedClickable(
                    onClick = onToggleCollapsed,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        showActionMenu = true
                    },
                )
                .couiMenuAnchor(couiAnchor)
                .padding(
                    horizontal = ConversationPanelMetrics.RowHorizontalPadding,
                    vertical = ConversationPanelMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(ConversationPanelMetrics.SectionChevronSize)
                    .rotate(rotation),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(ConversationPanelMetrics.SectionChevronGap))
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(ConversationPanelMetrics.SectionIconSize),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionIconGap))
            Text(
                text = workspace.name,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 不撑满整行：会话数紧跟名字，和分组标题一致，而不是贴到最右。
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(ConversationPanelMetrics.SectionCountGap))
            Text(
                text = workspace.conversations.size.toString(),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
            )
        }

        when (uiStyle) {
            AppearanceUiStyle.COUI -> {
                val renameText = stringResource(R.string.action_rename_workspace)
                val deleteText = stringResource(R.string.action_delete_workspace)
                val items = remember(renameText, deleteText) {
                    listOf(
                        CouiMenuItem(value = 0, text = renameText, icon = Icons.Rounded.Edit),
                        CouiMenuItem(
                            value = 1,
                            text = deleteText,
                            icon = Icons.Rounded.Delete,
                            dividerBefore = true,
                        ),
                    )
                }
                Menu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                    items = items,
                    onItemClick = { item ->
                        showActionMenu = false
                        when (item.value) {
                            0 -> onRename()
                            1 -> onDelete()
                        }
                    },
                )
            }

            AppearanceUiStyle.MIUIX -> WindowListPopup(
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
                            onRename()
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
                            onDelete()
                        },
                    )
                }
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

// ── 工作区弹窗 ────────────────────────────────────────────────────────────

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
    val hint = stringResource(R.string.workspace_name_hint)
    val saveText = stringResource(R.string.action_save)
    val canSave = input.isNotBlank()
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> CouiInputDialog(
            visible = true,
            title = title,
            value = input,
            onValueChange = { input = it },
            placeholder = hint,
            confirmText = saveText,
            confirmEnabled = canSave,
            onConfirm = { onConfirm(input.trim()) },
            onDismissRequest = onDismiss,
        )

        AppearanceUiStyle.MIUIX -> WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
        ) {
            Column {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = hint,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixDialogActions(
                    confirmText = saveText,
                    confirmEnabled = canSave,
                    onCancel = onDismiss,
                    onConfirm = { onConfirm(input.trim()) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceDeleteDialog(
    workspace: ConversationWorkspaceUi,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = stringResource(R.string.workspace_delete_title)
    val message = stringResource(R.string.workspace_delete_message, workspace.name)
    val deleteText = stringResource(R.string.action_delete)
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> CouiConfirmDialog(
            visible = true,
            title = title,
            message = message,
            confirmText = deleteText,
            destructive = true,
            onConfirm = onConfirm,
            onDismissRequest = onDismiss,
        )

        AppearanceUiStyle.MIUIX -> WindowDialog(
            show = true,
            title = title,
            summary = message,
            onDismissRequest = onDismiss,
        ) {
            MiuixDialogActions(
                confirmText = deleteText,
                destructive = true,
                onCancel = onDismiss,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun WorkspaceMoveDialog(
    conversation: ConversationSummaryUi,
    workspaces: List<ConversationWorkspaceUi>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    val title = stringResource(R.string.workspace_move_title)
    val emptyText = stringResource(R.string.workspace_move_empty)
    val removeText = stringResource(R.string.action_remove_from_workspace)
    val cancelText = stringResource(R.string.action_cancel)
    // 已经在某个工作区里时，不再重复提供「移入同一个工作区」。
    val targets = remember(workspaces, conversation.workspaceId) {
        workspaces.filter { it.id != conversation.workspaceId }
    }
    val canRemove = conversation.workspaceId != null

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (targets.isEmpty() && !canRemove) {
                    Text(
                        text = emptyText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
                if (canRemove) {
                    CouiPreference(
                        title = removeText,
                        onClick = { onMove(null) },
                        showChevron = false,
                    )
                    CouiIconDivider()
                }
                targets.forEachIndexed { index, workspace ->
                    CouiPreference(
                        title = workspace.name,
                        onClick = { onMove(workspace.id) },
                        showChevron = false,
                    )
                    if (index != targets.lastIndex) {
                        CouiIconDivider()
                    }
                }
                TextButton(
                    text = cancelText,
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        AppearanceUiStyle.MIUIX -> WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (targets.isEmpty() && !canRemove) {
                    Text(
                        text = emptyText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (canRemove) {
                    TextButton(
                        text = removeText,
                        onClick = { onMove(null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                targets.forEach { workspace ->
                    TextButton(
                        text = workspace.name,
                        onClick = { onMove(workspace.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    text = cancelText,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── 分组数据 ──────────────────────────────────────────────────────────────

private const val WorkspacesSectionKey = "workspaces"

/** 侧栏分组折叠状态落盘的 SharedPreferences 文件名（键就是分组 key）。 */
private const val PaneCollapsePrefsName = "conversation_pane_collapsed"

private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
)

private sealed interface ConversationDrawerSection {
    data object Pinned : ConversationDrawerSection
    data object Today : ConversationDrawerSection
    data class Dated(val label: String) : ConversationDrawerSection
}

private fun ConversationDrawerSection.storageKey(): String = when (this) {
    ConversationDrawerSection.Pinned -> "pinned"
    ConversationDrawerSection.Today -> "today"
    is ConversationDrawerSection.Dated -> "dated:$label"
}

private fun ConversationDrawerSection.iconVector(): ImageVector = when (this) {
    ConversationDrawerSection.Pinned -> Icons.Rounded.PushPin
    else -> Icons.Rounded.Schedule
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
        now.get(java.util.Calendar.DAY_OF_MONTH) == target.get(java.util.Calendar.DAY_OF_MONTH)
}
