package com.itos.coui.floatingtoolbar

import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.couiToolbarFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.menu.CouiMenuDefaults
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiScaleVisibility
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent
import kotlin.math.roundToInt

private val LocalToolbarSingle = compositionLocalOf { false }
private val LocalToolbarInteractive = compositionLocalOf { true }

@RequiresApi(34)
@Composable
internal fun ToolbarSurface(
    modifier: Modifier,
    visible: Boolean,
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    style: CouiFloatingToolbarStyle,
    animation: ToolbarAnimation,
    batch: ToolbarItems,
    content: @Composable () -> Unit,
) {
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val single = batch.target.size == 1
    val shape = if (single && style.shape === CouiCapsuleShape) {
        CouiShape(CouiFloatingToolbarDefaults.height / 2, 2f)
    } else style.shape
    val cornerRadius = with(LocalDensity.current) { (shape as? CouiShape)?.radius?.toPx() }
    val visibility = rememberCouiScaleVisibility(visible)
    val inputSession by rememberUpdatedState(if (visible) remember { Any() } else null)
    // 手势从按下所属的显示周期开始，期间收起过，即使已经重显也不能提交旧手势；退场期新触摸不拦截。
    val input = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            var pressed = false
            var session: Any? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!pressed) session = inputSession
                if (session != null && session !== inputSession) event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
        }
    }
    val motion = rememberCouiTouchMotion(visible)
    val press = rememberCouiPressMotion()
    val (lightInput, drawLight) = rememberCouiSpotlight(interactive = visible,
        preset = SpotlightPreset.OpaqueSmall2, dark = style.dark, color = style.lightColor)
    val contentLayer = rememberGraphicsLayer()
    val viewConfiguration = LocalViewConfiguration.current
    val buttonConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    Layout(modifier = modifier.height(CouiFloatingToolbarDefaults.height), content = {
        Box(Modifier.then(input).then(if (visible) Modifier else Modifier.clearAndSetSemantics {}
            .focusProperties { canFocus = false }.onPreviewKeyEvent { true })
            // 增删时胶囊边缘会落在像素之间。材质仍采样下层投影与光晕，只将可见前景叠回页面。
            .couiFloatingSurface(backdrop, renderer, style.material, style.shadow, shape,
                contentLayer, style.contourGlowEnabled, foregroundComposition = true, beforeContent = null,
                useMenuLightEdge = false, sampleDrawnSurfaces = true) {
            val x = motion.translationX.value
            val y = motion.translationY.value
            CouiFloatingSurface(
                bounds = Rect(x, y, x + width, y + height),
                scaleX = (motion.scaleX.value + press.scale.value - 1f) * visibility.scale.value,
                scaleY = (motion.scaleY.value + press.scale.value - 1f) * visibility.scale.value,
                cornerWeight = (shape as? CouiShape)?.cornerWeight ?: if (single) 2f else 1f,
                cornerRadius = cornerRadius,
                alpha = visibility.alpha.value.coerceIn(0f, 1f),
                visible = visibility.isPresent && width > 0f,
            )
        }) {
            CompositionLocalProvider(
                LocalCouiFloatingToolbarStyle provides style,
                LocalToolbarSingle provides single,
                LocalToolbarInteractive provides visible,
                LocalViewConfiguration provides buttonConfiguration,
            ) {
                ToolbarLayout(
                    modifier = Modifier.graphicsLayer {
                        if (size.width > 0f) transformOrigin = TransformOrigin(width / (2f * size.width), 0.5f)
                        translationX = motion.translationX.value
                        translationY = motion.translationY.value
                        scaleX = (motion.scaleX.value + press.scale.value - 1f) * visibility.scale.value
                        scaleY = (motion.scaleY.value + press.scale.value - 1f) * visibility.scale.value
                    }.couiTouchMotion(motion, visible, cancelOnConsumedMove = false, consumeDrag = true)
                        .couiPressMotion(press, visible)
                        .then(lightInput)
                        .drawWithContent {
                            contentLayer.pivotOffset = Offset(width / 2f, height / 2f)
                            contentLayer.renderEffect = visibility.foregroundBlurEffect
                            contentLayer.record { this@drawWithContent.drawContent() }
                        }
                        .clip(shape).drawBehind { drawLight?.invoke(this) },
                    animation = animation,
                    batch = batch,
                    onGeometry = { w, h ->
                        width = w
                        height = h
                    },
                    content = content,
                )
            }
        }
    }) { measurables, constraints ->
        val capsule = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val hostWidth = constraints.constrainWidth(capsule.width)
        val hostHeight = constraints.constrainHeight(capsule.height)
        layout(hostWidth, hostHeight) {
            if (!visibility.isPresent) return@layout
            // 原厂居中对齐的是当帧背景的浮点边界，整数布局坐标只在最后一次摆放时量化。
            val left = (hostWidth - width) / 2f
            val integerLeft = left.roundToInt()
            capsule.placeWithLayer(integerLeft, (hostHeight - capsule.height) / 2) {
                translationX = left - integerLeft
            }
        }
    }
}

@Composable
internal fun ToolbarButton(
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    interactive: Boolean,
    content: @Composable () -> Unit,
) {
    val style = LocalCouiFloatingToolbarStyle.current
    val source = remember { MutableInteractionSource() }
    val active = interactive && LocalToolbarInteractive.current
    val feedback = if (LocalToolbarSingle.current) CouiFloatingToolbarDefaults.singleItemFeedbackInset
        else -CouiFloatingToolbarDefaults.itemSpacing
    val input = if (active) Modifier.couiToolbarFeedback(style.dark, source, enabled, pressFeedback = true, maskInset = feedback)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        else Modifier.clearAndSetSemantics {}
    Box(modifier.height(CouiFloatingToolbarDefaults.itemHeight).widthIn(min = CouiFloatingToolbarDefaults.itemSize)
        .then(input), contentAlignment = Alignment.Center) {
        val color = if (enabled) style.contentColor else
            CouiTheme.colorSchemeFor(style.dark).onSurface.copy(alpha = CouiMenuDefaults.disabledContentAlpha)
        if (color.isSpecified) ProvideCouiContent(color = color) { content() }
        else content()
    }
}
