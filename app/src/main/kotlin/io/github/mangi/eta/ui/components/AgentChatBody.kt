package io.github.mangi.eta.ui.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import io.github.mangi.eta.ui.components.coui.CouiCircleIconButton
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.browser.BrowserSessionSnapshot
import io.github.mangi.eta.agent.imagegen.ImageGenToolCatalog
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.repository.ConversationBranchBoundary
import io.github.mangi.eta.ui.model.branchMessageOutline
import io.github.mangi.eta.ui.app.AgentConversationRevisionReducer
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.markdown.StreamingMarkdownStateCache
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.MessageEditUiState
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.mangi.eta.ui.model.PendingImageUi
import io.github.mangi.eta.ui.model.QueuedMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolSummaryMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.model.latestContextUsage
import io.github.mangi.eta.ui.voice.SpeechAutoReadAloud
import io.github.mangi.eta.ui.voice.SpeechPlaybackHost
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.glassPress
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 聊天主体：消息流 + 底部输入框。
 *
 * AI 对话使用正向时间线：第一条消息从对话区顶部开始，后续回复顺序向下追加。
 * 空 assistant 占位不参与布局，避免刚发送时出现一个无内容消息节点。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AgentChatBody(
    messages: List<AgentChatMessageUi>,
    modelPickerState: AgentModelPickerUiState,
    isCompacting: Boolean,
    isBranching: Boolean,
    input: String,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi> = emptyList(),
    inputSyncVersion: Int = 0,
    focusInputOnSync: Boolean = true,
    messageEdit: MessageEditUiState?,
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
    onEditMessage: (String) -> Unit,
    onCancelMessageEdit: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRegenerateMessage: (String) -> Unit,
    onBranchMessage: (String) -> Unit,
    onSelectReplyCandidate: (String, Int) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onRunTraceClick: () -> Unit,
    onOpenBrowser: () -> Unit,
    hasEarlierMessages: Boolean = false,
    messageStartIndex: Int = 0,
    historyLoadState: ConversationHistoryLoadState = ConversationHistoryLoadState.Idle,
    onLoadEarlierMessages: () -> Unit = {},
    onHistoryViewportChanged: (Boolean) -> Unit = {},
    isDrawerOpen: Boolean = false,
    focusInputRequest: Int = 0,
    onFocusInputHandled: () -> Unit = {},
    /** 顶栏高度：进入消息列表的顶部内容内边距，让正文能滑到顶栏毛玻璃下方。 */
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val visibleMessages = remember(messages, messageEdit?.targetMessageId, messageEdit?.preserveFollowingMessages) {
        AgentConversationRevisionReducer.visibleMessagesForEdit(
            messages = messages,
            targetMessageId = messageEdit?.takeUnless { it.preserveFollowingMessages }?.targetMessageId,
        ).filterNot { message ->
            message is AgentMessageUi && message.content.isBlank()
        }
    }
    val initialScrollIndex = remember {
        visibleMessages.toTimelineEntries().size + if (hasEarlierMessages) 1 else 0
    }
    val scrollState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardVisible = imeBottomPx > 0
    val browserSnapshot = LocalAgentBrowserSession.current?.snapshots?.collectAsState()?.value
        ?: BrowserSessionSnapshot()
    val contextUsage = remember(messages, modelPickerState.selectedModel) {
        latestContextUsage(messages, modelPickerState.selectedModel)
    }
    val currentBrowserMessageId = remember(
        visibleMessages,
        browserSnapshot.available,
        browserSnapshot.lastAgentRunId,
        browserSnapshot.lastAgentToolCallId,
    ) {
        val runId = browserSnapshot.lastAgentRunId
        val toolCallId = browserSnapshot.lastAgentToolCallId
        if (!browserSnapshot.available || runId == null || toolCallId == null) {
            null
        } else {
            visibleMessages.lastOrNull { message ->
                message is ToolActivityMessageUi &&
                    message.toolName == "browser_use" &&
                    message.id.startsWith("$runId-tool-") &&
                    message.id.endsWith("-$toolCallId")
            }?.id
        }
    }
    var sentFromKeyboard by remember { mutableStateOf(false) }
    var keepBottomAnchored by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(isStreaming) {
        if (isStreaming && sentFromKeyboard) {
            keyboard?.hide()
            sentFromKeyboard = false
        }
    }

    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            keyboard?.hide()
        }
    }

    SpeechPlaybackHost {
        SpeechAutoReadAloud(messages = messages, isStreaming = isStreaming)
        AgentChatScaffold(
            visibleMessages = visibleMessages,
            hasMessages = visibleMessages.isNotEmpty() || hasEarlierMessages,
            scrollState = scrollState,
            input = input,
            isCompacting = isCompacting,
            isBranching = isBranching,
            contextUsage = contextUsage,
            isStreaming = isStreaming,
            reasoningEffort = reasoningEffort,
            availableReasoningEfforts = availableReasoningEfforts,
            modelPickerState = modelPickerState,
            pendingImages = pendingImages,
            pendingFileReferences = pendingFileReferences,
            queuedMessages = queuedMessages,
            inputSyncVersion = inputSyncVersion,
            focusInputOnSync = focusInputOnSync,
            messageEdit = messageEdit,
            keepBottomAnchored = keepBottomAnchored,
            onBottomAnchorChanged = { keepBottomAnchored = it },
            onSubmit = { text ->
                sentFromKeyboard = true
                // 发送即重新锚定底部：用户从历史上方直接发送时，同帧内 isStreaming 与
                // 新消息一起到位，立即回到底部并恢复后续的流式平滑跟底。
                keepBottomAnchored = true
                onSubmit(text)
            },
            onInterruptAndSubmit = { text ->
                sentFromKeyboard = true
                keepBottomAnchored = true
                onInterruptAndSubmit(text)
            },
            onSteerSubmit = { text ->
                sentFromKeyboard = true
                keepBottomAnchored = true
                onSteerSubmit(text)
            },
            onSteerQueuedMessage = { id ->
                keepBottomAnchored = true
                onSteerQueuedMessage(id)
            },
            onRecallQueuedMessage = onRecallQueuedMessage,
            onRemoveQueuedMessage = onRemoveQueuedMessage,
            onReasoningEffortChange = onReasoningEffortChange,
            onModelSelected = onModelSelected,
            onCompactContext = onCompactContext,
            canCompactContext = canCompactContext,
            onStop = onStop,
            onAttachImage = onAttachImage,
            onRemoveImage = onRemoveImage,
            onAttachFiles = onAttachFiles,
            onAttachFolder = onAttachFolder,
            onAttachFilePath = onAttachFilePath,
            onRemoveFileReference = onRemoveFileReference,
            onEditMessage = onEditMessage,
            onCancelMessageEdit = onCancelMessageEdit,
            onDeleteMessage = onDeleteMessage,
            onRegenerateMessage = onRegenerateMessage,
            onBranchMessage = onBranchMessage,
            onSelectReplyCandidate = onSelectReplyCandidate,
            onSuggestionClick = onSuggestionClick,
            onRunTraceClick = onRunTraceClick,
            onOpenBrowser = onOpenBrowser,
            currentBrowserMessageId = currentBrowserMessageId,
            hasEarlierMessages = hasEarlierMessages,
            messageStartIndex = messageStartIndex,
            historyLoadState = historyLoadState,
            onLoadEarlierMessages = onLoadEarlierMessages,
            onHistoryViewportChanged = onHistoryViewportChanged,
            focusInputRequest = focusInputRequest,
            onFocusInputHandled = onFocusInputHandled,
            topInset = topInset,
            modifier = modifier,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AgentChatScaffold(
    visibleMessages: List<AgentChatMessageUi>,
    hasMessages: Boolean,
    scrollState: LazyListState,
    input: String,
    isCompacting: Boolean,
    isBranching: Boolean,
    contextUsage: AgentContextUsageUi,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi>,
    inputSyncVersion: Int,
    focusInputOnSync: Boolean,
    messageEdit: MessageEditUiState?,
    keepBottomAnchored: Boolean,
    onBottomAnchorChanged: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onInterruptAndSubmit: (String) -> Unit,
    onSteerSubmit: (String) -> Unit,
    onSteerQueuedMessage: (String) -> Unit,
    onRecallQueuedMessage: (String, String) -> Unit,
    onRemoveQueuedMessage: (String) -> Unit,
    modelPickerState: AgentModelPickerUiState,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onModelSelected: (String) -> Unit,
    onCompactContext: () -> Unit,
    canCompactContext: Boolean,
    onStop: () -> Unit,
    onAttachImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAttachFiles: (List<String>) -> Unit,
    onAttachFolder: (String) -> Unit,
    onAttachFilePath: (String) -> Unit,
    onRemoveFileReference: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onCancelMessageEdit: () -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRegenerateMessage: (String) -> Unit,
    onBranchMessage: (String) -> Unit,
    onSelectReplyCandidate: (String, Int) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onRunTraceClick: () -> Unit,
    onOpenBrowser: () -> Unit,
    currentBrowserMessageId: String?,
    hasEarlierMessages: Boolean,
    messageStartIndex: Int,
    historyLoadState: ConversationHistoryLoadState,
    onLoadEarlierMessages: () -> Unit,
    onHistoryViewportChanged: (Boolean) -> Unit,
    focusInputRequest: Int = 0,
    onFocusInputHandled: () -> Unit = {},
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val chatBackdrop = if (isCoui) null else rememberGlassBackdrop()

    // COUI 保留本地消息模糊层；上游已移除全局 LocalBlurEnabled，API/运行时判断仍保留。
    val surfaceColor = MiuixTheme.colorScheme.surface
    val frostEnabled = hasMessages && isRuntimeShaderSupported()
    val messageBackdrop = rememberLayerBackdrop {
        // Backdrop 必须包含不透明底色，否则文字边缘模糊到透明区域时会出现黑边。
        drawRect(surfaceColor)
        drawContent()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(
            left = 0.dp,
            top = 0.dp,
            right = 0.dp,
            bottom = 0.dp,
        ),
        bottomBar = {
            AgentChatBottomBar(
                messageBackdrop = messageBackdrop.takeIf { frostEnabled },
                input = input,
                isCompacting = isCompacting,
                contextUsage = contextUsage,
                showContextUsage = hasMessages,
                isStreaming = isStreaming,
                reasoningEffort = reasoningEffort,
                availableReasoningEfforts = availableReasoningEfforts,
                modelPickerState = modelPickerState,
                pendingImages = pendingImages,
                pendingFileReferences = pendingFileReferences,
                queuedMessages = queuedMessages,
                inputSyncVersion = inputSyncVersion,
                focusInputOnSync = focusInputOnSync,
                messageEdit = messageEdit,
                glassBackdrop = chatBackdrop,
                onSubmit = onSubmit,
                onInterruptAndSubmit = onInterruptAndSubmit,
                onSteerSubmit = onSteerSubmit,
                onSteerQueuedMessage = onSteerQueuedMessage,
                onRecallQueuedMessage = onRecallQueuedMessage,
                onRemoveQueuedMessage = onRemoveQueuedMessage,
                onReasoningEffortChange = onReasoningEffortChange,
                onModelSelected = onModelSelected,
                onCompactContext = onCompactContext,
                canCompactContext = canCompactContext,
                onStop = onStop,
                onAttachImage = onAttachImage,
                onRemoveImage = onRemoveImage,
                onAttachFiles = onAttachFiles,
                onAttachFolder = onAttachFolder,
                onAttachFilePath = onAttachFilePath,
                onRemoveFileReference = onRemoveFileReference,
                onCancelMessageEdit = onCancelMessageEdit,
            )
        },
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .captureGlassBackdrop(chatBackdrop),
        ) {
            if (!hasMessages) {
                EmptyChatState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomPadding),
                )
            } else {
                AgentConversationMessages(
                    visibleMessages = visibleMessages,
                    scrollState = scrollState,
                    isStreaming = isStreaming,
                    topInset = topInset,
                    bottomInset = bottomPadding,
                    keepBottomAnchored = keepBottomAnchored,
                    onBottomAnchorChanged = onBottomAnchorChanged,
                    onSuggestionClick = onSuggestionClick,
                    onRunTraceClick = onRunTraceClick,
                    onOpenBrowser = onOpenBrowser,
                    onEditMessage = onEditMessage,
                    onDeleteMessage = onDeleteMessage,
                    onRegenerateMessage = onRegenerateMessage,
                    onBranchMessage = onBranchMessage,
                    onSelectReplyCandidate = onSelectReplyCandidate,
                    onEditGeneratedImage = onAttachImage,
                    messageActionsEnabled = !isStreaming && !isCompacting && !isBranching && messageEdit == null,
                    editTargetMessageId = messageEdit?.targetMessageId,
                    currentBrowserMessageId = currentBrowserMessageId,
                    hasEarlierMessages = hasEarlierMessages,
                    messageStartIndex = messageStartIndex,
                    historyLoadState = historyLoadState,
                    onLoadEarlierMessages = onLoadEarlierMessages,
                    onHistoryViewportChanged = onHistoryViewportChanged,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (frostEnabled) Modifier.layerBackdrop(messageBackdrop) else Modifier),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AgentConversationMessages(
    visibleMessages: List<AgentChatMessageUi>,
    scrollState: LazyListState,
    isStreaming: Boolean,
    bottomInset: Dp,
    topInset: Dp = 0.dp,
    keepBottomAnchored: Boolean,
    onBottomAnchorChanged: (Boolean) -> Unit,
    assistantOverlay: Boolean = false,
    onSuggestionClick: (String) -> Unit = {},
    onRunTraceClick: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onEditMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onRegenerateMessage: (String) -> Unit = {},
    onBranchMessage: (String) -> Unit = {},
    onSelectReplyCandidate: (String, Int) -> Unit = { _, _ -> },
    onEditGeneratedImage: ((String) -> Unit)? = null,
    messageActionsEnabled: Boolean = false,
    editTargetMessageId: String? = null,
    currentBrowserMessageId: String? = null,
    hasEarlierMessages: Boolean = false,
    messageStartIndex: Int = 0,
    historyLoadState: ConversationHistoryLoadState = ConversationHistoryLoadState.Idle,
    onLoadEarlierMessages: () -> Unit = {},
    onHistoryViewportChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 上一版条目只作复用比对，不参与组合观察。
    val timelineCache = remember { TimelineEntriesCache() }
    val timelineEntries = remember(visibleMessages) {
        visibleMessages.toTimelineEntries(previous = timelineCache.entries)
    }
    SideEffect { timelineCache.entries = timelineEntries }
    // 滚动条按条目 key 记录已测量高度：分页加载更早消息时所有 index 会整体前移，
    // 按 index 缓存会把旧高度错配到别的消息上，进度和长度就会突然跳。
    val scrollIndicatorKeys = remember(timelineEntries, hasEarlierMessages) {
        buildList<Any> {
            if (hasEarlierMessages) add(ChatHistoryBoundaryKey)
            timelineEntries.forEach { add(it.key) }
            add(ChatBottomSentinelKey)
        }
    }
    val activeWorkProcessKey = if (isStreaming) {
        (timelineEntries.lastOrNull() as? AgentTimelineEntry.WorkProcess)?.key
    } else {
        null
    }
    // 复制按钮只出现在每轮对话的最终结果上，中间步骤的过渡文本不提供复制入口。
    // 流式进行中当前这一轮尚未收尾，此时的“最后一条正文”只是中间步骤，不标记。
    val finalResultMessageIds = remember(visibleMessages, isStreaming) {
        resolveFinalResultMessageIds(visibleMessages, isStreaming = isStreaming)
    }
    val branchMessageIds = remember(visibleMessages, isStreaming) {
        ConversationBranchBoundary.eligibleMessageIds(visibleMessages.branchMessageOutline(), running = isStreaming)
    }
    // 流式消息的渲染会话按 id 提升到列表层持有：item 滚出视口被 LazyColumn 销毁后，
    // 滑回时复用同一解析会话与打字机进度，避免整段内容重新解析并重放显现动画。
    val streamingMarkdownCache = remember { StreamingMarkdownStateCache() }
    val streamingMarkdownStates = streamingMarkdownCache.states
    val streamingMessageIds = remember(visibleMessages) {
        visibleMessages.mapNotNullTo(mutableSetOf()) { message ->
            when (message) {
                is AgentMessageUi -> message.id.takeIf { message.isStreaming }
                is ThinkingMessageUi -> message.id.takeIf { message.isStreaming }
                else -> null
            }
        }.also { ids ->
            ids.forEach { streamingMarkdownCache.getOrCreate(it) }
        }
    }
    LaunchedEffect(streamingMarkdownCache, streamingMessageIds) {
        snapshotFlow { streamingMarkdownCache.unusedMessageIds(streamingMessageIds) }
            .collect { unused ->
                if (unused.isNotEmpty()) streamingMarkdownCache.discardUnused(streamingMessageIds)
            }
    }
    val earlierMessagesItemCount = if (hasEarlierMessages) 1 else 0
    val bottomItemIndex = timelineEntries.size + earlierMessagesItemCount
    val isUserDragging by scrollState.interactionSource.collectIsDraggedAsState()
    val isAtBottom by remember(scrollState) {
        derivedStateOf { !scrollState.canScrollForward }
    }
    val isListMeasured by remember(scrollState) {
        derivedStateOf {
            scrollState.layoutInfo.let { it.totalItemsCount > 0 && it.viewportEndOffset > it.viewportStartOffset }
        }
    }
    val densityScale = LocalDensity.current.density
    val density = LocalDensity.current
    // 顶栏占据的高度（px）：正文停靠后首行要落在它下面。
    val topInsetPx = with(density) { topInset.roundToPx() }
    // 列表顶部内边距（顶栏之外的留白，px）：历史会话直接跳落点时要用它抵掉这段留白。
    val chatTopPaddingPx = with(density) { ChatTopContentPadding.roundToPx() }
    // 首行与顶栏底边之间只留这么点缝：必须远小于正文行距，看起来才是「贴着顶栏」。
    val dockGapPx = with(density) { DockGap.roundToPx() }
    // 守卫的提前量（px）：首行进到这个范围就冻结跟底，再交给停靠闭环慢慢收到落点。
    val dockLookaheadPx = with(density) { DockLookahead.roundToPx() }
    // 看门狗的偏离容差：必须远大于像素舍入与行高留白的正常波动，否则就成了逐帧微调。
    val dockWatchdogTolerancePx = with(density) { DockWatchdogTolerance.roundToPx() }
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    val imeInsets = WindowInsets.ime

    var expandSuppressVersion by remember { mutableIntStateOf(0) }
    var suppressAutoReanchor by remember { mutableStateOf(false) }

    LaunchedEffect(expandSuppressVersion) {
        if (expandSuppressVersion == 0) return@LaunchedEffect
        suppressAutoReanchor = true
        delay(EXPAND_REANCHOR_SUPPRESS_MS)
        suppressAutoReanchor = false
    }

    val onExpandItem = remember(onBottomAnchorChanged) {
        {
            suppressAutoReanchor = true
            expandSuppressVersion += 1
            onBottomAnchorChanged(false)
        }
    }

    LaunchedEffect(
        isListMeasured,
        isUserDragging,
        isAtBottom,
        keepBottomAnchored,
        suppressAutoReanchor,
    ) {
        if (!isListMeasured) return@LaunchedEffect
        val next = resolveKeepBottomAnchored(
            current = keepBottomAnchored,
            isUserDragging = isUserDragging,
            isAtBottom = isAtBottom,
            suppressAutoReanchor = suppressAutoReanchor,
        )
        if (next != keepBottomAnchored) {
            onBottomAnchorChanged(next)
        }
    }

    val tailMessage = visibleMessages.lastOrNull() as? AgentMessageUi
    val currentTailMessage by rememberUpdatedState(tailMessage)
    // 显现进度属于尾部条目的高频状态；列表作用域只观察离散化后的布尔值。
    val isTailRendering by remember {
        derivedStateOf {
            currentTailMessage?.let { message ->
                streamingMarkdownStates[message.id]?.let { state ->
                    state.revealedContent != message.content
                }
            } == true
        }
    }
    var isBottomSettling by remember { mutableStateOf(isStreaming) }
    // 键盘把输入器顶高时的一次性跟底：和 isBottomSettling 同一套跟底引擎，只是触发源不同。
    var keyboardLiftSettling by remember { mutableStateOf(false) }
    // 正文末尾（列表尾部哨兵）此刻是否还在视口里 —— 等价于「视口停在底部」。
    // 键盘把输入器顶高只改变底部内容内边距，不会挪动哨兵，所以这个判断在弹起前后一致，
    // 可以拿来区分「本来就停在底部」和「翻在历史中间」。
    val bottomContentVisible by remember(scrollState) {
        derivedStateOf {
            scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.key == ChatBottomSentinelKey
        }
    }

    // 正文停靠：本轮正式回答开始出字就先冻结跟底（不再向下滚），确认它不是过渡文本后，
    // 再把视口收到「正文首行贴着顶栏下方」。中途冒出工具步骤说明是过渡文本，只解冻、不位移。
    var finalAnswerAnchored by remember { mutableStateOf(false) }
    var finalAnswerHandledId by remember { mutableStateOf<String?>(null) }
    // 抖动熔断：本轮所有自动滚动写手全停，等用户手动一划或下一轮开始再恢复。
    var autoScrollFrozen by remember { mutableStateOf(false) }
    // 本轮由「流式停靠」真正落到位的回答 id：首行已经在顶栏下沿，收尾时绝不能再拿
    // 「历史会话首帧落点」把视口按条目顶边重设一次，否则正文会整体往下掉一段。
    var streamDockedAnswerId by remember { mutableStateOf<String?>(null) }
    // 本轮是否正在/刚刚流式过：收尾那一刻要把最后一条回答标成「已处理」，
    // 否则收尾会按「历史会话首帧落点」再硬跳一次（生成完突然瞬移）。
    var hasStreamed by remember { mutableStateOf(false) }
    // 停靠之后用户碰过屏幕：看门狗本轮彻底放手，不许再把视口拽回顶栏。
    var dockUserInterrupted by remember { mutableStateOf(false) }
    // 上面这个标记的置位时刻（elapsedRealtime）。它只按时间衰减，不再挂满整次 run：
    // 以前唯一的清除点是 LaunchedEffect(isStreaming) 里 false→true 那一下，而同一次 run
    // 的多段回答（-16-1 / -18-1 / -29-0 …）期间 isStreaming 一直为 true、该 effect 不重跑，
    // 于是一次拖动 / 一次键盘抬升就把后面每一轮的停靠全部关掉 —— 表现就是「一路跟到底、
    // 压根不停靠」。现在手指离开并静置 DockUserInterruptTtlMs 之后自动恢复。
    var dockInterruptAtMs by remember { mutableLongStateOf(0L) }
    var wasUserDragging by remember { mutableStateOf(false) }
    // 本轮回答已结束、但用户当时没有跟底（滚走了 / 停在会话页上方）：记下这条回答。
    // 收尾阶段不做任何自动滚动，只在「回到底部」圆钮右上角亮一个蓝点，并震一下。
    var pendingAnswerDockKey by remember { mutableStateOf<String?>(null) }
    // 收尾时视口不在底部、但也不能立刻下结论（后台跑完时这一帧还是离开时那一屏）：
    // 记下这条回答，等布局稳定几帧后再决定「贴到答首行」还是「保持原样」。
    var pendingTailSettleKey by remember { mutableStateOf<String?>(null) }
    // 正文首行字顶的窗口 y（px）：停靠闭环直接拿它和顶栏底边比，不换算条目顶边。
    var answerFirstLineWindowY by remember { mutableStateOf<Float?>(null) }
    // 上面这个测量值属于哪一条：换轮之后旧值必须作废，否则会拿上一轮的首行位置误判。
    var answerFirstLineOwnerKey by remember { mutableStateOf<String?>(null) }
    val latestTimelineEntries by rememberUpdatedState(timelineEntries)
    val latestIsStreaming by rememberUpdatedState(isStreaming)
    val streamingAnswerId = tailMessage
        ?.takeIf { message -> message.content.isNotBlank() }
        ?.id
    // 正在流式输出、且位于时间线末尾的正式回答。跟底协程是常驻的，必须通过
    // rememberUpdatedState 读取，不能捕获 effect 启动那一轮的旧值。
    val handoffStreamingAnswerId by rememberUpdatedState(
        streamingAnswerId?.takeIf { id -> isStreaming && timelineEntries.lastOrNull()?.key == id }
    )
    // 历史会话的首帧落点：在 LazyColumn 第一次测量之前就把视口请求到正文条目上。
    // 之前只在 LaunchedEffect 里跳：首帧先按 rememberLazyListState 的初值画在列表底部，
    // 随后再整体上移一大截——那就是用户看到的「突然向上抽动」。
    // requestScrollToItem 会被下一次布局采用，放在布局之前，首帧画出来就已经在落点上。
    val historyDockItemIndex = remember(
        isStreaming,
        streamingAnswerId,
        streamDockedAnswerId,
        finalAnswerAnchored,
        finalAnswerHandledId,
        earlierMessagesItemCount,
        timelineEntries,
        dockUserInterrupted,
    ) {
        when {
            isStreaming -> null
            // 这条回答本轮已经处理过（停靠到位，或收尾时判定为跟底）：视口已在落点上，
            // 这里再按条目顶边请求一次就是无动画硬跳。更关键的是，用户向下翻看时
            // 重组会再走到这里——那正是「翻下去又被拽回停靠位」的来源。
            finalAnswerHandledId == streamingAnswerId -> null
            streamingAnswerId == null -> null
            // 本轮用户碰过屏幕（滚走过 / 停在会话页上方）：视口交给用户，收尾不许再按
            // 「条目顶贴顶栏」请求落点。流式结束那一帧 finalAnswerHandledId 还没来得及
            // 赋值，这里正是「答完瞬间把人拽回去」最直接的入口。
            dockUserInterrupted -> null
            // 这一轮流式期间已经停靠到位：首行就贴着顶栏。收尾时若再按「条目顶贴顶栏」
            // 请求一次，正文会整体下移一个条目内边距（8dp + 首行行距留白），并且因为
            // 该回答已被标记处理过，没有任何逻辑会把它拉回落点——于是位置就永久掉下去。
            finalAnswerAnchored -> null
            streamDockedAnswerId == streamingAnswerId -> null
            timelineEntries.lastOrNull()?.key != streamingAnswerId -> null
            else -> timelineEntries.lastIndex + earlierMessagesItemCount
        }
    }
    val historyDockRequested = remember { intArrayOf(-1) }
    SideEffect {
        val index = historyDockItemIndex
        if (index != null && historyDockRequested[0] != index) {
            historyDockRequested[0] = index
            scrollState.requestScrollToItem(index, scrollOffset = chatTopPaddingPx)
        }
    }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            // 新一轮开始：清掉上一轮的停靠，恢复跟底等待这一轮的正文。
            finalAnswerAnchored = false
            finalAnswerHandledId = null
            streamDockedAnswerId = null
            pendingAnswerDockKey = null
            pendingTailSettleKey = null
            hasStreamed = true
            dockUserInterrupted = false
            dockInterruptAtMs = 0L
            wasUserDragging = false
            autoScrollFrozen = false
            answerFirstLineWindowY = null
            answerFirstLineOwnerKey = null
        } else if (hasStreamed) {
            // 流式收尾：把这一轮的回答标成「已处理」。视口已经由跟底或停靠带到位置，
            // 收尾不许再请求历史落点。首次组合（isStreaming 初值 false）不在这里标记，
            // 否则打开历史会话时会被当成已处理，直接失去停靠。
            hasStreamed = false
            val id = streamingAnswerId
            if (id != null && finalAnswerHandledId == null) finalAnswerHandledId = id
            // 收尾这一帧不能下判断：回答在后台跑完时，前台的帧是停的，这段逻辑要等到
            // 用户点通知回来的第一帧才跑，而那一刻的滚动几何还是「离开时那一屏」的旧值
            // —— 正文其实已经长出好几屏，canScrollForward 却还停在 false，于是一律被判成
            // 「本来就停在底部」，什么都不做，用户看到的就永远是最底部。
            // 现在只先冻结跟底（防止回前台这几帧又被跟底拉到新底部），把落点判定推迟到
            // 布局稳定之后由 pendingTailSettleKey 那个 effect 来做。
            if (id != null && !finalAnswerAnchored) {
                finalAnswerAnchored = true
                pendingTailSettleKey = id
            } else if (id != null) {
                // 流式期间已经停靠到位：收尾不再判定落点，这里直接给本轮收口。
                ChatScrollDiag.endRound(id, "stream_docked")
            } else {
                // 兜底：本轮从未拿到正文 id（停靠 effect 早早 return），也要留下结论。
                ChatScrollDiag.endRound(null, "stream_end_no_answer_id")
            }
            ChatScrollDiag.event(
                "STREAM_END",
                "id=" + id + " anchored=" + finalAnswerAnchored +
                    " handled=" + finalAnswerHandledId + " interrupted=" + dockUserInterrupted,
            )
        }
    }

    LaunchedEffect(
        isStreaming,
        isTailRendering,
        keepBottomAnchored,
        isUserDragging,
        finalAnswerAnchored,
    ) {
        if (finalAnswerAnchored) {
            // 停靠闭环独占滚动：跟底收尾必须让路。否则两边交替写滚动，收口后又抽两下。
            // 也不能让它挂着 isBottomSettling 等「滚回底部」——停靠在顶栏时永远等不到。
            isBottomSettling = false
            return@LaunchedEffect
        }
        if (!keepBottomAnchored || isUserDragging) {
            isBottomSettling = false
        } else if (isStreaming || isTailRendering) {
            isBottomSettling = true
        } else if (isBottomSettling) {
            // 显现完成后还会切换稳定排版并插入操作行，等其完成测量并对齐底部后收口。
            withFrameNanos { }
            withFrameNanos { }
            withTimeoutOrNull(BOTTOM_SETTLE_TIMEOUT_MS) {
                snapshotFlow { !scrollState.canScrollForward }.first { it }
            }
            isBottomSettling = false
        }
    }

    val historyPaging = rememberChatHistoryPaging(
        scrollState = scrollState,
        entries = timelineEntries,
        messageStartIndex = messageStartIndex,
        messageCount = visibleMessages.size,
        hasEarlier = hasEarlierMessages,
        loadState = historyLoadState,
        enabled = !isStreaming && editTargetMessageId == null,
        canReleasePages = !isTailRendering && !isBottomSettling,
        keepBottomAnchored = keepBottomAnchored,
        onBottomAnchorChanged = onBottomAnchorChanged,
        onLoadEarlier = onLoadEarlierMessages,
        onViewportChanged = onHistoryViewportChanged,
    )
    // 停靠落点：把正文首行的字顶压到「顶栏底边 + DockGap」。
    // 基准是首行的窗口 y（屏幕坐标），不是条目顶边：条目内边距、列表 contentPadding、
    // 行高留白都不参与计算，逐帧重新量一次差距闭环，不做一次性位移。
    suspend fun dockAnswerToTop(key: String, itemIndex: Int): Boolean {
        ChatScrollDiag.event("DOCK_ENTER", "key=" + key + " itemIndex=" + itemIndex)
        if (scrollState.layoutInfo.visibleItemsInfo.none { item -> item.key == key }) {
            scrollState.animateScrollToItem(itemIndex)
            withFrameNanos { }
        }
        // 这里不能再用「条目高度不足一屏」提前否决：跟底时条目底部还要让出输入器浮层与
        // 底部内边距，首行刚够到顶栏时条目高度本来就比 viewportHeight 小一截，
        // 拿它判断会把正常长度的正文误判成矮正文，直接放弃停靠、一路跟到底。
        // 矮正文的分流交给守卫：它的首行永远到不了顶栏，根本走不到这里。
        // 落点：首行字顶的窗口 y 应该正好落在顶栏底边。
        val target = topInsetPx + dockGapPx
        var previousFrameNanos = 0L
        // 只统计真正推动滚动的帧：「等正文继续长高」的帧单独计，
        // 否则等待本身就把帧预算烧光，长正文反而等不到收口。
        var frames = 0
        var waitedFrames = 0
        // 进展守卫：残差连续若干帧没有变小，说明有别的写手在往反方向拉视口，
        // 或者这个落点根本到不了。继续逐帧 scrollBy 就是屏幕上整段正文高频上下抽动。
        var bestAbsDelta = Float.MAX_VALUE
        var stalledFrames = 0
        // 等待「正文继续长高把落点带下来」的帧预算只有这么点：跟底被冻结之后列表保持
        // 首个可见条目的锚点，正文变长并不会让首行自己逼近顶栏，长时间等下去只会把画面
        // 冻在原地（以前是 900 帧 ≈ 15 秒），等不到就交回跟底，由外层隔几帧再试一次。
        // 流式期间正文一定还会继续长高，只留一个防死循环的宽松上限；非流式仍用 24 帧。
        // 以前一律 24 帧（≈0.4 秒），正文一时没长到让首行越过落点就判定失败，
        // 重试也只是重复踩同一个坑。
        val growthFrameBudget = if (latestIsStreaming) {
            StreamingGrowthFrameBudget
        } else {
            WaitForGrowthFrameBudget
        }
        while (frames < 900 && waitedFrames < growthFrameBudget) {
            // 用户正在手动滑动，或下方又冒出工具链（这段只是过渡文本）：立刻放弃停靠，交回默认跟底。
            if (isUserDragging || latestTimelineEntries.lastOrNull()?.key != key) {
                ChatScrollDiag.dockResult(
                    success = false,
                    reason = if (isUserDragging) "user_drag" else "tail_changed",
                    frames = frames,
                )
                return false
            }
            val item = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it2 -> it2.key == key }
            if (item == null) {
                // 计帧：条目取不到时不能无限循环，否则每帧一次 scrollToItem 就是可见的抖动。
                frames++
                scrollState.scrollToItem(itemIndex)
                withFrameNanos { }
                continue
            }
            // 首块不是文本块（代码/表格/引用）时拿不到首行字顶，退化为「条目顶边贴顶栏」。
            val delta = (answerFirstLineWindowY ?: item.offset.toFloat()) - target
            ChatScrollDiag.dockFrame(
                frame = frames,
                delta = delta,
                firstLineY = answerFirstLineWindowY,
                itemOffset = item.offset,
                canForward = scrollState.canScrollForward,
                canBackward = scrollState.canScrollBackward,
                fromText = answerFirstLineWindowY != null,
                streaming = latestIsStreaming,
            )
            // 断点追赶：App 在后台时帧停了，正文却继续长高，视口还停在离开时那一屏。
            // 回到前台后的第一次测量，残差常常有半屏以上；这种「用户根本没看到中间过程」
            // 的追赶绝不能播缓出曲线，否则恢复后第一秒整段正文会自己往下滑一段 ——
            // 后台把回答跑完、再点通知进来时看到的那次位移就是它。这里一次对齐，不留位移。
            val viewportPx = scrollState.layoutInfo.let {
                it.viewportEndOffset - it.viewportStartOffset
            }
            if (!latestIsStreaming && viewportPx > 0 && abs(delta) > viewportPx / 3f) {
                try {
                    scrollState.scroll { scrollBy(delta) }
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    return true
                }
                Log.i(
                    ScrollJitterTag,
                    "dock catch-up off-screen: delta=" + delta + " viewport=" + viewportPx,
                )
                // 追赶也要吃帧预算：一旦这个落点物理上到不了（scrollBy 被夹成 0 位移），
                // 残差会一直大于阈值，不计帧就是无上限的每帧一次 scroll —— 屏幕上是抽动。
                frames++
                withFrameNanos { }
                continue
            }
            if (abs(delta) <= DockSnapPx) {
                // 到线就一次对齐收口：不再逐帧 ±1px 微调，也不再要求「连续 3 帧稳定」——
                // 整像素量化让残差在 0/1px 之间反复，前一种写法表现为高频上下抽动，
                // 后一种条件几乎永远不成立，只会把循环空转到帧预算上限。
                if (abs(delta) >= 0.5f) {
                    try {
                        scrollState.scroll { scrollBy(delta) }
                    } catch (cancelled: CancellationException) {
                        if (!currentCoroutineContext().isActive) throw cancelled
                        ChatScrollDiag.dockResult(true, "snap_cancelled", frames)
                        return true
                    }
                }
                ChatScrollDiag.dockResult(true, "snap", frames)
                return true
            }
            // 还要往下沉却已经到底：没有可滚空间，等正文继续长高再收口，不空转报错。
            if (delta > 0f && !scrollState.canScrollForward) {
                waitedFrames++
                withFrameNanos { }
                continue
            }
            // 方向余量守卫：往目标方向已经没有可滚空间时，这一帧绝不发起 scroll。
            // 内容长度刚好卡在一屏时落点在物理上就到不了，照旧每帧发一次 scrollBy 会被
            // 夹成 0 位移，而边界上的回弹动画每帧被重启一次，屏幕上就是正文上下高频抽动。
            // 上方也已到顶（首行越过落点）说明停靠不可达，交回跟底，别把循环空转到帧预算。
            if (delta < 0f && !scrollState.canScrollBackward) {
                ChatScrollDiag.dockResult(false, "no_backward_room", frames)
                return false
            }
            // 残差在变小才算推进；连续若干帧毫无进展就收手，别把循环空转到帧预算上限。
            if (abs(delta) < bestAbsDelta - 0.5f) {
                bestAbsDelta = abs(delta)
                stalledFrames = 0
            } else if (++stalledFrames >= DockStallFrameLimit) {
                Log.w(ScrollJitterTag, "dock stalled: delta=" + delta + " frames=" + frames)
                ChatScrollDiag.dockResult(false, "stalled", frames)
                finalAnswerHandledId = key
                return false
            }
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = if (previousFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            }
            previousFrameNanos = frameNanos
            // 指数缓出：前段快、后段慢，收口时没有可见的一跳。
            frames++
            var step = delta * (1f - exp(-elapsedSeconds / DockTimeConstantSeconds))
            if (abs(step) < 1f) step = if (delta > 0f) 1f else -1f
            if (abs(step) > abs(delta)) step = delta
            try {
                scrollState.scroll { scrollBy(step) }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                return true
            }
        }
        ChatScrollDiag.dockResult(
            success = false,
            reason = if (waitedFrames >= growthFrameBudget) "growth_budget" else "frame_budget",
            frames = frames,
        )
        return false
    }

    // 历史会话打开时的落点：沿用当前结构对齐位置，不再按首行实测二次补偿。
    // 用户已确认这个视觉落点合适；这里只把「条目位置稳定」作为成功判据。
    suspend fun snapAnswerToTop(key: String, itemIndex: Int): Boolean {
        val target = topInsetPx + dockGapPx
        // 列表顶部留白（顶栏高度 + ChatTopContentPadding）正好被吃掉时，条目顶就落在顶栏底边上。
        val structuralOffset = (chatTopPaddingPx - dockGapPx).coerceAtLeast(0)
        // 必须用 scrollToItem：requestScrollToItem 只是登记请求，等下一次测量才采用，
        // 中间那几帧列表还停在原处，随后再被拉正，看上去就是「往上抽一下」。
        scrollState.scrollToItem(itemIndex, scrollOffset = structuralOffset)
        // 等这一跳被布局采用；首行测量只用于诊断，不参与落点判定。
        repeat(2) { withFrameNanos { } }
        var rounds = 0
        var previousItemOffset: Int? = null
        var stableFrames = 0
        var lastFirstLineY: Float? = null
        var lastItemOffset: Int? = null
        var lastDelta: Float? = null
        while (rounds < 10 && currentCoroutineContext().isActive) {
            if (isUserDragging || latestTimelineEntries.lastOrNull()?.key != key) {
                ChatScrollDiag.snapExit(
                    reason = if (isUserDragging) "dragging" else "tail_changed",
                    rounds = rounds,
                    firstLineY = lastFirstLineY,
                    itemOffset = lastItemOffset,
                    delta = lastDelta,
                )
                return false
            }
            val item = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { info -> info.key == key }
            if (item == null) {
                // 这一帧连条目都还没进可视区：重新请求同一个结构落点。
                ChatScrollDiag.snapProbe(
                    label = "no_item",
                    rounds = rounds,
                    rawFirstLineY = null,
                    ownerMatched = false,
                    itemOffset = null,
                )
                scrollState.scrollToItem(itemIndex, scrollOffset = structuralOffset)
                previousItemOffset = null
                stableFrames = 0
                lastItemOffset = null
                rounds++
                withFrameNanos { }
                continue
            }

            val rawFirstLineY = answerFirstLineWindowY
            val ownerMatched = answerFirstLineOwnerKey == key
            lastFirstLineY = rawFirstLineY?.takeIf { ownerMatched }
            lastItemOffset = item.offset
            // 仅记录首行与旧目标的关系，绝不能据此再移动视口。
            lastDelta = lastFirstLineY?.let { firstLineY -> firstLineY - target }

            // 条目连续两帧保持同一位置，就说明结构落点已被布局采用。
            // 不再交叉校验首行与 item.offset：两者还隔着条目内部上边距，
            // 旧校验会把当前可用落点误判为测量无效，空转十轮后才退出。
            if (previousItemOffset == item.offset) {
                stableFrames++
            } else {
                previousItemOffset = item.offset
                stableFrames = 1
            }
            if (stableFrames >= 2) {
                ChatScrollDiag.snapExit(
                    reason = "structural_stable",
                    rounds = rounds,
                    firstLineY = lastFirstLineY,
                    itemOffset = lastItemOffset,
                    delta = lastDelta,
                )
                return true
            }

            ChatScrollDiag.snapProbe(
                label = "settling",
                rounds = rounds,
                rawFirstLineY = rawFirstLineY,
                ownerMatched = ownerMatched,
                itemOffset = item.offset,
            )
            rounds++
            withFrameNanos { }
        }

        // 即使排版仍在微调，也保留当前结构落点，不做首行补偿或回到底部。
        val reachedStructure = lastItemOffset != null
        ChatScrollDiag.snapExit(
            reason = if (reachedStructure) "structural_timeout" else "item_missing",
            rounds = rounds,
            firstLineY = lastFirstLineY,
            itemOffset = lastItemOffset,
            delta = lastDelta,
        )
        return reachedStructure
    }

    // 停靠守卫：跟着跟底引擎逐帧盯正文首行的窗口 y，一旦首行逼近顶栏就立刻返回 true，
    // 让调用方马上冻结跟底、再交给 dockAnswerToTop 收口。
    // 关键是「不许过冲」：以前要等正文长够一屏才动手，等待期间跟底一路把首行推出屏幕，
    // 门槛满足后只能大幅回拉，看起来就是「先滚过头再突然拉回来」。
    // 矮正文的首行永远到不了顶栏，这里就一直等到本轮结束，保持默认跟底行为。
    suspend fun awaitAnswerReachTop(key: String): Boolean {
        var lastFirstLineY: Float? = null
        // 门槛必须就是落点本身。以前这里额外加 dockLookaheadPx（16dp）提前放行，
        // 结果一进停靠循环就必然还差 16dp 要往下滚；而流式跟底此刻通常已经在最底部，
        // 只能「等正文继续长高」，等不到就整轮放弃停靠 —— 那就是「一路跟到底、首行
        // 始终不贴顶栏」的主因。lookahead 只留给跟底冻结当提前量，不参与这里判定。
        val threshold = topInsetPx + dockGapPx
        var waited = 0
        while (currentCoroutineContext().isActive) {
            // streamingAnswerId 在流式结束后还会保留，单靠 LaunchedEffect 的 key 不会取消本协程。
            // 以前这里继续等到 900 帧上限，收尾判定早已完成，守卫还在空转采样。
            if (!latestIsStreaming) {
                ChatScrollDiag.reachTopResult(false, waited, lastFirstLineY)
                return false
            }
            if (isUserDragging) {
                ChatScrollDiag.reachTopResult(false, waited, lastFirstLineY)
                return false
            }
            // 下方又冒出工具链：这段只是过渡正文，交给默认跟底。
            if (latestTimelineEntries.lastOrNull()?.key != key) {
                ChatScrollDiag.reachTopResult(false, waited, lastFirstLineY)
                return false
            }
            val firstLineY = answerFirstLineWindowY
                ?.takeIf { answerFirstLineOwnerKey == key }
            if (firstLineY != null) {
                lastFirstLineY = firstLineY
                if (firstLineY <= threshold) {
                    ChatScrollDiag.reachTopResult(true, waited, firstLineY)
                    return true
                }
            } else {
                // 首块不是文本块（代码/表格/引用）时量不到首行字顶，退化成看条目顶边。
                val item = scrollState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> info.key == key }
                if (item != null && item.offset <= threshold) {
                    ChatScrollDiag.reachTopResult(true, waited, null)
                    return true
                }
            }
            // 死等上限：落点物理不可达时不要一直挂在这里，交回外层重试。
            if (++waited >= AwaitReachTopFrameBudget) {
                ChatScrollDiag.reachTopResult(false, waited, lastFirstLineY)
                return false
            }
            // 纯观测：定期采样这一帧的几何，区分「正文没长高」与「跟底把首行推出去了」。
            ChatScrollDiag.guardSample(
                waited = waited,
                firstLineY = lastFirstLineY,
                itemOffset = scrollState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> info.key == key }?.offset,
                canForward = scrollState.canScrollForward,
                atBottom = !scrollState.canScrollForward,
                streaming = latestIsStreaming,
            )
            withFrameNanos { }
        }
        ChatScrollDiag.guardCancelled(waited, lastFirstLineY)
        return false
    }

    // 正文一出现就盯住它，不等整轮结束：时间线末尾这段长正文就该顶到顶栏。
    // 门槛只有一个：首行逼近顶栏（矮正文保持默认跟底）。
    LaunchedEffect(streamingAnswerId, timelineEntries.lastOrNull()?.key) {
        val answerId = streamingAnswerId ?: return@LaunchedEffect
        if (finalAnswerHandledId == answerId) return@LaunchedEffect
        // 历史会话没有动画可抢：用户此刻真在拖动的话，下面的 snap 自己会退出。
        if (isStreaming && wasUserDragging) return@LaunchedEffect
        val entries = latestTimelineEntries
        val answerIndex = entries.indexOfLast { entry -> entry.key == answerId }
        if (answerIndex < 0) return@LaunchedEffect
        // 时间线最后一条不是它：下方还有工具链，交给默认跟底，等收尾正文再停。
        if (entries.lastOrNull()?.key != answerId) return@LaunchedEffect
        val itemIndex = answerIndex + earlierMessagesItemCount
        ChatScrollDiag.beginRound(
            answerId = answerId,
            streaming = isStreaming,
            topInsetPx = topInsetPx,
            targetY = topInsetPx + dockGapPx,
        )
        if (!isStreaming) {
            // 历史会话：打开就该停在「首行贴顶栏」的位置，不要先停在底部、再当面上移一次。
            // 所以这里跳过守卫和缓出曲线，直接把视口跳到落点（没有可见的上移动画）。
            // 两种情况都算处理完：true = 停靠到位并冻结跟底；false = 落点不可达/被用户打断，
            // 视口留在跟底位置。标记成已处理，避免同一个回答反复重跳、来回抽动。
            val snapped = snapAnswerToTop(key = answerId, itemIndex = itemIndex)
            ChatScrollDiag.snapResult(snapped, itemIndex)
            finalAnswerAnchored = snapped
            finalAnswerHandledId = answerId
            ChatScrollDiag.endRound(answerId, "history_snap")
            return@LaunchedEffect
        }
        // 首行逼近顶栏就立刻冻结跟底（矮正文到不了顶栏，始终走默认跟底）。
        // 一次没停成也不就此罢手：正文还在长，交回跟底、隔几帧再收一次。
        // 只试一次的话，任何一次提前失败都会变成「一路跟到底、完全不停靠」。
        // 用户碰过屏幕（滚走了 / 停在会话页上方）：流式跟顶与收尾停靠一律不做，
        // 免得收尾最后一帧又把正文拉回顶栏。
        if (dockUserInterrupted) {
            ChatScrollDiag.dockSkipped("dock_user_interrupted")
            return@LaunchedEffect
        }
        // 重试不再有次数上限。以前 4 次用完就收摊，可这个 LaunchedEffect 的 key
        // （streamingAnswerId 与时间线末尾条目）在流式期间都不变，不会重组重跑 ——
        // 于是这一轮就静默地一路跟到底了。现在改成「直到真正停住、被标记处理、
        // 用户接管或本轮结束」，节奏由逐步加长的退避控制。
        var attempts = 0
        var backoffFrames = 6
        while (currentCoroutineContext().isActive) {
            // 流式已结束：不再启动新一轮守卫，也不进入退避等待。
            if (!latestIsStreaming) return@LaunchedEffect
            // 停靠循环判定落点物理不可达时会自己写 handled：尊重那个结论，别再重来。
            if (finalAnswerHandledId == answerId) return@LaunchedEffect
            if (isUserDragging) return@LaunchedEffect
            if (latestTimelineEntries.lastOrNull()?.key != answerId) return@LaunchedEffect
            attempts++
            ChatScrollDiag.dockAttemptStart(attempts, itemIndex)
            val reached = awaitAnswerReachTop(key = answerId)
            // await 返回 false 有三种原因：用户接管、末尾换了条目、死等超时。
            // 前两种必须立刻退场；超时只是「这一轮落点还没到」，退避后再来一轮。
            // 流式结束同样立即退场，交给 pendingTailSettleKey 做收尾判定。
            if (!latestIsStreaming) return@LaunchedEffect
            if (isUserDragging) return@LaunchedEffect
            if (latestTimelineEntries.lastOrNull()?.key != answerId) return@LaunchedEffect
            if (reached) {
                finalAnswerAnchored = true
                if (dockAnswerToTop(key = answerId, itemIndex = itemIndex)) {
                    // 只有真正停住了才标记已处理：矮正文、被夹住时要允许正文变长后重来。
                    finalAnswerHandledId = answerId
                    streamDockedAnswerId = answerId
                    dockUserInterrupted = false
                    ChatScrollDiag.event("DOCK_OK", "attempts=" + attempts)
                    return@LaunchedEffect
                }
                finalAnswerAnchored = false
            }
            if (attempts >= DockRetryBackoffStart) {
                backoffFrames = (backoffFrames * 2).coerceAtMost(DockRetryMaxBackoffFrames)
            }
            // 退避期间也可能收到 STREAM_END；逐帧检查，避免再空等最长 60 帧。
            repeat(backoffFrames) {
                if (!latestIsStreaming) return@LaunchedEffect
                withFrameNanos { }
            }
        }
    }

    // 收尾落点判定：等布局按新正文重新测量稳定之后再决定。
    // 三种结局：本来就在底部（保持不动）／用户自己滚走了（不滚动，只亮蓝点+轻震）／
    // 用户没滚走却没在底部（回答在后台跑完、点通知进来）——直接把视口收到答首行贴顶。
    LaunchedEffect(pendingTailSettleKey) {
        val id = pendingTailSettleKey ?: return@LaunchedEffect
        // 后台期间帧是停的，这里天然会等到回前台；再多等几帧让 LazyColumn 完成测量。
        repeat(TailSettleFrameBudget) { withFrameNanos { } }
        if (pendingTailSettleKey != id) return@LaunchedEffect
        pendingTailSettleKey = null
        if (finalAnswerHandledId != null && finalAnswerHandledId != id) {
            // 预埋的 handled 不是这条：不去动视口，但要把冻结的跟底还原。
            finalAnswerAnchored = false
            return@LaunchedEffect
        }
        val atBottomNow = !scrollState.canScrollForward && bottomContentVisible
        if (atBottomNow) {
            // 就在底部：还原跟底，什么都不动。
            ChatScrollDiag.tailSettle("at_bottom")
            ChatScrollDiag.endRound(id)
            finalAnswerAnchored = false
            return@LaunchedEffect
        }
        val entries = latestTimelineEntries
        val answerIndex = entries.indexOfLast { entry -> entry.key == id }
        if (answerIndex < 0) {
            ChatScrollDiag.tailSettle("answer_missing")
            ChatScrollDiag.endRound(id)
            finalAnswerAnchored = false
            return@LaunchedEffect
        }
        if (entries.lastOrNull()?.key != id) {
            // 下方还有工具链：这条不是收尾正文，交回跟底。
            ChatScrollDiag.tailSettle("tail_is_not_answer")
            ChatScrollDiag.endRound(id)
            finalAnswerAnchored = false
            return@LaunchedEffect
        }
        if (dockUserInterrupted) {
            // 用户自己滚走的：绝不抢滚动，只在圆钮上加蓝点、轻震一次，等用户自己点。
            ChatScrollDiag.tailSettle("user_interrupted_badge")
            ChatScrollDiag.endRound(id)
            finalAnswerAnchored = false
            pendingAnswerDockKey = id
            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            return@LaunchedEffect
        }
        // 剩下的就是「后台跑完、点通知进来」：直接把视口收到这条回答的首行贴顶。
        // 没有位移动画，也不亮蓝点——用户第一眼要看的本来就是回答的开头。
        finalAnswerAnchored = true
        if (dockAnswerToTop(key = id, itemIndex = answerIndex + earlierMessagesItemCount)) {
            streamDockedAnswerId = id
            ChatScrollDiag.tailSettle("docked_to_first_line", "index=" + answerIndex)
            ChatScrollDiag.endRound(id)
            Log.i(ScrollJitterTag, "dock tail settle: docked to first line")
        } else {
            finalAnswerAnchored = false
            ChatScrollDiag.tailSettle("unreachable_keep_viewport", "index=" + answerIndex)
            ChatScrollDiag.endRound(id)
            Log.i(ScrollJitterTag, "dock tail settle: unreachable, keep viewport")
        }
    }

    // 蓝点提示「回到这条回答的首行」只在「首行还在顶栏下方」时才有意义。
    // 首行一旦上滑越过顶栏下沿，说明用户已经在往下读这条回答，此时点它反而是往回跳 ——
    // 于是自动取消停靠、去掉蓝点，「回到底部」按钮回归本来的「滚到列表最底部」语义。
    LaunchedEffect(pendingAnswerDockKey) {
        val pending = pendingAnswerDockKey ?: return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            if (pendingAnswerDockKey != pending) return@LaunchedEffect
            val firstLineY = answerFirstLineWindowY?.takeIf { answerFirstLineOwnerKey == pending }
            // 首行量不到（首块不是文本）时退化成看条目顶边：条目顶已经高过顶栏下沿，
            // 说明整条回答都滚到顶栏上面去了。
            val passedTop = if (firstLineY != null) {
                firstLineY <= topInsetPx - DockExceedTolerancePx
            } else {
                val itemTop = scrollState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> info.key == pending }
                    ?.offset
                itemTop != null && itemTop <= topInsetPx - DockExceedTolerancePx
            }
            if (passedTop) {
                pendingAnswerDockKey = null
                Log.i(ScrollJitterTag, "dock badge cleared: first line above top bar")
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    // 停靠之后下方又冒出工具链（时间线末尾不再是这段正文）：立刻解冻，恢复流式默认滚动。
    LaunchedEffect(timelineEntries.lastOrNull()?.key) {
        if (autoScrollFrozen) return@LaunchedEffect
        if (!finalAnswerAnchored) return@LaunchedEffect
        val handled = finalAnswerHandledId ?: return@LaunchedEffect
        if (timelineEntries.lastOrNull()?.key == handled) return@LaunchedEffect
        // 单向让路：交还滚动权给跟底，但保持「已处理」，同一条正文不再回头重钉。
        finalAnswerAnchored = false
    }

    // 停靠看门狗：流式结束后不再逐帧闭环，只隔一段时间量一次首行位置。
    // 偏离超过容差才纠正一次，纠满上限或用户一碰屏幕就彻底放手，避免退化成新的抖动源。
    LaunchedEffect(isStreaming, finalAnswerAnchored, finalAnswerHandledId, autoScrollFrozen) {
        // 看门狗已停用：自动跟顶只负责流式这段（由 dockAnswerToTop 逐帧闭环收口），
        // 回答结束就彻底放手。以前它流式结束后还会隔 500ms 量一次首行、偏离就滚回去，
        // 键盘弹起把视口顶上去之后又被拽回原处，就是它干的。
        if (!DockWatchdogEnabled) return@LaunchedEffect
        if (isStreaming || !finalAnswerAnchored || autoScrollFrozen) return@LaunchedEffect
        val key = finalAnswerHandledId ?: return@LaunchedEffect
        val target = topInsetPx + dockGapPx
        var corrections = 0
        while (currentCoroutineContext().isActive && corrections < DockWatchdogLimit) {
            delay(DockWatchdogIntervalMs)
            // 用户只要碰过屏幕（或此刻正在滚），看门狗本轮就彻底放手：
            // 否则手指刚抬起、下次检查正好落在两次滚动之间，就会把用户翻走的位置拽回顶栏。
            if (dockUserInterrupted || isUserDragging || scrollState.isScrollInProgress) {
                return@LaunchedEffect
            }
            if (autoScrollFrozen || !finalAnswerAnchored) return@LaunchedEffect
            if (latestTimelineEntries.lastOrNull()?.key != key) return@LaunchedEffect
            val firstLineY = answerFirstLineWindowY?.takeIf { answerFirstLineOwnerKey == key }
                ?: continue
            val delta = firstLineY - target
            if (abs(delta) <= dockWatchdogTolerancePx) continue
            val hasRoom = if (delta > 0f) scrollState.canScrollForward else scrollState.canScrollBackward
            if (!hasRoom) return@LaunchedEffect
            try {
                scrollState.scroll { scrollBy(delta) }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                return@LaunchedEffect
            }
            corrections++
            Log.d(ScrollJitterTag, "dock watchdog corrected: delta=" + delta + " count=" + corrections)
        }
    }

    LaunchedEffect(isUserDragging, isAtBottom) {
        if (isUserDragging) {
            wasUserDragging = true
            dockUserInterrupted = true
            dockInterruptAtMs = SystemClock.elapsedRealtime()
            ChatScrollDiag.userInterrupted("drag")
            // 用户一动手就解除抖动熔断：视口交还给人，自动滚动不再抢。
            autoScrollFrozen = false
        } else if (wasUserDragging && isAtBottom) {
            // 用户主动滑回底部：放弃本轮停靠，恢复跟底；提示点也就没意义了。
            // 接管状态一并解除：人都自己回到最底了，再挂着它只会把后面几轮的自动停靠
            // 一起关掉 —— 那正是「一路跟到底、压根不停靠」的另一条入口。
            wasUserDragging = false
            finalAnswerAnchored = false
            pendingAnswerDockKey = null
            dockUserInterrupted = false
            dockInterruptAtMs = 0L
        }
    }

    // 接管状态只在「仍等本轮停靠交接」时按时间衰减。用户已经滚到别处看旧内容时，
    // dockUserInterrupted 必须留到收尾，蓝点/震动才走「不抢滚动、只提示」的分支。
    LaunchedEffect(dockInterruptAtMs) {
        val stamp = dockInterruptAtMs
        if (stamp == 0L) return@LaunchedEffect
        delay(DockUserInterruptTtlMs)
        if (dockInterruptAtMs != stamp || isUserDragging) return@LaunchedEffect
        val id = handoffStreamingAnswerId ?: return@LaunchedEffect
        if (finalAnswerHandledId == id) return@LaunchedEffect
        if (latestTimelineEntries.lastOrNull()?.key != id) return@LaunchedEffect
        val itemVisible = scrollState.layoutInfo.visibleItemsInfo
            .any { info -> info.key == id }
        if (!itemVisible) return@LaunchedEffect
        // 流式正文还在视口里：把接管理解成「这帧短暂接管一下」，恢复自动停靠；
        // 正文不在视口：用户已经离开回答区，保持接管状态，等收尾亮蓝点。
        dockUserInterrupted = false
        ChatScrollDiag.event("DOCK_INTERRUPT_EXPIRED", "heldMs=" + DockUserInterruptTtlMs)
    }

    // 在消息列表上滑动（上翻或下翻）就自动收起键盘；键盘本来没弹着时什么也不做。
    // 用列表自己的拖动状态判断，不拦截也不消费滚动手势。
    LaunchedEffect(isUserDragging) {
        if (isUserDragging && imeInsets.getBottom(density) > 0) {
            keyboard?.hide()
        }
    }

    // 键盘弹起会把输入器连同 IME 内边距一起顶高。三种窗口形态都要照顾到：
    // 1) bottomBar 自己 imePadding：列表底部内容内边距变大（以前只认这一种）；
    // 2) 窗口 adjustResize：视口被压矮，底部内边距一点没变；
    // 3) 两者同时发生。
    // 只看 (1) 的话，(2) 这种窗口里整个效果根本不触发 —— 正文一动不动，最后一行
    // 被键盘盖住，正是「点了输入框，回答没跟着抬上来」。
    val imeBottomPx = imeInsets.getBottom(density)
    val viewportHeightPx by remember(scrollState) {
        derivedStateOf {
            scrollState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
        }
    }
    // 布局实测高度：键盘让位时列表一定会被压实。这个值由 LazyColumn 的 onSizeChanged
    // 上报，不依赖 WindowInsets 在当前节点是否还可观察，是唯一可靠的键盘抬升信号。
    var measuredListHeightPx by remember { mutableStateOf(0) }
    // 贴底状态缓存。只能在「视口高度没有变化」的稳定布局里取样：键盘让位那一帧列表被压实，
    // canScrollForward 会立刻从 false 翻成 true，此时再取实时值就会把「本来就停在底部」
    // 误判成「用户翻在历史中间」，于是整段抬升逻辑什么都不做 —— 这正是点了输入框、正文不抬的直接原因。
    var stableAtBottom by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState) {
        var lastHeight = -1
        snapshotFlow {
            val info = scrollState.layoutInfo
            (info.viewportEndOffset - info.viewportStartOffset) to !scrollState.canScrollForward
        }.collect { (height, atBottom) ->
            if (lastHeight < 0 || height == lastHeight) stableAtBottom = atBottom
            lastHeight = height
        }
    }
    var previousBottomInset by remember { mutableStateOf(bottomInset) }
    var previousImeBottomPx by remember { mutableStateOf(imeBottomPx) }
    var previousViewportHeightPx by remember { mutableStateOf(0) }
    var previousMeasuredListHeightPx by remember { mutableStateOf(0) }
    LaunchedEffect(bottomInset, imeBottomPx, viewportHeightPx, measuredListHeightPx) {
        val insetGrew = bottomInset > previousBottomInset
        val imeGrew = imeBottomPx > previousImeBottomPx
        val viewportShrank =
            previousViewportHeightPx > 0 && viewportHeightPx < previousViewportHeightPx
        val measuredShrank = previousMeasuredListHeightPx > 0 &&
            measuredListHeightPx < previousMeasuredListHeightPx
        val prevInset = previousBottomInset
        val prevMeasured = previousMeasuredListHeightPx
        previousBottomInset = bottomInset
        previousImeBottomPx = imeBottomPx
        previousViewportHeightPx = viewportHeightPx
        previousMeasuredListHeightPx = measuredListHeightPx
        val changed = insetGrew || imeGrew || viewportShrank || measuredShrank
        // 这里不能用实时几何：键盘把视口压矮之后 canScrollForward 已经是新值，
        // 取实时值必然判成「不在底部」。用稳定布局期采样下来的缓存。
        val wasAtBottom = stableAtBottom
        // 诊断用：无论判定结果如何都留一条，便于区分「信号没来」和「信号来了但被拦」。
        Log.i(
            ScrollJitterTag,
            "ime/lift: ime=" + imeBottomPx + " inset=" + bottomInset +
                " vp=" + viewportHeightPx + " measured=" + measuredListHeightPx +
                " prevInset=" + prevInset + " prevMeasured=" + prevMeasured +
                " changed=" + changed + " wasAtBottom=" + wasAtBottom +
                " anchored=" + finalAnswerAnchored + " keep=" + keepBottomAnchored +
                " dragging=" + isUserDragging + " streaming=" + isStreaming,
        )
        if (!changed) return@LaunchedEffect
        if (isStreaming || isUserDragging) return@LaunchedEffect
        if (!wasAtBottom) {
            // 翻在历史中间，或停靠在顶栏且下方还有内容：维持阅读位置，不抢滚动。
            if (finalAnswerAnchored) {
                finalAnswerAnchored = false
                dockUserInterrupted = true
                dockInterruptAtMs = SystemClock.elapsedRealtime()
                ChatScrollDiag.userInterrupted("keyboard_lift")
            }
            return@LaunchedEffect
        }
        // 视口确实停在底部：残留的停靠状态一律作废，然后直接贴到新的底部。
        // 不再只靠跟底引擎——它可能被锚点判定或抖动熔断挡着，抬一半就停住。
        // IME inset 逐帧变化时这里逐帧贴底，看上去就是正文跟着键盘一起平移。
        // 这里绝不能标「用户接管」：只是弹了下键盘、视口本来就在底部，用户要的仍是跟底。
        // 以前这里置 true，于是一次打字就把后面每一轮的自动停靠全部关掉。
        finalAnswerAnchored = false
        dockUserInterrupted = false
        dockInterruptAtMs = 0L
        if (!keepBottomAnchored) onBottomAnchorChanged(true)
        scrollState.scrollToItem(bottomItemIndex)
        keyboardLiftSettling = true
    }

    // 抬升到位就收口，别让跟底一直挂着（与流式收尾用同一套判定）。
    LaunchedEffect(keyboardLiftSettling, keepBottomAnchored) {
        if (!keyboardLiftSettling) return@LaunchedEffect
        if (!keepBottomAnchored) {
            // 用户把列表拉走了，这次抬升不再适用。
            keyboardLiftSettling = false
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        snapshotFlow { !scrollState.canScrollForward }.first { it }
        keyboardLiftSettling = false
    }

    // 抖动熔断：无人操作时视口被来回拉锯（两个写手互相抵消、或落点在物理上到不了），
    // 屏幕上就是整段正文高频上下抽动。这里逐帧盯首个可见条目的偏移，方向反复翻转就停掉
    // 本轮所有自动滚动——用户手动一划或下一轮开始再恢复。
    //
    // 两条硬约束（0318 的教训）：
    // 1. 流式期间一律不判：跟底与停靠已经用 finalAnswerAnchored 互斥，同时写滚动的情况没了；
    //    一旦把正常的流式跟底误判成拉锯，整轮画面会停在原地不动，直到收尾才猛地跳一下。
    // 2. 位置只在「同一个首个可见条目」内部比较：首个可见条目换成上一条时索引会跳一整条，
    //    以前用 index*1e6+offset 拼成一个数比较，索引变化就被当成一次巨大反向位移，
    //    五次就凑满翻转计数——这是误判的直接来源。
    // 熔断时也不再写 finalAnswerHandledId：那会把这一轮回答永久标成「已处理」，
    // 后续再没有任何逻辑会去停靠它。
    LaunchedEffect(scrollState) {
        var lastIndex = -1
        var lastOffset = Int.MIN_VALUE
        var lastDirection = 0
        var flips = 0
        var framesSinceFlip = 0
        while (currentCoroutineContext().isActive) {
            withFrameNanos { }
            if (isUserDragging || latestIsStreaming) {
                lastIndex = -1
                lastOffset = Int.MIN_VALUE
                lastDirection = 0
                flips = 0
                framesSinceFlip = 0
                continue
            }
            val first = scrollState.layoutInfo.visibleItemsInfo.firstOrNull() ?: continue
            if (first.index != lastIndex) {
                // 换了一条：位置基准重置，不参与方向翻转统计。
                lastIndex = first.index
                lastOffset = first.offset
                lastDirection = 0
                flips = 0
                framesSinceFlip = 0
                continue
            }
            val previous = lastOffset
            lastOffset = first.offset
            if (previous == Int.MIN_VALUE) continue
            val delta = first.offset - previous
            framesSinceFlip++
            if (framesSinceFlip > ScrollJitterFlipWindowFrames) flips = 0
            if (delta == 0) continue
            val direction = if (delta > 0) 1 else -1
            if (lastDirection != 0 && direction != lastDirection) {
                flips++
                framesSinceFlip = 0
            }
            lastDirection = direction
            if (flips < ScrollJitterFlipLimit || autoScrollFrozen) continue
            flips = 0
            autoScrollFrozen = true
            finalAnswerAnchored = false
            isBottomSettling = false
            keyboardLiftSettling = false
            ChatScrollDiag.jitterFrozen(first.index, first.offset)
            Log.w(ScrollJitterTag, "jitter guard tripped: index=" + first.index + " offset=" + first.offset)
        }
    }

    val shouldFollowBottom by rememberUpdatedState(
        resolveBottomFollowEnabled(
            isStreaming = isStreaming,
            keepBottomAnchored = keepBottomAnchored,
            isUserDragging = isUserDragging,
            isBottomSettling = isBottomSettling,
            keyboardLiftSettling = keyboardLiftSettling,
        ) && (!finalAnswerAnchored || keyboardLiftSettling) && !autoScrollFrozen
    )
    // 键盘抬升期间切到更快的位移曲线（KEYBOARD_LIFT_*）：和键盘弹起同步，而不是慢慢追。
    val keyboardLiftActive by rememberUpdatedState(keyboardLiftSettling && !isStreaming)
    val currentBottomItemIndex by rememberUpdatedState(bottomItemIndex)
    val bottomFollowDecisions = remember(scrollState) {
        Channel<BottomFollowDecision>(Channel.CONFLATED)
    }

    LaunchedEffect(
        bottomItemIndex,
        keepBottomAnchored,
        isUserDragging,
        isStreaming,
        finalAnswerAnchored,
        suppressAutoReanchor,
    ) {
        // 停靠持有期间一律不登记跳底：任何滚动都要给它让路（手动除外）。
        // 正式回答已经出现时也不在这里硬跳，交给平滑追赶与停靠守卫交接。
        if (isStreaming && keepBottomAnchored) {
            suppressAutoReanchor = false
        }
        if (shouldRequestInitialBottom(
                isStreaming = isStreaming,
                keepBottomAnchored = keepBottomAnchored,
                isUserDragging = isUserDragging,
            ) && !finalAnswerAnchored && handoffStreamingAnswerId == null
        ) {
            ChatScrollDiag.event(
                "BOTTOM_REQUEST",
                "source=initial index=" + currentBottomItemIndex,
            )
            scrollState.requestScrollToItem(currentBottomItemIndex)
        }
    }

    // 流式输出及渲染收尾期间发布最新的跟底距离。历史消息中的步骤/思考展开同样会改变
    // 列表高度，但那是用户主动查看内容，不能被误判成尾部文字增长。
    LaunchedEffect(scrollState) {
        snapshotFlow {
            val layoutInfo = scrollState.layoutInfo
            val sentinel = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key == ChatBottomSentinelKey
            }
            BottomFollowLayout(
                enabled = shouldFollowBottom,
                bottomItemIndex = currentBottomItemIndex,
                // 正式回答正在等待停靠交接时，底部哨兵一旦离开视口，绝不能直接
                // requestScrollToItem(底部)：那会把首行瞬间推过顶栏，再由停靠拉回。
                allowMissingSentinelJump = handoffStreamingAnswerId == null,
                // 哨兵不可见时，offset 变化也要让 snapshotFlow 持续发追赶目标；
                // 否则一个视口的代理距离走完后，长正文可能停在半路上。
                missingSentinelScrollOffset = scrollState.firstVisibleItemScrollOffset
                    .takeIf { sentinel == null },
                sentinelBottom = sentinel?.let { it.offset + it.size },
                // 输入器高度属于滚动内容的 bottom inset，而不是滚动容器高度。
                // 跟底目标应是 afterContentPadding 之前的正文边界。
                viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding,
                lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index,
            )
        }
            .distinctUntilChanged()
            .collect { layout ->
                val decision = resolveBottomFollowDecision(
                    enabled = layout.enabled,
                    bottomItemIndex = layout.bottomItemIndex,
                    sentinelBottom = layout.sentinelBottom,
                    viewportEnd = layout.viewportEnd,
                    lastVisibleIndex = layout.lastVisibleIndex,
                    allowMissingSentinelJump = layout.allowMissingSentinelJump,
                )
                bottomFollowDecisions.trySend(decision)
            }
    }

    // 一个持续存在的帧时钟从当前屏幕位置追向最新目标。新字符继续到达时只更新目标，
    // 不取消并重启动画，因此速度连续；用户开始拖动后，enabled=false 会立即停止跟随。
    LaunchedEffect(scrollState, bottomFollowDecisions) {
        var remainingDistancePx = 0f
        var requestIndex: Int? = null
        var previousFrameNanos = 0L

        fun accept(decision: BottomFollowDecision) {
            remainingDistancePx = decision.scrollByPx.toFloat()
            requestIndex = decision.requestIndex
        }

        // shouldFollowBottom 来自 rememberUpdatedState，要等下一次重组才刷新。
        // 停靠接管的那一帧它可能仍是 true，跟底协程便会再补一次向前滚动，
        // 随后 dockAnswerToTop 又把正文拉回来 —— 实测就是首行先多冲 18～35px 再回弹。
        // 这里同时直读快照状态，并在真正执行 scrollBy 的 ScrollScope 内再查一次，
        // 连「检查通过后才被挂起、恢复时已切到停靠」的排队写入也一起挡住。
        // 首行已经到达守卫门槛时，跟底必须在同一帧停笔，不能等外层协程
        // 设置 finalAnswerAnchored。否则从测量到接管之间仍可能再推进一次。
        // 若停靠已经判定不可达（handled），或用户/键盘正在接管，则不在此拦截。
        fun waitingForDockHandoff(): Boolean {
            if (dockUserInterrupted || keyboardLiftSettling) return false
            val id = handoffStreamingAnswerId ?: return false
            if (finalAnswerHandledId == id) return false
            if (latestTimelineEntries.lastOrNull()?.key != id) return false
            // 这里只认首行字顶门槛。条目 item.offset 与 firstLineY 不是同一坐标基准：
            // 用 item.offset <= topInsetPx 会提前几百像素停笔，而 awaitAnswerReachTop
            // 仍在等首行到线，结果画面停住，直到收尾才突然跳到答首行。
            val firstLineY = answerFirstLineWindowY
                ?.takeIf { answerFirstLineOwnerKey == id }
                ?: return false
            return firstLineY <= topInsetPx + dockGapPx
        }

        fun canFollowNow(): Boolean =
            shouldFollowBottom && (!finalAnswerAnchored || keyboardLiftSettling) &&
                !autoScrollFrozen && !waitingForDockHandoff()

        while (currentCoroutineContext().isActive) {
            if (remainingDistancePx <= 0f && requestIndex == null) {
                accept(bottomFollowDecisions.receive())
                previousFrameNanos = 0L
            }
            while (true) {
                val latest = bottomFollowDecisions.tryReceive().getOrNull() ?: break
                accept(latest)
            }

            if (!canFollowNow()) {
                remainingDistancePx = 0f
                requestIndex = null
                continue
            }

            val targetIndex = requestIndex
            if (targetIndex != null) {
                ChatScrollDiag.event(
                    "BOTTOM_REQUEST",
                    "source=missing_sentinel index=" + targetIndex,
                )
                scrollState.requestScrollToItem(targetIndex)
                requestIndex = null
                remainingDistancePx = 0f
                continue
            }
            if (remainingDistancePx <= 0f) continue

            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = if (previousFrameNanos == 0L) {
                1f / 60f
            } else {
                ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            }
            previousFrameNanos = frameNanos

            while (true) {
                val latest = bottomFollowDecisions.tryReceive().getOrNull() ?: break
                accept(latest)
            }
            if (!canFollowNow() || requestIndex != null || remainingDistancePx <= 0f) continue

            val step = if (keyboardLiftActive) {
                smoothBottomFollowStep(
                    distancePx = remainingDistancePx,
                    elapsedSeconds = elapsedSeconds,
                    density = densityScale,
                    responseSeconds = KEYBOARD_LIFT_RESPONSE_SECONDS,
                    maxSpeedDpPerSecond = KEYBOARD_LIFT_MAX_SPEED_DP_PER_SECOND,
                )
            } else {
                smoothBottomFollowStep(
                    distancePx = remainingDistancePx,
                    elapsedSeconds = elapsedSeconds,
                    density = densityScale,
                )
            }
            var consumedStep = 0f
            try {
                scrollState.scroll {
                    // scrollState.scroll 可能先排队挂起；恢复时必须按此刻的停靠状态决定，
                    // 不能把挂起前算出的那一步继续写进视口。
                    if (canFollowNow()) {
                        scrollBy(step)
                        consumedStep = step
                    }
                }
                remainingDistancePx = if (consumedStep > 0f) {
                    (remainingDistancePx - consumedStep).coerceAtLeast(0f)
                } else {
                    0f
                }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                remainingDistancePx = 0f
            }
        }
    }

    // 滚动层保持整屏，输入器作为后绘制浮层；输入器高度进入列表的
    // afterContentPadding，确保跟到底部时最后一行停在输入器上方。
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val messagesBackdrop = if (isCoui || assistantOverlay) null else rememberGlassBackdrop()
    CompositionLocalProvider(LocalOnChatItemExpand provides onExpandItem) {
        Box(modifier = modifier.clipToBounds()) {
            LazyColumn(
                state = scrollState,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxSize()
                    .captureGlassBackdrop(messagesBackdrop)
                    .onSizeChanged { measuredListHeightPx = it.height }
                    .scrollEndHaptic()
                    .nestedScroll(historyPaging.scrollConnection)
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    // 列表视口铺满整屏，顶栏高度在这里做内边距，正文才能滑到顶栏毛玻璃下方。
                    top = topInset + ChatTopContentPadding,
                    bottom = bottomInset + 14.dp,
                ),
                overscrollEffect = null,
            ) {
                if (hasEarlierMessages) {
                    item(key = ChatHistoryBoundaryKey) {
                        AgentHistoryLoadStatus(
                            state = historyLoadState,
                            enabled = !isStreaming && editTargetMessageId == null,
                            onRetry = historyPaging.retry,
                        )
                    }
                }
                items(
                    items = timelineEntries,
                    key = { it.key },
                    contentType = { it.contentType() },
                ) { entry ->
                    val itemModifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 180),
                        placementSpec = null,
                        // 历史轮次被编辑、删除或重新生成时必须立即退出；退出动画会让已从
                        // 状态中裁掉的旧消息继续绘制，并与同位置的新流式消息短暂重叠。
                        fadeOutSpec = null,
                    )
                    when (entry) {
                        is AgentTimelineEntry.Message -> {
                            val message = entry.message
                            ChatMessageItem(
                                message = message,
                                assistantOverlay = assistantOverlay,
                                retainedStreamingState = (message as? AgentMessageUi)
                                    ?.takeIf { it.isStreaming || streamingMarkdownStates.containsKey(it.id) }
                                    ?.let { agentMessage ->
                                        streamingMarkdownCache.getOrCreate(agentMessage.id)
                                    },
                                onSuggestionClick = onSuggestionClick,
                                onRunTraceClick = onRunTraceClick,
                                onOpenBrowser = onOpenBrowser,
                                showBrowserShortcut = message is ToolActivityMessageUi &&
                                    message.toolName == "browser_use" &&
                                    message.id == currentBrowserMessageId,
                                showCopyAction = message !is AgentMessageUi ||
                                    message.characterEditable || message.id in finalResultMessageIds,
                                showMessageActions = !assistantOverlay && (
                                    message.id in finalResultMessageIds ||
                                        (message is AgentMessageUi && message.characterEditable)
                                    ),
                                messageActionsEnabled = messageActionsEnabled,
                                showBranchAction = !assistantOverlay && message.id in branchMessageIds,
                                onBranchMessage = onBranchMessage,
                                isEditing = message.id == editTargetMessageId,
                                onEditMessage = onEditMessage,
                                onDeleteMessage = onDeleteMessage,
                                onRegenerateMessage = onRegenerateMessage,
                                onSelectReplyCandidate = onSelectReplyCandidate,
                                onAnswerFirstLineTop = if (entry.key == timelineEntries.lastOrNull()?.key) {
                                    { windowY ->
                                        answerFirstLineWindowY = windowY
                                        answerFirstLineOwnerKey = entry.key
                                    }
                                } else {
                                    null
                                },
                                onEditGeneratedImage = if (!assistantOverlay) onEditGeneratedImage else null,
                                modifier = itemModifier,
                            )
                        }

                        is AgentTimelineEntry.WorkProcess -> {
                            entry.messages.forEach { message ->
                                if (message is ThinkingMessageUi && message.isStreaming) {
                                    streamingMarkdownCache.getOrCreate(message.id)
                                }
                            }
                            AgentWorkProcess(
                                id = entry.key,
                                active = entry.key == activeWorkProcessKey,
                                messages = entry.messages,
                                assistantOverlay = assistantOverlay,
                                onOpenBrowser = onOpenBrowser,
                                currentBrowserMessageId = currentBrowserMessageId,
                                retainedStreamingStates = streamingMarkdownStates,
                                modifier = itemModifier,
                            )
                        }
                    }
                }
                item(key = ChatBottomSentinelKey) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp),
                    )
                }
            }

            // 滚动条：滚动时出现，停止后渐隐；两侧圆角。
            ChatScrollIndicator(
                scrollState = scrollState,
                itemKeys = scrollIndicatorKeys,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        top = topInset + ScrollIndicatorVerticalInset,
                        bottom = bottomInset + ScrollIndicatorVerticalInset,
                        end = ScrollIndicatorEndInset,
                    ),
            )

            AnimatedVisibility(
                visible = !keepBottomAnchored && !isAtBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // 与输入框发送按钮同一条中心线：外层 8dp + 输入框内边距 10dp + 发送钮 20dp，
                    // 自身也是 40dp（半径 20dp），所以右内边距等于 8 + 10。
                    .padding(
                        end = ChatInputOuterMargin + 10.dp,
                        // 与输入框的距离原为 12dp，按要求缩一半。
                        bottom = bottomInset + 6.dp,
                    ),
                // 与「输入框左侧加号」「思考强度」同一节奏：弹簧从下方托起，带一点回弹。
                enter = fadeIn(tween(140)) + scaleIn(
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.7f,
                ) + slideInVertically(
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    initialOffsetY = { it / 2 },
                ),
                exit = fadeOut(tween(90)) + scaleOut(tween(110), targetScale = 0.88f),
            ) {
                val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
                val couiDark = CouiTheme.isDark
                // 提示点颜色与主题蓝一致：COUI 用 accent，Miuix 用 primary。
                val dockBadgeColor = if (isCoui) {
                    CouiTheme.colorSchemeFor(couiDark).accent
                } else {
                    MiuixTheme.colorScheme.primary
                }
                val scrollToBottom: () -> Unit = {
                    // 点按钮先给一次轻震：这是唯一的「回到最底 / 回到回答首行」入口，
                    // 不给反馈会让人以为没点上。
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    // 折叠项展开会临时禁用自动回锚，用户手动要回最底时必须解除。
                    suppressAutoReanchor = false
                    // 用户明确要求「回到最底」：本轮停靠一并作废，滚动权交还给跟底。
                    // 只设 keepBottomAnchored 不够：程序化滚动不会触发 isUserDragging，
                    // 残留的 finalAnswerAnchored 会让下一次弹键盘被当成「还停在顶栏」而不抬。
                    finalAnswerAnchored = false
                    dockUserInterrupted = true
                    dockInterruptAtMs = SystemClock.elapsedRealtime()
                    autoScrollFrozen = false
                    val pending = pendingAnswerDockKey
                    val pendingIndex = if (pending == null) {
                        -1
                    } else {
                        latestTimelineEntries.indexOfLast { entry -> entry.key == pending }
                    }
                    if (pending != null && pendingIndex >= 0) {
                        // 答完时用户没跟底：按钮语义改成「回到这条回答的首行」，也就是跟顶的
                        // 落点，而不是直接飞到列表最底部；点一下顺便清掉提示点。
                        pendingAnswerDockKey = null
                        finalAnswerHandledId = pending
                        coroutineScope.launch {
                            snapAnswerToTop(
                                key = pending,
                                itemIndex = pendingIndex + earlierMessagesItemCount,
                            )
                        }
                    } else {
                        onBottomAnchorChanged(true)
                        coroutineScope.launch {
                            scrollState.animateScrollToItem(bottomItemIndex)
                        }
                    }
                }
                Box {
                    if (isCoui) {
                        // 纯色圆钮：去掉 COUI 的轮廓光与玻璃材质，只留一块实心底色 + 朝下的小折角箭头。
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (couiDark) Color(0xFF1E2024) else Color(0xFFF5F6F8))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = stringResource(R.string.ui_back_to_bottom_32282e),
                                    onClick = scrollToBottom,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ConversationPaneIcons.ChevronRight,
                                contentDescription = stringResource(R.string.ui_back_to_bottom_32282e),
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(90f),
                                tint = CouiTheme.colorSchemeFor(couiDark).onSurface,
                            )
                        }
                    } else {
                        GlassIconButton(
                            onClick = scrollToBottom,
                            backdrop = messagesBackdrop,
                            size = 40.dp,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = stringResource(R.string.ui_back_to_bottom_32282e),
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                    // 回答结束但用户没跟底：主题蓝实心点，圆心正好压在圆钮的圆周上
                    // （40dp 圆钮半径 20dp，右上 45° 边缘点是 (34.1, 5.9)；align(TopEnd)
                    // 之后点中心在 (36, 4)，所以再往内挪 2dp、往下挪 2dp 就落在边上）。
                    if (pendingAnswerDockKey != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                                .size(8.dp)
                                .background(dockBadgeColor, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private data class BottomFollowLayout(
    val enabled: Boolean,
    val bottomItemIndex: Int,
    val allowMissingSentinelJump: Boolean,
    val missingSentinelScrollOffset: Int?,
    val sentinelBottom: Int?,
    val viewportEnd: Int,
    val lastVisibleIndex: Int?,
)

internal data class BottomFollowDecision(
    val scrollByPx: Int = 0,
    val requestIndex: Int? = null,
)

internal fun resolveBottomFollowDecision(
    enabled: Boolean,
    bottomItemIndex: Int,
    sentinelBottom: Int?,
    viewportEnd: Int,
    lastVisibleIndex: Int?,
    allowMissingSentinelJump: Boolean = true,
): BottomFollowDecision {
    if (!enabled) return BottomFollowDecision()
    val overflow = sentinelBottom?.minus(viewportEnd)
    return when {
        overflow != null && overflow > 0 -> BottomFollowDecision(scrollByPx = overflow)
        sentinelBottom == null &&
            lastVisibleIndex != null &&
            lastVisibleIndex < bottomItemIndex && allowMissingSentinelJump ->
            BottomFollowDecision(requestIndex = bottomItemIndex)
        sentinelBottom == null &&
            lastVisibleIndex != null &&
            lastVisibleIndex < bottomItemIndex ->
            // 不知道离底部还有多远时，用一个视口高度作为追赶距离代理。
            // smoothBottomFollowStep 会限速；等哨兵重新进入视口后再改用精确 overflow。
            BottomFollowDecision(scrollByPx = viewportEnd.coerceAtLeast(0))
        else -> BottomFollowDecision()
    }
}

internal fun smoothBottomFollowStep(
    distancePx: Float,
    elapsedSeconds: Float,
    density: Float,
    responseSeconds: Float = BOTTOM_FOLLOW_RESPONSE_SECONDS,
    maxSpeedDpPerSecond: Float = BOTTOM_FOLLOW_MAX_SPEED_DP_PER_SECOND,
): Float {
    if (distancePx <= 0f || elapsedSeconds <= 0f) return 0f
    if (distancePx <= BOTTOM_FOLLOW_SNAP_DISTANCE_PX) return distancePx

    val frameSeconds = elapsedSeconds.coerceAtMost(BOTTOM_FOLLOW_MAX_FRAME_SECONDS)
    val easedStep = distancePx * (1f - exp(-frameSeconds / responseSeconds))
    val speedLimitedStep = maxSpeedDpPerSecond * density * frameSeconds
    return min(distancePx, min(easedStep.coerceAtLeast(BOTTOM_FOLLOW_MIN_STEP_PX), speedLimitedStep))
}

internal sealed interface AgentTimelineEntry {
    val key: String

    data class Message(
        val message: AgentChatMessageUi,
    ) : AgentTimelineEntry {
        override val key: String = message.id
    }

    data class WorkProcess(
        override val key: String,
        val messages: List<AgentChatMessageUi>,
    ) : AgentTimelineEntry
}

/**
 * 投影层只替换真正变化的消息实例；工作过程分组若成员实例全部未变，就复用上一版条目，
 * 让已结束的分组在正文流式刷新时保持参数同一性并跳过重组。
 */
internal fun List<AgentChatMessageUi>.toTimelineEntries(
    previous: List<AgentTimelineEntry> = emptyList(),
): List<AgentTimelineEntry> = buildList {
    val previousWorkByMessageId = previous.asSequence()
        .filterIsInstance<AgentTimelineEntry.WorkProcess>()
        .flatMap { entry -> entry.messages.asSequence().map { it.id to entry } }
        .toMap()
    val workMessages = mutableListOf<AgentChatMessageUi>()

    fun flushWorkProcess() {
        if (workMessages.isEmpty()) return
        // 历史页可能从同一工作过程的中间开始；前插补齐后保留已显示分组的身份和滚动锚点。
        val messageIds = workMessages.mapTo(HashSet()) { it.id }
        val previousEntry = workMessages.firstNotNullOfOrNull { previousWorkByMessageId[it.id] }
            ?.takeIf { entry -> entry.messages.all { it.id in messageIds } }
        val key = previousEntry?.key ?: "work-${workMessages.first().id}"
        val reusable = previousEntry?.takeIf { entry ->
            entry.messages.size == workMessages.size &&
                entry.messages.indices.all { index -> entry.messages[index] === workMessages[index] }
        }
        add(reusable ?: AgentTimelineEntry.WorkProcess(key = key, messages = workMessages.toList()))
        workMessages.clear()
    }

    this@toTimelineEntries.forEach { message ->
        if (message.isWorkProcessMessage()) {
            workMessages += message
        } else {
            flushWorkProcess()
            add(AgentTimelineEntry.Message(message))
        }
    }
    flushWorkProcess()
}

private fun AgentTimelineEntry.contentType(): Any = when (this) {
    is AgentTimelineEntry.WorkProcess -> AgentTimelineEntry.WorkProcess::class
    is AgentTimelineEntry.Message -> message::class
}

private fun AgentChatMessageUi.isWorkProcessMessage(): Boolean =
    this is ThinkingMessageUi ||
        this is ToolSummaryMessageUi ||
        (this is ToolActivityMessageUi && toolName != ImageGenToolCatalog.TOOL_NAME)

/**
 * 一轮对话（两条用户消息之间）里最后一条 Agent 正文视为最终结果，其余为中间步骤。
 * 流式传输期间当前轮次尚未结束，最后一轮不标记，等传输结束后复制按钮才出现；
 * 之前已结束轮次的最终结果不受影响。
 */
internal fun resolveFinalResultMessageIds(
    messages: List<AgentChatMessageUi>,
    isStreaming: Boolean = false,
): Set<String> {
    val ids = LinkedHashSet<String>()
    var lastAgentMessageId: String? = null
    messages.forEach { message ->
        when (message) {
            is UserMessageUi -> {
                lastAgentMessageId?.let(ids::add)
                lastAgentMessageId = null
            }
            is AgentMessageUi -> lastAgentMessageId = message.id
            else -> Unit
        }
    }
    if (!isStreaming) {
        lastAgentMessageId?.let(ids::add)
    }
    return ids
}

@Composable
private fun AgentChatBottomBar(
    messageBackdrop: LayerBackdrop?,
    input: String,
    isCompacting: Boolean,
    contextUsage: AgentContextUsageUi,
    showContextUsage: Boolean,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi>,
    inputSyncVersion: Int,
    focusInputOnSync: Boolean,
    messageEdit: MessageEditUiState?,
    glassBackdrop: Backdrop?,
    onSubmit: (String) -> Unit,
    onInterruptAndSubmit: (String) -> Unit,
    onSteerSubmit: (String) -> Unit,
    onSteerQueuedMessage: (String) -> Unit,
    onRecallQueuedMessage: (String, String) -> Unit,
    onRemoveQueuedMessage: (String) -> Unit,
    modelPickerState: AgentModelPickerUiState,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onModelSelected: (String) -> Unit,
    onCompactContext: () -> Unit,
    canCompactContext: Boolean,
    onStop: () -> Unit,
    onAttachImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAttachFiles: (List<String>) -> Unit,
    onAttachFolder: (String) -> Unit,
    onAttachFilePath: (String) -> Unit,
    onRemoveFileReference: (String) -> Unit,
    onCancelMessageEdit: () -> Unit,
    focusInputRequest: Int = 0,
    onFocusInputHandled: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        // 输入框的底色面板与渐变已经下沉到 AgentChatInputBar 里，只包住胶囊本身：
        // 上面的图片/文件/路径/排队消息改为透明浮层，新增组件时背景不再整体被顶上去。
        AgentChatInputBar(
            input = input,
            isCompacting = isCompacting,
            contextUsage = contextUsage,
            showContextUsage = showContextUsage,
            isStreaming = isStreaming,
            reasoningEffort = reasoningEffort,
            availableReasoningEfforts = availableReasoningEfforts,
            modelPickerState = modelPickerState,
            pendingImages = pendingImages,
            pendingFileReferences = pendingFileReferences,
            queuedMessages = queuedMessages,
            inputSyncVersion = inputSyncVersion,
            focusInputOnSync = focusInputOnSync,
            isEditingMessage = messageEdit != null,
            editHasLaterTurns = messageEdit?.hasLaterTurns == true,
            preserveFollowingMessages = messageEdit?.preserveFollowingMessages == true,
            onSubmit = onSubmit,
            onInterruptAndSubmit = onInterruptAndSubmit,
            onSteerSubmit = onSteerSubmit,
            onSteerQueuedMessage = onSteerQueuedMessage,
            onRecallQueuedMessage = onRecallQueuedMessage,
            onRemoveQueuedMessage = onRemoveQueuedMessage,
            onReasoningEffortChange = onReasoningEffortChange,
            onModelSelected = onModelSelected,
            onCompactContext = onCompactContext,
            canCompactContext = canCompactContext,
            onStop = onStop,
            onAttachImage = onAttachImage,
            onRemoveImage = onRemoveImage,
            onAttachFiles = onAttachFiles,
            onAttachFolder = onAttachFolder,
            onAttachFilePath = onAttachFilePath,
            onRemoveFileReference = onRemoveFileReference,
            onCancelMessageEdit = onCancelMessageEdit,
            focusInputRequest = focusInputRequest,
            onFocusInputHandled = onFocusInputHandled,
            glassBackdrop = glassBackdrop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val BOTTOM_FOLLOW_RESPONSE_SECONDS = 0.085f
private const val BOTTOM_FOLLOW_MAX_FRAME_SECONDS = 0.05f
private const val BOTTOM_FOLLOW_MAX_SPEED_DP_PER_SECOND = 720f
// 键盘抬升用更快的一套位移：系统键盘弹起约 0.25s 走完全程，正文要贴着输入框一起上去，
// 不能沿用流式跟底那套「慢慢追持续增长文字」的曲线（原地 0.5s 左右才收口，观感迟钝）。
internal const val KEYBOARD_LIFT_RESPONSE_SECONDS = 0.06f
internal const val KEYBOARD_LIFT_MAX_SPEED_DP_PER_SECOND = 2000f
private const val BOTTOM_FOLLOW_MIN_STEP_PX = 0.5f
private const val BOTTOM_FOLLOW_SNAP_DISTANCE_PX = 0.75f
// 停靠后正文首行与顶栏底边的缝：必须远小于正文行距，看起来才是「贴着顶栏」。
private val DockGap = 0.dp
// 提前量：守卫比落点早这么多就冻结跟底，用来吃掉「一帧测量滞后 + 一帧跟底位移」。
// 必须小于一个正文行高，否则矮正文会被误判成需要停靠。
private val DockLookahead = 16.dp
// 列表内容顶部内边距（顶栏高度之外的额外留白）。
private val ChatTopContentPadding = 14.dp
// 停靠位移的时间常数（秒）：逐帧指数缓出，前段快、后段收敛，避免观感生硬或像瞬移。
private const val DockTimeConstantSeconds = 0.085f
// 停靠收口的一次对齐阈值（px）：残差小于它就一次 scrollBy 到位，不再逐帧微调。
// 列表滚动是整像素的，残差不到 1px 时「向上 1px / 向下 1px」会逐帧交替，
// 看上去就是整段正文高频上下抽动。
private const val DockSnapPx = 3f
// 首行字顶相对条目顶边的合理上限（px）：超过说明这一帧的测量还是上一屏的旧值，不能用。
// 「等正文继续长高」的帧预算：约 0.4 秒。首行在跟底冻结后不会再动，长等无意义。
private const val WaitForGrowthFrameBudget = 24
// 流式期间的同一份预算：正文一定还在长，这里只防死循环（约 5 秒）。
private const val StreamingGrowthFrameBudget = 300
// awaitAnswerReachTop 的死等上限（约 15 秒）：落点物理不可达时及时交回外层重试。
private const val AwaitReachTopFrameBudget = 900
// 收尾落点判定前的稳定帧数：等 LazyColumn 按新正文重新测量完再读滚动几何。
private const val TailSettleFrameBudget = 3
// 「首行越过顶栏下沿」的判定容差（px）：吃掉整像素舍入，别把刚贴顶误判成越过。
private const val DockExceedTolerancePx = 2f
// 停靠重试的退避：前几次快速重来，之后指数放大到上限，避免空转烧帧。
private const val DockRetryBackoffStart = 4
private const val DockRetryMaxBackoffFrames = 60
// 「用户接管」状态的存活时间：手指离开并静置这么久之后就恢复自动停靠。
// 取 8 秒：连续阅读时会不断拖动、每次拖动都刷新计时，等于一直保护；读完停手后自然恢复。
// 以前这个状态只在 isStreaming false→true 时清除，同一次 run 的多段回答期间根本不清，
// 于是一次拖动 / 一次打字就把后续每一轮都变成「一路跟到底」。
private const val DockUserInterruptTtlMs = 8_000L


// 停靠看门狗：间隔、偏离容差与纠正次数上限。
private const val DockWatchdogIntervalMs = 500L
private val DockWatchdogTolerance = 8.dp
private const val DockWatchdogLimit = 3

/** 停靠看门狗开关：自动跟顶到流式结束为止，结束后不再自动纠正视口。 */
private const val DockWatchdogEnabled = false

// 抖动熔断：无人操作时视口方向翻转这么多次，就认定是自动滚动在拉锯。
private const val ScrollJitterFlipLimit = 5
// 两次翻转之间隔了这么多帧就把计数清零，正常的一次性跳转/缓慢浏览不会被误判。
private const val ScrollJitterFlipWindowFrames = 30
private const val ScrollJitterTag = "EtaChatScroll"
// 停靠闭环的进展守卫：连续这么多帧残差没变小就收手（约 0.35 秒）。
private const val DockStallFrameLimit = 20

private const val EXPAND_REANCHOR_SUPPRESS_MS = 350L
private const val BOTTOM_SETTLE_TIMEOUT_MS = 600L

private class TimelineEntriesCache {
    var entries: List<AgentTimelineEntry> = emptyList()
}

internal fun resolveKeepBottomAnchored(
    current: Boolean,
    isUserDragging: Boolean,
    isAtBottom: Boolean,
    suppressAutoReanchor: Boolean = false,
): Boolean = when {
    isUserDragging -> isAtBottom
    suppressAutoReanchor -> current
    isAtBottom -> true
    else -> current
}

internal fun resolveBottomFollowEnabled(
    isStreaming: Boolean,
    keepBottomAnchored: Boolean,
    isUserDragging: Boolean,
    isBottomSettling: Boolean = false,
    keyboardLiftSettling: Boolean = false,
): Boolean = (isStreaming || isBottomSettling || keyboardLiftSettling) &&
    keepBottomAnchored && !isUserDragging

internal fun shouldRequestInitialBottom(
    isStreaming: Boolean,
    keepBottomAnchored: Boolean,
    isUserDragging: Boolean,
): Boolean = isStreaming && keepBottomAnchored && !isUserDragging

@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
) {
    val character = LocalRoleplayChat.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (character != null) {
                val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
                CharacterAvatar(
                    name = character.characterName,
                    avatarPath = character.avatarPath,
                    size = 68.dp,
                    shape = CircleShape,
                    borderWidth = 0.5.dp,
                    borderColor = if (isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.Black.copy(alpha = 0.08f)
                    },
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = character.characterName,
                    style = MiuixTheme.textStyles.title2.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.chat_character_story_start),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                val titleStyle = MiuixTheme.textStyles.headline1
                Text(
                    text = stringResource(R.string.ui_how_can_i_help_you_e75391),
                    style = titleStyle,
                    fontSize = titleStyle.fontSize * EmptyGreetingFontScale,
                    lineHeight = titleStyle.lineHeight.scaleIfSpecified(EmptyGreetingFontScale),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(26.dp))
            }
        }
    }
}



/** 空白首页问候语的放大倍数（约两档字号）。 */
private const val EmptyGreetingFontScale = 1.3f

/** TextUnit 未指定时不能参与算术，原样返回，避免行高未设置时报错。 */
private fun TextUnit.scaleIfSpecified(factor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * factor

/** 滚动条宽度、与屏幕边缘的距离与上下留白。 */
private val ScrollIndicatorWidth = 3.dp
private val ScrollIndicatorEndInset = 2.dp
private val ScrollIndicatorVerticalInset = 10.dp
private val ScrollIndicatorMinThumb = 36.dp

/** 完全静止后等待多久开始渐隐，避免惯性滚动尾巴让滚动条闪一下。 */
private const val ScrollIndicatorIdleDelayMs = 550L

/**
 * 滚动条跟随采样值的平滑时长。
 *
 * LazyColumn 不暴露总内容高度，采样只能由「已测量过消息项的高度」累计推算；新消息项进入视口时
 * 推算值会小幅跳变，直接绘制就是用户看到的「长度突然一变」。这里用很短的线性补间把跳变磨平，
 * 又不足以让位置明显滞后于手指。
 */
private const val ScrollIndicatorFollowMs = 120

/** 采样结果：滚动进度与滑块长度占比；null 表示内容不足一屏。 */
private data class ScrollIndicatorMetrics(
    val progress: Float,
    val thumbFraction: Float,
)

/**
 * 滚动条采样器：按消息项索引缓存已测量的高度。
 *
 * 只用「当前可见项平均高度 × 总项数」估算总高度时，可见项集合每变一次平均高度就跳一次，
 * 滑块长度随之忽长忽短。缓存后总高度 = 已测量项之和 + 未测量项 × 平均值，随滚动单调收敛，
 * 位置也按像素而不是按项数推进，滚动速度才均匀。
 */
private class ScrollIndicatorSampler {
    /** 已测量高度，按条目 key 而不是 index 缓存。 */
    private val heights = HashMap<Any, Int>()

    /** 在底部实测到的滚动总距离（px）；0 表示还没到底过。 */
    private var calibratedRangePx = 0f
    private var lastTotalItems = 0

    fun sample(state: LazyListState, keys: List<Any>): ScrollIndicatorMetrics? {
        val info = state.layoutInfo
        val totalItems = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (totalItems <= 0 || visible.isEmpty()) return null

        // 会话切换或整体替换（项数骤减）：缓存与校准都失效，重新估算。
        val shrank = lastTotalItems > 0 && totalItems < lastTotalItems - 20 &&
            totalItems < lastTotalItems * 3 / 5
        if (shrank) {
            heights.clear()
            calibratedRangePx = 0f
        }

        if (heights.size > 6000) heights.clear()
        visible.forEach { item ->
            keys.getOrNull(item.index)?.let { key -> heights[key] = item.size }
        }

        val measuredSum = heights.values.sum().toFloat()
        val average = (measuredSum / heights.size).coerceAtLeast(1f)
        val viewportPx = (info.viewportEndOffset - info.viewportStartOffset)
            .coerceAtLeast(1)
            .toFloat()

        // 分页加载更早消息后项数增加：把新增部分按平均高度并入已校准的总距离。
        // 之前是直接丢弃校准回到推算值，分母一换滑块就会瞬移。
        if (!shrank && calibratedRangePx > 0f && lastTotalItems > 0 && totalItems > lastTotalItems) {
            calibratedRangePx += (totalItems - lastTotalItems) * average
        }
        lastTotalItems = totalItems

        val first = visible.first()
        // 视口顶部内边距（顶栏高度）不算滚动距离，减掉后滑到顶时进度才是 0。
        val firstTopInViewport = (first.offset - info.viewportStartOffset).toFloat()
        val insideFirst = (-firstTopInViewport).coerceIn(0f, first.size.toFloat())
        var beforeFirst = 0f
        for (index in 0 until first.index) {
            beforeFirst += keys.getOrNull(index)?.let { key -> heights[key] }?.toFloat() ?: average
        }
        val scrolledPx = beforeFirst + insideFirst

        // canScrollForward == false 表示已经真的到底：此时「已滚动距离 + 视口高度」就是真实内容高度。
        // 用探底实测值校准，避免高度推算偏高时进度永远停在中间、滑到底也到不了 100%。
        val atBottom = !state.canScrollForward && scrolledPx > 0f
        if (atBottom) calibratedRangePx = scrolledPx
        val contentPx = if (calibratedRangePx > 0f) {
            calibratedRangePx + viewportPx
        } else {
            measuredSum + (totalItems - heights.size).coerceAtLeast(0) * average
        }
        if (contentPx <= viewportPx * 1.02f) return null
        val maxScrollPx = (contentPx - viewportPx).coerceAtLeast(1f)
        return ScrollIndicatorMetrics(
            progress = if (atBottom) 1f else (scrolledPx / maxScrollPx).coerceIn(0f, 1f),
            thumbFraction = (viewportPx / contentPx).coerceIn(0.06f, 1f),
        )
    }
}

/**
 * 会话滚动条。
 *
 * - 只在滚动（拖动或惯性）时显示，停止后渐隐；
 * - 两端圆角，不是直角；
 * - 采样与绘制分离：采样在 snapshotFlow 里做补间，绘制在 Canvas 的 draw 阶段读动画值，
 *   不触发重组，滚动过程中每帧连续更新。
 */
@Composable
private fun ChatScrollIndicator(
    scrollState: LazyListState,
    itemKeys: List<Any>,
    modifier: Modifier = Modifier,
) {
    val sampler = remember(scrollState) { ScrollIndicatorSampler() }
    val progress = remember { Animatable(0f) }
    val length = remember { Animatable(0f) }
    val visibility = remember { Animatable(0f) }
    val thumbColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.32f)

    // 采样流共享给两条补间：位置跟手（直接落位），长度做短补间磨平跳变。
    val samples = remember(scrollState, itemKeys) {
        snapshotFlow { sampler.sample(scrollState, itemKeys) }.distinctUntilChanged()
    }
    LaunchedEffect(samples) {
        samples.collectLatest { sample ->
            val target = sample?.progress ?: return@collectLatest
            progress.snapTo(target)
        }
    }
    LaunchedEffect(samples) {
        samples.collectLatest { sample ->
            val target = sample?.thumbFraction ?: return@collectLatest
            // 差值很大说明是一次跳转（如打开长会话直接到底），直接落位，不做扫动。
            if (abs(target - length.value) > 0.4f) {
                length.snapTo(target)
            } else {
                length.animateTo(
                    targetValue = target,
                    animationSpec = tween(ScrollIndicatorFollowMs, easing = LinearEasing),
                )
            }
        }
    }

    val isScrolling by remember(scrollState) {
        derivedStateOf { scrollState.isScrollInProgress }
    }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            visibility.animateTo(1f, tween(durationMillis = 110))
        } else {
            delay(ScrollIndicatorIdleDelayMs)
            visibility.animateTo(0f, tween(durationMillis = 420))
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrollIndicatorWidth),
    ) {
        val opacity = visibility.value
        val fraction = length.value
        if (opacity <= 0.01f || fraction <= 0f) return@Canvas
        val track = size.height
        if (track <= 0f) return@Canvas
        val minThumb = ScrollIndicatorMinThumb.toPx().coerceAtMost(track)
        val thumb = (track * fraction).coerceIn(minThumb, track)
        val top = ((track - thumb) * progress.value).coerceIn(0f, track - thumb)
        drawRoundRect(
            color = thumbColor.copy(alpha = thumbColor.alpha * opacity),
            topLeft = Offset(0f, top),
            size = Size(size.width, thumb),
            cornerRadius = CornerRadius(thumb / 2f),
        )
    }
}
