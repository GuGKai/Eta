package com.itos.coui.messageinput

import android.graphics.BlendMode
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.layout.ConfigureCouiImeWindow
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiIcon
import com.itos.coui.theme.CouiTheme

/**
 * 底部消息输入栏，包含全宽渐变遮罩、预设留白和胶囊编辑区。
 * backdrop 录制下方正文，不包含输入栏自身；组件内部叠加单独录制的遮罩供胶囊采样。
 *
 * state 由宿主持有，onSubmit 接收 IME 提交事件，业务校验与草稿修改由宿主处理。
 * placeholder 是空草稿时显示的提示文字，默认单行省略。
 * textStyle 与 placeholderStyle 分别合并覆盖编辑和提示排版预设，文字颜色由 colors 控制。
 * placeholderMaxLines 必须大于 0，placeholderOverflow 控制提示的溢出方式。
 * lineLimits 控制编辑区的可见行数，默认最多三行，超出后滚动而不省略内容。
 * 模糊与描边跟随系统材质设置，省电模式关闭材质；未提供厂商设置的系统默认开启。
 * contourGlowEnabled 可额外关闭描边和光晕；阴影按有效光晕状态选择预设。
 * interactionSource 仅观察文本编辑区的交互，宿主可据此区分编辑焦点与操作按钮焦点。
 * leadingAction、trailingAction 和 primaryAction 分别配置前置、尾部和主操作。
 * 传入操作时显示，传 null 时退场并释放该位置；显隐规则和设置持久化由宿主处理。
 * 退场时立即释放点击和无障碍入口，图标及文本留白连续过渡。
 * modifier 作用于含遮罩和留白的整个底部区域，shape 只控制胶囊的裁剪与材质几何。
 * 组件消费剩余的底部导航栏占位，IME 避让由宿主容器处理。
 * Activity 的 IME 调整模式在同窗最后一个使用者退出时恢复，Compose Dialog 自行管理窗口模式。
 */
