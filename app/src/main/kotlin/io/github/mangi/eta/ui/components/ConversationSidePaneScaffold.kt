package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.LocalEtaTopLayer
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object ConversationPaneMetrics {
    val PaneMaxWidth = 340.dp
    val PaneWidthFraction = 0.84f
    /** 会话页分界侧向外渐隐的阴影：横向渐变的停靠点数。宽度按明暗分开取，见 [conversationPaneEdgeShadowWidth]。 */
    const val EdgeShadowGradientStops = 32
    /** 遮罩机制沿用上游：侧栏拉开进度 0 → 1 时叠 0% → 满值（亮 50% / 暗 30%），亮/暗各一套底色。 */
    const val SettleDampingRatio = 1f
    const val SettleStiffness = 146f
    const val SettleVisibilityThresholdPx = 0.5f
    const val SettlePositionThresholdFraction = 0.5f
    /** 点击开/合的固定时长：侧栏一次打开或收起都由同一条曲线走完，不再有弹簧尾巴。 */
    const val ClickDurationMillis = 400
}

/**
 * 「点击」触发的侧栏开合动画，开与合共用。
 *
 * 原来走 spring：临界阻尼的指数尾巴让最后 1% 行程（约 12px）磨掉近 300ms，
 * 观感是「滑到一半停住、再突然贴齐」。这里换成有明确终点的贝塞尔缓出，
 * 400ms 到点即止；拖动松手后的 fling settle 仍走 spring，保留手指速度的承接。
 */
private val ConversationPaneClickSpec: AnimationSpec<Float> = tween(
    durationMillis = ConversationPaneMetrics.ClickDurationMillis,
    easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f),
)

