package io.github.mangi.eta.ui.components.chat

import io.github.mangi.eta.ui.components.SpeechInputFeedback
import io.github.mangi.eta.ui.components.rememberSpeechInput
import androidx.compose.ui.text.TextRange
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlin.math.pow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.components.coui.CouiCircleIconButton
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.shape.CouiShape
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.markdown.EditorialLineHeightStyle
import io.github.mangi.eta.ui.state.composerFocusRequests
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.mangi.eta.ui.model.PendingImageUi
import io.github.mangi.eta.ui.model.QueuedMessageUi
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPress
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.mangi.eta.ui.components.ConversationModelPickerSheet
import io.github.mangi.eta.ui.components.LocalChatImagePreviewer
import io.github.mangi.eta.ui.components.LocalRoleplayChat
import io.github.mangi.eta.ui.components.ModelBrandMark
import io.github.mangi.eta.ui.components.TopLayer
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import io.github.mangi.eta.ui.components.rememberScreenCornerRadius
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 发送圆钮直径：与 COUI 样式对齐（上游 Miuix 是 24dp，此处不再跟随）。 */
private val SendButtonVisualSize = 32.dp
/** 发送箭头尺寸：与 COUI 样式的 18dp 对齐。 */
private val SendIconSize = 18.dp
/** 终止按钮的方块图标尺寸：原 10dp 放大 50%（10 × 1.5 = 15dp）。 */
private val StopIconSize = 15.dp
private val ThinkingIconSize = ChatInputActionIconSize
/** 输入栏正中的余额胶囊：由外壳页面注入，避免把余额状态一路透传进输入栏。 */
internal val LocalChatInputBalanceCapsule = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** 输入框底色面板向上多探出的高度：渐变从这段背景的顶边开始。 */
private val ChatBottomFadeOverhang = 32.dp

/** 最后一层条带（图片 / 文件 / 文件路径 / 排队消息 / 编辑提示）与胶囊上边缘之间的空隙。 */
private val ChatInputStripGapToInput = 5.dp

/** 条带彼此之间的间距。 */
private val ChatInputStripGap = 8.dp

/**
 * 输入框胶囊上方那块渐隐背景：顶边全透明，向下平滑升到实色。
 *
 * 沿用原有写法：Brush.verticalGradient、11 个 stop、alpha = progress^1.6、终点色 surface。
 */
private fun chatInputFadeBrush(color: Color, alphaScale: Float = 1f): Brush {
    val stops = 10
    return Brush.verticalGradient(
        *Array(stops + 1) { index ->
            val progress = index / stops.toFloat()
            progress to color.copy(alpha = progress.pow(1.6f) * alphaScale)
        },
    )
}

/**
 * 输入框圆角。跟着屏幕圆角做同心：内圆角 = 外圆角 − 这一侧的留白，
 * 两条弧共用圆心，圆角处的间距才和直边一致（离边缘 8dp 时，用 24dp 就会比直边宽一截）。
 * 屏幕本身没有圆角时算出 ≤ 0，退化成直角，不硬造。
 */
@Composable
private fun rememberInputContainerRadius(): Dp {
    val screenCornerRadius = rememberScreenCornerRadius()
    return (screenCornerRadius - ChatInputOuterMargin).coerceAtLeast(0.dp)
}

/** COUI 输入框的角权重（库里 CouiShape 的 cornerWeight 参数），越大越接近超椭圆。 */
private const val InputCornerWeight = 2.3f

/**
 * 输入栏容器底色。
 *
 * - COUI：固定灰（深色 #1C1E22 / 浅色 #F6F7F9）；
 * - MIUIUX：surfaceContainer（浅色下与 surface 撞色时回退 #F5F6F8）。
 *
 * 余额胶囊与输入框共用这一份颜色，两者形状内部在任何明暗模式下都完全一致。
 */
@Composable
internal fun chatInputContainerColor(): Color {
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color(0xFF1C1E22) else Color(0xFFF6F7F9)
    }
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    return if (!isDark && surfaceContainer == MiuixTheme.colorScheme.surface) {
        Color(0xFFF5F6F8)
    } else {
        surfaceContainer
    }
}

