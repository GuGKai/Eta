package io.github.mangi.eta.ui.components
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.RenderEffect as AndroidRenderEffect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import androidx.annotation.RequiresApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntSize
import com.itos.coui.chip.CouiChipTabDefaults
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.CouiMenuAnchor
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.menu.rememberCouiWindowBackdrop
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.ConversationWorkspaceUi
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

@Stable
private class PaneBarInsets {
    var top by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)
}

/** 侧栏分组折叠状态落盘的 SharedPreferences 文件名（键就是分组 key）。 */
private const val PaneCollapsePrefsName = "conversation_pane_collapsed"

/** 工作区 chip 栏里「全部」的固定 key；真实工作区 key 用的是工作区 id。 */
private const val WorkspaceChipAllKey = "__workspace_all__"

/** 工作区整段标题的折叠 key，与其它分组 key 共用一套命名空间。 */

/** 顶部浮层骨架之外，工作区与折叠标题额外需要的标尺。 */
private object PaneExtraMetrics {
    val SectionChevronSize = 13.dp
    val SectionChevronGap = 5.dp
    val SectionCountGap = 8.dp
    val SectionIconSize = 14.dp
    val SectionIconGap = 8.dp

    /** 工作区里的会话比外层列表缩进一档，用来体现「装在文件夹里」。 */
    val WorkspaceIndent = 14.dp

    val HeaderActionSize = 28.dp
    val HeaderActionIconSize = 18.dp
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
    isRevealed: Boolean = true,
    hasPermissionAttention: Boolean = false,
    /** 点「新建会话」时把当前 chip 选中的工作区带出去，传 null 表示「全部」。 */
    onNewConversationWorkspace: (String?) -> Unit = {},
    onNewConversation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // state.conversations 已由 AgentAppState 按标题、预览与消息内容过滤。
    val query = state.searchQuery.trim()
    val isSearching = query.isNotBlank()
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    // 已归入工作区的会话在下方分组里不再出现第二次。
    val looseConversations = remember(state.conversations) {
        state.conversations.filter { it.workspaceId == null }
    }

    // COUI 用顶部 chip 栏切换工作区：同屏只看一个，「全部」包含所有会话；
    // Miuix 保持原来的树形展开，下方分组仍只列未归类会话。
    var selectedWorkspaceKey by rememberSaveable { mutableStateOf(WorkspaceChipAllKey) }
    // 切换分类时列表是整段替换，分组标题不该跟着上下滑；但同一分类内折叠/展开置顶
    // 还是要保留滑动，所以只在切换的这几帧里临时关掉标题的 placement 动画。
    var suppressSectionPlacement by remember { mutableStateOf(false) }
    val placementScope = rememberCoroutineScope()
    val selectWorkspace: (String) -> Unit = { key ->
        if (key != selectedWorkspaceKey) {
            suppressSectionPlacement = true
            selectedWorkspaceKey = key
            placementScope.launch {
                delay(420L)
                suppressSectionPlacement = false
            }
        }
    }
    LaunchedEffect(state.workspaces) {
        if (selectedWorkspaceKey != WorkspaceChipAllKey &&
            state.workspaces.none { it.id == selectedWorkspaceKey }
        ) {
            selectedWorkspaceKey = WorkspaceChipAllKey
        }
    }
    // 「全部」= 未归类会话 + 所有工作区里的会话，去重后按置顶/更新时间重排；
    // chip 后面的每个工作区始终只是它的子集。
    val allConversations = remember(state.conversations, state.workspaces) {
        if (state.workspaces.all { it.conversations.isEmpty() }) {
            state.conversations
        } else {
            val seen = HashSet<String>(state.conversations.size * 2)
            val merged = ArrayList<ConversationSummaryUi>(
                state.conversations.size + state.workspaces.sumOf { it.conversations.size },
            )
            state.conversations.forEach { conversation ->
                if (seen.add(conversation.id)) merged += conversation
            }
            state.workspaces.forEach { workspace ->
                workspace.conversations.forEach { conversation ->
                    if (seen.add(conversation.id)) merged += conversation
                }
            }
            merged.sortedWith(
                compareByDescending<ConversationSummaryUi> { it.isPinned }
                    .thenByDescending { conversation -> conversation.updatedAtMillis },
            )
        }
    }
    val scopedConversations = remember(
        state.conversations,
        allConversations,
        looseConversations,
        isCoui,
        isSearching,
        selectedWorkspaceKey,
        state.workspaces,
    ) {
        when {
            isSearching -> state.conversations
            !isCoui -> looseConversations
            selectedWorkspaceKey == WorkspaceChipAllKey -> allConversations
            else -> state.workspaces
                .firstOrNull { it.id == selectedWorkspaceKey }
                ?.conversations
                .orEmpty()
        }
    }
    val groups = remember(scopedConversations) { scopedConversations.groupForDrawer() }

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

    fun toggleSection(key: String) {
        val next = collapsedSections[key] != true
        collapsedSections[key] = next
        collapsePrefs.edit().putBoolean(key, next).apply()
    }

    // 新建工作区时的默认名，例如「工作区 3」；在 Composable 作用域解析，供后续 lambda 复用。
    val defaultWorkspaceNameFormat = stringResource(R.string.workspace_default_name)
    val createWorkspaceLabel = stringResource(R.string.action_create_workspace)

    var workspaceNameTarget by remember { mutableStateOf<WorkspaceNameTarget?>(null) }
    var workspaceDeleteTarget by remember { mutableStateOf<ConversationWorkspaceUi?>(null) }
    var workspaceMoveTarget by remember { mutableStateOf<ConversationSummaryUi?>(null) }

