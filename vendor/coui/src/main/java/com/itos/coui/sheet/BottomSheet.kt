package com.itos.coui.sheet

import android.graphics.drawable.ColorDrawable
import android.graphics.BlendMode
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import android.view.WindowInsetsController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.doOnPreDraw
import com.itos.coui.R
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogColors
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.effect.CouiMaterialBlendMode
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.menu.LocalCouiMenuBackdrop
import com.itos.coui.menu.LocalCouiMenuVisibleBounds
import com.itos.coui.menu.CouiMenuVisibleBounds
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.input.isCouiTouchCancelled
import com.itos.coui.motion.CouiVelocityTracker
import com.itos.coui.scroll.LocalCouiClipUpwardOverscroll
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.scroll.couiVerticalScrollInput
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiDarkTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

object CouiSheetDefaults {
    // Clock 普通手机的 NEW_G2 分支，Oplus API > 37。
    val cornerRadius = 45.dp
    val maxWidth = 540.dp
    val topSpacing = 40.dp
    val handleHeight = 16.dp
    val bottomPadding = 60.dp
    internal val maxHeight = 2000.dp
    internal const val edgeWeight = 3f
    internal fun innerGlowAlpha(dark: Boolean) = if (dark) .1f else .4f
    internal fun lineAlpha(dark: Boolean) = if (dark) .15f else .8f
    internal fun lineWidth(dark: Boolean) = if (dark) 2.dp else 4.dp
    val toolbarTopSpacing = 11.dp
    val toolbarHeight = 50.dp
    val toolbarHorizontalPadding = 16.dp
    internal val toolbarTitleGap = 4.dp
    internal val toolbarTitleVerticalPadding = 12.dp
    internal val toolbarTitleMinHeight = 26.dp
    fun colors(dark: Boolean): CouiDialogColors = CouiDialogDefaults.colors(dark).copy(
        surface = Color(if (dark) 0xFF383838 else 0xFFFFFFFF))

    /** Clock 17 的 bottomdialog 资源。blurRadius 是 Fast Kawase 引擎等级。 */
    fun material(dark: Boolean) = CouiDialogMaterial(
        mix = Color(if (dark) 0xA4515151 else 0xDCC8C8C8),
        blend = Color(if (dark) 0xB30F0F0F else 0x376E6E6E),
        blurRadius = 400,
        blendMode = if (dark) CouiMaterialBlendMode.Overlay else CouiMaterialBlendMode.ColorDodge,
    )

    /** 按钮在面板底图上使用半透明模糊材质，轮廓沿用 CAPSULE_8 / CIRCLE_4。 */
    fun toolbarMaterial(dark: Boolean, capsule: Boolean) = CouiFloatingMaterial(
        firstColor = Color(if (dark) 0xBF333333 else 0x99F0F0F0),
        firstBlendMode = BlendMode.SRC_OVER,
        secondColor = Color.Transparent,
        secondBlendMode = BlendMode.SRC_OVER,
        strokeType = if (capsule) 1 else 2,
        edgeAlpha = if (dark) .15f else .8f, edgeWidthDp = 1.8f,
        innerGlowAlpha = if (dark) .01f else .1f,
        innerGlowScale = 1f,
    )
}

/**
 * COUI 底部面板。修改 visible.value 控制显隐，持续调用本组件以完成退场。
 * sheetHeight 为包含 handle、内容及底部安全区的可见目标高度；null 按内容测量。
 * 普通手机窗口顶部使用状态栏高度，无状态栏时保留 40dp；屏外绘制余量不占内容高度。
 * leadingButton、titleContent、trailingButton 位于正文上方。正文默认由面板负责滚动、回弹和上沿裁剪；
 * 正文自带纵向滚动容器时传 scrollableContent = false。
 * draggable 允许从留白处下拉关闭，nestedScrollEnabled 也允许正文到顶后的下拉关闭。
 * fullPageOverscroll 开启时，顶部与正文共享回弹，留白也可发起回弹。内置拖动条保持在回弹区域之外。
 * 关闭请求默认将 visible.value 设为 false，可通过 onDismissRequest 接管。
 * 确认、草稿、页面导航和二级面板属于宿主。
 * API 33 及以上且有宿主窗口源时，默认模糊背景并应用材质混色，material = null 可关闭。
 * themeTint 为模糊材质染色基准色（强度由库统一控制，默认为当前主题的染色配置，透明色关闭染色，
 * 顶部操作按钮采样面板底图且不二次染色）。
 * 窗口源须排除当前面板和遮罩。
 */
