package com.itos.coui.navigation

import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import com.itos.coui.theme.CouiIcon as Icon
import com.itos.coui.theme.CouiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFabFeedback
import com.itos.coui.effect.couiFloatingSurfaces
import com.itos.coui.effect.couiNavigationFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.CouiPressMotion
import com.itos.coui.motion.CouiTouchMotion
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiScaleVisibility
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent
import com.itos.coui.shape.CouiCapsuleShape

/** [selectedIcon] 用于选中和拖动预选，省略时两种状态共用 [icon]。 */
data class CouiNavigationItem(val title: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * 只负责底栏装配，页面状态和导航提交仍由宿主持有。
 * 附件点击回调为空时只显示图标并保留拖拽，不创建点击或 Button 语义。
 * 省略 [renderer] 时创建独立的三主体绘制器。宿主可先创建绘制器并处理失败，再显式传入。
 * 中间胶囊使用 [CouiCapsuleShape]，[material] 只作用于该胶囊。左右附件分别由 [leftAction] 和 [rightAction] 配置。
 * [lightColor] 和 [themeTint] 默认沿用材质中的值，显式传入时优先。透明 [themeTint] 关闭额外染色。
 * 附件配置为空时直接移除该附件。保留配置并将其 visible 设为 false 时播放收起动画。
 */
@RequiresApi(34)
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "底栏需要显式 backdrop 或 COUI Scaffold 的独立栏位"
    },
    renderer: CouiFloatingRenderer = remember { CouiFloatingRenderer() },
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    leftAction: CouiNavigationBarAction? = null,
    rightAction: CouiNavigationBarAction? = null,
    dark: Boolean = CouiTheme.isDark,
    material: CouiFloatingMaterial = CouiNavigationBarDefaults.material(dark),
    lightColor: Color = material.lightColor,
    themeTint: Color = material.themeTint,
    selectedColor: Color = (if (dark) Color.White else Color.Black).copy(alpha = 230f / 255f),
    unselectedColor: Color = (if (dark) Color.White else Color.Black).copy(alpha = 184f / 255f),
    onSelection: (Int) -> Unit,
) = NavigationBar(
    modifier = modifier, backdrop = backdrop, renderer = renderer, items = items, selectedIndex = selectedIndex,
    visible = true, leftAction = leftAction, rightAction = rightAction, dark = dark, material = material,
    lightColor = lightColor, themeTint = themeTint, selectedColor = selectedColor, unselectedColor = unselectedColor,
    onSelection = onSelection,
)

