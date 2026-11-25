package io.github.mangi.eta.ui.components
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.RenderEffect as AndroidRenderEffect
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.zIndex
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import androidx.annotation.RequiresApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
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
import com.itos.coui.layout.Divider
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.radiobutton.RadioButton
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
import io.github.mangi.eta.ui.components.coui.CouiIconDividerIndent
import io.github.mangi.eta.ui.components.coui.CouiSettingsIcon
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.menu.rememberCouiWindowBackdrop
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.components.coui.couiPrimaryDialogAction
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.ConversationWorkspaceUi
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.FolderOutline
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Pin
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import com.itos.coui.search.CouiSearchBarDefaults

@Stable
private class PaneBarInsets {
    var top by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)
}

/** 侧栏分组折叠状态落盘的 SharedPreferences 文件名（键就是分组 key）。 */
private const val PaneCollapsePrefsName = "conversation_pane_collapsed"

/** 工作区 chip 栏里「全部」的固定 key；真实工作区 key 用的是工作区 id。 */
internal const val WorkspaceChipAllKey = "__workspace_all__"

/** 工作区整段标题的折叠 key，与其它分组 key 共用一套命名空间。 */

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState,
    width: Dp,
    listState: LazyListState,
    onSearchChange: (String) -> Unit,
    onSubmitConversationSearch: (String) -> Unit = {},
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
    /** 「移动到工作区」弹窗里长按拖动后的新顺序（工作区 id 列表），与顶部 chip 行共用同一份顺序。 */
    onReorderWorkspaces: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenScheduledTasks: () -> Unit = {},
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
    // 第二阶段（全库正文扫描）在跑时，顶栏标题换成「正在搜索」；第一阶段与空闲时保持应用名。
    val searchRunningTitle = if (state.isSearchRunning) {
        stringResource(R.string.local_conversation_searching)
    } else {
        null
    }
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    // 空槽位里的转圈先压住 150ms 再淡入：搜索一上来列表会从几百条骤减到空，
    // LazyColumn 还要按旧偏移重排一帧，转圈立刻画会显得「先在老位置、下一帧往上跳」。
    var searchProgressVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.isSearchRunning) {
        searchProgressVisible = false
        if (!state.isSearchRunning) return@LaunchedEffect
        delay(SearchProgressDelayMillis)
        searchProgressVisible = true
    }
    // 没有会话可显示时那块区域当前该是哪种状态：三态共用同一个 item 与同一段交叉淡化。
    val emptyPaneSlot = when {
        !state.isSearchRunning -> EmptyPaneSlot.Message
        searchProgressVisible -> EmptyPaneSlot.SearchProgress
        else -> EmptyPaneSlot.Blank
    }
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
            // 切换分类后列表是整段替换，落点统一回到最顶部：
            // 登记一次滚动请求（下一次测量即采用），避免沿用上一个分类的滚动位置。
            listState.requestScrollToItem(0)
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
            selectedWorkspaceKey == WorkspaceChipAllKey -> allConversations
            else -> state.workspaces
                .firstOrNull { it.id == selectedWorkspaceKey }
                ?.conversations
                .orEmpty()
        }
    }
    // 搜索结果可能来自任意分类：只有搜索态下才把分类 chip 归位到「全部」，
    // 这样结束搜索时这条会话一定还在当前列表里，不会因为停留在别的文件夹而「消失」；
    // 非搜索态点会话就留在用户当前选的分类里，不要擅自跳回「全部」。
    val selectConversationRow: (String) -> Unit = { conversationId ->
        if (isSearching) selectWorkspace(WorkspaceChipAllKey)
        onConversationSelected(conversationId)
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
    val defaultWorkspaceNameFormat = stringResource(R.string.local_workspace_default_name)
    val createWorkspaceLabel = stringResource(R.string.local_action_create_workspace)

    var workspaceNameTarget by remember { mutableStateOf<WorkspaceNameTarget?>(null) }
    var workspaceDeleteTarget by remember { mutableStateOf<ConversationWorkspaceUi?>(null) }
    var workspaceMoveTarget by remember { mutableStateOf<ConversationSummaryUi?>(null) }

    val showWorkspacesSection = state.workspaces.isNotEmpty() || !isSearching
    // 两种样式都把工作区收进顶部 chip 栏：MIUIX 用 Miuix 组件渲染同一套布局。
    val hasWorkspaceChips = showWorkspacesSection

    val containerColor = conversationPaneContainerColor()
    val couiBackdrop = if (isCoui) rememberCouiBackdrop() else null
    val miuixBackdrop = if (!isCoui) rememberGlassBackdrop(containerColor) else null
    val barInsets = remember { PaneBarInsets() }
    // 底部遮罩要拿搜索框的窗口坐标换算到面板坐标，才能贴着胶囊顶边画渐变。
    var bodyOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    // COUI 侧只保留 COUI 自己的 backdrop 录制：顶栏、按钮、chip 全部从它采样，
    // 不再叠加 Miuix 的 LayerBackdrop 渐进模糊（两套引擎一起跑会闪、观感也对不上会话页）。
    val bodyCaptureModifier = when {
        isCoui && couiBackdrop != null -> Modifier.couiBackdrop(couiBackdrop)
        !isCoui && isRevealed && miuixBackdrop != null -> Modifier.captureGlassBackdrop(miuixBackdrop)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(containerColor),
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
                            .background(containerColor)
                            .onGloballyPositioned { bodyOriginInWindow = it.localToWindow(Offset.Zero) }
                            // 底部遮罩画在列表内容之上（drawContent 之后）：从搜索框胶囊顶边往上
                            // 由透明线性渐到页面底色，把列表被 LazyColumn 裁断的那一行彻底压掉；
                            // 胶囊顶边以下用整块纯色接到页面底部。画在面板层是因为 dock 容器的高
                            // 度不够（渐变会被容器边界裁成一截，看起来还是一条硬线）。
                            .drawWithContent {
                                drawContent()
                                val bar = ConversationPaneSearchDock.barBoundsInWindow
                                    ?: return@drawWithContent
                                if (bar.height <= 1f) return@drawWithContent
                                val capsuleHeight = CouiSearchBarDefaults.surfaceHeight.toPx()
                                val capsuleTop = bar.top - bodyOriginInWindow.y +
                                    (bar.height - capsuleHeight) / 2f
                                // 实色区上沿压到胶囊顶下方一点：渐变带整体下移后，淡出完全发生在
                                // 胶囊上方的可见区里，胶囊本身及其下方由后面的实色接住。
                                val solidTop = capsuleTop + 2.dp.toPx()
                                val fadeHeight = 66.dp.toPx()
                                val fadeTop = solidTop - fadeHeight
                                // 不用 Brush.verticalGradient：这块离屏层里的 shader 渐变实测画不出
                                // 可感知的过渡（整段等于没画，硬边就是这么来的）。改成逐条纯色矩形，
                                // 和下面的纯色区走同一条绘制路径，alpha 线性递增。
                                val slices = 28
                                val sliceHeight = fadeHeight / slices
                                for (index in 0 until slices) {
                                    val progress = (index + 0.5f) / slices
                                    drawRect(
                                        color = containerColor.copy(alpha = progress),
                                        topLeft = Offset(0f, fadeTop + index * sliceHeight),
                                        size = Size(size.width, sliceHeight + 0.5f),
                                    )
                                }
                                drawRect(
                                    color = containerColor,
                                    topLeft = Offset(0f, solidTop),
                                    size = Size(
                                        size.width,
                                        (size.height - solidTop).coerceAtLeast(1f),
                                    ),
                                )
                            },
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
                            // 侧栏不再有顶部工作台卡片，第一条就是工作区段或会话分组。
                            // 顶部不再有任何选择框/工作台卡片，第一条就是工作区段或会话分组。
                            // 工作区已收进顶部 chip 栏，树形段不再渲染（两种样式一致）。
                            if (!hasWorkspaceChips && showWorkspacesSection) {
                                item(key = "section-workspaces") {
                                    Column {
                                        PaneSectionHeader(
                                            label = stringResource(R.string.local_workspace_section_title),
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
                                                miuixBackdrop = miuixBackdrop,
                                                onClick = { selectConversationRow(conversation.id) },
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
                            if (!hasWorkspaceChips && showWorkspacesSection && state.workspaces.isNotEmpty()) {
                                item(key = "section-workspaces-divider") {
                                    PaneHeaderDivider()
                                }
                            }

                            val hasAnyConversation = looseConversations.isNotEmpty() ||
                                state.workspaces.any { it.conversations.isNotEmpty() }
                            if (!hasAnyConversation) {
                                // 三态走同一个 item、同一个 key：状态切换只在槽位内部交叉淡化，
                                // 换 key 会让整块 item 重建，位置就会跳一帧。
                                item(key = "pane_empty_slot") {
                                    Crossfade(
                                        targetState = emptyPaneSlot,
                                        animationSpec = tween(EmptyPaneSlotFadeMillis),
                                        label = "pane-empty-slot",
                                    ) { target ->
                                        when (target) {
                                            EmptyPaneSlot.SearchProgress -> PaneSearchProgress()
                                            EmptyPaneSlot.Blank -> Spacer(
                                                Modifier.height(EmptyPaneSlotHeight),
                                            )
                                            EmptyPaneSlot.Message -> EmptyConversations(
                                                isSearching = isSearching,
                                            )
                                        }
                                    }
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
                                                miuixBackdrop = miuixBackdrop,
                                                onClick = { selectConversationRow(conversation.id) },
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
                        brandTitle = searchRunningTitle,
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
                        onSubmitConversationSearch = onSubmitConversationSearch,
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

        }
    }

    // ── 工作区三个弹窗：新建/重命名、删除确认、移入工作区 ─────────────────────
    workspaceNameTarget?.let { target ->
        val title = when (target) {
            is WorkspaceNameTarget.Create -> stringResource(R.string.local_workspace_create_title)
            is WorkspaceNameTarget.Rename -> stringResource(R.string.local_action_rename_workspace)
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
            onReorder = onReorderWorkspaces,
        )
    }
}

internal const val WorkspaceChipPressShiftDp = 1.2f

/** 工作区 chip 的材质模糊半径（像素）。COUI 原生 ChipTab 是 40f，这里加大到 80f。 */
internal const val WorkspaceChipBlurRadiusPx = 80f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationHistoryRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    miuixBackdrop: Backdrop?,
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
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var popupAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    val anchor = rememberCouiMenuAnchor()
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val couiDark = CouiTheme.isDark
    val couiRowShape = remember { CouiShape(ConversationPanelMetrics.RowCornerRadius, 2.1f) }
    val miuixColors = MiuixTheme.colorScheme
    val miuixDark = miuixColors.surface.luminance() < 0.5f

    // 侧栏会话行统一采用 COUI 的选中态外观：超椭圆、纯白/#262932 底色、0.5dp 描边
    // 与浅色下的柔和投影；MIUIX 样式不再使用 squircleSurface 的默认行底。
    val rowDark = if (isCoui) couiDark else miuixDark
    val rowBackground = when {
        !selected -> Color.Transparent
        rowDark -> Color(0xFF262932)
        else -> Color.White
    }
    val rowModifier = run {
        val source = remember { MutableInteractionSource() }
        Modifier
            .then(
                if (selected && !rowDark) {
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
                        color = if (rowDark) Color(0x1AFFFFFF) else Color(0x0D000000),
                        shape = couiRowShape,
                    )
                } else {
                    Modifier
                },
            )
            .couiButtonFeedback(rowDark, source, enabled = true, list = true)
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
    val avatarBorderColor = if (if (isCoui) couiDark else miuixDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.08f)
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
                .onGloballyPositioned { rowBounds = it.boundsInWindow() }
                .couiMenuAnchor(anchor)
                .then(rowModifier)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        popupAnchorBounds = rowBounds
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
                    size = ConversationPanelMetrics.CharacterAvatarSize,
                    shape = CircleShape,
                    borderWidth = 0.5.dp,
                    borderColor = avatarBorderColor,
                )
                Spacer(Modifier.width(ConversationPanelMetrics.CharacterAvatarGap))
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

        // 将选中指示条放在左内边距留白区，普通与角色会话共用同一套选中反馈语言与左对齐节奏。
        if (selected) {
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
        val moveText = stringResource(R.string.local_action_move_to_workspace)
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
                val overlayBackdrop = currentGlassOverlayBackdrop() ?: miuixBackdrop
                TopLayer {
                    GlassPopup(
                        show = showActionMenu,
                        onDismissRequest = { showActionMenu = false },
                        anchorBounds = popupAnchorBounds,
                        backdrop = overlayBackdrop,
                    ) {
                        GlassPopupItem(
                            text = pinText,
                            icon = MiuixIcons.Glass.Pin,
                            onClick = {
                                showActionMenu = false
                                onTogglePin()
                            },
                        )
                        GlassPopupItem(
                            text = renameText,
                            icon = MiuixIcons.Glass.Edit,
                            onClick = {
                                showActionMenu = false
                                onRename()
                            },
                        )
                        GlassPopupItem(
                            text = exportText,
                            icon = MiuixIcons.Glass.Download,
                            onClick = {
                                showActionMenu = false
                                onExport()
                            },
                        )
                        GlassPopupItem(
                            text = moveText,
                            icon = MiuixIcons.Glass.FolderOutline,
                            onClick = {
                                showActionMenu = false
                                onMoveToWorkspace()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                horizontal = GlassPopupDefaults.DividerPaddingHorizontal,
                                vertical = GlassPopupDefaults.DividerPaddingVertical,
                            ),
                            thickness = GlassPopupDefaults.DividerThickness,
                        )
                        GlassPopupItem(
                            text = deleteText,
                            icon = MiuixIcons.Glass.Delete,
                            contentColor = MiuixTheme.colorScheme.error,
                            onClick = {
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

/**
 * 全库正文扫描（第二阶段）进行中、列表还一条结果都没有时的转圈。
 *
 * 位置和「没有匹配的对话」空态里那个 44dp 图标卡完全重合：内边距、槽位高度都照抄 EmptyConversations，
 * 只是把卡片图标换成转圈，所以搜索中→搜完无结果的切换不会跳位。
 * 不写文字——顶栏标题这时已经写着「正在搜索」，重复一次只会显得啰嗦。
 *//** 空槽位三态（空白占位 / 转圈 /「没有匹配的对话」）之间的交叉淡化时长。 */
private const val EmptyPaneSlotFadeMillis = 200

/**
 * 转圈出现前的等待。
 *
 * 搜索一上来列表会从几百条骤减到空，LazyColumn 要先按旧偏移重排一帧才收回顶部；
 * 转圈立刻画出来就会「先在老位置、下一帧往上跳」。等这一帧过去再淡入。
 */
private const val SearchProgressDelayMillis = 150L

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
                .size(EmptyConversationBadgeSize)
                .then(
                    if (isCoui) {
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(badgeBg)
                    } else {
                        Modifier.squircleSurface(color = badgeBg, cornerRadius = 14.dp)
                    },
                ),
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

private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
)

internal sealed interface ConversationDrawerSection {
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