@RequiresApi(34)
@Composable
fun MessageInputBar(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    dark: Boolean = CouiTheme.isDark,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) { "消息输入栏需要所属页面的正文背景源" },
    renderer: CouiFloatingRenderer = remember { CouiFloatingRenderer.standalone() },
    colors: CouiMessageInputBarColors = CouiMessageInputBarDefaults.colors(dark),
    material: CouiFloatingMaterial = CouiMessageInputBarDefaults.material(dark),
    contourGlowEnabled: Boolean = LocalCouiContourGlowEnabled.current,
    shape: CouiShape = CouiMessageInputBarDefaults.shape,
    textStyle: TextStyle = TextStyle.Default,
    placeholder: String = "",
    inputTransformation: InputTransformation? = null,
    leadingAction: CouiMessageInputAction? = null,
    trailingAction: CouiMessageInputAction? = null,
    primaryAction: CouiMessageInputAction? = null,
    interactionSource: MutableInteractionSource? = null,
    placeholderStyle: TextStyle = TextStyle.Default,
    placeholderMaxLines: Int = 1,
    placeholderOverflow: TextOverflow = TextOverflow.Ellipsis,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
) {
    require(placeholderMaxLines > 0) { "提示文字的最大行数必须大于 0" }
    ConfigureCouiImeWindow()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val materialSettings = rememberMessageInputMaterialSettings()
    val contour = contourGlowEnabled && materialSettings.stroke
    val shadow = CouiMessageInputBarDefaults.shadow(dark, density.density, contour)
    val resolvedMaterial = if (materialSettings.blur) material else material.copy(
        blurRadius = 0f,
        firstColor = CouiMessageInputBarDefaults.backgroundColor(dark),
        firstBlendMode = BlendMode.SRC_OVER,
        secondColor = Color.Transparent,
        secondBlendMode = BlendMode.SRC_OVER,
    )
    val fontScale = LocalConfiguration.current.fontScale
    var multiline by remember(state) { mutableStateOf(false) }
    val textPadding = with(density) {
        val base = CouiMessageInputBarDefaults.textPadding.roundToPx()
        when {
            fontScale <= 1f -> base
            multiline -> (base * fontScale).toInt()
            else -> 0
        }.toDp()
    }
    var ownsFocus by remember { mutableStateOf(false) }
    var boundsSize by remember { mutableStateOf(IntSize.Zero) }
    val contentLayer = rememberGraphicsLayer()
    val leadingVisible = leadingAction != null
    val trailingVisible = trailingAction != null
    val primaryVisible = primaryAction != null
    val leadingPresence by animateFloatAsState(if (leadingVisible) 1f else 0f,
        tween(CouiMessageInputBarDefaults.actionDurationMillis, easing = FastOutSlowInEasing), label = "MessageLeadingAction")
    val trailingPresence by animateFloatAsState(if (trailingVisible) 1f else 0f,
        tween(CouiMessageInputBarDefaults.actionDurationMillis, easing = FastOutSlowInEasing), label = "MessageTrailingAction")
    val primaryPresence by animateFloatAsState(if (primaryVisible) 1f else 0f,
        tween(CouiMessageInputBarDefaults.actionDurationMillis, easing = FastOutSlowInEasing), label = "MessagePrimaryAction")
    val textStart = CouiMessageInputBarDefaults.textStart +
        (CouiMessageInputBarDefaults.textStartWithLeadingAction - CouiMessageInputBarDefaults.textStart) * leadingPresence
    val actionCount = (if (trailingVisible) 1 else 0) + (if (primaryVisible) 1 else 0)
    val textEnd by animateDpAsState(
        if (actionCount == 0) CouiMessageInputBarDefaults.textEnd
        else CouiMessageInputBarDefaults.actionsEnd + CouiMessageInputBarDefaults.actionSize * actionCount +
            CouiMessageInputBarDefaults.actionSpacing * (actionCount - 1) + CouiMessageInputBarDefaults.textActionSpacing,
        tween(CouiMessageInputBarDefaults.actionDurationMillis, easing = FastOutSlowInEasing), label = "MessageTextEnd")
    LaunchedEffect(enabled) {
        if (!enabled && ownsFocus) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val contentColor = if (enabled) colors.content else colors.content.copy(alpha = colors.content.alpha * .38f)
    val resolvedTextStyle = CouiMessageInputBarDefaults.textStyle().merge(textStyle).copy(color = contentColor)
    val resolvedPlaceholderStyle = CouiMessageInputBarDefaults.placeholderStyle().merge(placeholderStyle).copy(color = colors.hint)
    MessageInputBarSurface(modifier, backdrop,
        colors = CouiMessageInputBarDefaults.surfaceColors(dark, materialSettings.blur)) { surfaceBackdrop ->
        Box(Modifier.padding(CouiMessageInputBarDefaults.surfacePadding).fillMaxWidth()
            .heightIn(min = CouiMessageInputBarDefaults.minHeight)
            .onSizeChanged { boundsSize = it }
            .couiFloatingSurface(surfaceBackdrop, renderer, resolvedMaterial, shadow, shape, contentLayer, contour,
                foregroundComposition = true, beforeContent = null, useMenuLightEdge = false,
                sampleDrawnSurfaces = true) {
                CouiFloatingSurface(Rect(Offset.Zero, boundsSize.toSize()), cornerWeight = shape.cornerWeight,
                    cornerRadius = with(density) { shape.radius.toPx() })
            }) {
            Box(Modifier.fillMaxWidth()
                .drawWithContent { contentLayer.record { this@drawWithContent.drawContent() } }
                .clip(shape)) {
                Box(Modifier.fillMaxWidth().padding(start = textStart, end = textEnd)
                    .heightIn(min = CouiMessageInputBarDefaults.minHeight),
                    contentAlignment = Alignment.CenterStart) {
                    BasicTextField(state = state,
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { ownsFocus = it.hasFocus },
                        enabled = enabled, textStyle = resolvedTextStyle,
                        interactionSource = interactionSource,
                        cursorBrush = SolidColor(colors.cursor), inputTransformation = inputTransformation,
                        lineLimits = lineLimits,
                        onTextLayout = { result -> multiline = (result()?.lineCount ?: 1) > 1 },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        onKeyboardAction = { if (enabled) onSubmit() },
                        decorator = { field ->
                            Box(Modifier.heightIn(min = CouiMessageInputBarDefaults.minHeight),
                                contentAlignment = Alignment.CenterStart) {
                                Box(Modifier.fillMaxWidth().padding(vertical = textPadding)) { field() }
                                if (state.text.isEmpty() && placeholder.isNotEmpty()) BasicText(placeholder,
                                    modifier = Modifier.graphicsLayer {
                                        translationY = CouiMessageInputBarDefaults.placeholderOffsetY.toPx()
                                    },
                                    style = resolvedPlaceholderStyle,
                                    maxLines = placeholderMaxLines, overflow = placeholderOverflow)
                            }
                        })
                }
                // 裁剪边界与输入留白使用同一帧尺寸，退场图标随占位收拢，避免覆盖文字。
                // 子项保留按钮原尺寸和起止锚点，区域变窄时由外围裁剪。
                Box(Modifier.align(Alignment.BottomStart).width(textStart)
                    .height(CouiMessageInputBarDefaults.minHeight).clipToBounds()) {
                    MessageAction(Modifier.align(Alignment.BottomStart)
                        .wrapContentSize(Alignment.BottomStart, unbounded = true)
                        .padding(start = CouiMessageInputBarDefaults.leadingActionStart, bottom = CouiMessageInputBarDefaults.leadingActionBottom)
                        .size(CouiMessageInputBarDefaults.leadingActionSize), leadingAction, colors.content, enabled,
                        visible = leadingVisible, presence = leadingPresence)
                }
                Box(Modifier.align(Alignment.BottomEnd).width(textEnd)
                    .height(CouiMessageInputBarDefaults.minHeight).clipToBounds()) {
                    MessageAction(Modifier.align(Alignment.BottomEnd)
                        .wrapContentSize(Alignment.BottomEnd, unbounded = true)
                        .padding(end = CouiMessageInputBarDefaults.actionsEnd, bottom = CouiMessageInputBarDefaults.actionsBottom)
                        .offset(x = -(CouiMessageInputBarDefaults.actionSize + CouiMessageInputBarDefaults.actionSpacing) * primaryPresence)
                        .size(CouiMessageInputBarDefaults.actionSize), trailingAction, colors.content, enabled,
                        visible = trailingVisible, presence = trailingPresence)
                    MessageAction(Modifier.align(Alignment.BottomEnd)
                        .wrapContentSize(Alignment.BottomEnd, unbounded = true)
                        .padding(end = CouiMessageInputBarDefaults.actionsEnd, bottom = CouiMessageInputBarDefaults.actionsBottom)
                        .size(CouiMessageInputBarDefaults.actionSize), primaryAction, colors.content, enabled,
                        visible = primaryVisible, presence = primaryPresence, pressedColor = colors.pressedIcon)
                }
            }
        }
    }
}

@Composable
private fun MessageAction(modifier: Modifier, action: CouiMessageInputAction?, color: Color,
    enabled: Boolean, visible: Boolean, presence: Float, pressedColor: Color = color) {
    var retainedAction by remember { mutableStateOf(action) }
    val displayedAction = action ?: retainedAction
    // 配置移除后保留退场图标，动画结束即释放图标及宿主回调。
    SideEffect { retainedAction = if (visible || presence > 0f) displayedAction else null }
    if (displayedAction == null || (!visible && presence == 0f)) return
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val actionEnabled = enabled && displayedAction.enabled
    val interactionModifier = if (visible) Modifier
        .clickable(interaction, indication = null, enabled = actionEnabled, role = Role.Button, onClick = displayedAction.onClick)
        .semantics { contentDescription = displayedAction.contentDescription }
    else Modifier.clearAndSetSemantics {}
    val tint = if (!actionEnabled) color.copy(alpha = color.alpha * .38f) else if (pressed) pressedColor else color
    Box(modifier.then(interactionModifier), contentAlignment = Alignment.Center) {
        CouiIcon(Modifier.size(CouiMessageInputBarDefaults.iconSize).graphicsLayer {
            alpha = presence
            scaleX = CouiMessageInputBarDefaults.hiddenIconScale + (1f - CouiMessageInputBarDefaults.hiddenIconScale) * presence
            scaleY = scaleX
        }, displayedAction.icon, null, tint = tint)
    }
}