/** [visible] 控制整栏在槽位内缩放显隐。收起时立即停用交互，槽位继续提供原有布局高度。 */
@RequiresApi(34)
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "底栏需要显式 backdrop 或 COUI Scaffold 的独立栏位"
    },
    renderer: CouiFloatingRenderer = remember { CouiFloatingRenderer() },
    items: List<CouiNavigationItem>,
    selectedIndex: Int,
    visible: Boolean,
    leftAction: CouiNavigationBarAction? = null,
    rightAction: CouiNavigationBarAction? = null,
    dark: Boolean = CouiTheme.isDark,
    material: CouiFloatingMaterial = CouiNavigationBarDefaults.material(dark),
    lightColor: Color = material.lightColor,
    themeTint: Color = material.themeTint,
    selectedColor: Color = (if (dark) Color.White else Color.Black).copy(alpha = 230f / 255f),
    unselectedColor: Color = (if (dark) Color.White else Color.Black).copy(alpha = 184f / 255f),
    onSelection: (Int) -> Unit,
) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftVisible = leftAction?.visible == true
    val rightVisible = rightAction?.visible == true
    val visibility = rememberCouiScaleVisibility(visible)
    val leftExpansion = remember(density, layoutDirection, leftAction != null) { CouiFabExpansion(leftVisible) }
    val rightExpansion = remember(density, layoutDirection, rightAction != null) { CouiFabExpansion(rightVisible) }
    // 附件融合完成或整栏开始收起时就停用交互，避免退场中的按钮拦截点击。
    val leftInteractive = visible && leftExpansion.present && leftExpansion.interactive
    val rightInteractive = visible && rightExpansion.present && rightExpansion.interactive
    DisposableEffect(leftExpansion) {
        onDispose { leftExpansion.dispose() }
    }
    DisposableEffect(rightExpansion) {
        onDispose { rightExpansion.dispose() }
    }
    val contour = LocalCouiContourGlowEnabled.current
    val defaultActionStyle = CouiNavigationBarDefaults.transparentActionStyle(dark)
    val leftStyle = leftAction?.style ?: defaultActionStyle
    val rightStyle = rightAction?.style ?: defaultActionStyle
    val extension = with(density) { 4.dp.roundToPx() }
    val materials = remember(material, lightColor, themeTint, leftStyle.material, rightStyle.material, extension) {
        listOf(
            leftStyle.material.copy(index = 2, extension = extension),
            material.copy(index = 1, extension = extension, lightColor = lightColor, themeTint = themeTint),
            rightStyle.material.copy(index = 2, extension = extension),
        )
    }
    val capsuleShadow = remember(density, lightColor, dark, contour) {
        CouiNavigationBarDefaults.transparentShadow(dark, density.density, contour, lightColor, capsule = true)
    }
    val shadows = remember(leftStyle.shadow, capsuleShadow, rightStyle.shadow) {
        listOf(leftStyle.shadow, capsuleShadow, rightStyle.shadow)
    }
    val shapes = remember(leftStyle.shape, rightStyle.shape) {
        listOf(leftStyle.shape, CouiCapsuleShape, rightStyle.shape)
    }
    val contentLayers = List(3) { rememberGraphicsLayer() }
    val menuMotion = rememberCouiTouchMotion(visible)
    val leftMotion = rememberCouiTouchMotion(visible && leftExpansion.touchMotionEnabled)
    val rightMotion = rememberCouiTouchMotion(visible && rightExpansion.touchMotionEnabled)
    val menuPress = rememberCouiPressMotion()
    val leftPress = rememberCouiPressMotion()
    val rightPress = rememberCouiPressMotion()
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var menuBounds by remember { mutableStateOf(Rect.Zero) }
    var leftBounds by remember { mutableStateOf(Rect.Zero) }
    var rightBounds by remember { mutableStateOf(Rect.Zero) }
    fun menuSurface(): CouiFloatingSurface = surface(menuBounds, menuMotion, menuPress)
    fun leftSurface(): CouiFloatingSurface = leftExpansion.surface(
        surface(
            leftBounds, leftMotion, leftPress,
            cornerWeight = leftStyle.shape.cornerWeight,
            cornerRadius = with(density) { leftStyle.shape.radius.toPx() },
            touchMotionEnabled = leftExpansion.touchMotionEnabled,
        ),
    )
    fun rightSurface(): CouiFloatingSurface = rightExpansion.surface(
        surface(
            rightBounds, rightMotion, rightPress,
            cornerWeight = rightStyle.shape.cornerWeight,
            cornerRadius = with(density) { rightStyle.shape.radius.toPx() },
            touchMotionEnabled = rightExpansion.touchMotionEnabled,
        ),
    )
    LaunchedEffect(leftExpansion, leftVisible, menuBounds, leftBounds) {
        leftExpansion.updateTarget(leftVisible, menuBounds, leftBounds,
            collapseToLeft = layoutDirection == LayoutDirection.Ltr)
    }
    LaunchedEffect(rightExpansion, rightVisible, menuBounds, rightBounds) {
        rightExpansion.updateTarget(rightVisible, menuBounds, rightBounds,
            collapseToLeft = layoutDirection == LayoutDirection.Rtl)
    }
    LaunchedEffect(leftExpansion, menuMotion, leftMotion, menuPress, leftPress) {
        snapshotFlow { couiFabOverlapRatio(leftSurface().couiDrawingBounds(), menuSurface().couiDrawingBounds()) }
            .collect { leftExpansion.finishCollapseIfOverlapped(it) }
    }
    LaunchedEffect(rightExpansion, menuMotion, rightMotion, menuPress, rightPress) {
        snapshotFlow { couiFabOverlapRatio(rightSurface().couiDrawingBounds(), menuSurface().couiDrawingBounds()) }
            .collect { rightExpansion.finishCollapseIfOverlapped(it) }
    }
    var previewSelection by remember { mutableIntStateOf(selectedIndex) }
    val currentSelection by rememberUpdatedState(selectedIndex)
    val onSelect by rememberUpdatedState(onSelection)
    LaunchedEffect(selectedIndex) { previewSelection = selectedIndex }
    val maximumMenuWidth = 24.dp + 70.dp * items.size + 8.dp * (items.size - 1)
    val bottomMargin = if (WindowInsets.tappableElement.getBottom(density) > 0) 0.dp else 8.dp
    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomMargin),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = maximumMenuWidth + 136.dp).fillMaxWidth()) {
            val menuWidth = (maxWidth - 136.dp).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .onGloballyPositioned { rowCoordinates = it }
                    .couiFloatingSurfaces(backdrop, renderer, materials, shadows, shapes, contentLayers, contour) {
                        val blur = visibility.foregroundBlurEffect
                        contentLayers.forEach { it.renderEffect = blur }
                        val left = leftSurface()
                        val menu = menuSurface()
                        val right = rightSurface()
                        if (!visibility.isPresent) {
                            listOf(
                                left.copy(alpha = 0f, visible = false),
                                menu.copy(alpha = 0f, visible = false),
                                right.copy(alpha = 0f, visible = false),
                            )
                        } else {
                            val barCenter = menuBounds.center
                            val barScale = visibility.scale.value.coerceAtLeast(0.001f)
                            val barAlpha = visibility.alpha.value.coerceIn(0f, 1f)
                            fun CouiFloatingSurface.applyBarVisibility(): CouiFloatingSurface {
                                if (!this.visible) return this
                                val offset = (bounds.center - barCenter) * (barScale - 1f)
                                return copy(
                                    bounds = bounds.translate(offset),
                                    scaleX = scaleX * barScale,
                                    scaleY = scaleY * barScale,
                                    alpha = alpha * barAlpha,
                                )
                            }
                            listOf(
                                left.copy(alpha = left.alpha * couiFabOverlapAlpha(
                                    couiFabOverlapRatio(left.couiDrawingBounds(), menu.couiDrawingBounds()),
                                )).applyBarVisibility(),
                                menu.applyBarVisibility(),
                                right.copy(alpha = right.alpha * couiFabOverlapAlpha(
                                    couiFabOverlapRatio(right.couiDrawingBounds(), menu.couiDrawingBounds()),
                                )).applyBarVisibility(),
                            )
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationActionNode(
                    action = leftAction,
                    style = leftStyle,
                    motion = leftMotion,
                    press = leftPress,
                    dark = dark,
                    contentLayer = contentLayers[0],
                    interactive = leftInteractive,
                    present = visibility.isPresent,
                    expansion = leftExpansion,
                    onBounds = { node -> rowCoordinates?.let { leftBounds = it.localBoundingBoxOf(node, false) } },
                )
                FloatingNode(
                    width = menuWidth, motion = menuMotion, press = menuPress, dark = dark,
                    shape = CouiCapsuleShape,
                    lightColor = lightColor,
                    contentLayer = contentLayers[1],
                    interactive = visible,
                    present = visibility.isPresent,
                    onBounds = { node -> rowCoordinates?.let { menuBounds = it.localBoundingBoxOf(node, false) } },
                ) {
                    val itemBounds = remember(items.size) { mutableMapOf<Int, Rect>() }
                    var menuCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    Row(
                        Modifier.fillMaxSize()
                            .then(if (visible) Modifier.selectableGroup() else Modifier)
                            .onGloballyPositioned { menuCoordinates = it }
                            .then(if (visible) Modifier.pointerInput(items.size) {
                                var active: PointerId? = null
                                var pending: Int? = null
                                var downItem: Int? = null
                                fun hit(position: Offset): Int? = itemBounds.entries.firstOrNull { it.value.contains(position) }?.key
                                fun finish(cancelled: Boolean, position: Offset?) {
                                    val target = couiNavigationReleaseTarget(
                                        previewSelection, pending, downItem, position?.let(::hit), cancelled,
                                    )
                                    if (target != null) {
                                        previewSelection = target
                                        onSelect(target)
                                    }
                                    active = null
                                    pending = null
                                    downItem = null
                                }
                                try {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.isCouiTouchCancelled()) {
                                                finish(true, null)
                                                continue
                                            }
                                            val tracked = event.changes.firstOrNull { it.id == active }
                                            val pointer = tracked?.takeIf { it.pressed }
                                                ?: event.changes.firstOrNull { it.pressed }
                                                ?: tracked ?: continue
                                            if (active == null) {
                                                downItem = hit(pointer.position)
                                            } else if (pointer.pressed && pointer.position != pointer.previousPosition) {
                                                hit(pointer.position)?.let { index ->
                                                    if (previewSelection != index) {
                                                        previewSelection = index
                                                        pending = index
                                                    }
                                                }
                                            }
                                            active = pointer.id
                                            if (!pointer.pressed) finish(false, pointer.position)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } finally {
                                    // 节点移除不等于原厂 ACTION_CANCEL, 不能在离开页面时触发导航。
                                    previewSelection = currentSelection
                                }
                            } else Modifier).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items.forEachIndexed { index, item ->
                            val isSelected = previewSelection == index
                            val itemContentColor = if (isSelected) selectedColor else unselectedColor
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .onGloballyPositioned { node -> menuCoordinates?.let { itemBounds[index] = it.localBoundingBoxOf(node, false) } }
                                    .then(
                                        if (visible) {
                                            Modifier.semantics(mergeDescendants = true) {
                                                role = Role.Tab
                                                selected = isSelected
                                                onClick {
                                                    if (index != currentSelection) onSelect(index)
                                                    true
                                                }
                                            }.onKeyEvent {
                                                if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                                                    if (index != currentSelection) onSelect(index)
                                                    true
                                                } else false
                                            }.focusable(interactionSource = interactionSource)
                                        } else {
                                            Modifier.clearAndSetSemantics {}
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    Modifier.fillMaxSize().couiNavigationFeedback(dark, interactionSource).padding(top = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Icon(modifier = Modifier.size(24.dp), imageVector = if (isSelected) item.selectedIcon else item.icon,
                                        contentDescription = null, tint = itemContentColor)
                                    Spacer(Modifier.height(2.dp))
                                    ProvideCouiContent {
                                        Text(text = item.title, color = itemContentColor, fontSize = 10.sp,
                                            lineHeight = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                NavigationActionNode(
                    action = rightAction,
                    style = rightStyle,
                    motion = rightMotion,
                    press = rightPress,
                    dark = dark,
                    contentLayer = contentLayers[2],
                    interactive = rightInteractive,
                    present = visibility.isPresent,
                    expansion = rightExpansion,
                    onBounds = { node -> rowCoordinates?.let { rightBounds = it.localBoundingBoxOf(node, false) } },
                )
            }
        }
    }
}

@Composable
private fun NavigationActionNode(
    action: CouiNavigationBarAction?,
    style: CouiNavigationBarActionStyle,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
    dark: Boolean,
    contentLayer: GraphicsLayer,
    interactive: Boolean,
    present: Boolean,
    expansion: CouiFabExpansion,
    onBounds: (LayoutCoordinates) -> Unit,
) {
    FloatingNode(
        width = 56.dp, motion = motion, press = press, dark = dark,
        shape = style.shape, contentLayer = contentLayer, spotlight = style.spotlight,
        lightColor = style.material.lightColor, interactive = interactive,
        present = present, expansion = expansion, onBounds = onBounds,
    ) {
        if (action != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val onClick = action.onClick
            val click = if (interactive && onClick != null) {
                val feedback = if (style.hoverFeedback) Modifier.couiFabFeedback(dark, interactionSource)
                    else Modifier
                feedback.clickable(
                    interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick,
                )
            } else Modifier
            Icon(
                imageVector = action.icon,
                contentDescription = if (interactive) action.contentDescription else null,
                modifier = Modifier.fillMaxSize().then(click).padding(16.dp),
                tint = style.contentColor,
            )
        }
    }
}

@Composable
private fun FloatingNode(
    width: Dp,
    motion: CouiTouchMotion,
    press: CouiPressMotion,
    dark: Boolean,
    shape: Shape,
    contentLayer: GraphicsLayer,
    spotlight: SpotlightPreset = SpotlightPreset.OpaqueSmall2,
    lightColor: Color = Color.White,
    interactive: Boolean = true,
    present: Boolean = true,
    expansion: CouiFabExpansion? = null,
    onBounds: (LayoutCoordinates) -> Unit,
    content: @Composable () -> Unit,
) {
    val touchMotionEnabled = interactive && (expansion?.touchMotionEnabled ?: true)
    LaunchedEffect(press, touchMotionEnabled) {
        if (!touchMotionEnabled) press.release()
    }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = spotlight,
        dark = dark,
        color = lightColor,
    )
    Box(Modifier.width(width).height(56.dp).onGloballyPositioned(onBounds)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = motion.translationX.value + (expansion?.offsetX() ?: 0f)
                translationY = motion.translationY.value
                scaleX = (if (touchMotionEnabled) motion.scaleX.value + press.scale.value - 1f else 1f) *
                    (expansion?.snappedScale(size.width) ?: 1f)
                scaleY = (if (touchMotionEnabled) motion.scaleY.value + press.scale.value - 1f else 1f) *
                    (expansion?.snappedScale(size.height) ?: 1f)
            }.then(
                // 禁用 FAB 时移除输入节点; 空的 pointerInput 仍会挡住下方导航项。
                if (interactive) Modifier.couiTouchMotion(motion, touchMotionEnabled,
                    cancelOnConsumedMove = false, consumeDrag = true)
                    .couiPressMotion(press, touchMotionEnabled).then(lightInput)
                else Modifier.clearAndSetSemantics {},
            ).drawWithContent {
                if (present) contentLayer.record { this@drawWithContent.drawContent() }
            }.clip(shape)
                .drawBehind { drawLight?.invoke(this) },
        ) { content() }
    }
}

private fun surface(
    bounds: Rect, motion: CouiTouchMotion, press: CouiPressMotion,
    cornerWeight: Float = 1f,
    cornerRadius: Float? = null,
    touchMotionEnabled: Boolean = true,
): CouiFloatingSurface = CouiFloatingSurface(
    bounds.translate(Offset(motion.translationX.value, motion.translationY.value)),
    if (touchMotionEnabled) motion.scaleX.value + press.scale.value - 1f else 1f,
    if (touchMotionEnabled) motion.scaleY.value + press.scale.value - 1f else 1f, cornerWeight,
    cornerRadius = cornerRadius,
)