@Composable
fun ConversationSidePaneScaffold(
    state: ConversationPaneUiState,
    visible: Boolean,
    backHandlerEnabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
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
    onReorderWorkspaces: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenScheduledTasks: () -> Unit = {},
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    onNewConversation: () -> Unit = {},
    onNewConversationWorkspace: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sceneLifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val existingTopLayer = LocalEtaTopLayer.current
    val fallbackTopLayer = if (existingTopLayer == null) remember { EtaTopLayerState() } else null
    val activeTopLayer = existingTopLayer ?: fallbackTopLayer!!

    CompositionLocalProvider(LocalEtaTopLayer provides activeTopLayer) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // 会话页贴住分界一侧的圆角，跟机身屏幕圆角一致。
        val screenCornerRadius = rememberScreenCornerRadius()
        val edgeShadowColor = conversationPaneEdgeShadowColor()
        val chatScrimAlpha = conversationPaneChatScrimAlpha()
        val edgeShadowWidth = conversationPaneEdgeShadowWidth()
        val chatScrimColor = conversationPaneChatScrimColor()
        val edgeStrokeColor = conversationPaneEdgeStrokeColor()
        val edgeStrokeWidth = conversationPaneEdgeStrokeWidth()
        val paneWidth = minOf(maxWidth * ConversationPaneMetrics.PaneWidthFraction, ConversationPaneMetrics.PaneMaxWidth)
        val paneWidthPx = with(density) { paneWidth.toPx() }
        val anchors = remember(paneWidthPx) {
            DraggableAnchors {
                ConversationPaneAnchor.Closed at 0f
                ConversationPaneAnchor.Open at paneWidthPx
            }
        }
        val paneDragState = remember {
            AnchoredDraggableState(
                initialValue = if (visible) ConversationPaneAnchor.Open else ConversationPaneAnchor.Closed,
                anchors = anchors,
            )
        }
        val settleAnimation = remember {
            spring<Float>(
                dampingRatio = ConversationPaneMetrics.SettleDampingRatio,
                stiffness = ConversationPaneMetrics.SettleStiffness,
                visibilityThreshold = ConversationPaneMetrics.SettleVisibilityThresholdPx,
            )
        }
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = paneDragState,
            positionalThreshold = { distance ->
                distance * ConversationPaneMetrics.SettlePositionThresholdFraction
            },
            animationSpec = settleAnimation,
        )
        val currentVisible by rememberUpdatedState(visible)
        val currentOnOpen by rememberUpdatedState(onOpen)
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val hapticFeedback = LocalHapticFeedback.current
        val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
        val dragInteractions = remember { MutableInteractionSource() }
        val isDragging by dragInteractions.collectIsDraggedAsState()
        LaunchedEffect(dragInteractions) {
            dragInteractions.interactions.collect { interaction ->
                if (interaction is DragInteraction.Stop) {
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
            }
        }
        val isRevealed by remember(paneDragState) {
            derivedStateOf {
                val offset = paneDragState.offset
                !offset.isNaN() && offset > 0.5f
            }
        }
        val panelListState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val closePane: () -> Unit = {
            currentHapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
            focusManager.clearFocus()
            keyboard?.hide()
            if (currentVisible) {
                currentOnDismiss()
            } else {
                scope.launch { paneDragState.animateTo(ConversationPaneAnchor.Closed, settleAnimation) }
            }
        }
        LaunchedEffect(isRevealed) {
            if (isRevealed) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }

        SideEffect {
            paneDragState.updateAnchors(anchors)
        }

        LaunchedEffect(visible, paneWidthPx) {
            if (!visible && isRevealed) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            val target = if (visible) ConversationPaneAnchor.Open else ConversationPaneAnchor.Closed
            if (paneDragState.targetValue != target || paneDragState.settledValue != target) {
                // 点击触发的开与合都是静止起点，统一走固定时长曲线，400ms 到点即止；
                // 拖动松手后的 fling settle 仍走 settleAnimation 的弹簧，保留速度承接。
                paneDragState.animateTo(target, ConversationPaneClickSpec)
            }
        }

        LaunchedEffect(paneDragState) {
            conversationPaneSettledValues(paneDragState) { isDragging }.collectLatest { settledValue ->
                val settledOpen = settledValue == ConversationPaneAnchor.Open
                if (settledOpen != currentVisible) {
                    if (settledOpen) {
                        currentOnOpen()
                    } else {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        currentOnDismiss()
                    }
                }
            }
        }

        // NavDisplay 的退出条目在转场期间仍会保留组合；仅允许已稳定显示的首页
        // 处理侧栏返回，避免它抢先消费二级页面的第一次返回事件。
        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = isRevealed &&
                backHandlerEnabled &&
                sceneLifecycleState == Lifecycle.State.RESUMED,
            onBackCompleted = closePane,
        )

        Box(
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .background(conversationPaneContainerColor())
                // 手势由共同容器拥有，侧栏和聊天舞台均可拖动；子级先处理滚动与选择。
                .anchoredDraggable(
                    state = paneDragState,
                    reverseDirection = false,
                    orientation = Orientation.Horizontal,
                    enabled = backHandlerEnabled && sceneLifecycleState == Lifecycle.State.RESUMED,
                    flingBehavior = flingBehavior,
                    interactionSource = dragInteractions,
                ),
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            // 两个槽位并排测量和放置，由同一位移推移；不在聊天内容下面叠放侧栏。
            // 侧栏保留组合，避免每次起手重新创建列表和搜索框。
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    ConversationPanePanel(
                        state = state,
                        width = paneWidth,
                        listState = panelListState,
                        isRevealed = isRevealed,
                        onNewConversation = onNewConversation,
                        onNewConversationWorkspace = onNewConversationWorkspace,
                        onSearchChange = onSearchChange,
                        onConversationSelected = onConversationSelected,
                        onConversationTogglePin = onConversationTogglePin,
                        onConversationRename = onConversationRename,
                        onConversationPinnedChange = onConversationPinnedChange,
                        onConversationExport = onConversationExport,
                        onConversationDelete = onConversationDelete,
                        onCreateWorkspace = onCreateWorkspace,
                        onRenameWorkspace = onRenameWorkspace,
                        onDeleteWorkspace = onDeleteWorkspace,
                        onWorkspaceCollapsedChange = onWorkspaceCollapsedChange,
                        onConversationMoveToWorkspace = onConversationMoveToWorkspace,
                        onReorderWorkspaces = onReorderWorkspaces,
                        onOpenSettings = onOpenSettings,
                        onOpenModelProviders = onOpenModelProviders,
                        onOpenTools = onOpenTools,
                        onOpenSkills = onOpenSkills,
                        onOpenScheduledTasks = onOpenScheduledTasks,
                        onOpenCharacters = onOpenCharacters,
                        onOpenPermissions = onOpenPermissions,
                        modifier = Modifier
                            .focusProperties { onEnter = { if (!isRevealed) cancelFocusChange() } }
                            .focusGroup()
                            .then(if (!isRevealed) Modifier.clearAndSetSemantics {} else Modifier),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize()
                            // 侧栏推开后，聊天区是一张固定圆角的卡片：圆角恒等于机身屏幕圆角，
                            // 收起状态下才回到直角。只在这里裁画布，不套 graphicsLayer——
                            // 离屏合成会把内部的模糊与投影裁成硬边。
                            .drawWithCache {
                                // 尺寸、圆角、阴影色都不随拖动变化，只在这里算一次：
                                // 裁剪路径建一次复用，阴影带预渲染成位图。
                                val cornerRadiusPx = screenCornerRadius.toPx()
                                val shadowWidthPx = edgeShadowWidth.toPx()
                                // 左缘两个角走超椭圆，和输入框同一族形状：
                                // 半径仍等于机身圆角，指数 2.25 让「外框到输入框」的间距
                                // 整条轮廓恒等 8dp（正圆弧时斜角处会被吃掉 1.3dp）。
                                val pageClipPath = superellipsePagePath(
                                    size = size,
                                    radius = cornerRadiusPx,
                                    exponent = SuperellipseCorner.PageExponent,
                                )
                                // 分界阴影带：把页面轮廓整体向外膨一个阴影宽度得到外圈，再用
                                // Difference 把页面本身挖掉，剩下的正好是贴着左上圆角、左竖边、
                                // 左下圆角的一条弯带，圆角处自然跟着弧线贴合。
                                val edgeShadowOuterPath = superellipseInflatedPath(
                                    size = size,
                                    radius = cornerRadiusPx,
                                    inflate = shadowWidthPx,
                                    exponent = SuperellipseCorner.PageExponent,
                                )
                                // 直边段用横向渐变，上下圆角用径向渐变（圆心与圆角同心），
                                // 淡出方向始终垂直于轮廓，圆角处才真的跟着弧线走。
                                val edgeShadowStraightBrush = conversationEdgeShadowBrush(
                                    shadowWidthPx = shadowWidthPx,
                                    color = edgeShadowColor,
                                )
                                val edgeShadowTopBrush = conversationEdgeShadowRadialBrush(
                                    center = Offset(cornerRadiusPx, cornerRadiusPx),
                                    cornerRadiusPx = cornerRadiusPx,
                                    shadowWidthPx = shadowWidthPx,
                                    color = edgeShadowColor,
                                )
                                val edgeShadowBottomBrush = conversationEdgeShadowRadialBrush(
                                    center = Offset(cornerRadiusPx, size.height - cornerRadiusPx),
                                    cornerRadiusPx = cornerRadiusPx,
                                    shadowWidthPx = shadowWidthPx,
                                    color = edgeShadowColor,
                                )
                                // 分界描边路径：只描左竖边和上下两个圆角——从左上弧的 (d, r) 起，
                                // 经左竖边到左下弧的 (d, h - d) 止，顶边、底边、右侧都不描，
                                // 免得页面另外三条边多出一圈线。圆角段同样是超椭圆。
                                // 半径取 r - d、圆心不动，等于把轮廓整体内缩 d，整条描边都落在
                                // 页面内侧，不会被裁掉一半而发虚。
                                val edgeStrokeWidthPx = edgeStrokeWidth.toPx()
                                val pageEdgeStrokePath = if (edgeStrokeWidthPx <= 0f) {
                                    null
                                } else {
                                    superellipsePageEdgeStrokePath(
                                        size = size,
                                        radius = cornerRadiusPx,
                                        exponent = SuperellipseCorner.PageExponent,
                                        inset = edgeStrokeWidthPx / 2f,
                                    )
                                }
                                onDrawWithContent chatPage@{
                                    // 只有进度相关的东西逐帧读，缓存里的一律不动，
                                    // 否则拖动每一帧都会重建位图。
                                    val progress =
                                        conversationPaneMotion(paneDragState.offset, paneWidthPx).progress
                                    if (progress <= 0f) {
                                        drawContent()
                                        return@chatPage
                                    }
                                    // 单次渐变填充：外侧 alpha 按 smoothstep 平滑收束到 0，
                                    // 不再叠几十层描边（叠层的极小 alpha 写进 8 位位图会被量化成 0，
                                    // 尾部整段消失，看着就是"没渐变 + 最外侧一刀切"）。
                                    // 宽度 0（暗色临时关阴影）时整块短路，不做多余的裁剪与填充。
                                    if (shadowWidthPx > 0f) clipPath(edgeShadowOuterPath) {
                                        clipPath(pageClipPath, clipOp = ClipOp.Difference) {
                                            val bandLeft = -shadowWidthPx
                                            // 左竖边：横向渐变，垂直于轮廓淡出。
                                            clipRect(
                                                left = bandLeft,
                                                top = cornerRadiusPx,
                                                right = 0f,
                                                bottom = size.height - cornerRadiusPx,
                                            ) {
                                                drawRect(
                                                    brush = edgeShadowStraightBrush,
                                                    topLeft = Offset(bandLeft, cornerRadiusPx),
                                                    size = Size(
                                                        width = shadowWidthPx,
                                                        height = size.height - cornerRadiusPx * 2f,
                                                    ),
                                                )
                                            }
                                            // 左上圆角：径向渐变，等距淡出，弧线才跟着走。
                                            clipRect(
                                                left = bandLeft,
                                                top = 0f,
                                                right = size.width,
                                                bottom = cornerRadiusPx,
                                            ) {
                                                drawRect(
                                                    brush = edgeShadowTopBrush,
                                                    topLeft = Offset(bandLeft, 0f),
                                                    size = Size(
                                                        width = size.width - bandLeft,
                                                        height = cornerRadiusPx,
                                                    ),
                                                )
                                            }
                                            // 左下圆角：同上。
                                            clipRect(
                                                left = bandLeft,
                                                top = size.height - cornerRadiusPx,
                                                right = size.width,
                                                bottom = size.height,
                                            ) {
                                                drawRect(
                                                    brush = edgeShadowBottomBrush,
                                                    topLeft = Offset(bandLeft, size.height - cornerRadiusPx),
                                                    size = Size(
                                                        width = size.width - bandLeft,
                                                        height = cornerRadiusPx,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    clipPath(pageClipPath) {
                                        this@chatPage.drawContent()
                                        // 遮罩机制沿用上游：拉开进度 0 → 1 时叠 0% → 满值，只换底色。
                                        // 浅色压白 50%，深色不再叠遮罩（只靠分界线阴影分层），
                                        // 为 0 时直接跳过这次全页 mixed 填充。
                                        val scrimAlpha = chatScrimAlpha * progress
                                        if (scrimAlpha > 0f) {
                                            drawRect(color = chatScrimColor, alpha = scrimAlpha)
                                        }
                                    }
                                    // 描边放在最后：遮罩和页面内容都画完再压上这条线，深度才准。
                                    if (pageEdgeStrokePath != null) {
                                        drawPath(
                                            path = pageEdgeStrokePath,
                                            color = edgeStrokeColor,
                                            style = Stroke(width = edgeStrokeWidthPx),
                                        )
                                    }
                                }
                            }
                            .background(MiuixTheme.colorScheme.background),
                    ) {
                        Box(
                            modifier = Modifier
                                .focusProperties {
                                    onEnter = { if (isRevealed) cancelFocusChange() }
                                }
                                .focusGroup()
                                .then(if (isRevealed) Modifier.clearAndSetSemantics {} else Modifier),
                        ) {
                            content()
                        }
                        if (isRevealed) {
                            Box(
                                modifier = Modifier.fillMaxSize().clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClickLabel = stringResource(R.string.action_close),
                                    onClick = closePane,
                                ),
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val panel = measurables[0].measure(
                    Constraints.fixed(paneWidthPx.fastRoundToInt(), constraints.maxHeight),
                )
                val page = measurables[1].measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 物理左侧是会话区；不随 RTL 改变两个槽位的关系。
                    val motion = conversationPaneMotion(paneDragState.offset, paneWidthPx)
                    val offset = motion.foregroundOffset.fastRoundToInt()
                    panel.placeWithLayer(offset - panel.width, 0)
                    page.placeWithLayer(offset, 0)
                }
            }
        }
        if (fallbackTopLayer != null) {
            EtaTopLayerHost(fallbackTopLayer)
        }
    }
    }
}

/**
 * 分界阴影的横向渐变：贴轮廓处（x = 0）是满值，向外到 -shadowWidth 收干净。
 *
 * alpha 走 smoothstep，两端斜率都为 0：最外侧不会硬切出一道边，贴轮廓处也不会突然变亮。
 */
private fun conversationEdgeShadowBrush(shadowWidthPx: Float, color: Color): Brush {
    if (shadowWidthPx <= 0f || color.alpha <= 0f) {
        return SolidColor(Color.Transparent)
    }
    val stops = ConversationPaneMetrics.EdgeShadowGradientStops
    val outerAlpha = color.alpha
    // index 0 对应 startX（最外端，alpha 必须是 0），index stops 对应 endX（贴轮廓，满值）。
    val colors = List(stops + 1) { index ->
        val t = index.toFloat() / stops
        color.copy(alpha = outerAlpha * (t * t * (3f - 2f * t)))
    }
    return Brush.horizontalGradient(
        colors = colors,
        startX = -shadowWidthPx,
        endX = 0f,
    )
}

/**
 * 圆角处的径向渐变：圆心与屏幕圆角同心，从圆角半径处（贴轮廓，满值）向外
 * 扩散 [shadowWidthPx]，alpha 同样按 smoothstep 收束到 0。
 */
private fun conversationEdgeShadowRadialBrush(
    center: Offset,
    cornerRadiusPx: Float,
    shadowWidthPx: Float,
    color: Color,
): Brush {
    if (cornerRadiusPx <= 0f || shadowWidthPx <= 0f || color.alpha <= 0f) {
        return SolidColor(Color.Transparent)
    }
    val stops = ConversationPaneMetrics.EdgeShadowGradientStops
    val outerAlpha = color.alpha
    val outerRadius = cornerRadiusPx + shadowWidthPx
    val colorStops = Array(stops + 1) { index ->
        val u = index.toFloat() / stops
        val t = 1f - u
        val position = (cornerRadiusPx + u * shadowWidthPx) / outerRadius
        position to color.copy(alpha = outerAlpha * (t * t * (3f - 2f * t)))
    }
    return Brush.radialGradient(
        colorStops = colorStops,
        center = center,
        radius = outerRadius,
    )
}