@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    visible: MutableState<Boolean>,
    onDismissRequest: (() -> Unit)? = null,
    dismissDescription: String = stringResource(R.string.coui_sheet_dismiss),
    dark: Boolean = CouiTheme.isDark,
    sheetHeight: Dp? = null,
    draggable: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    backdrop: CouiBackdrop? = null,
    material: CouiDialogMaterial? = if (
        CouiDialogDefaults.isMaterialSupported() && (backdrop ?: LocalCouiMenuBackdrop.current) != null
    ) CouiSheetDefaults.material(dark) else null,
    showDragHandle: Boolean = false,
    nestedScrollEnabled: Boolean = false,
    fullPageOverscroll: Boolean = false,
    scrollableContent: Boolean = true,
    leadingButton: CouiSheetButton? = null,
    titleContent: @Composable (() -> Unit)? = null,
    trailingButton: CouiSheetButton? = null,
    themeTint: Color = CouiTheme.materialTintFor(dark),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isVisible = visible.value
    require(sheetHeight == null || (sheetHeight.value.isFinite() && sheetHeight > 0.dp)) { "面板高度必须为有限正值" }
    val windowBackdrop = backdrop ?: LocalCouiMenuBackdrop.current
    if (material != null) {
        check(Build.VERSION.SDK_INT >= 33) { "面板材质要求 Android 13 或更高版本" }
        checkNotNull(windowBackdrop) { "面板材质需要不包含面板与遮罩的宿主窗口源" }
    }
    var mounted by remember { mutableStateOf(isVisible) }
    if (!isVisible && !mounted) return
    val requestDismiss by rememberUpdatedState<() -> Boolean> {
        if (!visible.value) false else {
            if (onDismissRequest != null) onDismissRequest() else visible.value = false
            true
        }
    }
    val motion = remember { CouiSheetMotion() }
    SideEffect {
        if (isVisible) mounted = true
        motion.onHidden = { if (!visible.value) mounted = false }
    }
    DisposableEffect(motion) { onDispose { motion.dispose() } }
    Dialog(onDismissRequest = { requestDismiss() }, properties = DialogProperties(
        dismissOnBackPress = isVisible, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        val window = checkNotNull((view.parent as? DialogWindowProvider)?.window)
        DisposableEffect(window) {
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setWindowAnimations(0)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            window.isNavigationBarContrastEnforced = false
            onDispose { }
        }
        SideEffect {
            val barAppearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else barAppearance, barAppearance)
        }
        val density = LocalDensity.current
        val surfaceBackdrop = rememberCouiBackdrop(parent = windowBackdrop)
        var windowHeight by remember { mutableIntStateOf(-1) }
        var parentHeight by remember { mutableIntStateOf(-1) }
        var panelHeight by remember { mutableIntStateOf(-1) }
        var windowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
        var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
        var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
        var geometryReady by remember(view) { mutableStateOf(false) }
        DisposableEffect(view) {
            // 普通面板在窗口首次布局结束后启动入场。
            val listener = view.doOnPreDraw { geometryReady = true }
            onDispose { listener.removeListener() }
        }
        // 原厂 translateUpdate 在开启背景模糊且存在遮罩时对位移取整，避免边缘亚像素错位。
        val roundTranslation = material != null && colors.dimAmount > 0f
        val sheetTranslation = remember(motion, roundTranslation) {
            {
                if (motion.height == 0f) windowHeight.coerceAtLeast(0).toFloat()
                else if (roundTranslation) motion.offset.roundToInt().toFloat()
                else motion.offset
            }
        }
        val menuBounds = remember(view, density, motion, sheetTranslation) {
            CouiMenuVisibleBounds(view) {
                val panel = panelCoordinates?.takeIf { it.isAttached }
                val viewport = viewportCoordinates?.takeIf { it.isAttached }?.boundsInWindow()
                if (panel == null || viewport == null) null else if (motion.height == 0f) {
                    // 几何就绪前表面在窗口之外，菜单可见区也为空。
                    val left = viewport.left.roundToInt()
                    val bottom = viewport.bottom.roundToInt()
                    IntRect(left, bottom, left, bottom)
                } else {
                    // 完整 frame 先随面板位移，再裁到真实父视口；不能先裁静态 H 再平移。
                    val width = panel.size.width.toFloat()
                    val top = sheetTranslation()
                    val bottom = top + panel.size.height + with(density) { CouiSheetDefaults.bottomPadding.roundToPx() }
                    val topLeft = panel.localToWindow(Offset(0f, top))
                    val topRight = panel.localToWindow(Offset(width, top))
                    val bottomLeft = panel.localToWindow(Offset(0f, bottom))
                    val bottomRight = panel.localToWindow(Offset(width, bottom))
                    val left = minOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
                        .coerceIn(viewport.left, viewport.right)
                    val right = maxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
                        .coerceIn(left, viewport.right)
                    val visibleTop = minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
                        .coerceIn(viewport.top, viewport.bottom)
                    val visibleBottom = maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
                        .coerceIn(visibleTop, viewport.bottom)
                    val inset = with(density) { 16.dp.toPx() }
                        .coerceAtMost((right - left - 1f).coerceAtLeast(0f) / 2f)
                    IntRect((left + inset).roundToInt(), visibleTop.roundToInt(),
                        (right - inset).roundToInt(), visibleBottom.roundToInt())
                }
            }
        }
        val statusInset = WindowInsets.statusBars.getTop(density)
        val topSpacing = if (LocalConfiguration.current.smallestScreenWidthDp < 600 && statusInset > 0) {
            with(density) { statusInset.toDp() }
        } else CouiSheetDefaults.topSpacing
        val gestureNavigation = WindowInsets.tappableElement.getBottom(density) == 0
        val peek = with(density) { (if (gestureNavigation) 82.dp else 110.dp).toPx() }
        val ime = WindowInsets.ime
        val parentInsets = if (gestureNavigation) ime.only(WindowInsetsSides.Bottom)
            else ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
        val topSpacingPx = with(density) { topSpacing.toPx() }
        LaunchedEffect(isVisible, density, ime, topSpacingPx, peek) {
            snapshotFlow {
                if (!geometryReady || windowHeight < 0 || parentHeight < 0 || panelHeight < 0) return@snapshotFlow null
                val imeHeight = if (ime.getBottom(density) > 0) {
                    (windowHeight - topSpacingPx - parentHeight).coerceAtLeast(0f)
                } else 0f
                SheetMeasurement(windowHeight, parentHeight, panelHeight, imeHeight)
            }.filterNotNull().collect { measured ->
                motion.update(
                    visible = isVisible,
                    height = measured.panelHeight.toFloat(),
                    parentHeight = measured.parentHeight.toFloat(),
                    windowHeight = measured.windowHeight.toFloat(),
                    imeHeight = measured.imeHeight,
                    peek = peek,
                )
            }
        }
        val heightModifier = remember(view, sheetHeight) {
            val visibleFrame = Rect()
            Modifier.layout { measurable, constraints ->
                // 只有父容器可能超过面板上限时才查询窗口。上限仍包含屏外余量。
                val frameLimit = CouiSheetDefaults.maxHeight.toPx().toInt()
                val panelLimit = frameLimit - CouiSheetDefaults.bottomPadding.roundToPx()
                val maxHeight = if (constraints.maxHeight > panelLimit) {
                    view.getWindowVisibleDisplayFrame(visibleFrame)
                    if (visibleFrame.height() > frameLimit) panelLimit.coerceAtLeast(constraints.minHeight)
                    else constraints.maxHeight
                } else constraints.maxHeight
                val height = sheetHeight?.roundToPx()?.coerceIn(constraints.minHeight, maxHeight)
                val placeable = measurable.measure(constraints.copy(
                    minHeight = height ?: constraints.minHeight,
                    maxHeight = height ?: maxHeight,
                ))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
        }
        val interactive = isVisible && motion.entered
        val canDrag = draggable && interactive
        val canNestedDrag = canDrag && nestedScrollEnabled && !fullPageOverscroll
        val canNestedDragNow by rememberUpdatedState(canNestedDrag)
        val touch = remember { SheetTouchSession() }
        LaunchedEffect(canDrag) { if (!canDrag) motion.cancelDrag() }
        LaunchedEffect(canNestedDrag) { if (!canNestedDrag && motion.hasNestedDrag) motion.cancelDrag() }
        val nested = remember(motion) { object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (canNestedDragNow && touch.canDrag && source == NestedScrollSource.UserInput && available.y < 0f &&
                    (motion.offset > 0f || motion.hasNestedDrag))
                    Offset(0f, motion.drag(available.y, nested = true)) else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (canNestedDragNow && touch.canDrag && source == NestedScrollSource.UserInput && available.y > 0f)
                    Offset(0f, motion.drag(available.y, nested = true)) else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!canNestedDragNow || touch.firstPointer != null || !motion.hasNestedDrag) return Velocity.Zero
                val consume = motion.offset > 0f
                if (motion.releaseNested(available.y)) requestDismiss()
                return if (consume) Velocity(0f, available.y) else Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (canNestedDragNow && touch.firstPointer == null && motion.releaseNested(available.y)) requestDismiss()
                return Velocity.Zero
            }
        } }
        // 自身表面仍采样外层窗口；新窗口源只供内容里的后续 Popup/Dialog 使用。
        CompositionLocalProvider(LocalCouiMenuBackdrop provides windowBackdrop) {
            ProvideCouiMenuBackdrop(isolateContent = false) { source ->
                Box(Modifier.fillMaxSize().onSizeChanged { windowHeight = it.height }
                    .onGloballyPositioned { windowCoordinates = it }.then(source)) {
                    val outside = remember { MutableInteractionSource() }
                    val dismissOnScrim = if (interactive && dismissOnClickOutside) {
                        Modifier.clickable(outside, null, onClickLabel = dismissDescription) { requestDismiss() }
                            .semantics { contentDescription = dismissDescription }
                    } else Modifier
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        // 遮罩只有一个黑色矩形，直接调制 alpha 可省去整窗离屏合成。
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = motion.dim
                    }
                        .background(Color.Black.copy(alpha = colors.dimAmount)).then(dismissOnScrim))
                    // 三键导航在父容器避让，手势导航在内容留白；IME 与导航取并集并由 Insets 统一消费。
                    Box(Modifier.fillMaxSize().windowInsetsPadding(parentInsets)
                        // 原厂 Coordinator 不裁顶部留白，上甩回弹只在实际窗口可用区裁切。
                        .clipToBounds().onPlaced { viewportCoordinates = it }
                        .padding(top = topSpacing).consumeWindowInsets(PaddingValues(top = topSpacing))
                        .onSizeChanged { parentHeight = it.height }) {
                        // motion 与菜单使用可见高度；Surface 单独保留原厂屏外绘制余量。
                        Box(modifier.align(Alignment.BottomCenter).widthIn(max = CouiSheetDefaults.maxWidth).fillMaxWidth()
                            .then(heightModifier).onSizeChanged { panelHeight = it.height }
                            .onGloballyPositioned { panelCoordinates = it }) {
                            Box(Modifier.fillMaxWidth().graphicsLayer {
                                    translationY = sheetTranslation()
                                }
                                .nestedScroll(nested).sheetDrag(motion, touch, canDrag) { requestDismiss() }.pointerInput(motion, touch) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        // UserInput 也包含键盘、滚轮和无障碍滚动；这些输入没有触摸释放阶段。
                                        touch.firstPointer = down.id
                                        touch.toolbarOwnsTouch = false
                                        var released = false
                                        try {
                                            while (true) {
                                                // 系统取消先于子滚动容器的释放处理，避免将 CANCEL 当作向下抬手。
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.isCouiTouchCancelled()) break
                                                if (event.changes.none { it.pressed }) { released = true; break }
                                            }
                                        } finally {
                                            touch.firstPointer = null
                                            if (!released) motion.cancelDrag()
                                        }
                                    }
                                }.semantics {
                                    if (isVisible) dismiss { requestDismiss() }
                                }) {
                                CouiSheetSurface(Modifier.fillMaxWidth(), material, windowBackdrop, colors, dark,
                                    themeTint = themeTint,
                                    surfaceBackdrop = surfaceBackdrop,
                                    windowCoordinates = { windowCoordinates },
                                    panelCoordinates = { panelCoordinates }, translationY = sheetTranslation) {
                                    CompositionLocalProvider(LocalCouiDarkTheme provides dark,
                                        LocalCouiMenuVisibleBounds provides menuBounds,
                                        LocalSheetTouchSession provides touch,
                                        LocalCouiClipUpwardOverscroll provides true,
                                        LocalCouiBackdrop provides surfaceBackdrop,
                                        LocalCouiSheetSurfaceBackdrop provides surfaceBackdrop) {
                                        Column(Modifier.fillMaxWidth().then(if (sheetHeight != null) Modifier.fillMaxHeight() else Modifier)
                                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
                                            if (showDragHandle) SheetDragHandle(dark)
                                            SheetContent(
                                                fixedHeight = sheetHeight != null,
                                                enabled = interactive,
                                                fullPageOverscroll = fullPageOverscroll,
                                                scrollableContent = scrollableContent,
                                                toolbar = {
                                                    if (leadingButton != null || titleContent != null || trailingButton != null) {
                                                        SheetToolbar(leadingButton, titleContent, trailingButton)
                                                    }
                                                },
                                                content = content,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SheetContent(
    fixedHeight: Boolean,
    enabled: Boolean,
    fullPageOverscroll: Boolean,
    scrollableContent: Boolean,
    toolbar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pageEffect = if (fullPageOverscroll) rememberCouiOverscrollEffect() else null
    val sheetContent: @Composable ColumnScope.() -> Unit = {
        toolbar()
        if (scrollableContent) {
            val state = rememberScrollState()
            val modifier = Modifier.fillMaxWidth()
            val scroll = if (pageEffect == null) modifier.couiVerticalScroll(state, enabled = enabled)
                else modifier.couiVerticalScrollInput(state, enabled, pageEffect)
            Column(scroll, content = content)
        } else content()
    }
    if (pageEffect == null) {
        sheetContent()
        return
    }
    val modifier = if (fixedHeight) Modifier.weight(1f) else Modifier
    Column(
        modifier.fillMaxWidth().clipToBounds().overscroll(pageEffect).scrollable(
            state = rememberScrollableState { 0f },
            orientation = Orientation.Vertical,
            overscrollEffect = pageEffect,
            enabled = enabled,
            reverseDirection = true,
        ),
        content = sheetContent,
    )
}

@Composable
private fun SheetDragHandle(dark: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(CouiSheetDefaults.handleHeight)) {
        val width = 36.dp.toPx()
        val height = 4.dp.toPx()
        drawRoundRect(Color(if (dark) 0x40FFFFFF else 0x29000000),
            Offset((size.width - width) / 2f, 12.dp.toPx()), Size(width, height), CornerRadius(height / 2f))
    }
}

private data class SheetMeasurement(
    val windowHeight: Int,
    val parentHeight: Int,
    val panelHeight: Int,
    val imeHeight: Float,
)

/** 首指的命中归属在同一次触摸期间保持，连带阻止嵌套滚动接管。 */
internal class SheetTouchSession {
    var firstPointer: PointerId? = null
    var toolbarOwnsTouch = false
    val canDrag get() = firstPointer != null && !toolbarOwnsTouch
}

internal val LocalSheetTouchSession = staticCompositionLocalOf<SheetTouchSession?> { null }

@Composable
private fun Modifier.sheetDrag(
    motion: CouiSheetMotion,
    touch: SheetTouchSession,
    enabled: Boolean,
    onDismiss: () -> Unit,
): Modifier {
    if (!enabled) return this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val configuration = android.view.ViewConfiguration.get(LocalView.current.context)
    return onGloballyPositioned { coordinates = it }.pointerInput(motion, touch, configuration) {
        var cancelled = false
        var dragVelocity = 0f
        var releaseVelocity = Velocity.Zero
        fun track(tracker: CouiVelocityTracker, change: PointerInputChange, start: Boolean = false) {
            coordinates?.takeIf { it.isAttached }?.let { tracker.add(change, it, start) }
        }
        try {
            coroutineScope {
                // Initial 先于拖动的 Main 阶段，UP 也在释放判定前完成采样；观察器不消费事件。
                launch(start = CoroutineStart.UNDISPATCHED) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var tracker = CouiVelocityTracker(down.uptimeMillis)
                        cancelled = false
                        dragVelocity = 0f
                        releaseVelocity = Velocity.Zero
                        track(tracker, down)
                        var pointerId = down.id
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.isCouiTouchCancelled()) { cancelled = true; break }
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null) { cancelled = true; break }
                                track(tracker, change)
                                if (change.pressed && configuration.scaledMaximumFlingVelocity > 10000) {
                                    dragVelocity = tracker.calculateVelocity(
                                        maximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat()).y
                                }
                                if (!change.pressed) {
                                    val next = event.changes.firstOrNull { it.pressed }
                                    if (next == null) {
                                        val velocity = tracker.calculateVelocity(
                                            maximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat())
                                        val minimum = configuration.scaledMinimumFlingVelocity.toFloat()
                                        releaseVelocity = Velocity(
                                            if (abs(velocity.x) < minimum) 0f else velocity.x,
                                            if (abs(velocity.y) < minimum) 0f else velocity.y)
                                        break
                                    }
                                    // 主指切换重新估速；坐标由当前节点换到窗口，不叠加尚未提交的动画状态。
                                    pointerId = next.id
                                    dragVelocity = 0f
                                    tracker.recycle()
                                    tracker = CouiVelocityTracker(next.uptimeMillis)
                                    track(tracker, next, start = true)
                                }
                            }
                        } finally {
                            tracker.recycle()
                        }
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 工具栏在 Initial 标记首指，点击和反馈继续处理原事件。
                    if (touch.toolbarOwnsTouch) return@awaitEachGesture
                    val drag = awaitVerticalPointerSlopOrCancellation(down.id, down.type) { change, overSlop ->
                        change.consume()
                        motion.drag(overSlop, velocity = dragVelocity)
                    } ?: return@awaitEachGesture
                    val released = verticalDrag(drag.id) { change ->
                        val delta = change.positionChange().y
                        change.consume()
                        motion.drag(delta, velocity = dragVelocity)
                    }
                    if (!released || cancelled) motion.cancelDrag()
                    else if (motion.release(releaseVelocity)) onDismiss()
                }
            }
        } finally {
            motion.cancelDrag()
        }
    }
}