/**
 * Agent 输入器始终保持同一空间结构，聚焦、输入和执行过程只改变状态，不搬动操作入口。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AgentChatInputBar(
    input: String,
    isCompacting: Boolean,
    contextUsage: AgentContextUsageUi,
    showContextUsage: Boolean,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    modelPickerState: AgentModelPickerUiState,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi> = emptyList(),
    inputSyncVersion: Int = 0,
    focusInputOnSync: Boolean = true,
    isEditingMessage: Boolean,
    editHasLaterTurns: Boolean,
    preserveFollowingMessages: Boolean,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onModelSelected: (String) -> Unit,
    onCompactContext: () -> Unit,
    canCompactContext: Boolean,
    onSubmit: (String) -> Unit,
    onInterruptAndSubmit: (String) -> Unit = onSubmit,
    onSteerSubmit: (String) -> Unit = onSubmit,
    onSteerQueuedMessage: (String) -> Unit = {},
    onRecallQueuedMessage: (String, String) -> Unit = { _, _ -> },
    onRemoveQueuedMessage: (String) -> Unit = {},
    onStop: () -> Unit,
    onAttachImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAttachFiles: (List<String>) -> Unit,
    onAttachFolder: (String) -> Unit,
    onAttachFilePath: (String) -> Unit,
    onRemoveFileReference: (String) -> Unit,
    onCancelMessageEdit: () -> Unit,
    /** 新建对话后的一次性聚焦信号。 */
    focusInputRequest: Int = 0,
    onFocusInputHandled: () -> Unit = {},
    glassBackdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    var showModelSheet by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val textFieldState = rememberTextFieldState(initialText = input)
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsStateWithLifecycle(initialValue = null)
    val recognitionEnabled = speechSettings?.recognitionEnabled == true
    val dictation = rememberSpeechInput { recognized ->
        textFieldState.edit {
            val range = selection
            replace(range.min, range.max, recognized)
            selection = TextRange(range.min + recognized.length)
        }
    }
    LaunchedEffect(isEditingMessage, recognitionEnabled) { dictation.cancel() }
    var wasEditingMessage by remember { mutableStateOf(isEditingMessage) }
    var syncedVersion by remember { mutableIntStateOf(inputSyncVersion) }
    val canSend = textFieldState.text.isNotBlank() ||
        pendingImages.isNotEmpty() ||
        pendingFileReferences.isNotEmpty()

    LaunchedEffect(isEditingMessage, inputSyncVersion) {
        // 编辑态与排队消息撤回由外部业务状态驱动；普通输入只保留在本地，避免每个字符把聊天舞台
        // 的消息流、滚动和 Markdown 一起带入重组。
        val versionChanged = inputSyncVersion != syncedVersion
        if (isEditingMessage || wasEditingMessage || versionChanged) {
            textFieldState.setTextAndPlaceCursorAtEnd(input)
        }
        if (isEditingMessage || (versionChanged && input.isNotEmpty() && focusInputOnSync)) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        wasEditingMessage = isEditingMessage
        syncedVersion = inputSyncVersion
    }

    // 用「请求序号」而不是一次性布尔：新建对话会连带重建输入栏，
    // 布尔信号容易被即将销毁的旧实例吞掉，导致新会话不弹键盘。
    var handledFocusRequestId by remember { mutableIntStateOf(0) }
    var inputFocused by remember { mutableStateOf(false) }
    var pendingFocusRequest by remember { mutableStateOf(false) }
    var inputPlaced by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val busFocusRequest by composerFocusRequests.collectAsState()
    val effectiveFocusRequest = if (busFocusRequest > 0) busFocusRequest else focusInputRequest
    LaunchedEffect(effectiveFocusRequest) {
        if (effectiveFocusRequest <= 0 || effectiveFocusRequest == handledFocusRequestId) return@LaunchedEffect
        handledFocusRequestId = effectiveFocusRequest
        pendingFocusRequest = true
        withFrameNanos { }
        // 只有已经完成过布局的输入框（复用场景）才在这里请求；新建会话交给 onPlaced，
        // 否则会抢在 attach 之前 requestFocus，抛异常后把 pending 标记清掉，onPlaced 就不重试了。
        if (inputPlaced && pendingFocusRequest) {
            pendingFocusRequest = false
            runCatching { focusRequester.requestFocus() }
        }
        // 等焦点真正落到输入框（最多 1.5 秒），再拉起键盘；IME 需要有效的输入连接。
        withTimeoutOrNull(1500) { snapshotFlow { inputFocused }.first { it } }
        withFrameNanos { }
        keyboard?.show()
        onFocusInputHandled()
        // 处理完把通道值复位，避免 Activity 重建时重复聚焦拉起键盘。
        delay(600)
        composerFocusRequests.compareAndSet(effectiveFocusRequest, 0)
    }

    // 退到后台时清掉输入焦点：回到前台时不再自动弹键盘。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pendingFocusRequest = false
                focusManager.clearFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 键盘当前是否抬起：打开会话选模型弹层前据此决定要不要收键盘、关掉后要不要还回去。
    val imeVisibleNow = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var restoreImeAfterModelSheet by remember { mutableStateOf(false) }
    /**
     * 打开会话选模型弹窗，并让键盘像 COUI 一样让位。
     *
     * COUI 用的是独立窗口的 Dialog，系统自己会把键盘收起来、关闭后再弹回去；
     * MIUIX 的 OverlayBottomSheet 只是本页浮层，键盘不会让位，反而把它往上顶。
     * 所以这里手动对齐：开之前收键盘，关之后把焦点和键盘还给输入框。
     */
    val openModelSheet: () -> Unit = {
        restoreImeAfterModelSheet = imeVisibleNow
        if (imeVisibleNow) keyboard?.hide()
        showModelSheet = true
    }
    LaunchedEffect(showModelSheet) {
        if (showModelSheet || !restoreImeAfterModelSheet) return@LaunchedEffect
        restoreImeAfterModelSheet = false
        runCatching { focusRequester.requestFocus() }
        // 焦点先回到输入框（最多等 0.8 秒），再拉起键盘：IME 需要有效的输入连接。
        withTimeoutOrNull(800) { snapshotFlow { inputFocused }.first { it } }
        withFrameNanos { }
        keyboard?.show()
    }

    // 胶囊顶边在窗口里的位置：输入栏菜单的回退定位要用它。
    var inputContainerTopPx by remember { mutableFloatStateOf(0f) }
    // 最后一层条带与胶囊之间只留 5dp，其它条带之间保持 8dp。
    val queueStripBottomGap = if (
        pendingFileReferences.isEmpty() && pendingImages.isEmpty() && !isEditingMessage
    ) {
        ChatInputStripGapToInput
    } else {
        ChatInputStripGap
    }
    val fileStripBottomGap = if (pendingImages.isEmpty() && !isEditingMessage) {
        ChatInputStripGapToInput
    } else {
        ChatInputStripGap
    }
    val imageStripBottomGap = if (isEditingMessage) ChatInputStripGap else ChatInputStripGapToInput

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        if (recognitionEnabled) SpeechInputFeedback(dictation)
        // 输入框上方的渐隐背景，完全沿用原有的实现：终点色就是 surface。
        val inputFadeColor = MiuixTheme.colorScheme.surface
        // MIUIX：这块渐隐整体降到 50% 透明度，别把上方内容压得太实；COUI 保持原样。
        val fadeAlphaScale = if (LocalUiStyle.current == AppearanceUiStyle.COUI) 1f else 0.5f
        val inputFadeBrush = remember(inputFadeColor, fadeAlphaScale) {
            chatInputFadeBrush(inputFadeColor, fadeAlphaScale)
        }
        // 输入框上方那条渐变就是这块区域的背景：底部对齐、至少留出 ChatBottomFadeOverhang，
        // 条带作为它的子节点画在渐变上面（离胶囊 5dp），所以加了条带渐变也跟着变高、且始终垫在下面。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ChatBottomFadeOverhang)
                .background(inputFadeBrush),
            verticalArrangement = Arrangement.Bottom,
        ) {
            AnimatedVisibility(
                visible = queuedMessages.isNotEmpty(),
                enter = fadeIn(tween(160)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(150)),
            ) {
                AgentChatQueueStrip(
                    queuedMessages = queuedMessages,
                    isStreaming = isStreaming,
                    onSteer = onSteerQueuedMessage,
                    onRecall = { id -> onRecallQueuedMessage(id, textFieldState.text.toString()) },
                    onRemove = onRemoveQueuedMessage,
                    modifier = Modifier.padding(
                        start = ChatInputOuterMargin,
                        end = ChatInputOuterMargin,
                        bottom = queueStripBottomGap,
                    ),
                )
            }
            AnimatedVisibility(
                visible = pendingFileReferences.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                PendingFileReferenceStrip(
                    references = pendingFileReferences,
                    onRemoveReference = onRemoveFileReference,
                    modifier = Modifier.padding(
                        start = ChatInputOuterMargin,
                        end = ChatInputOuterMargin,
                        bottom = fileStripBottomGap,
                    ),
                )
            }

            AnimatedVisibility(
                visible = pendingImages.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(tween(120)) + shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                PendingImageStrip(
                    images = pendingImages,
                    onRemoveImage = onRemoveImage,
                    modifier = Modifier.padding(
                        start = ChatInputOuterMargin,
                        end = ChatInputOuterMargin,
                        bottom = imageStripBottomGap,
                    ),
                )
            }

            AnimatedVisibility(
                visible = isEditingMessage,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
            ) {
                Text(
                    text = if (preserveFollowingMessages) {
                        stringResource(R.string.chat_edit_preserve_following)
                    } else if (editHasLaterTurns) {
                        stringResource(R.string.chat_edit_replace_later)
                    } else {
                        stringResource(R.string.chat_edit_replace_message)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    // 与输入框内正文同一起点（容器 10dp + 文本 8dp）。
                    modifier = Modifier.padding(
                        start = ChatInputOuterMargin + 18.dp,
                        end = ChatInputOuterMargin + 18.dp,
                        bottom = ChatInputStripGapToInput,
                    ),
                )
            }

        }

        val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
        val couiDark = CouiTheme.isDark
        val inputRadius = rememberInputContainerRadius()
        val inputContainerShape = remember(inputRadius) { RoundedCornerShape(inputRadius) }
        val couiInputShape = remember(inputRadius) { CouiShape(inputRadius, InputCornerWeight) }
        val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        // 输入框与余额胶囊共用同一份底色，两者在明暗模式下都不会出现色差。
        val inputContainerColor = chatInputContainerColor()
        val miuixInputBg = if (!isDark && MiuixTheme.colorScheme.surfaceContainer == MiuixTheme.colorScheme.surface) {
            Color(0xFFF5F6F8)
        } else {
            MiuixTheme.colorScheme.surfaceContainer
        }
        val defaultInputBorderColor = if (isCoui) {
            if (couiDark) Color(0x20FFFFFF) else Color(0x0E000000)
        } else {
            MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
        }
        val inputBorderColor = if (isCoui) {
            if (couiDark) Color(0x20FFFFFF) else Color(0x0E000000)
        } else {
            MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
        }
        val miuixInputShape = remember { GlassShape(24.dp) }
        // 实色面板从胶囊顶边开始；它上方的渐变是条带区自己的背景，接缝同色不可见。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(
                    start = ChatInputOuterMargin,
                    end = ChatInputOuterMargin,
                    bottom = ChatInputOuterMargin,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        // 渐变要贴着胶囊上边缘画，这里记下胶囊顶边在窗口里的位置。
                        inputContainerTopPx = coordinates.positionInWindow().y
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCoui) {
                                Modifier
                                    .dropShadow(
                                        shape = couiInputShape,
                                        shadow = Shadow(
                                            radius = 16.dp,
                                            color = Color.Black,
                                            alpha = if (couiDark) 0.22f else 0.045f,
                                        ),
                                    )
                                    .clip(couiInputShape)
                                    .background(
                                        inputContainerColor,
                                        couiInputShape,
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = if (couiDark) Color(0x20FFFFFF) else Color(0x0E000000),
                                        shape = couiInputShape,
                                    )
                            } else {
                                Modifier
                                    .glassPanel(
                                        backdrop = glassBackdrop,
                                        shape = miuixInputShape,
                                        style = GlassDefaults.style(isDark),
                                        material = GlassMaterials.puredThinGlass(isDark),
                                        stroke = GlassEdgeStrokes.forTheme(
                                            isDark = isDark,
                                            light = GlassEdgeStrokes.SmallLight,
                                            dark = GlassEdgeStrokes.SmallDark,
                                        ),
                                        shadow = GlassShadows.Regular,
                                        shading = false,
                                        fallback = Modifier
                                            .squircleSurface(
                                                color = miuixInputBg,
                                                cornerRadius = 24.dp,
                                            )
                                            .squircleBorder(
                                                width = 0.5.dp,
                                                color = inputBorderColor,
                                                cornerRadius = 24.dp,
                                            ),
                                    )
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 36.dp)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        // 占位文字与输入正文共用同一行高，首字输入时基线不跳动。
                        val inputTextStyle = MiuixTheme.textStyles.body1.copy(
                            lineHeight = 22.sp,
                            lineHeightStyle = EditorialLineHeightStyle,
                        )
                        val character = LocalRoleplayChat.current
                        // 纯空格也算已输入：占位文字必须让位，否则光标前会一直挂着提示语。
                        if (textFieldState.text.isEmpty()) {
                            Text(
                                text = when {
                                    // 角色对话的占位文字跟随角色，避免“交给 Eta”打断沉浸感。
                                    character != null && isStreaming ->
                                        stringResource(R.string.chat_character_typing, character.characterName)
                                    character != null -> stringResource(R.string.chat_character_input_hint, character.characterName)
                                    isStreaming -> stringResource(R.string.chat_eta_working)
                                    else -> stringResource(R.string.chat_input_hint)
                                },
                                style = inputTextStyle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        BasicTextField(
                            state = textFieldState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { inputFocused = it.isFocused }
                                .onPlaced {
                                    // 节点完成布局才可能真正拿到焦点，这里是新建会话的主要触发点。
                                    inputPlaced = true
                                    if (pendingFocusRequest) {
                                        pendingFocusRequest = false
                                        runCatching { focusRequester.requestFocus() }
                                    }
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            textStyle = inputTextStyle.copy(color = MiuixTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            lineLimits = TextFieldLineLimits.MultiLine(
                                minHeightInLines = 1,
                                maxHeightInLines = 6,
                            ),
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                            if (isEditingMessage) {
                                IconButton(
                                    onClick = onCancelMessageEdit,
                                    minWidth = ChatInputActionSize,
                                    minHeight = ChatInputActionSize,
                                ) {
                                    Icon(
                                        imageVector = if (isCoui) Icons.Rounded.Close else MiuixIcons.Glass.Close,
                                        contentDescription = stringResource(R.string.ui_cancel_edit_c698df),
                                        modifier = Modifier.size(ChatInputActionIconSize),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            } else {
                                AgentAttachmentPickerButton(
                                    glassBackdrop = glassBackdrop,
                                    inputContainerTopPx = inputContainerTopPx,
                                    onAttachImage = onAttachImage,
                                    onAttachFiles = onAttachFiles,
                                    onAttachFolder = onAttachFolder,
                                    onAttachFilePath = onAttachFilePath,
                                )

                                Spacer(modifier = Modifier.width(2.dp))

                                if (isCoui || availableReasoningEfforts.isNotEmpty()) {
                                    ThinkingEffortChip(
                                        effort = reasoningEffort,
                                        options = availableReasoningEfforts,
                                        model = modelPickerState.selectedModel,
                                        enabled = !isStreaming,
                                        glassBackdrop = glassBackdrop,
                                        inputContainerTopPx = inputContainerTopPx,
                                        onEffortChange = onReasoningEffortChange,
                                        onRequestModelSheet = openModelSheet,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (showContextUsage) {
                                AgentContextUsageButton(usage = contextUsage, onCompact = onCompactContext, canCompact = canCompactContext && !isStreaming)

                                Spacer(modifier = Modifier.width(2.dp))
                            }


                            val showStreamingSend = isStreaming && !isCompacting && canSend
                            val submitCurrentDraft: (Boolean) -> Unit = { interrupt ->
                                if (canSend) {
                                    dictation.cancel()
                                    val submittedText = textFieldState.text.toString()
                                    textFieldState.clearText()
                                    if (interrupt) {
                                        onSteerSubmit(submittedText)
                                    } else {
                                        onSubmit(submittedText)
                                    }
                                }
                            }

                            val showPrimaryStop = isStreaming && !showStreamingSend
                            val sendCircleSize = if (isCoui) 32.dp else SendButtonVisualSize
                            // 圆底只有两档：可发送走主题色，不可发送与生成中终止都取左侧加号按钮那块底色，
                            // 于是终止态不再是深色实心圆，而是和加号同一颗灰圆。
                            val sendButtonColor by animateColorAsState(
                                targetValue = if (canSend) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    chatInputActionCircleColor()
                                },
                                animationSpec = tween(durationMillis = 160),
                                label = "send_button_color",
                            )
                            val sendIconLabel = if (isEditingMessage && preserveFollowingMessages) {
                                stringResource(R.string.chat_save_message)
                            } else {
                                stringResource(R.string.chat_send)
                            }
                            if (isCoui && canSend) {
                                // 只有「可发送」的蓝底状态走 COUI 圆形图标按钮（轮廓光晕 + 触摸位移 + 压感 + 高光）；
                                // 灰色不可发送与终止态保持原有样式（底色与图标都跟随附件加号按钮）。
                                Box(
                                    modifier = Modifier.size(ChatInputActionSize),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CouiCircleIconButton(
                                        icon = Icons.Rounded.ArrowUpward,
                                        contentDescription = sendIconLabel,
                                        onClick = { submitCurrentDraft(false) },
                                        onLongClick = if (showStreamingSend) {
                                            {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                submitCurrentDraft(true)
                                            }
                                        } else {
                                            null
                                        },
                                        fillColor = CouiTheme.colorSchemeFor(couiDark).accent,
                                        iconColor = Color.White,
                                        buttonSize = sendCircleSize,
                                        iconSize = 18.dp,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(ChatInputActionSize)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            enabled = isStreaming || canSend,
                                            onClick = {
                                                if (showPrimaryStop) {
                                                    onStop()
                                                } else {
                                                    submitCurrentDraft(false)
                                                }
                                            },
                                            onLongClick = if (showStreamingSend) {
                                                {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    submitCurrentDraft(true)
                                                }
                                            } else {
                                                null
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(sendCircleSize)
                                            .clip(CircleShape)
                                            .background(sendButtonColor),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AnimatedContent(
                                            targetState = showPrimaryStop,
                                            transitionSpec = {
                                                (fadeIn(tween(130)) + scaleIn(tween(160), initialScale = 0.72f))
                                                    .togetherWith(
                                                        fadeOut(tween(90)) +
                                                            scaleOut(tween(110), targetScale = 0.72f)
                                                    )
                                            },
                                            label = "send_stop_icon",
                                        ) { stopIcon ->
                                            Icon(
                                                imageVector = if (stopIcon) {
                                                    Icons.Rounded.Stop
                                                } else {
                                                    Icons.Rounded.ArrowUpward
                                                },
                                                contentDescription = when {
                                                    stopIcon -> stringResource(R.string.chat_stop)
                                                    isEditingMessage && preserveFollowingMessages -> stringResource(R.string.chat_save_message)
                                                    else -> stringResource(R.string.chat_send)
                                                },
                                                modifier = Modifier.size(
                                                    if (stopIcon) StopIconSize else if (isCoui) 18.dp else SendIconSize,
                                                ),
                                                // 终止方块与加号图标同色；可发送时仍取 onPrimary 压主题色底。
                                                tint = if (canSend) {
                                                    MiuixTheme.colorScheme.onPrimary
                                                } else {
                                                    couiSendIdleIconColor()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                    }
                    // 余额胶囊相对整行绝对居中：变长时向两侧对称延伸，不会把右侧按钮挤偏。
                    LocalChatInputBalanceCapsule.current?.let { capsule ->
                        Box(modifier = Modifier.align(Alignment.Center)) { capsule() }
                    }
                    }
                }
            }
        }
        // 长按思考强度按钮弹出的会话内选模型弹窗；样式与设置页标题模型弹窗一致。
        ConversationModelPickerSheet(
            visible = showModelSheet,
            pickerState = modelPickerState,
            onDismissRequest = { showModelSheet = false },
            onSelectModel = onModelSelected,
        )
    }

}

/**
 * 思考强度 / 会话模型二合一控件：图标显示当前会话所用模型的品牌标识。
 * 单击弹出思考强度档位；当前模型没有可选档位时，单击直接弹出会话内选模型弹窗。
 * 长按（带震动反馈）始终弹出会话内选模型弹窗。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThinkingEffortChip(
    effort: ReasoningEffort,
    options: List<ReasoningEffort>,
    model: AgentModelOptionUi?,
    enabled: Boolean,
    glassBackdrop: Backdrop?,
    inputContainerTopPx: Float,
    onEffortChange: (ReasoningEffort) -> Unit,
    onRequestModelSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val menuEnabled = enabled && options.size > 1
    /** 长按切模型：这个行为由当前会话模型驱动，没有模型时无从切换。 */
    val modelSwitchEnabled = enabled && model != null
    var chipTopPx by remember { mutableFloatStateOf(0f) }
    var chipBottomPx by remember { mutableFloatStateOf(0f) }
    var measuredPopupHeightPx by remember { mutableFloatStateOf(0f) }
    val active = effort != ReasoningEffort.OFF
    val interactive = enabled && options.size > 1
    val binaryToggleTarget = remember(options, effort) {
        if (options.size == 2 && ReasoningEffort.OFF in options) {
            options.firstOrNull { it != effort }
        } else {
            null
        }
    }
    val anchor = rememberCouiMenuAnchor()
    val popupAnchor = rememberGlassPopupAnchor()
    // 非焦点菜单收不到系统返回键，这里补一条：打开时按返回只关菜单。
    BackHandler(enabled = showPopup) { showPopup = false }
    LaunchedEffect(menuEnabled) {
        if (!menuEnabled) showPopup = false
    }

    val colors = MiuixTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val ink = colors.onSurface.copy(alpha = 1f)
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            colors.primary
        } else {
            ink.copy(alpha = if (isDark) 0.58f else 0.54f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "thinking_content",
    )
    val chipLabel = when (effort) {
        ReasoningEffort.OFF -> stringResource(R.string.reasoning_chip_off)
        ReasoningEffort.DEFAULT -> stringResource(R.string.reasoning_chip_default)
        else -> effort.displayName
    }
    val accessibilityLabel = stringResource(R.string.chat_reasoning_effort, effort.displayName)
    val interactionSource = remember { MutableInteractionSource() }
    val useTransformPopup = !isCoui && binaryToggleTarget == null
    val popupLayout = rememberInputBarPopupLayout(
        inputContainerTopPx = inputContainerTopPx,
        anchorTopPx = chipTopPx,
        anchorBottomPx = chipBottomPx,
        measuredHeightPx = measuredPopupHeightPx,
    )
    // 变形弹层的锚点内容：与输入栏里的圆形品牌图标保持同一视觉。
    val chipContent: @Composable () -> Unit = {
        ModelBrandMark(
            modelId = model?.modelId,
            sourceType = model?.providerSourceType,
            size = ThinkingIconSize,
        )
    }

    Box(modifier = modifier) {
        // 图标跟随会话当前模型：单击仍是思考强度，长按切模型。
        Box(
            modifier = Modifier
                .then(
                    if (useTransformPopup) {
                        Modifier
                            .glassPopupAnchor(
                                anchor = popupAnchor,
                                cornerRadius = 20.dp,
                            )
                            .onGloballyPositioned {
                                val bounds = it.boundsInWindow()
                                chipTopPx = bounds.top
                                chipBottomPx = bounds.bottom
                            }
                    } else {
                        Modifier.couiMenuAnchor(anchor)
                    },
                )
                .size(ChatInputActionSize)
                .clip(CircleShape)
                // 两种样式都只保留模型图标本体：圆形浅色底和细描边统一给左侧附件按钮。
                .then(
                    if (isCoui) {
                        Modifier.couiButtonFeedback(isDark, interactionSource, enabled = interactive)
                    } else {
                        Modifier.glassPress(interactionSource, enabled = interactive)
                    },
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = menuEnabled || modelSwitchEnabled,
                    role = Role.Button,
                    onClick = {
                        when {
                            !isCoui && binaryToggleTarget != null -> {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onEffortChange(binaryToggleTarget)
                            }
                            // COUI 走自己的锚点菜单；MIUIX 多档位直接展开变形弹层。
                            menuEnabled && isCoui -> anchor.open { showPopup = true }
                            menuEnabled -> showPopup = true
                            modelSwitchEnabled -> onRequestModelSheet()
                        }
                    },
                    onLongClick = {
                        if (modelSwitchEnabled) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRequestModelSheet()
                        }
                    },
                )
                .semantics { contentDescription = accessibilityLabel },
            contentAlignment = Alignment.Center,
        ) {
            chipContent()
        }
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                val menuItems = remember(options, effort) {
                    options.map { option ->
                        CouiMenuItem(
                            value = option,
                            text = option.displayName,
                            selected = option == effort,
                        )
                    }
                }
                Menu(
                    expanded = showPopup && menuEnabled,
                    onDismissRequest = { showPopup = false },
                    items = menuItems,
                    onItemClick = { item ->
                        showPopup = false
                        onEffortChange(item.value)
                    },
                    anchor = anchor,
                    // COUI：不抢焦点，键盘保持打开（返回键由上面的 BackHandler 兜住）。
                    properties = NonFocusablePopupProperties,
                )
            }
                AppearanceUiStyle.MIUIX -> {
                    val overlayBackdrop = currentGlassOverlayBackdrop() ?: glassBackdrop
                    TopLayer {
                        GlassTransformPopup(
                            show = showPopup && interactive,
                            onDismissRequest = { showPopup = false },
                            anchor = popupAnchor,
                            backdrop = overlayBackdrop,
                            gap = popupLayout.gap,
                            sizing = GlassPopupSizing(maxHeight = popupLayout.maxHeight),
                            onMeasured = { measuredPopupHeightPx = it.height },
                            anchorContent = {
                                // 不能带上主题色圆底/描边：锚点内容会在收起动画里被放回按钮位置，
                                // 一带底就会闪出那颗蓝圆。
                                // 但也不能只放 24dp 图标：GlassTransformPopup 用固定约束（= 整个
                                // 40dp 锚点控件）量这段内容，图标会被撑满 40dp 并贴在左上角，动画
                                // 期间就比按钮里的图标大一圈且错位。套一层撑满的 Box 居中才对齐。
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    chipContent()
                                }
                            },
                        ) {
                            options.forEach { option ->
                                GlassPopupItem(
                                    text = option.displayName,
                                    selected = option == effort,
                                    onClick = {
                                        showPopup = false
                                        onEffortChange(option)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun PendingImageStrip(
    images: List<PendingImageUi>,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val imagePreviewer = LocalChatImagePreviewer.current
    val itemBounds = remember { HashMap<String, Rect>() }
    var previousCount by remember { mutableIntStateOf(images.size) }
    LaunchedEffect(images.size) {
        if (images.size > previousCount && images.isNotEmpty()) {
            listState.animateScrollToItem(images.lastIndex)
        }
        previousCount = images.size
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = images,
            key = { _, item -> item.id },
        ) { index, image ->
            Box(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        fadeOutSpec = tween(durationMillis = 120),
                    )
                    .attachmentItemEntrance()
                    .size(64.dp)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) itemBounds[image.id] = coords.boundsInWindow()
                    }
                    .graphicsLayer {
                        alpha = if (imagePreviewer?.isPresenting(PendingImagesGroupKey, index) == true) 0f else 1f
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .then(
                        if (imagePreviewer != null) {
                            Modifier.clickable {
                                imagePreviewer.open(
                                    images = images.map { it.dataUrl },
                                    initialIndex = index,
                                    groupKey = PendingImagesGroupKey,
                                    sourceBoundsProvider = { page -> images.getOrNull(page)?.let { itemBounds[it.id] } },
                                    sourceCornerRadius = 12.dp,
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                rememberDataUrlBitmap(image.dataUrl)?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.chat_view_image),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // 可见圆点保持小巧，触控区放大到右上角 32dp，避免误触缩略图。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .clickable(interactionSource = null, indication = null) { onRemoveImage(image.id) },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_remove_image_089db3),
                            modifier = Modifier.size(11.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private const val PendingImagesGroupKey = "pending_images_strip"
