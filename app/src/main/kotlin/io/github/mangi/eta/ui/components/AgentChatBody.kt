package io.github.mangi.eta.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.browser.BrowserSessionSnapshot
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.ui.app.AgentConversationRevisionReducer
import io.github.mangi.eta.ui.app.LocalBlurEnabled
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
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
    input: String,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi> = emptyList(),
    inputSyncVersion: Int = 0,
    messageEdit: MessageEditUiState?,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
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
    val scrollState = rememberLazyListState(initialFirstVisibleItemIndex = messages.size + if (hasEarlierMessages) 1 else 0)
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardVisible = imeBottomPx > 0
    val browserSnapshot = LocalAgentBrowserSession.current?.snapshots?.collectAsState()?.value
        ?: BrowserSessionSnapshot()
    val contextUsage = remember(messages, modelPickerState.selectedModel) {
        latestContextUsage(messages, modelPickerState.selectedModel)
    }

    val visibleMessages = remember(messages, messageEdit?.targetMessageId, messageEdit?.preserveFollowingMessages) {
        AgentConversationRevisionReducer.visibleMessagesForEdit(
            messages = messages,
            targetMessageId = messageEdit?.takeUnless { it.preserveFollowingMessages }?.targetMessageId,
        ).filterNot { message ->
            message is AgentMessageUi && message.content.isBlank()
        }
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
            modelPickerState = modelPickerState,
            isCompacting = isCompacting,
            contextUsage = contextUsage,
            isStreaming = isStreaming,
            reasoningEffort = reasoningEffort,
            availableReasoningEfforts = availableReasoningEfforts,
            pendingImages = pendingImages,
            pendingFileReferences = pendingFileReferences,
            queuedMessages = queuedMessages,
            inputSyncVersion = inputSyncVersion,
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
    modelPickerState: AgentModelPickerUiState,
    isCompacting: Boolean,
    contextUsage: AgentContextUsageUi,
    isStreaming: Boolean,
    reasoningEffort: ReasoningEffort,
    availableReasoningEfforts: List<ReasoningEffort>,
    pendingImages: List<PendingImageUi>,
    pendingFileReferences: List<PendingFileReferenceUi>,
    queuedMessages: List<QueuedMessageUi>,
    inputSyncVersion: Int,
    messageEdit: MessageEditUiState?,
    keepBottomAnchored: Boolean,
    onBottomAnchorChanged: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onInterruptAndSubmit: (String) -> Unit,
    onSteerSubmit: (String) -> Unit,
    onSteerQueuedMessage: (String) -> Unit,
    onRecallQueuedMessage: (String, String) -> Unit,
    onRemoveQueuedMessage: (String) -> Unit,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
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
    val surfaceColor = MiuixTheme.colorScheme.surface
    val frostEnabled = hasMessages && LocalBlurEnabled.current && isRuntimeShaderSupported()
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
                modelPickerState = modelPickerState,
                isCompacting = isCompacting,
                contextUsage = contextUsage,
                showContextUsage = hasMessages,
                isStreaming = isStreaming,
                reasoningEffort = reasoningEffort,
                availableReasoningEfforts = availableReasoningEfforts,
                pendingImages = pendingImages,
                pendingFileReferences = pendingFileReferences,
                queuedMessages = queuedMessages,
                inputSyncVersion = inputSyncVersion,
                messageEdit = messageEdit,
                onSubmit = onSubmit,
                onInterruptAndSubmit = onInterruptAndSubmit,
                onSteerSubmit = onSteerSubmit,
                onSteerQueuedMessage = onSteerQueuedMessage,
                onRecallQueuedMessage = onRecallQueuedMessage,
                onRemoveQueuedMessage = onRemoveQueuedMessage,
                onReasoningEffortChange = onReasoningEffortChange,
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
                bottomInset = bottomPadding,
                topInset = topInset,
                keepBottomAnchored = keepBottomAnchored,
                onBottomAnchorChanged = onBottomAnchorChanged,
                onSuggestionClick = onSuggestionClick,
                onRunTraceClick = onRunTraceClick,
                onOpenBrowser = onOpenBrowser,
                onEditMessage = onEditMessage,
                onDeleteMessage = onDeleteMessage,
                onRegenerateMessage = onRegenerateMessage,
                onSelectReplyCandidate = onSelectReplyCandidate,
                messageActionsEnabled = !isStreaming && messageEdit == null,
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
    onSelectReplyCandidate: (String, Int) -> Unit = { _, _ -> },
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
    val previousTimelineEntries = remember { arrayOf(emptyList<AgentTimelineEntry>()) }
    val timelineEntries = remember(visibleMessages) {
        visibleMessages.toTimelineEntries(previous = previousTimelineEntries[0])
    }
    SideEffect { previousTimelineEntries[0] = timelineEntries }
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
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val imeInsets = WindowInsets.ime

    LaunchedEffect(
        isListMeasured,
        isUserDragging,
        isAtBottom,
        keepBottomAnchored,
    ) {
        if (!isListMeasured) return@LaunchedEffect
        val next = resolveKeepBottomAnchored(
            current = keepBottomAnchored,
            isUserDragging = isUserDragging,
            isAtBottom = isAtBottom,
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
    // 本轮由「流式停靠」真正落到位的回答 id：首行已经在顶栏下沿，收尾时绝不能再拿
    // 「历史会话首帧落点」把视口按条目顶边重设一次，否则正文会整体往下掉一段。
    var streamDockedAnswerId by remember { mutableStateOf<String?>(null) }
    var wasUserDragging by remember { mutableStateOf(false) }
    // 正文首行字顶的窗口 y（px）：停靠闭环直接拿它和顶栏底边比，不换算条目顶边。
    var answerFirstLineWindowY by remember { mutableStateOf<Float?>(null) }
    // 上面这个测量值属于哪一条：换轮之后旧值必须作废，否则会拿上一轮的首行位置误判。
    var answerFirstLineOwnerKey by remember { mutableStateOf<String?>(null) }
    val latestTimelineEntries by rememberUpdatedState(timelineEntries)
    val streamingAnswerId = tailMessage
        ?.takeIf { message -> message.content.isNotBlank() }
        ?.id
    // 历史会话的首帧落点：在 LazyColumn 第一次测量之前就把视口请求到正文条目上。
    // 之前只在 LaunchedEffect 里跳：首帧先按 rememberLazyListState 的初值画在列表底部，
    // 随后再整体上移一大截——那就是用户看到的「突然向上抽动」。
    // requestScrollToItem 会被下一次布局采用，放在布局之前，首帧画出来就已经在落点上。
    val historyDockItemIndex = remember(
        isStreaming,
        streamingAnswerId,
        streamDockedAnswerId,
        earlierMessagesItemCount,
        timelineEntries,
    ) {
        when {
            isStreaming -> null
            streamingAnswerId == null -> null
            // 这一轮流式期间已经停靠到位：首行就贴着顶栏。收尾时若再按「条目顶贴顶栏」
            // 请求一次，正文会整体下移一个条目内边距（8dp + 首行行距留白），并且因为
            // 该回答已被标记处理过，没有任何逻辑会把它拉回落点——于是位置就永久掉下去。
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
            wasUserDragging = false
            answerFirstLineWindowY = null
            answerFirstLineOwnerKey = null
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
            // 显现完成后还会切换稳定排版并插入操作行，等其完成测量再收口跟底。
            withFrameNanos { }
            withFrameNanos { }
            snapshotFlow { !scrollState.canScrollForward }.first { it }
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
        // 等待「正文继续长高把落点带下来」的帧预算只有这么点：跟底被冻结之后列表保持
        // 首个可见条目的锚点，正文变长并不会让首行自己逼近顶栏，长时间等下去只会把画面
        // 冻在原地（以前是 900 帧 ≈ 15 秒），等不到就交回跟底，由外层隔几帧再试一次。
        while (frames < 900 && waitedFrames < WaitForGrowthFrameBudget) {
            // 用户正在手动滑动，或下方又冒出工具链（这段只是过渡文本）：立刻放弃停靠，交回默认跟底。
            if (isUserDragging || latestTimelineEntries.lastOrNull()?.key != key) return false
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
            if (abs(delta) <= DockSnapPx) {
                // 到线就一次对齐收口：不再逐帧 ±1px 微调，也不再要求「连续 3 帧稳定」——
                // 整像素量化让残差在 0/1px 之间反复，前一种写法表现为高频上下抽动，
                // 后一种条件几乎永远不成立，只会把循环空转到帧预算上限。
                if (abs(delta) >= 0.5f) {
                    try {
                        scrollState.scroll { scrollBy(delta) }
                    } catch (cancelled: CancellationException) {
                        if (!currentCoroutineContext().isActive) throw cancelled
                        return true
                    }
                }
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
            if (delta < 0f && !scrollState.canScrollBackward) return false
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
        return false
    }

    // 历史会话打开时的落点：直接把视口放到「首行贴顶栏」，不播位移曲线。
    // 起步交给结构对齐（条目顶贴顶栏，一次到位），再按首行实测补掉条目内部的上边距。
    suspend fun snapAnswerToTop(key: String, itemIndex: Int): Boolean {
        val target = topInsetPx + dockGapPx
        // 列表顶部留白（顶栏高度 + ChatTopContentPadding）正好被吃掉时，条目顶就落在顶栏底边上。
        val structuralOffset = (chatTopPaddingPx - dockGapPx).coerceAtLeast(0)
        // 必须用 scrollToItem：requestScrollToItem 只是登记请求，等下一次测量才采用，
        // 中间那几帧列表还停在原处，随后再被拉正，看上去就是「往上抽一下」。
        scrollState.scrollToItem(itemIndex, scrollOffset = structuralOffset)
        // 等这一跳被布局采用、首行重新测量（测量回调比帧回调晚一帧，多等一帧更保险）。
        repeat(2) { withFrameNanos { } }
        var rounds = 0
        while (rounds < 10 && currentCoroutineContext().isActive) {
            if (isUserDragging || latestTimelineEntries.lastOrNull()?.key != key) return false
            val item = scrollState.layoutInfo.visibleItemsInfo.firstOrNull { info -> info.key == key }
            if (item == null) {
                scrollState.scrollToItem(itemIndex, scrollOffset = structuralOffset)
                rounds++
                withFrameNanos { }
                continue
            }
            // 首行字顶必须和条目实测顶边对得上，这一帧的测量才算可信。
            val firstLineY = answerFirstLineWindowY
                ?.takeIf { answerFirstLineOwnerKey == key }
                ?.takeIf { y -> y - item.offset.toFloat() in -4f..FirstLineTopPlausibleSlackPx }
            // 关键：拿不到可信的首行测量时，绝不能拿「条目顶边」当成已到位就收工。
            // 条目顶边本来就贴着顶栏，那样会在首行真正测出来之前结束，落点比目标低一截——
            // 低出来的正是条目内部的上边距，用户看到的就是「停靠位置往下掉了」。
            if (firstLineY == null) {
                rounds++
                withFrameNanos { }
                continue
            }
            val delta = firstLineY - target
            if (abs(delta) <= 1f) return true
            // 方向余量守卫：落点在物理上到不了时（正文长度刚好卡一屏或更短），
            // 连续 10 轮 scrollBy 都会被夹成 0 位移，每帧重启一次边界回弹动画——
            // 屏幕上同样是正文上下高频抽动。这里一次收手：回到底部，交回默认跟底。
            val hasRoom = if (delta > 0f) {
                scrollState.canScrollForward
            } else {
                scrollState.canScrollBackward
            }
            if (!hasRoom) {
                if (delta > 0f) scrollState.scrollToItem(bottomItemIndex)
                return false
            }
            rounds++
            try {
                // 一次挪到底：分步挪会在屏幕上留下看得见的一段上移。
                scrollState.scroll { scrollBy(delta) }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                return true
            }
            withFrameNanos { }
        }
        // 到头来还是没拿到可信首行：停在「条目顶贴顶栏」这个保底位置，不要再挪。
        return true
    }

    // 停靠守卫：跟着跟底引擎逐帧盯正文首行的窗口 y，一旦首行逼近顶栏就立刻返回 true，
    // 让调用方马上冻结跟底、再交给 dockAnswerToTop 收口。
    // 关键是「不许过冲」：以前要等正文长够一屏才动手，等待期间跟底一路把首行推出屏幕，
    // 门槛满足后只能大幅回拉，看起来就是「先滚过头再突然拉回来」。
    // 矮正文的首行永远到不了顶栏，这里就一直等到本轮结束，保持默认跟底行为。
    suspend fun awaitAnswerReachTop(key: String): Boolean {
        val threshold = topInsetPx + dockGapPx + dockLookaheadPx
        while (currentCoroutineContext().isActive) {
            if (isUserDragging) return false
            // 下方又冒出工具链：这段只是过渡正文，交给默认跟底。
            if (latestTimelineEntries.lastOrNull()?.key != key) return false
            val firstLineY = answerFirstLineWindowY
                ?.takeIf { answerFirstLineOwnerKey == key }
            if (firstLineY != null) {
                if (firstLineY <= threshold) return true
            } else {
                // 首块不是文本块（代码/表格/引用）时量不到首行字顶，退化成看条目顶边。
                val item = scrollState.layoutInfo.visibleItemsInfo
                    .firstOrNull { info -> info.key == key }
                if (item != null && item.offset <= threshold) return true
            }
            withFrameNanos { }
        }
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
        if (!isStreaming) {
            // 历史会话：打开就该停在「首行贴顶栏」的位置，不要先停在底部、再当面上移一次。
            // 所以这里跳过守卫和缓出曲线，直接把视口跳到落点（没有可见的上移动画）。
            // 两种情况都算处理完：true = 停靠到位并冻结跟底；false = 落点不可达/被用户打断，
            // 视口留在跟底位置。标记成已处理，避免同一个回答反复重跳、来回抽动。
            finalAnswerAnchored = snapAnswerToTop(key = answerId, itemIndex = itemIndex)
            finalAnswerHandledId = answerId
            return@LaunchedEffect
        }
        // 首行逼近顶栏就立刻冻结跟底（矮正文到不了顶栏，始终走默认跟底）。
        // 一次没停成也不就此罢手：正文还在长，交回跟底、隔几帧再收一次。
        // 只试一次的话，任何一次提前失败都会变成「一路跟到底、完全不停靠」。
        var attempts = 0
        while (attempts < 4 && currentCoroutineContext().isActive) {
            attempts++
            if (!awaitAnswerReachTop(key = answerId)) return@LaunchedEffect
            finalAnswerAnchored = true
            if (dockAnswerToTop(key = answerId, itemIndex = itemIndex)) {
                // 只有真正停住了才标记已处理：矮正文、被夹住时要允许正文变长后重来。
                finalAnswerHandledId = answerId
                streamDockedAnswerId = answerId
                return@LaunchedEffect
            }
            finalAnswerAnchored = false
            repeat(6) { withFrameNanos { } }
        }
    }

    // 停靠之后下方又冒出工具链（时间线末尾不再是这段正文）：立刻解冻，恢复流式默认滚动。
    LaunchedEffect(timelineEntries.lastOrNull()?.key) {
        if (!finalAnswerAnchored) return@LaunchedEffect
        val handled = finalAnswerHandledId ?: return@LaunchedEffect
        if (timelineEntries.lastOrNull()?.key == handled) return@LaunchedEffect
        finalAnswerAnchored = false
        finalAnswerHandledId = null
    }

    LaunchedEffect(isUserDragging, isAtBottom) {
        if (isUserDragging) {
            wasUserDragging = true
        } else if (wasUserDragging && isAtBottom) {
            // 用户主动滑回底部：放弃本轮停靠，恢复跟底。
            wasUserDragging = false
            finalAnswerAnchored = false
        }
    }

    // 在消息列表上滑动（上翻或下翻）就自动收起键盘；键盘本来没弹着时什么也不做。
    // 用列表自己的拖动状态判断，不拦截也不消费滚动手势。
    LaunchedEffect(isUserDragging) {
        if (isUserDragging && imeInsets.getBottom(density) > 0) {
            keyboard?.hide()
        }
    }

    // 键盘弹起会把输入器连同 IME 内边距一起顶高，列表的底部内容内边距随之变大，
    // 但 LazyColumn 会保持原来的滚动锚点，正文最后一行就留在输入器背后被盖住
    // （流式期间一路跟底，所以看不出这个问题）。
    // 弹起前视口确实停在底部时，交给现成的跟底引擎把正文平滑地顶上去 ——
    // 和流式输出用的是同一套位移曲线，所以不是瞬移，也能跟着 IME 一起逐帧移动；
    // 翻在历史中间则什么都不做，维持原来的阅读位置。
    var previousBottomInset by remember { mutableStateOf(bottomInset) }
    LaunchedEffect(bottomInset) {
        val insetGrew = bottomInset > previousBottomInset
        previousBottomInset = bottomInset
        if (!insetGrew || isStreaming || !bottomContentVisible || !keepBottomAnchored) {
            return@LaunchedEffect
        }
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

    val shouldFollowBottom by rememberUpdatedState(
        resolveBottomFollowEnabled(
            isStreaming = isStreaming,
            keepBottomAnchored = keepBottomAnchored,
            isUserDragging = isUserDragging,
            isBottomSettling = isBottomSettling,
            keyboardLiftSettling = keyboardLiftSettling,
        ) && (!finalAnswerAnchored || keyboardLiftSettling)
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
    ) {
        if (shouldRequestInitialBottom(
                isStreaming = isStreaming,
                keepBottomAnchored = keepBottomAnchored,
                isUserDragging = isUserDragging,
            )
        ) {
            scrollState.requestScrollToItem(bottomItemIndex)
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

        while (currentCoroutineContext().isActive) {
            if (remainingDistancePx <= 0f && requestIndex == null) {
                accept(bottomFollowDecisions.receive())
                previousFrameNanos = 0L
            }
            while (true) {
                val latest = bottomFollowDecisions.tryReceive().getOrNull() ?: break
                accept(latest)
            }

            if (!shouldFollowBottom) {
                remainingDistancePx = 0f
                requestIndex = null
                continue
            }

            requestIndex?.let { targetIndex ->
                scrollState.requestScrollToItem(targetIndex)
                requestIndex = null
                remainingDistancePx = 0f
                return@let
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
            if (!shouldFollowBottom || requestIndex != null || remainingDistancePx <= 0f) continue

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
                    scrollBy(step)
                    consumedStep = step
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
    Box(modifier = modifier.clipToBounds()) {
        LazyColumn(
            state = scrollState,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
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
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 12.dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.82f),
            exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.86f),
        ) {
            Box(
                // 统一圆形：阴影、底色、描边都跟随 CircleShape，不再出现两层不同形状。
                modifier = Modifier
                    .dropShadow(
                        shape = CircleShape,
                        shadow = Shadow(radius = 8.dp, color = Color.Black, alpha = 0.08f),
                    )
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.55f),
                        shape = CircleShape,
                    )
                    .clickable {
                        onBottomAnchorChanged(true)
                        coroutineScope.launch {
                            scrollState.animateScrollToItem(bottomItemIndex)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = stringResource(R.string.ui_back_to_bottom_32282e),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private data class BottomFollowLayout(
    val enabled: Boolean,
    val bottomItemIndex: Int,
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
): BottomFollowDecision {
    if (!enabled) return BottomFollowDecision()
    val overflow = sentinelBottom?.minus(viewportEnd)
    return when {
        overflow != null && overflow > 0 -> BottomFollowDecision(scrollByPx = overflow)
        sentinelBottom == null &&
            lastVisibleIndex != null &&
            lastVisibleIndex < bottomItemIndex -> BottomFollowDecision(requestIndex = bottomItemIndex)
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
    this is ThinkingMessageUi || this is ToolActivityMessageUi || this is ToolSummaryMessageUi

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
    modelPickerState: AgentModelPickerUiState,
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
    messageEdit: MessageEditUiState?,
    onSubmit: (String) -> Unit,
    onInterruptAndSubmit: (String) -> Unit,
    onSteerSubmit: (String) -> Unit,
    onSteerQueuedMessage: (String) -> Unit,
    onRecallQueuedMessage: (String, String) -> Unit,
    onRemoveQueuedMessage: (String) -> Unit,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
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
        if (messageBackdrop != null) {
            val blurColors = BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.72f))
                ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChatBottomFrostHeight)
                    // DstIn 让真实磨砂在顶部透明、靠近输入框时逐渐变实，消除硬裁切线。
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .textureBlur(
                        backdrop = messageBackdrop,
                        shape = RectangleShape,
                        blurRadius = 20f,
                        colors = blurColors,
                    ),
            )
        } else {
            // 空白主页沿用原来的轻微渐隐，不改变主页视觉。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MiuixTheme.colorScheme.surface,
                            ),
                        )
                    ),
            )
        }
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
            AgentChatInputBar(
                input = input,
                modelPickerState = modelPickerState,
                isCompacting = isCompacting,
                contextUsage = contextUsage,
                showContextUsage = showContextUsage,
                isStreaming = isStreaming,
                reasoningEffort = reasoningEffort,
                availableReasoningEfforts = availableReasoningEfforts,
                pendingImages = pendingImages,
                pendingFileReferences = pendingFileReferences,
                queuedMessages = queuedMessages,
                inputSyncVersion = inputSyncVersion,
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val ChatBottomFrostHeight = 24.dp

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
private const val FirstLineTopPlausibleSlackPx = 200f
// 「等正文继续长高」的帧预算：约 0.4 秒。首行在跟底冻结后不会再动，长等无意义。
private const val WaitForGrowthFrameBudget = 24


internal fun resolveKeepBottomAnchored(
    current: Boolean,
    isUserDragging: Boolean,
    isAtBottom: Boolean,
): Boolean = when {
    isUserDragging -> isAtBottom
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
                CharacterAvatar(
                    name = character.characterName,
                    avatarPath = character.avatarPath,
                    size = 88.dp,
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = character.characterName,
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.chat_character_story_start),
                    style = MiuixTheme.textStyles.body2,
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
