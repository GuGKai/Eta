package com.itos.coui.sheet

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.Coui
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.theme.ProvideCouiContent
import com.itos.coui.topappbar.CouiToolbarStyle
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.LocalCouiToolbarBackdrop
import com.itos.coui.topappbar.TopAppBarButton

internal val LocalCouiSheetSurfaceBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

/** 当前面板已绘制的纯背景源，供同窗口浮动控件使用；独立弹窗仍消费窗口内容源。 */
@Composable
fun rememberCouiSheetSurfaceBackdrop(): CouiBackdrop =
    checkNotNull(LocalCouiSheetSurfaceBackdrop.current) { "面板背景源必须在 BottomSheet 内容中读取" }

/** 面板顶部的单个操作按钮。label 和 icon 选其一。popupContent 以整个按钮为锚点。 */
data class CouiSheetButton(
    val onClick: () -> Unit,
    val label: String? = null,
    val icon: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val modifier: Modifier = Modifier,
    val popupContent: (@Composable () -> Unit)? = null,
) {
    init {
        require((label != null) xor (icon != null)) { "按钮必须提供文字或图标之一" }
    }
}

/** 顶部按钮只采样面板底图，避免采入按钮自身。 */
@Composable
internal fun SheetToolbar(
    leadingButton: CouiSheetButton?,
    titleContent: @Composable (() -> Unit)?,
    trailingButton: CouiSheetButton?,
) {
    val backdrop = rememberCouiSheetSurfaceBackdrop()
    val dark = CouiTheme.isDark
    val density = LocalDensity.current.density
    val contour = LocalCouiContourGlowEnabled.current
    val buttonTextStyle = rememberSheetButtonTextStyle()
    val colors = CouiDialogDefaults.themeColors(dark)
    val style = remember(dark, density, contour, buttonTextStyle, colors.action) {
        CouiToolbarStyle(dark, CouiTopAppBarButtonDefaults.circleShape, CouiCapsuleShape,
            material = { CouiSheetDefaults.toolbarMaterial(dark, it) },
            shadow = { CouiTopAppBarButtonDefaults.shadow(dark, it, density, contour, alphaScale = 1f) },
            contentColor = colors.action, textStyle = buttonTextStyle,
            cursorColor = colors.action, contourGlowEnabled = contour,
            contourStyle = { capsule -> if (capsule) CouiContourStyle.Button else CouiContourStyle.CircleButton })
    }
    val touch = LocalSheetTouchSession.current
    val actionInput = if (touch == null) Modifier else Modifier.pointerInput(touch) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (size.width > 0 && size.height > 0 && touch.firstPointer == down.id) {
                // 对应原厂工具栏按下后禁止父层拦截；不消费按钮自身的点击和按压事件。
                touch.toolbarOwnsTouch = true
            }
        }
    }
    CompositionLocalProvider(LocalCouiToolbarBackdrop provides backdrop) {
        Layout(modifier = Modifier.fillMaxWidth().padding(top = CouiSheetDefaults.toolbarTopSpacing)
            .padding(horizontal = CouiSheetDefaults.toolbarHorizontalPadding), content = {
            Box(actionInput) { if (leadingButton != null) SheetToolbarButton(leadingButton, style) }
            Box(actionInput) { if (trailingButton != null) SheetToolbarButton(trailingButton, style) }
            Box(Modifier.padding(vertical = CouiSheetDefaults.toolbarTitleVerticalPadding)
                .heightIn(min = CouiSheetDefaults.toolbarTitleMinHeight)
                .semantics { if (titleContent != null) heading() }, contentAlignment = Alignment.Center) {
                if (titleContent != null) {
                    CompositionLocalProvider(LocalCouiContentColor provides colors.title, content = titleContent)
                }
            }
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val gap = CouiSheetDefaults.toolbarTitleGap.roundToPx()
            val buttonConstraints = Constraints(maxWidth = (width / 2 - gap).coerceAtLeast(0),
                maxHeight = constraints.maxHeight)
            val navigationPlaceable = measurables[0].measure(buttonConstraints)
            val actionPlaceable = measurables[1].measure(buttonConstraints)
            // 两边按较宽操作区等量避让，标题保持窗口居中。
            val inset = maxOf(navigationPlaceable.width, actionPlaceable.width) + gap
            val titlePlaceable = measurables[2].measure(Constraints(
                maxWidth = (width - 2 * inset).coerceAtLeast(0), maxHeight = constraints.maxHeight))
            val height = constraints.constrainHeight(maxOf(CouiSheetDefaults.toolbarHeight.roundToPx(),
                navigationPlaceable.height, actionPlaceable.height, titlePlaceable.height))
            layout(width, height) {
                navigationPlaceable.placeRelative(0, (height - navigationPlaceable.height) / 2)
                actionPlaceable.placeRelative(width - actionPlaceable.width, (height - actionPlaceable.height) / 2)
                titlePlaceable.placeRelative((width - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
            }
        }
    }
}

@Composable
private fun SheetToolbarButton(button: CouiSheetButton, style: CouiToolbarStyle) {
    val label = button.label
    val content: @Composable () -> Unit = {
        if (label != null) {
            BasicText(
                text = label,
                style = LocalCouiTextStyle.current.copy(color = LocalCouiContentColor.current, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else button.icon?.invoke()
    }
    if (Build.VERSION.SDK_INT >= 34) {
        TopAppBarButton(button.modifier, button.onClick, enabled = button.enabled, text = label != null, style = style) {
            content()
        }
    } else {
        val size = if (label != null) Modifier.widthIn(min = 60.dp).height(40.dp) else Modifier.size(40.dp)
        val color = if (button.enabled) style.contentColor else style.contentColor.copy(alpha = style.contentColor.alpha * .38f)
        Box(button.modifier.then(Coui.defaultStyle.minimumInteractiveComponentSize()).then(size)
            .clickable(enabled = button.enabled, role = Role.Button, onClick = button.onClick)
            .padding(horizontal = if (label != null) 12.dp else 0.dp), contentAlignment = Alignment.Center) {
            ProvideCouiContent(color = color, textStyle = style.textStyle, content = content)
        }
    }
    button.popupContent?.invoke()
}
