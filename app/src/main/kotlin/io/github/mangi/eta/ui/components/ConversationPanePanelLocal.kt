package io.github.mangi.eta.ui.components

import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.chip.CouiChipTabDefaults
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.menu.CouiMenuAnchor
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDividerIndent
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiSettingsIcon
import io.github.mangi.eta.ui.components.coui.couiPrimaryDialogAction
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.ConversationWorkspaceUi
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

// fork 本地新增：工作区 chip 行、面板分区头、工作区命名/删除/移动弹窗、空态与搜索占位。

/** 顶部浮层骨架之外，工作区与折叠标题额外需要的标尺。 */
internal object PaneExtraMetrics {
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

/** 工作区 chip 浮动材质外半影预留的纵向余量：亮色灰影比暗色明显，按最亮的一档留。 */
private val WorkspaceChipShadowRoom = 20.dp

/** chip 之间、以及 chip 与面板内边距之间的固定间隔。 */
private val WorkspaceChipGap = 8.dp

/** chip 行两端溢出时的渐隐宽度，超出面板边框的 chip 在这段距离内淡到透明。 */
private val WorkspaceChipEdgeFade = 30.dp

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
internal fun ConversationPaneWorkspaceChips(
    workspaces: List<ConversationWorkspaceUi>,
    selectedKey: String,
    onSelectedKeyChange: (String) -> Unit,
    onCreateWorkspace: () -> Unit,
    onRenameWorkspace: (ConversationWorkspaceUi) -> Unit,
    onDeleteWorkspace: (ConversationWorkspaceUi) -> Unit,
    couiBackdrop: CouiBackdrop?,
) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    var miuixMenuWorkspace by remember { mutableStateOf<ConversationWorkspaceUi?>(null) }
    var miuixMenuBounds by remember { mutableStateOf(Rect.Zero) }
    // 玻璃菜单自己维护「展开中」标志：show 从 false 翻到 true 才有展开动画。
    var miuixMenuVisible by remember { mutableStateOf(false) }
    // COUI 必须拿到背景采样；MIUIX 由 Miuix 组件自绘，不再依赖 COUI 材质。
    if (isCoui && couiBackdrop == null) return
    val dark = CouiTheme.isDark
    val allLabel = stringResource(R.string.local_inbox_all)
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
    val renameLabel = stringResource(R.string.local_action_rename_workspace)
    val deleteLabel = stringResource(R.string.local_action_delete_workspace)
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
            onLongPress = { onCreateWorkspace() },
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
                    onLongPress = { bounds ->
                        if (isCoui) {
                            menuAnchor = chipAnchor
                            menuWorkspace = workspace
                        } else {
                            miuixMenuBounds = bounds
                            miuixMenuWorkspace = workspace
                            miuixMenuVisible = true
                        }
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

    // MIUIX：长按 chip 用 Miuix 玻璃菜单，与会话行的 GlassPopup 同一套。
    // show 必须由 false 翻到 true，GlassPopup 才会跑展开动画；直接写死 true 就是「首帧全展开」，
    // 看起来像没有动画。
    if (!isCoui) {
        EtaTopLayer {
            GlassPopup(
                show = miuixMenuVisible,
                onDismissRequest = {
                    miuixMenuVisible = false
                    miuixMenuWorkspace = null
                },
                anchorBounds = miuixMenuBounds,
                backdrop = currentGlassOverlayBackdrop(),
            ) {
                val target = miuixMenuWorkspace
                if (target != null) {
                    GlassPopupItem(
                        text = renameLabel,
                        onClick = {
                            miuixMenuVisible = false
                            miuixMenuWorkspace = null
                            onRenameWorkspace(target)
                        },
                    )
                    GlassPopupItem(
                        text = deleteLabel,
                        onClick = {
                            miuixMenuVisible = false
                            miuixMenuWorkspace = null
                            onDeleteWorkspace(target)
                        },
                    )
                }
            }
        }
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
    backdrop: CouiBackdrop?,
    onClick: () -> Unit,
    /** 长按回调带上 chip 的窗口坐标：MIUIX 的玻璃菜单按这个矩形定位。 */
    onLongPress: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    /** 列表此刻是否正在横向滑动；正在滑动时不认长按。 */
    isListScrolling: () -> Boolean = { false },
) {
    val currentLongPress by rememberUpdatedState(onLongPress)
    var chipBounds by remember { mutableStateOf(Rect.Zero) }
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
                currentLongPress(chipBounds)
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
    val isCouiChip = LocalUiStyle.current == AppearanceUiStyle.COUI
    if (isCouiChip) {
        // COUI：保留原有的浮动材质表面。
        PaneChipSurface(
            modifier = modifier
                .onGloballyPositioned { chipBounds = it.boundsInWindow() }
                .then(longPressObserver)
                .graphicsLayer { translationY = pressShift.value.dp.toPx() },
            label = label,
            selected = selected,
            backdrop = requireNotNull(backdrop) { "COUI 工作区 chip 需要 COUI 背景采样" },
            dark = dark,
            enabled = true,
            onClick = onClick,
        )
    } else {
        // MIUIX：布局照 COUI 放顶部 chip 栏，芯片本身换成 Miuix 的 EtaChoiceChip。
        EtaChoiceChip(
            text = label,
            selected = selected,
            onClick = onClick,
            modifier = modifier
                .onGloballyPositioned { chipBounds = it.boundsInWindow() }
                .then(longPressObserver),
        )
    }
}

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
internal fun PaneSectionHeader(
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
internal fun PaneHeaderAction(
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
internal fun PaneHeaderDivider() {
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
internal fun WorkspaceEmptyHint() {
    Text(
        text = stringResource(R.string.local_workspace_empty_hint),
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
internal fun WorkspaceRow(
    workspace: ConversationWorkspaceUi,
    onToggleCollapsed: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
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
                .onGloballyPositioned { rowBounds = it.boundsInWindow() }
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
                val renameText = stringResource(R.string.local_action_rename_workspace)
                val deleteText = stringResource(R.string.local_action_delete_workspace)
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

            AppearanceUiStyle.MIUIX -> {
                val renameText = stringResource(R.string.local_action_rename_workspace)
                val deleteText = stringResource(R.string.local_action_delete_workspace)
                EtaTopLayer {
                    GlassPopup(
                        show = showActionMenu,
                        onDismissRequest = { showActionMenu = false },
                        anchorBounds = rowBounds,
                        backdrop = currentGlassOverlayBackdrop(),
                    ) {
                        GlassPopupItem(
                            text = renameText,
                            icon = MiuixIcons.Glass.Edit,
                            onClick = {
                                showActionMenu = false
                                onRename()
                            },
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

/** 延迟窗口里的占位高度，与转圈槽位一致（上下 36dp 内边距 + 44dp 槽位），避免高度抖动。 */
internal val EmptyPaneSlotHeight = 116.dp

/** 没有会话可显示时，那块区域的三种状态。 */
internal enum class EmptyPaneSlot { Blank, SearchProgress, Message }

/** 空态图标卡的槽位尺寸：搜索转圈复用同一尺寸，两种状态的位置完全重合。 */
internal val EmptyConversationBadgeSize = 44.dp

@Composable
internal fun PaneSearchProgress() {
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
            modifier = Modifier.size(EmptyConversationBadgeSize),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/** 命名弹窗的两种入口：新建工作区、重命名工作区。 */
internal sealed interface WorkspaceNameTarget {
    data class Create(val defaultName: String) : WorkspaceNameTarget

    data class Rename(val id: String, val currentName: String) : WorkspaceNameTarget
}

@Composable
internal fun WorkspaceNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val inputState = remember(initialName) { TextFieldState(initialName) }
    val input = inputState.text.toString()
    val hint = stringResource(R.string.local_workspace_name_hint)
    val saveText = stringResource(R.string.action_save)
    val canSave = input.isNotBlank()
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> CouiInputDialog(
            visible = true,
            title = title,
            state = inputState,
            placeholder = hint,
            confirmText = saveText,
            confirmEnabled = canSave,
            onConfirm = { onConfirm(inputState.text.toString().trim()) },
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
                    onValueChange = { inputState.setTextAndPlaceCursorAtEnd(it) },
                    label = hint,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixDialogActions(
                    confirmText = saveText,
                    confirmEnabled = canSave,
                    onCancel = onDismiss,
                    onConfirm = { onConfirm(inputState.text.toString().trim()) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
internal fun WorkspaceDeleteDialog(
    workspace: ConversationWorkspaceUi,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = stringResource(R.string.local_workspace_delete_title)
    val message = stringResource(R.string.local_workspace_delete_message, workspace.name)
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
internal fun WorkspaceMoveDialog(
    conversation: ConversationSummaryUi,
    workspaces: List<ConversationWorkspaceUi>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val title = stringResource(R.string.local_workspace_move_title)
    val emptyText = stringResource(R.string.local_workspace_move_empty)
    val removeText = stringResource(R.string.local_action_remove_from_workspace)
    val cancelText = stringResource(R.string.action_cancel)
    // 保留全部分类，当前所在的那个也用右侧圆点标出来（与设置页模型提供商的单选一致）。
    // 这里的顺序就是侧栏顶部 chip 行的顺序：长按拖动改完立即落库，两处同步。
    val targets = remember { mutableStateListOf<ConversationWorkspaceUi>() }
    LaunchedEffect(workspaces) {
        if (workspaces.map { it.id } != targets.map { it.id }) {
            targets.clear()
            targets.addAll(workspaces)
        }
    }
    // 行高固定（48dp 行 + 1px 分隔线），换位按整行步进判断。
    val rowStepPx = with(LocalDensity.current) {
        (CouiPageDefaults.rowMinHeight + CouiPageDefaults.dividerHeight).toPx().coerceAtLeast(1f)
    }
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    fun beginReorderDrag(id: String) {
        draggingId = id
        dragOffsetY = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragReorderBy(id: String, delta: Float) {
        if (draggingId != id) return
        dragOffsetY += delta
        val from = targets.indexOfFirst { it.id == id }
        if (from < 0) return
        val steps = (dragOffsetY / rowStepPx).roundToInt()
        if (steps == 0) return
        val to = (from + steps).coerceIn(0, targets.lastIndex)
        if (to == from) return
        targets.add(to, targets.removeAt(from))
        // 扣掉已经换掉的行数，剩下的位移继续算下一行的阈值。
        dragOffsetY -= (to - from) * rowStepPx
    }

    fun finishReorderDrag() {
        if (draggingId == null) return
        draggingId = null
        dragOffsetY = 0f
        onReorder(targets.map { it.id })
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
                // 已在分类下时，「移出工作区」是页脚右侧的红色主操作，取消排在它左边。
                confirmAction = if (canRemove) {
                    couiPrimaryDialogAction(
                        text = removeText,
                        onClick = { onMove(null) },
                        destructive = true,
                        dark = couiDark,
                    )
                } else {
                    CouiDialogAction(text = cancelText, onClick = onDismiss)
                },
                dismissAction = if (canRemove) {
                    CouiDialogAction(text = cancelText, onClick = onDismiss)
                } else {
                    null
                },
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
                                .padding(horizontal = CouiPageDefaults.rowPadding)
                                .padding(vertical = 8.dp),
                        )
                    }
                    targets.forEachIndexed { index, workspace ->
                        val selected = workspace.id == conversation.workspaceId
                        // 用 id 做 key：拖动会重排顺序，Composable 不能按 index 互换身份，否则手势会被取消。
                        key(workspace.id) {
                            WorkspaceMoveOptionRow(
                                text = workspace.name,
                                selected = selected,
                                showDivider = index != targets.lastIndex,
                                dragging = draggingId == workspace.id,
                                dragOffsetPx = if (draggingId == workspace.id) dragOffsetY else 0f,
                                onDragStart = { beginReorderDrag(workspace.id) },
                                onDragDelta = { delta -> dragReorderBy(workspace.id, delta) },
                                onDragEnd = { finishReorderDrag() },
                                // 选中分类即落库，弹窗随之收起；点当前分类只关掉弹窗。
                                onClick = {
                                    if (selected) onDismiss() else onMove(workspace.id)
                                },
                            )
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
                targets.forEach { workspace ->
                    TextButton(
                        text = workspace.name,
                        onClick = { onMove(workspace.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (canRemove) {
                    MiuixDialogActions(
                        confirmText = removeText,
                        destructive = true,
                        onCancel = onDismiss,
                        onConfirm = { onMove(null) },
                    )
                } else {
                    TextButton(
                        text = cancelText,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 「移动到工作区」的分类选项行：沿用设置页分组行的规格（16dp 内边距、48dp 行高、标题上下居中），
 * 左侧是文件夹图标（用标题同色，不额外上色），行尾用 COUI 单选圆点标记当前分类，
 * 与设置页「模型提供商」里选模型的选中样式一致。
 * 选项之间的分隔线左侧按图标宽度缩进（CouiIconDividerIndent），右侧保持 16dp。
 */
@Composable
private fun WorkspaceMoveOptionRow(
    text: String,
    selected: Boolean,
    showDivider: Boolean,
    dragging: Boolean,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val source = remember { MutableInteractionSource() }
    val rowModifier = Modifier
        .fillMaxWidth()
        // 拖动中的行浮在其它行之上，并跟着手指移动。
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            if (dragging) {
                translationY = dragOffsetPx
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(12.dp)
            }
        }
        // 行本身是透明的，拖动浮起时必须自带底色，否则下面的行会透上来。
        .background(
            color = if (dragging) CouiTheme.colorScheme.surface else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
        )
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.y)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
            )
        }
        .couiButtonFeedback(dark, source, true, list = true)
        .selectable(
            selected = selected,
            interactionSource = source,
            indication = null,
            enabled = true,
            role = Role.RadioButton,
            onClick = onClick,
        )
        .heightIn(min = CouiPageDefaults.rowMinHeight)
        .padding(horizontal = CouiPageDefaults.rowPadding)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CouiSettingsIcon(Icons.Rounded.FolderOpen, colors.title)
        Spacer(modifier = Modifier.width(CouiPageDefaults.iconSpacing))
        CouiText(
            text = text,
            kind = CouiTextKind.ItemTitle,
            color = colors.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(24.dp),
            selected = selected,
            enabled = true,
        )
    }
    if (showDivider) {
        Divider(
            startIndent = CouiIconDividerIndent,
            endIndent = CouiPageDefaults.rowPadding,
        )
    }
}

/**
 * 分组折叠偏好与 LazyColumn item key 共用的稳定标识。
 *
 * 只有置顶分组会写进折叠偏好，其余时间分组的 key 仅用于列表项去重。
 */
internal fun ConversationDrawerSection.storageKey(): String = when (this) {
    ConversationDrawerSection.Pinned -> "pinned"
    ConversationDrawerSection.Today -> "today"
    is ConversationDrawerSection.Dated -> "dated:$label"
}