    val showWorkspacesSection = state.workspaces.isNotEmpty() || !isSearching
    // COUI 的工作区收进顶部 chip 栏；Miuix 仍是树形展开，所以两边的列表头逻辑分开判断。
    val hasWorkspaceChips = isCoui && showWorkspacesSection

    val containerColor = conversationPaneContainerColor()
    val couiBackdrop = if (isCoui) rememberCouiBackdrop() else null
    val miuixBackdrop = if (!isCoui) rememberTopBarBackdrop() else null
    val barInsets = remember { PaneBarInsets() }
    // 侧栏根节点坐标：用来把按下的局部坐标换算到窗口坐标，和 dock 登记的搜索框范围比对。
    var paneRootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // COUI 侧只保留 COUI 自己的 backdrop 录制：顶栏、按钮、chip 全部从它采样，
    // 不再叠加 Miuix 的 LayerBackdrop 渐进模糊（两套引擎一起跑会闪、观感也对不上会话页）。
    val bodyCaptureModifier = when {
        isCoui && couiBackdrop != null -> Modifier.couiBackdrop(couiBackdrop)
        !isCoui && miuixBackdrop != null -> Modifier.captureForTopBar(miuixBackdrop)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(containerColor)
            .onGloballyPositioned { paneRootCoordinates = it }
            .pointerInput(Unit) {
                // 只观察、不消费按下事件：列表滚动、条目点击、顶栏按钮全部照旧，
                // 只是额外判断这一下是否落在搜索胶囊外面，是就让 dock 收起搜索态。
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        val searchBounds = ConversationPaneSearchDock.barBoundsInWindow ?: continue
                        val coords = paneRootCoordinates ?: continue
                        val position = event.changes.firstOrNull()?.position ?: continue
                        if (!searchBounds.contains(coords.localToWindow(position))) {
                            ConversationPaneSearchDock.dismissRequest++
                        }
                    }
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 测量顶栏与底栏真实高度（含安全区与 IME），同步给滚动画布的 contentPadding，
            // 使列表自然穿梭于顶底浮层之下并分别被各自 backdrop 采样，同时避免 Subcompose 重组开销。
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        // 顺序必须和 COUI 的 Scaffold 一致：先 couiBackdrop、后 background。
                        // Modifier 链里靠前的在外层，background 写在 couiBackdrop 之后才会被录进
                        // backdrop；写成 background.then(couiBackdrop) 时录进去的只有子内容没有底色，
                        // 顶栏采样出来就是一层近乎透明的空膜（这就是刚才那块透明板的原因）。
                        modifier = Modifier
                            .fillMaxSize()
                            .then(bodyCaptureModifier)
                            .background(containerColor),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                                )
                                .scrollEndHaptic()
                                .overScrollVertical(),
                            contentPadding = PaddingValues(
                                top = barInsets.top + 10.dp,
                                bottom = barInsets.bottom + 10.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ConversationPanelMetrics.RowGap),
                            overscrollEffect = null,
                        ) {
                            // 顶部不再有任何选择框/工作台卡片，第一条就是工作区段或会话分组。
                            if (!isCoui && showWorkspacesSection) {
                                item(key = "section-workspaces") {
                                    Column {
                                        PaneSectionHeader(
                                            label = stringResource(R.string.workspace_section_title),
                                            topPadding = ConversationPanelMetrics.FirstSectionTopPadding,
                                            largeTitle = true,
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
                                }
                                if (state.workspaces.isEmpty()) {
                                    item(key = "section-workspaces-empty") {
                                        WorkspaceEmptyHint()
                                    }
                                }
                                state.workspaces.forEach { workspace ->
                                    item(key = "workspace-${workspace.id}") {
                                        WorkspaceRow(
                                            workspace = workspace,
                                            onToggleCollapsed = {
                                                onWorkspaceCollapsedChange(
                                                    workspace.id,
                                                    !workspace.collapsed,
                                                )
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
                                            ConversationHistoryRow(
                                                conversation = conversation,
                                                selected = conversation.id == state.selectedConversationId,
                                                onClick = { onConversationSelected(conversation.id) },
                                                onTogglePin = { onConversationTogglePin(conversation) },
                                                onRename = { onConversationRename(conversation) },
                                                onExport = { onConversationExport(conversation) },
                                                onMoveToWorkspace = { workspaceMoveTarget = conversation },
                                                onDelete = { onConversationDelete(conversation) },
                                                modifier = Modifier.animateItem(
                                                    fadeInSpec = tween(durationMillis = 170),
                                                    placementSpec = spring(
                                                        dampingRatio = 0.9f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                    fadeOutSpec = tween(durationMillis = 120),
                                                ),
                                                indented = true,
                                            )
                                        }
                                    }
                                }
                            }

                            // 分隔线从「工作区标题下」移到「工作区段与置顶/时间分组之间」。
                            if (!isCoui && showWorkspacesSection && state.workspaces.isNotEmpty()) {
                                item(key = "section-workspaces-divider") {
                                    PaneHeaderDivider()
                                }
                            }

                            val hasAnyConversation = looseConversations.isNotEmpty() ||
                                state.workspaces.any { it.conversations.isNotEmpty() }
                            if (!hasAnyConversation) {
                                item(key = "pane_empty_conversations") {
                                    EmptyConversations(isSearching = isSearching)
                                }
                            } else {
                                groups.forEachIndexed { groupIndex, group ->
                                    val sectionKey = group.section.storageKey()
                                    // 只有置顶可以折叠；今天/昨天等时间分组始终展开。
                                    val collapsible = group.section is ConversationDrawerSection.Pinned
                                    val collapsed = collapsible &&
                                        collapsedSections[sectionKey] == true
                                    item(key = "section-$sectionKey") {
                                        PaneSectionHeader(
                                            label = group.localizedLabel(),
                                            // 只有同一分类内的折叠/展开才带动画；切分类时标题直接落位。
                                            modifier = if (suppressSectionPlacement) {
                                                Modifier
                                            } else {
                                                Modifier.animateItem(
                                                    fadeInSpec = tween(durationMillis = 170),
                                                    placementSpec = spring(
                                                        dampingRatio = 0.9f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                    fadeOutSpec = tween(durationMillis = 120),
                                                )
                                            },
                                            topPadding = if (groupIndex == 0 && !hasWorkspaceChips) {
                                                ConversationPanelMetrics.FirstSectionTopPadding
                                            } else {
                                                ConversationPanelMetrics.SectionTopPadding
                                            },
                                            count = group.items.size,
                                            collapsed = if (collapsible) collapsed else null,
                                            // 置顶：能点能折叠，但不画箭头、不画点击色。
                                            chevronVisible = !collapsible,
                                            clickFeedback = !collapsible,
                                            onToggle = if (collapsible) {
                                                { toggleSection(sectionKey) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                    if (!collapsed) {
                                        items(
                                            items = group.items,
                                            key = { it.id },
                                        ) { conversation ->
                                            ConversationHistoryRow(
                                                conversation = conversation,
                                                selected = conversation.id == state.selectedConversationId,
                                                onClick = { onConversationSelected(conversation.id) },
                                                onTogglePin = { onConversationTogglePin(conversation) },
                                                onRename = { onConversationRename(conversation) },
                                                onExport = { onConversationExport(conversation) },
                                                onMoveToWorkspace = { workspaceMoveTarget = conversation },
                                                onDelete = { onConversationDelete(conversation) },
                                                modifier = Modifier.animateItem(
                                                    fadeInSpec = tween(durationMillis = 170),
                                                    placementSpec = spring(
                                                        dampingRatio = 0.9f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                    fadeOutSpec = tween(durationMillis = 120),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ConversationPaneTopBar(
                        containerColor = containerColor,
                        couiBackdrop = couiBackdrop,
                        miuixBackdrop = miuixBackdrop,
                        paneBlurBackdrop = null,
                        onNewConversation = {
                            // 当前选中的工作区跟着「新建会话」一起带下去，首条消息落库时直接归档。
                            onNewConversationWorkspace(
                                selectedWorkspaceKey.takeIf { it != WorkspaceChipAllKey },
                            )
                            onNewConversation()
                        },
                        secondaryBar = if (hasWorkspaceChips) {
                            { toolbarBackdrop, _ ->
                                ConversationPaneWorkspaceChips(
                                    workspaces = state.workspaces,
                                    selectedKey = selectedWorkspaceKey,
                                    onSelectedKeyChange = selectWorkspace,
                                    onCreateWorkspace = {
                                        workspaceNameTarget = WorkspaceNameTarget.Create(
                                            defaultName = defaultWorkspaceNameFormat.format(
                                                state.workspaces.size + 1,
                                            ),
                                        )
                                    },
                                    onRenameWorkspace = { workspace ->
                                        workspaceNameTarget = WorkspaceNameTarget.Rename(
                                            id = workspace.id,
                                            currentName = workspace.name,
                                        )
                                    },
                                    onDeleteWorkspace = { workspace -> workspaceDeleteTarget = workspace },
                                    couiBackdrop = toolbarBackdrop,
                                )
                            }
                        } else {
                            null
                        },
                    )
                    ConversationPaneBottomDock(
                        query = state.searchQuery,
                        isRevealed = isRevealed,
                        hasPermissionAttention = hasPermissionAttention,
                        containerColor = containerColor,
                        couiBackdrop = couiBackdrop,
                        miuixBackdrop = miuixBackdrop,
                        onSearchChange = onSearchChange,
                        onOpenPermissions = onOpenPermissions,
                        onOpenSettings = onOpenSettings,
                    )
                },
            ) { measurables, constraints ->
                val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
                val topBar = measurables[1].measure(looseConstraints)
                val bottomDock = measurables[2].measure(looseConstraints)
                val topDp = topBar.height.toDp()
                val bottomDp = bottomDock.height.toDp()
                if (barInsets.top != topDp) barInsets.top = topDp
                if (barInsets.bottom != bottomDp) barInsets.bottom = bottomDp
                val body = measurables[0].measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    body.placeRelative(0, 0)
                    topBar.placeRelative(0, 0)
                    bottomDock.placeRelative(0, constraints.maxHeight - bottomDock.height)
                }
            }

            // 侧栏物理右侧细微收边，保证浅/深/纯黑模式下与右侧聊天舞台始终保持利落边界。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(0.5.dp)
                    .background(conversationPaneEdgeBorderColor()),
            )
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

/** 工作区 chip 浮动材质外半影预留的纵向余量：亮色灰影比暗色明显，按最亮的一档留。 */
private val WorkspaceChipShadowRoom = 20.dp

/** chip 之间、以及 chip 与面板内边距之间的固定间隔。 */
private val WorkspaceChipGap = 8.dp

/** chip 行两端溢出时的渐隐宽度，超出面板边框的 chip 在这段距离内淡到透明。 */
private val WorkspaceChipEdgeFade = 30.dp

/**
 * 工作区 chip 行两端的渐隐遮罩：把整个图层与一张水平渐变图做 DST_IN。
 *
 * 只能用图层后处理做：chip 的玻璃、阴影、按压材质各画在自己的 RenderNode 里，父层在
 * canvas 上混 DST_IN 读不到（实测两版都没效果）；逐 chip 设 alpha 又只能整块变淡，
 * 做不出「同一个 chip 左实右虚」。滚到头的一侧不淡化，否则末尾 chip 会一直发灰。
 */
private fun buildChipEdgeFadeEffect(
    widthPx: Float,
    fadeStartPx: Float,
    fadeEndPx: Float,
): androidx.compose.ui.graphics.RenderEffect? {
    if (widthPx <= 0f) return null
    val colors = ArrayList<Int>(4)
    val positions = ArrayList<Float>(4)
    val startStop = (fadeStartPx / widthPx).coerceIn(0f, 1f)
    if (startStop > 0.001f) {
        colors += android.graphics.Color.TRANSPARENT
        positions += 0f
        colors += android.graphics.Color.BLACK
        positions += startStop
    } else {
        colors += android.graphics.Color.BLACK
        positions += 0f
    }
    val endStop = (1f - fadeEndPx / widthPx).coerceIn(0f, 1f)
        .coerceAtLeast(positions.last() + 0.001f)
        .coerceAtMost(1f)
    if (endStop < 1f) {
        colors += android.graphics.Color.BLACK
        positions += endStop
        colors += android.graphics.Color.TRANSPARENT
        positions += 1f
    } else {
        colors += android.graphics.Color.BLACK
        positions += 1f
    }
    val mask = AndroidRenderEffect.createShaderEffect(
        LinearGradient(
            0f,
            0f,
            widthPx,
            0f,
            colors.toIntArray(),
            positions.toFloatArray(),
            Shader.TileMode.CLAMP,
        ),
    )
    val content = AndroidRenderEffect.createOffsetEffect(0f, 0f)
    return AndroidRenderEffect
        .createBlendModeEffect(content, mask, AndroidBlendMode.DST_IN)
        .asComposeRenderEffect()
}

/**
 * COUI 顶部工作区 chip 栏，画在顶栏的同一块材质里，所以自己不再叠背景、也不另开背景采样。
 *
 * chip 用 COUI 原生 ChipTab（和分类栏同款浮动材质），点击切换工作区，长按弹管理：
 * 长按「全部」直接新建工作区，长按某个工作区只给重命名 / 删除。
 */
@Composable
private fun ConversationPaneWorkspaceChips(
    workspaces: List<ConversationWorkspaceUi>,
    selectedKey: String,
    onSelectedKeyChange: (String) -> Unit,
    onCreateWorkspace: () -> Unit,
    onRenameWorkspace: (ConversationWorkspaceUi) -> Unit,
    onDeleteWorkspace: (ConversationWorkspaceUi) -> Unit,
    couiBackdrop: CouiBackdrop?,
) {
    if (couiBackdrop == null) return
    val dark = CouiTheme.isDark
    val allLabel = stringResource(R.string.inbox_all)
    val chipsScroll = rememberScrollState()
    val density = LocalDensity.current
    val fadePx = with(density) { WorkspaceChipEdgeFade.toPx() }
    var viewportPx by remember { mutableStateOf(0) }
    val canScrollLeft by remember {
        derivedStateOf { chipsScroll.value > 0f }
    }
    val edgeFadeEffect = remember(viewportPx, canScrollLeft, fadePx) {
        buildChipEdgeFadeEffect(
            widthPx = viewportPx.toFloat(),
            fadeStartPx = if (canScrollLeft) fadePx else 0f,
            // 右边恒定淡到透明：面板右边界就是侧栏分界线，chip 贴上去会硬切。
            fadeEndPx = fadePx,
        )
    }
    // 长按菜单的目标工作区 + 锚点：菜单统一渲染在 chip 行外面（原因见下方注释）。
    var menuWorkspace by remember { mutableStateOf<ConversationWorkspaceUi?>(null) }
    var menuAnchor by remember { mutableStateOf<CouiMenuAnchor?>(null) }
    val renameLabel = stringResource(R.string.action_rename_workspace)
    val deleteLabel = stringResource(R.string.action_delete_workspace)
    val destructiveColor = CouiDialogDefaults.themeColors().destructive
    val menuItems = remember(renameLabel, deleteLabel, destructiveColor) {
        listOf(
            CouiMenuItem(value = 1, text = renameLabel, icon = Icons.Outlined.Edit),
            CouiMenuItem(
                value = 2,
                text = deleteLabel,
                icon = Icons.Outlined.Delete,
                iconTint = destructiveColor,
                dividerBefore = true,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 这一栏在顶栏材质里的占位仍是 WorkspaceChipsHeight。
            .height(ConversationPanelMetrics.WorkspaceChipsHeight),
        // 子项比 Box 高，居中摆放 → 上下各溢出一半，chip 的纵向中心严格等于 Box 中心。
        contentAlignment = Alignment.Center,
    ) {
    // 裁剪层自己占满面板宽度：超出右边框（侧栏与会话区的分界线）的部分在这里被裁掉，
    // 高度留足上下半影余量，所以纵向的外半影不会被裁。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(
                ConversationPanelMetrics.WorkspaceChipsHeight + WorkspaceChipShadowRoom * 2,
            )
            .clipToBounds()
            .onSizeChanged { viewportPx = it.width }
            .then(
                if (edgeFadeEffect != null) {
                    Modifier.graphicsLayer { renderEffect = edgeFadeEffect }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(
                    ConversationPanelMetrics.WorkspaceChipsHeight + WorkspaceChipShadowRoom * 2,
                )
                .horizontalScroll(chipsScroll)
                .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(WorkspaceChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        WorkspaceChipTab(
            label = allLabel,
            selected = selectedKey == WorkspaceChipAllKey,
            dark = dark,
            backdrop = couiBackdrop,
            modifier = Modifier,
            onClick = { onSelectedKeyChange(WorkspaceChipAllKey) },
            onLongPress = onCreateWorkspace,
            isListScrolling = { chipsScroll.isScrollInProgress },
        )
        workspaces.forEach { workspace ->
            key(workspace.id) {
                val chipAnchor = rememberCouiMenuAnchor()
                WorkspaceChipTab(
                    label = workspace.name,
                    selected = selectedKey == workspace.id,
                    dark = dark,
                    backdrop = couiBackdrop,
                    modifier = Modifier.couiMenuAnchor(chipAnchor),
                    onClick = { onSelectedKeyChange(workspace.id) },
                    onLongPress = {
                        menuAnchor = chipAnchor
                        menuWorkspace = workspace
                    },
                    isListScrolling = { chipsScroll.isScrollInProgress },
                )
            }
        }
        }
    }
    }
    // 菜单必须挂在 chip 行外面：Menu 虽然是 Popup，但它的调用点仍会在父 Row 里留一个
    // 0 宽节点，Row 的 spacedBy 会照样给这个空节点算一份间隔 —— 日志实测长按弹菜单时
    // 内容宽度从 1599px 变成 1631px（正好 8dp×4），右边所有 chip 就整体右移 8dp。
    val activeAnchor = menuAnchor
    val activeWorkspace = menuWorkspace
    if (activeAnchor != null && activeWorkspace != null) {
        Menu(
            expanded = true,
            onDismissRequest = { menuWorkspace = null },
            items = menuItems,
            onItemClick = { item ->
                menuWorkspace = null
                when (item.value) {
                    1 -> onRenameWorkspace(activeWorkspace)
                    2 -> onDeleteWorkspace(activeWorkspace)
                }
            },
            anchor = activeAnchor,
        )
    }
}

/**
 * 单个工作区 chip：点击切换，长按回调。
 *
 * ChipTab 自带的点击会吃掉常规按下的手势，所以长按在 Initial pass 上旁听：
 * 只观察不消费，触摸点移动超过容差就放弃，保证横向滚动照常工作。
 */
@Composable
private fun WorkspaceChipTab(
    label: String,
    selected: Boolean,
    dark: Boolean,
    backdrop: CouiBackdrop,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** 列表此刻是否正在横向滑动；正在滑动时不认长按。 */
    isListScrolling: () -> Boolean = { false },
) {
    val currentLongPress by rememberUpdatedState(onLongPress)
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    // 按下时的「拖拽位移」：COUI chip 内部只做缩放，位移这半截由外层补，
    // 手指按下时整体轻微下压，松手弹回。
    val pressShift = animateFloatAsState(
        targetValue = if (pressed) WorkspaceChipPressShiftDp else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "workspace-chip-press-shift",
    )
    val currentIsScrolling by rememberUpdatedState(isListScrolling)
    val longPressObserver = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val origin = down.position
            pressed = true
            // 只要手指挪动超过 touchSlop 就不算长按（横向滑动 chip 行时手指必然先超过它），
            // 抬起同样取消；超时且全程没动才继续判定。
            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val track = event.changes.firstOrNull { it.id == down.id }
                    if (track == null || !track.pressed) return@withTimeoutOrNull false
                    if ((track.position - origin).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull false
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            pressed = false
            // held == null 表示计时走满：这段时间里既没抬起、也没挪动超过 touchSlop。
            // 已经在滑动列表时再弹菜单一定是误触，直接放弃。
            if (held == null && !currentIsScrolling()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentLongPress()
                // 一次按住只算一次长按：等手指彻底抬起再回到监听循环。
                // 抬起事件在这里消费掉，否则 ChipTab 自己的 clickable 还会收到这次松手，
                // 长按后顺手把工作区切了；切换会重排 chip 行，看起来就是后面几个按钮在挪位。
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.none { it.pressed }) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                }
            }
        }
    }
    // 表面自己画：COUI 原生 ChipTab 的材质模糊偏轻，也没有整块颜色遮罩，
    // 这里换成同一套浮动表面的 Eta 版（模糊更重 + 亮色白 30% / 暗色黑 30%）。
    // 外壳只补按下时的 1.2dp 位移，不再垫任何自制玻璃。
    PaneChipSurface(
        modifier = modifier
            .then(longPressObserver)
            .graphicsLayer { translationY = pressShift.value.dp.toPx() },
        label = label,
        selected = selected,
        backdrop = backdrop,
        dark = dark,
        enabled = true,
        onClick = onClick,
    )
}

private const val WorkspaceChipPressShiftDp = 1.2f

/** 工作区 chip 的材质模糊半径（像素）。COUI 原生 ChipTab 是 40f，这里加大到 80f。 */
private const val WorkspaceChipBlurRadiusPx = 80f

/** 分类 chip 的整块颜色遮罩：亮色模式白色 30%，暗色模式黑色 30%。 */
private fun workspaceChipOverlayColor(dark: Boolean): Color =
    if (dark) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.30f)

/**
 * 工作区 chip 的浮动表面。
 *
 * 结构照搬 COUI `ChipTab` 的 ChipSurface（选中进度动画、按压缩放、spotlight、
 * 内容层录制），只在材质上做两处改动：
 * 1. blurRadius 从 40f 提到 [WorkspaceChipBlurRadiusPx]，玻璃更糊；
 * 2. 材质之上叠一层 [workspaceChipOverlayColor]，亮色白 30%、暗色黑 30%。
 */
@RequiresApi(34)
@Composable
private fun PaneChipSurface(
    label: String,
    selected: Boolean,
    dark: Boolean,
    backdrop: CouiBackdrop,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        // COUI 用 200ms 临界阻尼弹簧，这里用等价的 Compose spring。
        animationSpec = spring(dampingRatio = 1f, stiffness = 986.96f),
        label = "workspace-chip-selection",
    )
    val density = LocalDensity.current.density
    val press = rememberCouiPressMotion()
    val interaction = remember { MutableInteractionSource() }
    val renderer = remember { CouiFloatingRenderer.standalone() }
    val contentLayer = rememberGraphicsLayer()
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = enabled,
        preset = SpotlightPreset.ChipTab,
        dark = dark,
        cancelOnConsumedMove = true,
    )
    val contour = LocalCouiContourGlowEnabled.current
    val checked = fraction.coerceIn(0f, 1f)
    val material = remember(dark, checked) {
        CouiChipTabDefaults
            .material(dark = dark, checkedFraction = checked)
            .copy(blurRadius = WorkspaceChipBlurRadiusPx)
    }
    val shadow = remember(dark, density, checked, contour) {
        CouiChipTabDefaults.shadow(
            dark = dark,
            density = density,
            checkedFraction = checked,
            caustic = contour,
        )
    }
    val overlayColor = remember(dark) { workspaceChipOverlayColor(dark) }
    val sizeModifier = Modifier.widthIn(min = 52.dp, max = 300.dp).height(32.dp)
    Box(
        modifier = modifier
            .then(sizeModifier)
            .onSizeChanged { bounds = it }
            // 输入区域保持原尺寸，避免按压缩放使靠边触点在移动时越界。
            .couiPressMotion(press, enabled, cancelOnConsumedMove = true)
            .then(lightInput)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = CouiCapsuleShape,
                contentLayer = contentLayer,
                contourGlowEnabled = contour,
                foregroundComposition = true,
                beforeContent = { path ->
                    // 材质之上、内容之下叠整块遮罩；跟随按压缩放贴住可见胶囊。
                    withTransform({
                        scale(press.scale.value, press.scale.value, center)
                    }) {
                        drawPath(path, overlayColor)
                    }
                },
            ) {
                CouiFloatingSurface(
                    bounds = Rect(Offset.Zero, Size(bounds.width.toFloat(), bounds.height.toFloat())),
                    scaleX = press.scale.value,
                    scaleY = press.scale.value,
                    cornerWeight = 1f,
                )
            },
    ) {
        Row(
            modifier = Modifier
                .then(sizeModifier)
                .graphicsLayer {
                    scaleX = press.scale.value
                    scaleY = press.scale.value
                }
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                }
                .clip(CouiCapsuleShape)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                style = CouiChipTabDefaults.textStyle.copy(
                    color = CouiChipTabDefaults.contentColor(dark = dark, enabled = enabled),
                ),
                modifier = Modifier.widthIn(max = 200.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 分组标题。
 *
 * [collapsed] 为 null 时不折叠、也不显示箭头（时间分组），只留标题；
 * 传了 [collapsed] 就是可折叠分组（工作区、置顶），标题后跟一个随状态旋转的箭头。
 */
@Composable
private fun PaneSectionHeader(
    label: String,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    count: Int? = null,
    collapsed: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    labelIcon: ImageVector? = null,
    largeTitle: Boolean = false,
    /** 可折叠但不想露出箭头时传 false（例如置顶分组）。 */
    chevronVisible: Boolean = true,
    /** 点击是否给按压色／水波纹反馈。 */
    clickFeedback: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val dark = if (isCoui) CouiTheme.isDark else MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val labelColor = if (isCoui) {
        CouiPageDefaults.themeColors(dark).summary
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant
    }
    val metaColor = if (isCoui) {
        CouiPageDefaults.themeColors(dark).summary.copy(alpha = 0.72f)
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.72f)
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed == true) 0f else 90f,
        animationSpec = tween(durationMillis = 180),
        label = "pane-section-chevron",
    )
    val horizontalInset = ConversationPanelMetrics.PaneHorizontalPadding +
        ConversationPanelMetrics.RowHorizontalPadding

    val labelBlock: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (collapsed != null && chevronVisible) {
                Icon(
                    imageVector = ConversationPaneIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = PaneExtraMetrics.SectionChevronGap)
                        .size(PaneExtraMetrics.SectionChevronSize)
                        .rotate(chevronRotation),
                    tint = metaColor,
                )
            }
            if (labelIcon != null) {
                Icon(
                    imageVector = labelIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = PaneExtraMetrics.SectionChevronGap)
                        .size(PaneExtraMetrics.SectionIconSize),
                    tint = labelColor,
                )
            }
            if (isCoui) {
                CouiText(
                    text = label,
                    kind = if (largeTitle) CouiTextKind.ItemTitle else CouiTextKind.GroupTitle,
                    color = labelColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.semantics { heading() },
                )
            } else {
                Text(
                    text = label,
                    style = if (largeTitle) MiuixTheme.textStyles.body1 else MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                    modifier = Modifier.semantics { heading() },
                )
            }
            count?.let { value ->
                if (isCoui) {
                    CouiText(
                        text = value.toString(),
                        kind = CouiTextKind.ItemValue,
                        color = labelColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(start = PaneExtraMetrics.SectionCountGap),
                    )
                } else {
                    Text(
                        text = value.toString(),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(start = PaneExtraMetrics.SectionCountGap),
                    )
                }
            }
            // 置顶分区的折叠指示：只在折叠时挂在数字后面，展开时保持标题行干净。
            // 朝下的小折角（把 ChevronRight 顺时针转 90°），颜色与「置顶/时间」标题一致。
            if (collapsed == true) {
                Icon(
                    imageVector = ConversationPaneIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = PaneExtraMetrics.SectionChevronGap)
                        .size(PaneExtraMetrics.SectionChevronSize)
                        .rotate(90f),
                    tint = labelColor,
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = horizontalInset,
                top = topPadding,
                end = horizontalInset,
                bottom = ConversationPanelMetrics.SectionBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggle != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = if (clickFeedback) LocalIndication.current else null,
                        onClickLabel = label,
                        onClick = onToggle,
                    ),
            ) {
                labelBlock()
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                labelBlock()
            }
        }
        trailing?.invoke()
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
            .size(PaneExtraMetrics.HeaderActionSize)
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(PaneExtraMetrics.HeaderActionIconSize),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
    }
}

/** 分组标题下方的浅色分隔线，与标题文字左右对齐。 */
@Composable
private fun PaneHeaderDivider() {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val dark = if (isCoui) CouiTheme.isDark else MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val color = if (isCoui) {
        if (dark) Color(0x1FFFFFFF) else Color(0x14000000)
    } else {
        MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ConversationPanelMetrics.PaneHorizontalPadding +
                    ConversationPanelMetrics.RowHorizontalPadding,
                end = ConversationPanelMetrics.PaneHorizontalPadding +
                    ConversationPanelMetrics.RowHorizontalPadding,
                top = 2.dp,
            )
            .height(0.5.dp)
            .background(color),
    )
}

/** 工作区为空时的提示行。 */
@Composable
private fun WorkspaceEmptyHint() {
    Text(
        text = stringResource(R.string.workspace_empty_hint),
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        style = MiuixTheme.textStyles.footnote1,
        modifier = Modifier.padding(
            start = ConversationPanelMetrics.PaneHorizontalPadding +
                ConversationPanelMetrics.RowHorizontalPadding,
            end = ConversationPanelMetrics.PaneHorizontalPadding +
                ConversationPanelMetrics.RowHorizontalPadding,
            bottom = ConversationPanelMetrics.SectionBottomPadding,
        ),
    )
}

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
    val isCoui = uiStyle == AppearanceUiStyle.COUI
    val dark = if (isCoui) CouiTheme.isDark else MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val workspaceTitleColor = if (isCoui) {
        CouiPageDefaults.themeColors(dark).summary
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant
    }
    val couiAnchor = rememberCouiMenuAnchor()
    val rotation by animateFloatAsState(
        targetValue = if (workspace.collapsed) 0f else 90f,
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
                    start = ConversationPanelMetrics.RowHorizontalPadding + PaneExtraMetrics.WorkspaceIndent,
                    end = ConversationPanelMetrics.RowHorizontalPadding,
                    top = ConversationPanelMetrics.RowVerticalPadding,
                    bottom = ConversationPanelMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ConversationPaneIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(PaneExtraMetrics.SectionChevronSize)
                    .rotate(rotation),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(PaneExtraMetrics.SectionChevronGap))
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(PaneExtraMetrics.SectionIconSize),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(modifier = Modifier.width(PaneExtraMetrics.SectionIconGap))
            Text(
                text = workspace.name,
                // 与分组标题同色，避免子工作区比标题更亮。
                color = workspaceTitleColor,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 不撑满整行：会话数紧跟名字，和分组标题一致，而不是贴到最右。
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(PaneExtraMetrics.SectionCountGap))
            Text(
                text = workspace.conversations.size.toString(),
                color = workspaceTitleColor,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationHistoryRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onMoveToWorkspace: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    indented: Boolean = false,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val anchor = rememberCouiMenuAnchor()
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val couiDark = CouiTheme.isDark
    val couiRowShape = remember { CouiShape(ConversationPanelMetrics.RowCornerRadius, 2.1f) }
    val miuixColors = MiuixTheme.colorScheme
    val miuixDark = miuixColors.surface.luminance() < 0.5f

    val rowBackground = when {
        !selected -> Color.Transparent
        isCoui -> if (couiDark) Color(0xFF262932) else Color.White
        else -> miuixColors.tertiaryContainer.copy(alpha = if (miuixDark) 0.72f else 0.88f)
    }
    val rowModifier = if (isCoui) {
        val source = remember { MutableInteractionSource() }
        Modifier
            .then(
                if (selected && !couiDark) {
                    Modifier.dropShadow(
                        shape = couiRowShape,
                        shadow = Shadow(
                            radius = 8.dp,
                            color = Color.Black,
                            alpha = 0.028f,
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .clip(couiRowShape)
            .background(rowBackground, couiRowShape)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 0.5.dp,
                        color = if (couiDark) Color(0x1AFFFFFF) else Color(0x0D000000),
                        shape = couiRowShape,
                    )
                } else {
                    Modifier
                },
            )
            .couiButtonFeedback(couiDark, source, enabled = true, list = true)
    } else {
        Modifier.squircleSurface(
            color = rowBackground,
            cornerRadius = ConversationPanelMetrics.RowCornerRadius,
        )
    }

    val titleColor = if (isCoui) {
        CouiPageDefaults.themeColors(couiDark).title
    } else {
        miuixColors.onSurface
    }
    val summaryColor = if (isCoui) {
        CouiPageDefaults.themeColors(couiDark).summary
    } else {
        miuixColors.onSurfaceVariantSummary
    }
    val accentColor = if (isCoui) {
        CouiTheme.colorSchemeFor(couiDark).accent
    } else {
        miuixColors.primary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ConversationPanelMetrics.RowMinHeight)
                .padding(start = if (indented) PaneExtraMetrics.WorkspaceIndent else 0.dp)
                .couiMenuAnchor(anchor)
                .then(rowModifier)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        anchor.open { showActionMenu = true }
                    },
                )
                .padding(
                    start = ConversationPanelMetrics.RowHorizontalPadding,
                    end = 8.dp,
                    top = ConversationPanelMetrics.RowVerticalPadding,
                    bottom = ConversationPanelMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (conversation.characterName != null) {
                CharacterAvatar(
                    name = conversation.characterName,
                    avatarPath = conversation.characterAvatarPath,
                    size = 30.dp,
                    shape = CircleShape,
                    modifier = if (selected) {
                        Modifier.border(1.5.dp, accentColor.copy(alpha = 0.75f), CircleShape)
                    } else {
                        Modifier
                    },
                )
                Spacer(Modifier.width(11.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val title = conversation.title.ifBlank { conversation.preview }
                Text(
                    text = title,
                    color = titleColor,
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                    // 选中与否字重一致（都不加粗），选中只靠行背景区分。
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 标题与角色名相同（如未改名的角色会话）时不再重复第二行。
                conversation.characterName?.takeIf { it != title }?.let { name ->
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                        color = summaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                conversation.runStatus?.let { status ->
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (conversation.isActiveRun) {
                Box(
                    modifier = Modifier
                        .padding(start = ConversationPanelMetrics.ActiveDotGap)
                        .size(ConversationPanelMetrics.ActiveDotSize)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }

        }

        // 将选中指示条放在左内边距留白区，保证选中行与普通行标题严格处于同一条左对齐基线。
        if (selected && conversation.characterName == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // 工作区内的行整体右缩了 WorkspaceIndent，竖条跟着挪，保持在行底色内部。
                    .padding(
                        start = (if (indented) PaneExtraMetrics.WorkspaceIndent else 0.dp) + 5.5.dp,
                    )
                    .width(2.5.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
        }

        val pinText = stringResource(
            if (conversation.isPinned) {
                R.string.action_unpin_conversation
            } else {
                R.string.action_pin_conversation
            },
        )
        val renameText = stringResource(R.string.action_rename)
        val exportText = stringResource(R.string.action_export)
        val moveText = stringResource(R.string.action_move_to_workspace)
        val deleteText = stringResource(R.string.action_delete)
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                val destructiveColor = CouiDialogDefaults.themeColors().destructive
                val menuItems = remember(
                    pinText,
                    renameText,
                    exportText,
                    moveText,
                    deleteText,
                    destructiveColor,
                ) {
                    listOf(
                        CouiMenuItem(value = 0, text = pinText, icon = Icons.Outlined.PushPin),
                        CouiMenuItem(value = 1, text = renameText, icon = Icons.Outlined.Edit),
                        CouiMenuItem(value = 2, text = exportText, icon = Icons.Outlined.Download),
                        CouiMenuItem(
                            value = 3,
                            text = moveText,
                            icon = Icons.Rounded.FolderOpen,
                        ),
                        CouiMenuItem(
                            value = 4,
                            text = deleteText,
                            icon = Icons.Outlined.Delete,
                            iconTint = destructiveColor,
                            dividerBefore = true,
                        ),
                    )
                }
                Menu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                    items = menuItems,
                    onItemClick = { item ->
                        showActionMenu = false
                        when (item.value) {
                            0 -> onTogglePin()
                            1 -> onRename()
                            2 -> onExport()
                            3 -> onMoveToWorkspace()
                            4 -> onDelete()
                        }
                    },
                    anchor = anchor,
                )
            }
            AppearanceUiStyle.MIUIX -> {
                WindowListPopup(
                    show = showActionMenu,
                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onDismissRequest = { showActionMenu = false },
                ) {
                    val pinItem = remember(pinText) {
                        DropdownItem(
                            text = pinText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
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
                                    imageVector = Icons.Outlined.Edit,
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
                                    imageVector = Icons.Outlined.Download,
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
                                    imageVector = Icons.Outlined.Delete,
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
                                onTogglePin()
                            },
                        )
                        DropdownImpl(
                            item = renameItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 1,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onRename()
                            },
                        )
                        DropdownImpl(
                            item = exportItem,
                            optionSize = 5,
                            isSelected = false,
                            index = 2,
                            onSelectedIndexChange = {
                                showActionMenu = false
                                onExport()
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
}

@Composable
private fun EmptyConversations(isSearching: Boolean) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val dark = if (isCoui) CouiTheme.isDark else MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val primaryTextColor = if (isCoui) {
        CouiPageDefaults.themeColors(dark).summary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val badgeBg = conversationPaneCardColor()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ConversationPanelMetrics.PaneHorizontalPadding + 12.dp,
                vertical = ConversationPanelMetrics.EmptyVerticalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSearching) ConversationPaneIcons.Search else ConversationPaneIcons.BrandEta,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = primaryTextColor.copy(alpha = 0.72f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (isSearching) R.string.conversation_no_results else R.string.conversation_empty,
            ),
            color = primaryTextColor,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (!isSearching) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.conversation_empty_hint),
                color = primaryTextColor.copy(alpha = 0.75f),
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                textAlign = TextAlign.Center,
            )
        }
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
        AppearanceUiStyle.COUI -> {
            val couiDark = CouiTheme.isDark
            Dialog(
                visible = true,
                onDismissRequest = onDismiss,
                dark = couiDark,
                title = title,
                confirmAction = CouiDialogAction(text = cancelText, onClick = onDismiss),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(couiDark),
                backdrop = rememberCouiWindowBackdrop(),
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
                }
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

private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
)

private sealed interface ConversationDrawerSection {
    data object Pinned : ConversationDrawerSection
    data object Today : ConversationDrawerSection
    data class Dated(val label: String) : ConversationDrawerSection
}

/**
 * 分组折叠偏好与 LazyColumn item key 共用的稳定标识。
 *
 * 只有置顶分组会写进折叠偏好，其余时间分组的 key 仅用于列表项去重。
 */
private fun ConversationDrawerSection.storageKey(): String = when (this) {
    ConversationDrawerSection.Pinned -> "pinned"
    ConversationDrawerSection.Today -> "today"
    is ConversationDrawerSection.Dated -> "dated:$label"
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
