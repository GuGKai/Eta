package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.model.AgentContextSnapshot

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.device.AgentFileReferenceGateway
import io.github.mangi.eta.agent.device.RootAccess
import io.github.mangi.eta.agent.media.AgentImageCodec
import io.github.mangi.eta.agent.model.AgentFileReference
import io.github.mangi.eta.agent.model.AgentFileReferenceKind
import io.github.mangi.eta.agent.model.AgentFileReferencePolicy
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.ConversationTitleGenerator
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.agent.roleplay.CharacterMacros
import io.github.mangi.eta.agent.roleplay.CharacterCardCodec
import io.github.mangi.eta.agent.roleplay.RoleplayMessageLink
import io.github.mangi.eta.agent.roleplay.RoleplayMessageState
import io.github.mangi.eta.data.repository.CharacterRepository
import io.github.mangi.eta.agent.runtime.AgentEvent
import io.github.mangi.eta.agent.runtime.AgentExecutionService
import io.github.mangi.eta.agent.runtime.AgentExternalArchivePayload
import io.github.mangi.eta.agent.runtime.AgentRunArchiveStore
import io.github.mangi.eta.agent.runtime.AgentRunCheckpointStore
import io.github.mangi.eta.agent.runtime.AgentRuntimeClient
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.agent.runtime.AgentUiHandoffPayload
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.data.datastore.ConversationWorkspaceRecord
import io.github.mangi.eta.data.datastore.ConversationWorkspaceState
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.ModelReasoningCapabilities
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.EtaBackupRepository
import io.github.mangi.eta.data.repository.EtaBackupSummary
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.ConversationModeUi
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.model.ConversationWorkspaceUi
import io.github.mangi.eta.ui.model.MessageEditUiState
import io.github.mangi.eta.ui.model.PendingFileReferenceUi
import io.github.mangi.eta.ui.model.PendingImageUi
import io.github.mangi.eta.ui.model.QueuedMessageUi
import io.github.mangi.eta.ui.model.SystemNoticeCode
import io.github.mangi.eta.ui.model.SystemNoticeMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.TokenUsageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.model.contentMatches
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 新建对话的聚焦请求通道：输入栏直接订阅，绕开逐层传参在重建时丢信号。 */
internal val composerFocusRequests = MutableStateFlow(0)

internal class AgentAppState(
    context: Context,
    private val scope: CoroutineScope,
    initialConversations: AgentConversationStore.Snapshot = AgentConversationStore.load(context),
    /** 余额数据源：用来结算「本轮消耗」（首尾余额差）。为空时不做结算。 */
    private val balanceStore: DeepSeekBalanceStore? = null,
) {
    private val appContext = context.applicationContext
    private val runConversationIds = mutableMapOf<String, String>()
    private val runMessageProjector = AgentRunMessageProjector()
    private val runEventCoalescer = AgentRunEventCoalescer()
    private val runEventFlushJobs = mutableMapOf<String, Job>()
    private var currentRunId: String? = null
    private var currentRunJob: Job? = null
    private val persistenceLock = Any()
    private var persistenceJob: Job? = null
    // 导入期间暂停保存：旧状态的增量保存会删除刚导入的会话；导入成功后整体重载，失败时数据库已回滚。
    private var persistencePaused = false
    private val runtimeRecoveryInProgress = AtomicBoolean(false)
    private val defaultThinkingEnabled = agentBooleanForUi(Prefs.Keys.AGENT_THINKING_ENABLED)
    @Volatile
    private var conversationPersistence = AgentConversationPersistence(initialConversations)
    private var currentReasoningCapabilities: ModelReasoningCapabilities? = null
    private var providers: List<ProviderSetting> = emptyList()
    private var defaultModelId: String? = null
    private var defaultProviderId: String? = null
    private var fileAttachmentOwnerVersion = 0L
    private val conversationLoadMutex = Mutex()
    private val conversationContentPins = mutableMapOf<String, Int>()
    private var conversationSelectionJob: Job? = null
    private var conversationSelectionVersion = 0L
    private var historyPageJob: Job? = null
    private var historyPageVersion = 0L
    private var historyPageReleasesEarlier = false
    private var conversationSearchJob: Job? = null
    private var persistedSearchMatches: Set<String> = emptySet()

    var isLoadingConversation by mutableStateOf(false)
        private set
    var conversationLoadFailed by mutableStateOf(false)
        private set
    var historyLoadState by mutableStateOf(ConversationHistoryLoadState.Idle)
        private set
    val isLoadingEarlierMessages: Boolean get() = historyLoadState == ConversationHistoryLoadState.Loading

    /** 第一轮还没命名完的会话：顶栏在这期间显示「新任务」。 */
    private val autoNamingPending = mutableSetOf<String>()
    /** 正在补命名的外部入口会话（语音浮窗／小布／小爱／定时任务）：同一会话不重复请求。 */
    private val externalNamingInFlight = mutableSetOf<String>()
    /** 正在补命名的恢复/导入会话：重复恢复时避免同一会话并发请求。 */
    private val recoveredNamingInFlight = mutableSetOf<String>()

    private var selectedConversationId: String? = initialConversations.selectedConversationId
    private var conversationsById: Map<String, AgentChatHomeUiState> = initialConversations.conversationsById
    private var conversationTitles: Map<String, String> = initialConversations.titles
    private var conversationUpdatedAt: Map<String, Long> = initialConversations.updatedAt
    private var conversationPinned: Set<String> = initialConversations.pinned
    /** 侧栏工作区（会话文件夹）：列表即展示顺序，折叠状态随之持久化。 */
    private var workspaceRecords: List<ConversationWorkspaceRecord> = emptyList()

    /** 会话 → 工作区归属；没有键的会话不在任何工作区里。 */
    private var workspaceAssignments: Map<String, String> = emptyMap()


    var homeState by mutableStateOf(
        selectedConversationId?.let(conversationsById::get) ?: emptyChatState(defaultThinkingEnabled)
    )
        private set

    var modelPickerState by mutableStateOf(AgentModelPickerUiState())
        private set

    /**
     * 新建对话后的一次性输入焦点信号：置位后由聊天输入框消费（聚焦 + 拉起键盘）。
     */
    var composerFocusRequestId by mutableIntStateOf(0)
        private set

    var conversationPaneState by mutableStateOf(
        ConversationPaneUiState(
            conversations = emptyList(),
            selectedConversationId = selectedConversationId,
            searchQuery = "",
        )
    )
        private set

    init {
        refreshConversationSummaries()
        observeRuntimeSelection()
        scope.launch {
            // 工作区与设置同库（DataStore），与会话库解耦：读不到就按空处理，不影响会话加载。
            val workspaceState = runCatching { SettingsDataStore.conversationWorkspaces() }.getOrNull()
            if (workspaceState != null) applyWorkspaceState(workspaceState)
        }
        runtimeRecoveryInProgress.set(true)
        scope.launch(Dispatchers.IO) {
            try {
                recoverRuntimeRuns()
                importArchivedExternalRuns()
            } finally {
                runtimeRecoveryInProgress.set(false)
            }
        }
    }

    private fun observeRuntimeSelection() {
        scope.launch(Dispatchers.IO) {
            combine(
                RuntimeConfigRepository.selectedProviderIdFlow(),
                RuntimeConfigRepository.selectedModelIdFlow(),
                ProviderRepository.providersFlow(),
            ) { providerId, modelId, providers ->
                Triple(providerId, modelId, providers)
            }
                .distinctUntilChanged()
                .collectLatest { (providerId, modelId, providers) ->
                    withContext(Dispatchers.Main) {
                        this@AgentAppState.providers = providers
                        defaultProviderId = providerId
                        defaultModelId = modelId
                        refreshConversationModel()
                    }
                }
        }
    }

    /**
     * 选择器、思考强度与上下文窗口统一投影自当前会话的有效模型：
     * 会话绑定的模型仍可用时用它，否则用默认模型。回落只影响显示与发送，不改写会话绑定，
     * 模型重新启用后会话自动回到原模型；真正发送后才把实际模型写入绑定。
     */
    private fun refreshConversationModel() {
        val bound = providers.enabledModel(homeState.modelId)
        val pickerState = if (bound != null) {
            AgentModelPickerProjector.project(providers, bound.provider.id, bound.model.id)
        } else {
            AgentModelPickerProjector.project(providers, defaultProviderId, defaultModelId)
        }
        modelPickerState = pickerState.copy(isChanging = modelPickerState.isChanging)
        val effective = bound ?: providers.enabledModel(pickerState.selectedModel?.id)
        currentReasoningCapabilities = effective?.let { (provider, model) ->
            RuntimeConfigRepository.reasoningCapabilities(provider, model)
        }
        if (!homeState.isConversationLoaded) return
        val resolved = homeState.withCurrentReasoningCapabilities()
        val conversationId = selectedConversationId
        if (conversationId == null) homeState = resolved
        else updateConversation(conversationId, resolved, updateTimestamp = false)
    }

    private fun AgentChatHomeUiState.withCurrentReasoningCapabilities(): AgentChatHomeUiState {
        val normalized = currentReasoningCapabilities?.normalize(reasoningEffort) ?: ReasoningEffort.OFF
        return copy(
            thinkingEnabled = normalized.enablesReasoning,
            reasoningEffort = normalized,
            availableReasoningEfforts = currentReasoningCapabilities?.selectableEfforts.orEmpty(),
        )
    }

    private val AgentChatHomeUiState.effectiveModelId: String?
        get() = providers.enabledModel(modelId)?.model?.id ?: modelPickerState.selectedModel?.id

    fun refreshRuntimeResults() {
        if (!runtimeRecoveryInProgress.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                recoverRuntimeRuns()
                importArchivedExternalRuns()
            } finally {
                runtimeRecoveryInProgress.set(false)
            }
        }
    }

    suspend fun exportBackup(output: OutputStream): EtaBackupSummary =
        EtaBackupRepository.export(appContext, output)

    suspend fun importBackup(input: InputStream): EtaBackupSummary {
        val locallyBusy = withContext(Dispatchers.Main.immediate) {
            currentRunId != null || conversationsById.values.any { it.isStreaming }
        }
        if (locallyBusy) {
            throw IllegalStateException("请先停止正在运行的 Agent 任务")
        }

        val activeRunQuery = withContext(Dispatchers.IO) {
            AgentRuntimeClient(appContext, AndroidAgentLogger).queryActiveRun()
        }
        when (val active = activeRunQuery) {
            is AgentRuntimeClient.ActiveRunQuery.Known -> {
                if (active.runId != null) {
                    throw IllegalStateException("请先停止正在运行的 Agent 任务")
                }
            }
            AgentRuntimeClient.ActiveRunQuery.Unavailable -> {
                throw IllegalStateException("无法确认 Agent Runtime 状态，请稍后重试")
            }
        }

        val pendingPersistence = synchronized(persistenceLock) {
            check(!persistencePaused) { "备份正在导入" }
            persistencePaused = true
            persistenceJob
        }
        var imported = false
        var reloaded = false
        try {
            pendingPersistence?.join()
            return conversationLoadMutex.withLock {
                try {
                    val summary = EtaBackupRepository.import(appContext, input, onCommitted = { imported = true })
                    reloadConversationsAfterBackup()
                    reloaded = true
                    summary
                } catch (failure: Throwable) {
                    if (imported && !reloaded) {
                        try {
                            withContext(NonCancellable) { reloadConversationsAfterBackup() }
                            reloaded = true
                        } catch (reloadFailure: Throwable) {
                            failure.addSuppressed(reloadFailure)
                        }
                    }
                    throw failure
                }
            }
        } finally {
            // 导入已提交但重载失败时内存仍是旧状态，继续暂停，避免覆盖导入结果；重启后按库内数据加载。
            if (!imported || reloaded) {
                synchronized(persistenceLock) { persistencePaused = false }
            }
            // 仅提交前失败才回写旧内存；提交后的清理失败或取消仍以导入数据为准。
            if (!imported) withContext(Dispatchers.Main.immediate + NonCancellable) { persistConversations() }
        }
    }

    private suspend fun reloadConversationsAfterBackup() {
        val snapshot = withContext(Dispatchers.IO) {
            AgentConversationStore.load(appContext)
        }
        withContext(Dispatchers.Main.immediate) {
            conversationSelectionVersion += 1
            cancelHistoryPageLoad()
            conversationSelectionJob?.cancel()
            isLoadingConversation = false
            conversationLoadFailed = false
            contentMatchCache.clear()
            conversationSearchJob?.cancel()
            persistedSearchMatches = emptySet()
            selectedConversationId = snapshot.selectedConversationId
            conversationsById = snapshot.conversationsById
            conversationTitles = snapshot.titles
            conversationUpdatedAt = snapshot.updatedAt
            conversationPersistence = AgentConversationPersistence(snapshot)
            conversationPinned = snapshot.pinned
            fileAttachmentOwnerVersion += 1
            homeState = selectedConversationId
                ?.let(conversationsById::get)
                ?: emptyChatState(defaultThinkingEnabled)
            refreshConversationModel()
            conversationPaneState = conversationPaneState.copy(
                selectedConversationId = selectedConversationId,
                searchQuery = "",
            )
            refreshConversationSummaries()
        }
    }

    /** 用 checkpoint、终态 outbox 与 active session 一次性对账，避免用进程存活推断 run 状态。 */
    private suspend fun recoverRuntimeRuns() {
        val client = AgentRuntimeClient(appContext, AndroidAgentLogger)
        val checkpoints = withContext(Dispatchers.IO) {
            AgentRunCheckpointStore.list(appContext)
        }
        val initialCompletedQuery = client.queryCompletedRuns()
        if (initialCompletedQuery is AgentRuntimeClient.CompletedRunsQuery.Unavailable) {
            AndroidAgentLogger.warnThrottled("agent_ui_drain_results_failed") {
                "Agent UI pending result recovery failed"
            }
        }
        val initialCompletedRuns =
            (initialCompletedQuery as? AgentRuntimeClient.CompletedRunsQuery.Known)
                ?.runs
                .orEmpty()
        val activeRunQuery = client.queryActiveRun()
        val terminalRaceQuery = if (
            activeRunQuery is AgentRuntimeClient.ActiveRunQuery.Known && checkpoints.isNotEmpty()
        ) {
            client.queryCompletedRuns()
        } else {
            initialCompletedQuery
        }
        val terminalRaceCompletedRuns =
            (terminalRaceQuery as? AgentRuntimeClient.CompletedRunsQuery.Known)
                ?.runs
                .orEmpty()
        val completedRuns = (initialCompletedRuns + terminalRaceCompletedRuns)
            .associateBy { completed ->
                completed.result.runId.ifBlank { completed.handoff.id }
            }
            .values
            .toList()
        val activeStateKnown = activeRunQuery is AgentRuntimeClient.ActiveRunQuery.Known
        val terminalStateKnown = terminalRaceQuery is AgentRuntimeClient.CompletedRunsQuery.Known
        val activeRunId = (activeRunQuery as? AgentRuntimeClient.ActiveRunQuery.Known)?.runId
        val locallyObservedRunId = withContext(Dispatchers.Main) { currentRunId }
        val plan = AgentRunRecoveryCoordinator.plan(
            checkpoints = checkpoints,
            completedRuns = completedRuns,
            activeStateKnown = activeStateKnown,
            terminalStateKnown = terminalStateKnown,
            activeRunId = activeRunId,
            locallyObservedRunId = locallyObservedRunId,
        )
        val recoveringConversationIds = buildSet {
            plan.completed.forEach { add(AgentUiHandoffPayload.from(it.result.handoff.payload).conversationId) }
            plan.interrupted.forEach { add(AgentUiHandoffPayload.from(it.handoff.payload).conversationId) }
            plan.reattach?.let { add(AgentUiHandoffPayload.from(it.handoff.payload).conversationId) }
        }
        withLoadedConversations(recoveringConversationIds) {
            val orphanRewrites = if (activeStateKnown && terminalStateKnown) withContext(Dispatchers.Main) {
                val observed = checkpoints.mapTo(mutableSetOf()) { it.runId }.apply {
                    addAll(completedRuns.map { it.result.runId })
                    activeRunId?.let(::add)
                    currentRunId?.let(::add)
                }
                conversationsById.flatMap { (id, state) ->
                    state.roleplayMessages.pendingRewrites.keys.filterNot { it in observed }.map { id to it }
                }
            } else emptyList()
            if (
                plan.completed.isEmpty() &&
                plan.interrupted.isEmpty() &&
                plan.reattach == null && orphanRewrites.isEmpty()
            ) {
                return@withLoadedConversations
            }

            val acknowledgeAfterSave = mutableListOf<String>()
            val removeAfterSave = mutableListOf<String>()
            val changed = withContext(Dispatchers.Main) {
                var stateChanged = false
                plan.completed.forEach { recoveryPlan ->
                    val completedRun = recoveryPlan.result
                    val runId = completedRun.result.runId.ifBlank { completedRun.handoff.id }
                    val payload = AgentUiHandoffPayload.from(completedRun.handoff.payload)
                    val conversationId = payload.conversationId
                    val state = conversationsById[conversationId] ?: return@forEach
                    recoveryPlan.checkpoint?.let { checkpoint ->
                        stateChanged = restoreCheckpointTrace(
                            checkpoint = checkpoint,
                            interrupted = false,
                        ) || stateChanged
                    }
                    val result = completedRun.result
                    val recovery = AgentPendingResultRecovery.apply(
                        state = conversationsById[conversationId] ?: state,
                        runId = runId,
                        result = result,
                        promptSupplement = payload.promptSupplement,
                        supplements = payload.supplements,
                    )
                    if (recovery.alreadyApplied) {
                        acknowledgeAfterSave += runId
                        return@forEach
                    }
                    updateConversation(conversationId, recovery.state)
                    acknowledgeAfterSave += runId
                    stateChanged = true
                }

                plan.interrupted.forEach { checkpoint ->
                    removeAfterSave += checkpoint.runId
                    stateChanged = restoreCheckpointTrace(
                        checkpoint = checkpoint,
                        interrupted = true,
                    ) || stateChanged
                }
                orphanRewrites.forEach { (conversationId, runId) ->
                    conversationsById[conversationId]?.let { state ->
                        updateConversation(conversationId, RoleplayConversationReducer.applyRewrite(state, runId,
                            AgentRuntimeWire.RunResult(runId, false, "", "重新生成已中断，原回复已保留。",
                                operation = AgentRuntimeWire.OP_REWRITE_REPLY)))
                        stateChanged = true
                    }
                }
                if (stateChanged) refreshConversationSummaries()
                stateChanged || acknowledgeAfterSave.isNotEmpty() || removeAfterSave.isNotEmpty()
            }

            if (changed) {
                val saved = withContext(Dispatchers.Main) { persistConversations() }.await()
                if (saved) {
                    acknowledgeAfterSave.forEach(client::ackResult)
                    removeAfterSave.forEach { runId ->
                        AgentRunCheckpointStore.remove(appContext, runId)
                    }
                }
            }

            plan.reattach?.let { checkpoint ->
                withContext(Dispatchers.Main) { startReattachedRun(checkpoint) }
            }
        }
    }

    /** 把安全事件恢复为 UI 轨迹；半截回复不进入模型 history，设备工具也不会重放。 */
    private fun restoreCheckpointTrace(
        checkpoint: AgentRunCheckpointStore.Checkpoint,
        interrupted: Boolean,
    ): Boolean {
        val runId = checkpoint.runId
        if (runId.isBlank()) return false
        val conversationId = AgentUiHandoffPayload
            .from(checkpoint.handoff.payload)
            .conversationId
        val existing = conversationsById[conversationId] ?: return false
        if (AgentRuntimeHistoryReducer.wasApplied(existing, runId)) return false
        if (checkpoint.operation == AgentRuntimeWire.OP_REWRITE_REPLY) {
            val restored = RoleplayConversationReducer.restorePendingRewrite(existing, runId, checkpoint.rewriteTargetMessageId)
            if (interrupted) updateConversation(conversationId, RoleplayConversationReducer.applyRewrite(
                restored, runId, AgentRuntimeWire.RunResult(runId, false, "", "重新生成已中断，原回复已保留。",
                    operation = AgentRuntimeWire.OP_REWRITE_REPLY, rewriteTargetMessageId = checkpoint.rewriteTargetMessageId),
            )) else updateConversation(conversationId, restored)
            return interrupted || restored != existing
        }

        runConversationIds[runId] = conversationId
        updateConversation(conversationId, existing.copy(isStreaming = true, isCompacting = checkpoint.operation == AgentRuntimeWire.OP_COMPACT))
        restoreRunEvents(runId, checkpoint.events)
        flushPendingRunDelta(runId)
        updateRunTrace(runId) { messages ->
            val finalizedThinking = runMessageProjector.finalizeThinking(runId, messages)
            val finalizedText = runMessageProjector.finalizeText(runId, finalizedThinking)
            if (interrupted) {
                val interruptedTools = runMessageProjector.interruptRunningTools(
                    reason = appContext.getString(R.string.system_notice_interrupted),
                    messages = runMessageProjector.finishContextCompaction(runId, finalizedText, "上下文压缩已中断"),
                )
                val noticeId = "interrupted-$runId"
                if (interruptedTools.any { it.id == noticeId }) {
                    interruptedTools
                } else {
                    interruptedTools + SystemNoticeMessageUi(
                        id = noticeId,
                        code = SystemNoticeCode.Interrupted,
                    )
                }
            } else {
                runMessageProjector.finalizeRun(runId, finalizedText)
            }
        }
        if (interrupted && (checkpoint.contextSnapshot != null || checkpoint.transcript.isNotEmpty())) {
            applyConversationHistoryResult(runId, checkpoint.transcript, checkpoint.contextSnapshot, true)
        }
        conversationsById[conversationId]?.let { state ->
            updateConversation(conversationId, RoleplayConversationReducer.linkRun(state, runId))
        }
        setConversationStreaming(runId, false)
        runMessageProjector.clearRun(runId)
        runConversationIds.remove(runId)
        conversationUpdatedAt = conversationUpdatedAt +
            (conversationId to checkpoint.updatedAt)
        return true
    }

    private fun startReattachedRun(checkpoint: AgentRunCheckpointStore.Checkpoint) {
        val runId = checkpoint.runId
        val conversationId = AgentUiHandoffPayload
            .from(checkpoint.handoff.payload)
            .conversationId
        val existing = conversationsById[conversationId] ?: return
        if (currentRunId != null || AgentRuntimeHistoryReducer.wasApplied(existing, runId)) return

        runConversationIds[runId] = conversationId
        currentRunId = runId
        val restored = if (checkpoint.operation == AgentRuntimeWire.OP_REWRITE_REPLY) {
            RoleplayConversationReducer.restorePendingRewrite(existing, runId, checkpoint.rewriteTargetMessageId)
        } else existing
        updateConversation(conversationId, restored.copy(isStreaming = true, isCompacting = checkpoint.operation == AgentRuntimeWire.OP_COMPACT))
        refreshConversationSummaries()
        currentRunJob = scope.launch(Dispatchers.IO) {
            val client = AgentRuntimeClient(appContext, AndroidAgentLogger)
            val outcome = client.attachRun(
                runId = runId,
                onReplay = { events -> restoreRunEvents(runId, events) },
                onEvent = { event -> enqueueRunEvent(runId, event) },
            )
            when (outcome) {
                is AgentRuntimeClient.AttachOutcome.Completed -> withContext(Dispatchers.Main) {
                    applyRunResult(
                        runId = runId,
                        result = outcome.result,
                        acknowledgeRuntimeResult = true,
                    )
                    // 重新接管后才拿到终态的一轮同样不经过 finishRound。
                    if (outcome.result.ok) {
                        autoNameRecoveredRuns(listOf(conversationId to outcome.result.content))
                    }
                }
                AgentRuntimeClient.AttachOutcome.NotActive -> {
                    withContext(Dispatchers.Main) {
                        if (currentRunId == runId) {
                            currentRunId = null
                            currentRunJob = null
                            setConversationStreaming(runId, false)
                        }
                    }
                    recoverRuntimeRuns()
                }
                AgentRuntimeClient.AttachOutcome.Unavailable -> withContext(Dispatchers.Main) {
                    if (currentRunId == runId) {
                        currentRunId = null
                        currentRunJob = null
                        setConversationStreaming(runId, false)
                        refreshConversationSummaries()
                    }
                }
            }
        }
    }

    private suspend fun importArchivedExternalRuns() {
        val archivedRuns = withContext(Dispatchers.IO) {
            AgentRunArchiveStore.list(appContext)
                .filter { AgentExternalArchivePayload.from(it.handoff.payload) != null }
        }
        if (archivedRuns.isEmpty()) return

        for (archivedRun in archivedRuns) {
            val payload = AgentExternalArchivePayload.from(archivedRun.handoff.payload) ?: continue
            val conversationId = archiveConversationId(archivedRun.handoff.source, payload.conversationKey)
            val saved = withLoadedConversations(setOf(conversationId)) {
                val importedRunId = withContext(Dispatchers.Main) {
                    importExternalRun(archivedRun).also { refreshConversationSummaries() }
                } ?: return@withLoadedConversations true
                if (!withContext(Dispatchers.Main) { persistConversations() }.await()) return@withLoadedConversations false
                AgentRunArchiveStore.remove(appContext, importedRunId)
                true
            }
            if (!saved) break
            autoNameImportedExternalRuns(archivedRuns)
        }
    }

    /**
     * 外部入口（语音浮窗、小布、小爱、定时任务）发起的会话也要自动命名。
     *
     * 这些 run 由 Runtime 直接归档，不经过 [finishRound]，导入后标题一直是提问原文。
     * 只在导入时补一次：标题已经不是导入时的占位原文就不再改它。
     * */
    private fun autoNameImportedExternalRuns(runs: List<AgentRunArchiveStore.ArchivedRun>) {
        val targets = runs.asSequence()
            .sortedByDescending { it.createdAt }
            .mapNotNull { run ->
                val payload = AgentExternalArchivePayload.from(run.handoff.payload) ?: return@mapNotNull null
                if (run.result.operation != AgentRuntimeWire.OP_CHAT) return@mapNotNull null
                val answer = run.result.content.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = archiveConversationId(run.handoff.source, payload.conversationKey)
                if (conversationTitles[id] != payload.title) return@mapNotNull null
                if (id in externalNamingInFlight) return@mapNotNull null
                id to (payload.userText to answer)
            }
            .distinctBy { it.first }
            // 一次导入最多补这么多，避免积压很多语音会话时开机就发一串命名请求。
            .take(8)
            .toList()
        if (targets.isEmpty()) return
        targets.forEach { (id, _) -> externalNamingInFlight += id }
        scope.launch(Dispatchers.IO) {
            targets.forEach { (conversationId, promptAnswer) ->
                try {
                    val title = ConversationTitleGenerator()
                        .generate(promptAnswer.first, promptAnswer.second)
                    if (!title.isNullOrBlank()) {
                        withContext(Dispatchers.Main.immediate) {
                            conversationTitles = conversationTitles + (conversationId to title)
                            refreshConversationSummaries()
                        }
                        withContext(Dispatchers.Main.immediate + NonCancellable) { persistConversations() }
                        AndroidAgentLogger.info("External conversation auto named: chars=${title.length}")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    AndroidAgentLogger.warn("External conversation naming failed: type=${throwable.safeLogType()}")
                } finally {
                    withContext(Dispatchers.Main.immediate + NonCancellable) {
                        externalNamingInFlight -= conversationId
                    }
                }
            }
        }
    }

    /**
     * 恢复/导入回填的那一轮不经过 [finishRound]，标题会停在第一轮提问原文。
     *
     * 这里只在标题仍是占位时补一次语义命名；已经命名的会话不动，重复恢复也不会重复请求。
     */
    private fun autoNameRecoveredRuns(candidates: List<Pair<String, String>>) {
        val targets = candidates
            .filter { (_, answer) -> answer.isNotBlank() }
            .distinctBy { it.first }
            .filterNot { (id, _) -> id in recoveredNamingInFlight }
        if (targets.isEmpty()) return
        targets.forEach { (id, _) -> recoveredNamingInFlight += id }
        scope.launch(Dispatchers.IO) {
            targets.forEach { (conversationId, answer) ->
                try {
                    val prompt = withContext(Dispatchers.Main.immediate) {
                        conversationsById[conversationId]
                            ?.messages
                            ?.firstOrNull { it is UserMessageUi }
                            ?.let { (it as UserMessageUi).content }
                    }
                    if (prompt.isNullOrBlank()) return@forEach
                    val placeholder = prompt.defaultConversationTitleFromMessage()
                    val currentTitle = withContext(Dispatchers.Main.immediate) {
                        conversationTitles[conversationId]
                    }
                    if (!currentTitle.isNullOrBlank() && currentTitle != placeholder) return@forEach
                    val title = ConversationTitleGenerator().generate(prompt, answer)
                    if (title.isNullOrBlank()) return@forEach
                    withContext(Dispatchers.Main.immediate) {
                        conversationTitles = conversationTitles + (conversationId to title)
                        refreshConversationSummaries()
                    }
                    withContext(Dispatchers.Main.immediate + NonCancellable) { persistConversations() }
                    AndroidAgentLogger.info("Recovered conversation auto named: chars=${title.length}")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    AndroidAgentLogger.warn(
                        "Recovered conversation naming failed: type=${throwable.javaClass.simpleName}"
                    )
                } finally {
                    withContext(Dispatchers.Main.immediate + NonCancellable) {
                        recoveredNamingInFlight -= conversationId
                    }
                }
            }
        }
    }

    suspend fun openAssistantConversation(
        conversationKey: String,
        source: String = AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE,
    ): Boolean = openArchivedConversation(source, conversationKey)

    /** 打开外部入口按 [source] 与 [conversationKey] 归档的会话；先导入尚未入库的归档结果。 */
    suspend fun openArchivedConversation(source: String, conversationKey: String): Boolean {
        if (source.isBlank() || conversationKey.isBlank()) return false
        if (source != AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE) importArchivedExternalRuns()
        return withContext(Dispatchers.Main.immediate) {
            val conversationId = if (source == AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE) {
                conversationKey
            } else {
                archiveConversationId(source = source, conversationKey = conversationKey)
            }
            if (conversationsById[conversationId] == null) {
                false
            } else {
                selectConversation(conversationId)
                true
            }
        }
    }

    private fun importExternalRun(archivedRun: AgentRunArchiveStore.ArchivedRun): String? {
        val runId = archivedRun.result.runId.ifBlank { archivedRun.handoff.id }
        if (runId.isBlank()) return null
        val payload = AgentExternalArchivePayload.from(archivedRun.handoff.payload) ?: return null
        val conversationId = archiveConversationId(
            source = archivedRun.handoff.source,
            conversationKey = payload.conversationKey,
        )
        val archivedEffort = payload.reasoningEffort
            ?: payload.thinkingEnabled?.let(ReasoningEffort::fromLegacy)
            ?: ReasoningEffort.fromLegacy(defaultThinkingEnabled)
        val existingState = conversationsById[conversationId] ?: emptyChatState(
            archivedEffort.enablesReasoning
        ).copy(reasoningEffort = archivedEffort)
        val alreadyImported = AgentRuntimeHistoryReducer.wasApplied(existingState, runId) ||
            existingState.messages.any {
                it is AgentMessageUi &&
                    (it.id == "assistant-$runId" || it.id.startsWith("assistant-$runId-")) &&
                    !it.isStreaming
            }
        if (alreadyImported) return runId

        if (conversationTitles[conversationId].isNullOrBlank()) {
            conversationTitles = conversationTitles + (conversationId to payload.title)
        }
        runConversationIds[runId] = conversationId
        updateConversation(
            conversationId,
            ExternalConversationHistory.appendUser(existingState, runId, payload.userText).copy(
                input = "",
                isStreaming = true,
                thinkingEnabled = archivedEffort.enablesReasoning,
                reasoningEffort = archivedEffort,
                pendingImages = emptyList(),
                messages = existingState.messages +
                    UserMessageUi(
                        id = "user-$runId",
                        content = payload.userText,
                        images = archivedRun.userImagePreviews,
                    ) +
                    AgentMessageUi(
                        id = "assistant-$runId",
                        content = "",
                        isStreaming = true,
                        renderMarkdown = false,
                    ),
            )
        )
        archivedRun.events.forEach { event -> applyRunEvent(runId, event) }
        applyRunResult(runId, archivedRun.result)
        conversationUpdatedAt = conversationUpdatedAt + (conversationId to archivedRun.createdAt)
        return runId
    }

    fun updateThinkingEnabled(enabled: Boolean) {
        updateReasoningEffort(ReasoningEffort.fromLegacy(enabled))
    }

    fun updateReasoningEffort(effort: ReasoningEffort) {
        if (!homeState.isConversationLoaded) return
        val normalized = currentReasoningCapabilities?.normalize(effort) ?: ReasoningEffort.OFF
        updateCurrentConversation(
            homeState.copy(
                thinkingEnabled = normalized.enablesReasoning,
                reasoningEffort = normalized,
            )
        )
        if (selectedConversationId != null) persistConversations()
    }

    /** 聊天内切换同时改当前会话绑定与默认模型；默认模型供新会话和系统助手入口使用。 */
    fun selectModel(modelId: String) {
        if (
            !homeState.isConversationLoaded ||
            homeState.isStreaming ||
            modelPickerState.isChanging ||
            modelPickerState.selectedModel?.id == modelId
        ) {
            return
        }
        updateCurrentConversation(homeState.copy(modelId = modelId))
        refreshConversationModel()
        if (selectedConversationId != null) persistConversations()
        modelPickerState = modelPickerState.copy(isChanging = true)
        scope.launch(Dispatchers.IO) {
            try {
                RuntimeConfigRepository.setSelectedModelId(modelId)
                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, appContext.getString(R.string.state_ui_model_switching_failed_please_try_again_later_4af439), Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    modelPickerState = modelPickerState.copy(isChanging = false)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        conversationPaneState = conversationPaneState.copy(searchQuery = query)
        conversationSearchJob?.cancel()
        persistedSearchMatches = emptySet()
        contentMatchCache.clear()
        refreshConversationSummaries()
        if (query.isBlank()) return
        conversationSearchJob = scope.launch {
            delay(200)
            try {
                persistedSearchMatches = AgentConversationStore.searchConversationIds(
                    appContext, query.trim(),
                    SystemNoticeCode.entries.filter { noticeText(it).contains(query.trim(), ignoreCase = true) },
                )
                refreshConversationSummaries()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AndroidAgentLogger.warn("Agent conversation search failed: type=${failure.safeLogType()}")
            }
        }
    }

    fun selectConversation(conversationId: String) {
        if (homeState.messageEdit != null) cancelMessageEdit()
        val state = conversationsById[conversationId] ?: return
        val selectionVersion = ++conversationSelectionVersion
        cancelHistoryPageLoad()
        conversationSelectionJob?.cancel()
        fileAttachmentOwnerVersion += 1
        selectedConversationId = conversationId
        homeState = state
        conversationLoadFailed = false
        isLoadingConversation = !state.isConversationLoaded
        conversationPaneState = conversationPaneState.copy(selectedConversationId = conversationId)
        conversationSelectionJob = scope.launch {
            try {
                if (!ensureConversationLoaded(conversationId)) {
                    if (conversationSelectionVersion == selectionVersion) conversationLoadFailed = true
                    return@launch
                }
                if (conversationSelectionVersion != selectionVersion) return@launch
                refreshConversationModel()
                if (homeState.roleplayMessages.pendingRewrites.isNotEmpty()) refreshRuntimeResults()
                if (homeState.modelId != null && providers.enabledModel(homeState.modelId) == null) {
                    modelPickerState.selectedModel?.let { fallback ->
                        Toast.makeText(appContext, appContext.getString(
                            R.string.conversation_model_unavailable, fallback.displayName,
                        ), Toast.LENGTH_SHORT).show()
                    }
                }
                persistConversations()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AndroidAgentLogger.error("Agent conversation load failed: type=${failure.safeLogType()}")
                if (conversationSelectionVersion == selectionVersion) conversationLoadFailed = true
            } finally {
                if (conversationSelectionVersion == selectionVersion) isLoadingConversation = false
            }
        }
    }

    fun retryConversationLoad() {
        selectedConversationId?.let(::selectConversation)
    }

    private suspend fun ensureConversationLoaded(conversationId: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            conversationLoadMutex.withLock {
                val state = conversationsById[conversationId] ?: return@withLock false
                if (state.isConversationLoaded) return@withLock true
                synchronized(persistenceLock) { persistenceJob }?.join()
                val loaded = AgentConversationStore.loadConversation(appContext, conversationId)
                    ?: return@withLock false
                val current = conversationsById[conversationId] ?: return@withLock false
                val restored = loaded.copy(
                    input = current.input,
                    pendingImages = current.pendingImages,
                    pendingFileReferences = current.pendingFileReferences,
                )
                conversationPersistence.adoptConversation(
                    conversationId, restored, conversationTitles[conversationId].orEmpty(),
                    conversationUpdatedAt[conversationId] ?: 0L,
                )
                val latest = conversationsById[conversationId] ?: return@withLock false
                updateConversation(conversationId, restored.copy(
                    input = latest.input,
                    pendingImages = latest.pendingImages,
                    pendingFileReferences = latest.pendingFileReferences,
                ), updateTimestamp = false)
                refreshConversationSummaries()
                true
            }
        }

    private suspend fun <T> withLoadedConversations(ids: Set<String>, block: suspend () -> T): T {
        withContext(Dispatchers.Main.immediate) {
            ids.forEach { conversationContentPins[it] = (conversationContentPins[it] ?: 0) + 1 }
        }
        try {
            ids.forEach { id ->
                val loaded = ensureConversationLoaded(id)
                check(loaded || withContext(Dispatchers.Main.immediate) { id !in conversationsById }) {
                    "Conversation content unavailable"
                }
            }
            return block()
        } finally {
            withContext(Dispatchers.Main.immediate + NonCancellable) {
                ids.forEach { id ->
                    val count = (conversationContentPins[id] ?: 1) - 1
                    if (count == 0) conversationContentPins.remove(id) else conversationContentPins[id] = count
                }
                if (ids.isNotEmpty()) persistConversations()
            }
        }
    }

    fun loadEarlierMessages() = changeMessageWindow(loadEarlier = true)

    fun updateHistoryViewport(atLatest: Boolean) {
        if (!atLatest) {
            if (historyPageReleasesEarlier) cancelHistoryPageLoad()
        } else if (AgentConversationStore.shouldReleaseEarlierMessages(homeState.messages.size)) {
            changeMessageWindow(loadEarlier = false)
        }
    }

    private fun changeMessageWindow(loadEarlier: Boolean) {
        val conversationId = selectedConversationId ?: return
        if (historyPageJob?.isActive == true || !homeState.isConversationLoaded ||
            (loadEarlier && !homeState.hasEarlierMessages) ||
            homeState.isStreaming || homeState.messageEdit != null) return
        val pageVersion = ++historyPageVersion
        historyPageReleasesEarlier = !loadEarlier
        historyLoadState = ConversationHistoryLoadState.Loading
        historyPageJob = scope.launch {
            try {
                conversationLoadMutex.withLock {
                    // 翻页或释放旧页都先提交当前窗口，避免覆盖编辑结果或丢弃未保存的记录。
                    val state = conversationsById[conversationId] ?: return@withLock
                    val title = conversationTitles[conversationId].orEmpty()
                    val updatedAt = conversationUpdatedAt[conversationId] ?: 0L
                    check(persistConversations().await()) { "Conversation persistence failed" }
                    val page = if (loadEarlier) {
                        AgentConversationStore.loadEarlierMessages(appContext, conversationId, state.messageStartIndex)
                    } else {
                        AgentConversationStore.loadRecentMessages(appContext, conversationId)
                    }
                    val current = conversationsById[conversationId] ?: return@withLock
                    if (!current.canReplaceMessageWindowOf(state)) return@withLock
                    var window = RoleplayConversationReducer.decorate(state.copy(
                        messages = if (loadEarlier) page.messages + state.messages else page.messages,
                        messageStartIndex = page.startIndex,
                        hasEarlierMessages = page.hasEarlierMessages,
                    ))
                    window = window.copy(roleplayOriginalContents = AgentConversationStore.loadRoleplayMessageContents(
                        appContext, conversationId, RoleplayConversationReducer.requiredOriginalContentIds(window),
                    ))
                    conversationPersistence.adoptConversation(
                        conversationId, window, title, updatedAt,
                    )
                    val latest = conversationsById[conversationId] ?: return@withLock
                    if (!latest.canReplaceMessageWindowOf(state)) return@withLock
                    updateConversation(conversationId, latest.copy(
                        messages = window.messages,
                        messageStartIndex = window.messageStartIndex,
                        hasEarlierMessages = window.hasEarlierMessages,
                        roleplayOriginalContents = window.roleplayOriginalContents,
                    ), updateTimestamp = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AndroidAgentLogger.error("Agent message page load failed: type=${failure.safeLogType()}")
                if (historyPageVersion == pageVersion && loadEarlier) {
                    historyLoadState = ConversationHistoryLoadState.Failed
                }
            } finally {
                if (historyPageVersion == pageVersion) {
                    historyPageReleasesEarlier = false
                    if (historyLoadState == ConversationHistoryLoadState.Loading) {
                        historyLoadState = ConversationHistoryLoadState.Idle
                    }
                }
            }
        }
    }

    private fun cancelHistoryPageLoad() {
        historyPageVersion += 1
        historyPageReleasesEarlier = false
        historyPageJob?.cancel()
        historyPageJob = null
        historyLoadState = ConversationHistoryLoadState.Idle
    }

    private suspend fun releaseInactiveConversations(saved: Map<String, AgentChatHomeUiState>) {
        conversationLoadMutex.withLock {
            for ((id, state) in saved) {
                // 较旧的排队保存可能在卸载后重新持有该会话基线，必须再次释放。
                if (conversationsById[id]?.isConversationLoaded != true) {
                    conversationPersistence.forgetConversation(id)
                    continue
                }
                if (!state.isConversationLoaded || id == selectedConversationId || id in conversationContentPins || state.isStreaming ||
                    state.messageEdit != null || conversationsById[id] !== state) continue
                conversationPersistence.forgetConversation(id)
                if (id == selectedConversationId || id in conversationContentPins || conversationsById[id] !== state) continue
                conversationsById = conversationsById + (id to AgentConversationStore.unloadConversation(state))
                contentMatchCache.remove(id)
            }
        }
    }

    fun createConversation() {
        conversationSelectionVersion += 1
        cancelHistoryPageLoad()
        conversationSelectionJob?.cancel()
        isLoadingConversation = false
        conversationLoadFailed = false
        if (homeState.messageEdit != null) cancelMessageEdit()
        fileAttachmentOwnerVersion += 1
        selectedConversationId = null
        homeState = emptyChatState(defaultThinkingEnabled)
        refreshConversationModel()
        conversationPaneState = conversationPaneState.copy(
            selectedConversationId = null,
            searchQuery = "",
        )
        refreshConversationSummaries()
        composerFocusRequestId += 1
        composerFocusRequests.value = composerFocusRequestId
        runCatching {
            java.io.File(appContext.filesDir, "eta_focus.log")
                .appendText("${System.currentTimeMillis()} createConversation id=$composerFocusRequestId\n")
        }
        persistConversations()
    }

    /** 焦点请求改为序号制后无需清除；保留空实现，兼容既有调用点。 */
    fun consumeComposerFocusRequest() = Unit

    /** 执行通知的「补充」按钮：回到主界面后把输入框聚焦并拉起键盘。 */
    fun requestComposerFocus() {
        composerFocusRequestId += 1
    }

    fun startCharacterConversation(binding: RoleplayBinding, greeting: String) {
        createConversation()
        val id = newConversationId()
        val greetingId = "greeting-$id"
        val text = CharacterMacros.expand(
            text = greeting, card = CharacterCardCodec.decodeJson(binding.cardSnapshotJson),
            userName = binding.userName, userDescription = binding.userDescription,
        )
        val transcript = if (text.isBlank()) emptyList() else listOf(
            AgentModelClient.ConversationMessage(role = "assistant", content = text, messageId = greetingId),
        )
        selectedConversationId = id
        homeState = emptyChatState(defaultThinkingEnabled).withCurrentReasoningCapabilities().copy(
            roleplay = binding,
            history = transcript,
            journal = transcript,
            messages = if (text.isBlank()) emptyList() else listOf(AgentMessageUi(greetingId, text, characterEditable = true)),
            roleplayMessages = RoleplayMessageState(links = if (text.isBlank()) emptyMap() else mapOf(
                greetingId to RoleplayMessageLink(greetingId),
            )),
        )
        conversationTitles = conversationTitles + (id to binding.characterName)
        updateConversation(id, homeState)
        conversationPaneState = conversationPaneState.copy(selectedConversationId = id, searchQuery = "")
        refreshConversationSummaries()
        persistConversations()
    }

    fun selectReplyCandidate(messageId: String, index: Int) {
        if (homeState.isStreaming || homeState.messageEdit != null) return
        val updated = RoleplayConversationReducer.select(homeState, messageId, index) ?: return
        updateCurrentConversation(updated)
        refreshConversationSummaries()
        persistConversations()
    }

    fun deleteConversation(conversationId: String) {
        val wasSelected = selectedConversationId == conversationId
        conversationsById = conversationsById - conversationId
        conversationTitles = conversationTitles - conversationId
        conversationUpdatedAt = conversationUpdatedAt - conversationId
        conversationPinned = conversationPinned - conversationId
        if (workspaceAssignments.containsKey(conversationId)) {
            workspaceAssignments = workspaceAssignments - conversationId
            persistWorkspaces()
        }
        if (wasSelected) {
            conversationSelectionVersion += 1
            cancelHistoryPageLoad()
            conversationSelectionJob?.cancel()
            isLoadingConversation = false
            conversationLoadFailed = false
            fileAttachmentOwnerVersion += 1
            val nextId = conversationsById.keys.firstOrNull()
            if (nextId != null) {
                selectedConversationId = nextId
                homeState = conversationsById.getValue(nextId)
            } else {
                selectedConversationId = null
                homeState = emptyChatState(defaultThinkingEnabled)
            }
            if (nextId != null) selectConversation(nextId) else refreshConversationModel()
        }
        conversationPaneState = conversationPaneState.copy(selectedConversationId = selectedConversationId)
        refreshConversationSummaries()
        persistConversations()
    }

    fun renameConversation(conversationId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        conversationTitles = conversationTitles + (conversationId to trimmed)
        conversationUpdatedAt = conversationUpdatedAt + (conversationId to System.currentTimeMillis())
        refreshConversationSummaries()
        persistConversations()
    }

    fun setConversationPinned(conversationId: String, pinned: Boolean) {
        if (!conversationsById.containsKey(conversationId)) return
        val updated = if (pinned) conversationPinned + conversationId else conversationPinned - conversationId
        if (updated == conversationPinned) return
        conversationPinned = updated
        refreshConversationSummaries()
        persistConversations()
    }

    suspend fun exportConversationMarkdown(conversationId: String): String? {
        if (conversationId !in conversationsById) return null
        if (!persistConversations().await()) error(appContext.getString(R.string.conversation_persistence_failed))

        val title = conversationTitles[conversationId]?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.conversation_unnamed)
        return withContext(Dispatchers.IO) { ConversationMarkdownExporter.export(
            title = title,
            messages = AgentConversationStore.loadAllMessages(appContext, conversationId),
            labels = ConversationMarkdownExporter.Labels(
                user = appContext.getString(R.string.conversation_export_user),
                assistant = appContext.getString(R.string.conversation_export_assistant),
                thinking = appContext.getString(R.string.conversation_export_thinking),
                toolLineFormat = appContext.getString(R.string.conversation_export_tool_line),
                toolsLineFormat = appContext.getString(R.string.conversation_export_tools_line),
                argumentsFormat = appContext.getString(R.string.conversation_export_tool_arguments),
                resultFormat = appContext.getString(R.string.conversation_export_tool_result),
                imagesFormat = appContext.getString(R.string.conversation_export_images),
                toolStatusRunning = appContext.getString(R.string.tool_status_running),
                toolStatusSuccess = appContext.getString(R.string.tool_status_success),
                toolStatusFailed = appContext.getString(R.string.tool_status_failed),
                toolStatusUnknown = appContext.getString(R.string.tool_status_unknown),
                noticeStopped = noticeText(SystemNoticeCode.Stopped),
                noticeEmptyResult = noticeText(SystemNoticeCode.EmptyResult),
                noticeModelRetry = noticeText(SystemNoticeCode.ModelRetry),
                noticeContextCompaction = noticeText(SystemNoticeCode.ContextCompaction),
                noticeRuntimeFailed = noticeText(SystemNoticeCode.RuntimeFailed),
                noticeUiPaused = noticeText(SystemNoticeCode.UiPaused),
                noticeInterrupted = noticeText(SystemNoticeCode.Interrupted),
            ),
        ) }
    }

    /** 新建工作区（会话文件夹）。name 为空时用「工作区 N」兜底，返回新 id。 */
    fun createWorkspace(name: String? = null): String {
        val id = UUID.randomUUID().toString()
        val resolved = name?.trim().orEmpty().ifBlank {
            appContext.getString(R.string.workspace_default_name, workspaceRecords.size + 1)
        }
        workspaceRecords = workspaceRecords + ConversationWorkspaceRecord(id = id, name = resolved)
        persistWorkspaces()
        refreshConversationSummaries()
        return id
    }

    fun renameWorkspace(workspaceId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val index = workspaceRecords.indexOfFirst { it.id == workspaceId }
        if (index < 0 || workspaceRecords[index].name == trimmed) return
        workspaceRecords = workspaceRecords.toMutableList().also { it[index] = it[index].copy(name = trimmed) }
        persistWorkspaces()
        refreshConversationSummaries()
    }

    /** 删除工作区只删文件夹本身，里面的会话回到普通列表，不会被一并删除。 */
    fun deleteWorkspace(workspaceId: String) {
        if (workspaceRecords.none { it.id == workspaceId }) return
        workspaceRecords = workspaceRecords.filterNot { it.id == workspaceId }
        workspaceAssignments = workspaceAssignments.filterValues { it != workspaceId }
        persistWorkspaces()
        refreshConversationSummaries()
    }

    fun setWorkspaceCollapsed(workspaceId: String, collapsed: Boolean) {
        val index = workspaceRecords.indexOfFirst { it.id == workspaceId }
        if (index < 0 || workspaceRecords[index].collapsed == collapsed) return
        workspaceRecords = workspaceRecords.toMutableList().also { it[index] = it[index].copy(collapsed = collapsed) }
        persistWorkspaces()
        refreshConversationSummaries()
    }

    /** 把会话放进工作区；workspaceId 为空表示移出工作区。 */
    fun moveConversationToWorkspace(conversationId: String, workspaceId: String?) {
        if (!conversationsById.containsKey(conversationId)) return
        val target = workspaceId?.takeIf { candidate -> workspaceRecords.any { it.id == candidate } }
        val updated = if (target == null) {
            workspaceAssignments - conversationId
        } else {
            workspaceAssignments + (conversationId to target)
        }
        if (updated == workspaceAssignments) return
        workspaceAssignments = updated
        persistWorkspaces()
        refreshConversationSummaries()
    }

    private fun applyWorkspaceState(state: ConversationWorkspaceState) {
        workspaceRecords = state.workspaces
        workspaceAssignments = state.assignments
        refreshConversationSummaries()
    }

    private fun persistWorkspaces() {
        val state = ConversationWorkspaceState(workspaces = workspaceRecords, assignments = workspaceAssignments)
        scope.launch(Dispatchers.IO) {
            runCatching { SettingsDataStore.setConversationWorkspaces(state) }
        }
    }

    private fun noticeText(code: SystemNoticeCode): String = appContext.getString(
        when (code) {
            SystemNoticeCode.Stopped -> R.string.system_notice_stopped
            SystemNoticeCode.EmptyResult -> R.string.system_notice_empty_result
            SystemNoticeCode.ContextCompaction -> R.string.context_compaction
            SystemNoticeCode.ModelRetry -> R.string.system_notice_model_retry
            SystemNoticeCode.RuntimeFailed -> R.string.system_notice_runtime_failed
            SystemNoticeCode.UiPaused -> R.string.virtual_screen_paused
            SystemNoticeCode.Interrupted -> R.string.system_notice_interrupted
        },
    )

    fun sendCurrentMessage(submittedText: String? = null) {
        submitOrQueueCurrentMessage(submittedText = submittedText, immediateInterrupt = false)
    }

    fun interruptAndSendCurrentMessage(submittedText: String? = null) {
        submitOrQueueCurrentMessage(submittedText = submittedText, immediateInterrupt = true)
    }

    /**
     * 流式期间「长按发送」：把这句话直接注入正在运行的回合（steerRun），
     * 不排队也不打断当前运行；注入失败时把草稿放回输入框，避免消息丢失。
     */
    fun steerCurrentMessage(submittedText: String? = null) {
        if (!homeState.isConversationLoaded || homeState.isCompacting) return
        val prompt = (submittedText ?: homeState.input).trim()
        if (prompt.isBlank()) return
        val conversationId = selectedConversationId ?: return
        val runId = currentRunId?.takeIf { runConversationIds[it] == conversationId }
        if (runId == null) {
            restoreInterjectionDraft(conversationId, prompt)
            return
        }
        scope.launch(Dispatchers.IO) {
            val accepted = AgentRuntimeClient(appContext, AndroidAgentLogger).steerRun(runId, prompt)
            if (!accepted) withContext(Dispatchers.Main) {
                restoreInterjectionDraft(conversationId, prompt)
            }
        }
    }

    fun steerQueuedMessage(id: String) {
        if (!homeState.isConversationLoaded) return
        val (nextState, promoted) = AgentMessageQueueReducer.promoteToInterrupt(homeState, id)
        if (promoted == null) return
        updateCurrentConversation(nextState, updateTimestamp = false)
        if (nextState.isStreaming && !nextState.isCompacting) {
            requestInterruptCurrentRun()
        } else if (!nextState.isStreaming) {
            dispatchQueuedMessage(selectedConversationId, promoted)
        }
    }

    fun recallQueuedMessage(id: String, currentDraftText: String) {
        if (!homeState.isConversationLoaded) return
        val nextState = AgentMessageQueueReducer.recallToDraft(
            state = homeState,
            currentInputText = currentDraftText,
            id = id,
        ) ?: return
        updateCurrentConversation(nextState, updateTimestamp = false)
    }

    fun removeQueuedMessage(id: String) {
        if (!homeState.isConversationLoaded) return
        val nextState = AgentMessageQueueReducer.remove(homeState, id)
        if (nextState !== homeState) {
            updateCurrentConversation(nextState, updateTimestamp = false)
        }
    }

    private fun submitOrQueueCurrentMessage(
        submittedText: String?,
        immediateInterrupt: Boolean,
    ) {
        if (!homeState.isConversationLoaded || homeState.isCompacting) return
        val prompt = (submittedText ?: homeState.input).trim()
        val pendingImages = homeState.pendingImages
        val pendingFileReferences = homeState.pendingFileReferences
        if (
            prompt.isBlank() && pendingImages.isEmpty() && pendingFileReferences.isEmpty()
        ) {
            return
        }
        if (!homeState.isStreaming) {
            homeState.messageEdit?.takeIf { it.preserveFollowingMessages }?.let { edit ->
                val updated = RoleplayConversationReducer.edit(homeState, edit.targetMessageId, prompt) ?: return
                updateCurrentConversation(updated.copy(
                    input = edit.previousInput,
                    pendingImages = edit.previousImages,
                    pendingFileReferences = edit.previousFileReferences,
                    messageEdit = null,
                ))
                refreshConversationSummaries()
                persistConversations()
                return
            }
        }
        val fileReferences = pendingFileReferences.map { it.reference }
        if (
            !AgentFileReferencePolicy.canSend(
                references = fileReferences,
                terminalToolsEnabled = agentBooleanForUi(Prefs.Keys.AGENT_TERMINAL_TOOLS),
            )
        ) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.state_ui_file_path_reference_requires_opening_the_termina_deca4c),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        if (homeState.isStreaming) {
            val queued = QueuedMessageUi(
                id = "queue-${UUID.randomUUID()}",
                content = prompt,
                pendingImages = pendingImages,
                pendingFileReferences = pendingFileReferences,
                immediateInterrupt = immediateInterrupt,
            )
            updateCurrentConversation(
                state = AgentMessageQueueReducer.enqueue(homeState, queued),
                updateTimestamp = false,
            )
            if (immediateInterrupt) {
                requestInterruptCurrentRun()
            }
            return
        }

        val edit = homeState.messageEdit

        val editBoundary = edit?.let {
            AgentConversationRevisionReducer.boundary(homeState, it.targetMessageId)
        }
        if (edit != null && editBoundary == null) {
            cancelMessageEdit()
            return
        }

        val conversationId = selectedConversationId ?: newConversationId().also {
            selectedConversationId = it
        }
        dispatchMessageToConversation(
            conversationId = conversationId,
            state = homeState,
            prompt = prompt,
            pendingImages = pendingImages,
            pendingFileReferences = pendingFileReferences,
            editBoundary = editBoundary,
        )
    }

    private fun dispatchQueuedMessage(
        conversationId: String?,
        item: QueuedMessageUi,
    ) {
        val targetConversationId = conversationId ?: selectedConversationId ?: newConversationId().also {
            selectedConversationId = it
        }
        val state = conversationsById[targetConversationId] ?: homeState
        if (state.isStreaming || state.isCompacting) return
        val fileReferences = item.pendingFileReferences.map { it.reference }
        if (
            !AgentFileReferencePolicy.canSend(
                references = fileReferences,
                terminalToolsEnabled = agentBooleanForUi(Prefs.Keys.AGENT_TERMINAL_TOOLS),
            )
        ) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.state_ui_file_path_reference_requires_opening_the_termina_deca4c),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        dispatchMessageToConversation(
            conversationId = targetConversationId,
            state = state,
            prompt = item.content,
            pendingImages = item.pendingImages,
            pendingFileReferences = item.pendingFileReferences,
            editBoundary = null,
            preserveCurrentDraft = true,
        )
    }

    private fun dispatchMessageToConversation(
        conversationId: String,
        state: AgentChatHomeUiState,
        prompt: String,
        pendingImages: List<PendingImageUi>,
        pendingFileReferences: List<PendingFileReferenceUi>,
        editBoundary: AgentConversationRevisionReducer.Boundary?,
        preserveCurrentDraft: Boolean = false,
    ) {
        val fileReferences = pendingFileReferences.map { it.reference }
        val runtimePrompt = AgentFileReferencePromptCodec.format(prompt, fileReferences)
        val runId = "run-${UUID.randomUUID()}"
        val userMessage = UserMessageUi(
            id = editBoundary?.userMessage?.id ?: "user-$runId",
            content = runtimePrompt,
            images = pendingImages.map { it.dataUrl },
            isEdited = editBoundary != null,
        )
        val history = editBoundary?.historyPrefix ?: state.history
        val messages = if (editBoundary == null) {
            state.messages + userMessage
        } else {
            state.messages.take(editBoundary.userMessageIndex) + userMessage
        }
        val userHistoryMessage = AgentModelClient.buildUserHistoryMessage(
            text = runtimePrompt,
            images = pendingImages.toHistoryImages(),
        ).copy(messageId = userMessage.id)

        val currentTitle = conversationTitles[conversationId]
        val oldAutoTitle = editBoundary
            ?.takeIf { it.userMessageIndex == 0 }
            ?.userMessage
            ?.content
            ?.defaultConversationTitleFromMessage()
        val nextAutoTitle = defaultConversationTitle(prompt, fileReferences)
        val title = if (
            editBoundary?.userMessageIndex == 0 &&
            (currentTitle == oldAutoTitle || currentTitle.isNullOrBlank())
        ) {
            nextAutoTitle
        } else {
            currentTitle?.takeIf(String::isNotBlank) ?: nextAutoTitle
        }

        // 这个会话还没有正式名字：等第一轮回答完整结束后按语义命名，期间顶栏显示占位。
        if (currentTitle.isNullOrBlank()) autoNamingPending += conversationId
        conversationTitles = conversationTitles + (conversationId to title)
        if (selectedConversationId == conversationId) {
            conversationPaneState = conversationPaneState.copy(selectedConversationId = conversationId)
        }
        launchConversationRun(
            conversationId = conversationId,
            runId = runId,
            prompt = runtimePrompt,
            images = pendingImages,
            history = history,
            userHistoryMessage = userHistoryMessage,
            messages = messages,
            state = if (preserveCurrentDraft) {
                state.copy(
                    journal = editBoundary?.journalPrefix ?: state.journal,
                    messageEdit = null,
                )
            } else {
                state.copy(
                    journal = editBoundary?.journalPrefix ?: state.journal,
                    input = "",
                    pendingImages = emptyList(),
                    pendingFileReferences = emptyList(),
                    messageEdit = null,
                )
            },
            reasoningEffort = state.reasoningEffort,
        )
    }

    private fun restoreInterjectionDraft(conversationId: String, text: String) {
        val current = conversationsById[conversationId] ?: return
        if (current.input.isBlank()) updateConversation(conversationId, current.copy(input = text))
        Toast.makeText(appContext, appContext.getString(R.string.chat_interject_unavailable), Toast.LENGTH_SHORT).show()
    }

    fun beginMessageEdit(messageId: String) {
        if (homeState.isStreaming || homeState.messageEdit != null) return
        if (homeState.roleplay != null) {
            if (messageId !in homeState.roleplayMessages.links) return
            val content = when (val message = homeState.messages.firstOrNull { it.id == messageId }) {
                is UserMessageUi -> message.content
                is AgentMessageUi -> message.content
                else -> return
            }
            updateCurrentConversation(homeState.copy(
                input = content, pendingImages = emptyList(), pendingFileReferences = emptyList(),
                messageEdit = MessageEditUiState(messageId, homeState.input, homeState.pendingImages,
                    homeState.pendingFileReferences, hasLaterTurns = false, preserveFollowingMessages = true),
            ))
            return
        }
        val boundary = revisionBoundary(messageId) ?: return
        val images = boundary.userMessage.images.mapIndexed { index, dataUrl ->
            PendingImageUi(
                id = "edit-${boundary.userMessage.id}-$index",
                uri = dataUrl,
                dataUrl = dataUrl,
                mimeType = dataUrl.imageMimeType(),
            )
        }
        val parsedPrompt = AgentFileReferencePromptCodec.parse(boundary.userMessage.content)
        val fileReferences = parsedPrompt.references.mapIndexed { index, reference ->
            PendingFileReferenceUi(
                id = "edit-${boundary.userMessage.id}-file-$index",
                reference = reference,
            )
        }
        updateCurrentConversation(
            homeState.copy(
                input = parsedPrompt.request,
                pendingImages = images,
                pendingFileReferences = fileReferences,
                messageEdit = MessageEditUiState(
                    targetMessageId = boundary.userMessage.id,
                    previousInput = homeState.input,
                    previousImages = homeState.pendingImages,
                    previousFileReferences = homeState.pendingFileReferences,
                    hasLaterTurns = boundary.laterTurnCount > 0,
                ),
            )
        )
        if (boundary.contextWasCompacted) showCompactedRevisionNotice()
    }

    fun cancelMessageEdit() {
        val edit = homeState.messageEdit ?: return
        updateCurrentConversation(
            homeState.copy(
                input = edit.previousInput,
                pendingImages = edit.previousImages,
                pendingFileReferences = edit.previousFileReferences,
                messageEdit = null,
            )
        )
    }

    fun messageRevisionImpact(messageId: String): MessageRevisionImpact? =
        if (homeState.roleplay != null && messageId.startsWith("greeting-")) MessageRevisionImpact(0)
        else revisionBoundary(messageId)?.let { boundary ->
            MessageRevisionImpact(laterTurnCount = boundary.laterTurnCount)
        }

    private fun revisionBoundary(messageId: String): AgentConversationRevisionReducer.Boundary? =
        AgentConversationRevisionReducer.boundary(homeState, messageId).also { boundary ->
            if (boundary == null && homeState.hasEarlierMessages) {
                Toast.makeText(appContext, R.string.conversation_load_turn_start, Toast.LENGTH_SHORT).show()
            }
        }

    fun deleteMessageTurn(messageId: String) {
        if (homeState.isStreaming || homeState.messageEdit != null) return
        val conversationId = selectedConversationId ?: return
        if (homeState.roleplay != null && messageId.startsWith("greeting-")) {
            val originalId = homeState.roleplayMessages.links[messageId]?.transcriptMessageId ?: return
            val updated = homeState.copy(
                messages = homeState.messages.filterNot { it.id == messageId },
                journal = homeState.journal.filterNot { it.messageId == originalId },
                history = homeState.history.filterNot { it.messageId == originalId || it.contextSummary },
                roleplayMessages = homeState.roleplayMessages.copy(
                    links = homeState.roleplayMessages.links - messageId,
                    revisions = homeState.roleplayMessages.revisions - messageId,
                ),
            )
            val history = RoleplayConversationReducer.projectJournal(updated) ?: return
            updateConversation(conversationId, updated.copy(history = history))
            refreshConversationSummaries()
            persistConversations()
            return
        }
        if (revisionBoundary(messageId) == null) return
        val removed = AgentConversationRevisionReducer.deleteFromTurn(homeState, messageId) ?: return
        val removedIds = homeState.messages.mapTo(mutableSetOf()) { it.id }.apply {
            removeAll(removed.messages.map { it.id }.toSet())
        }
        val revised = if (removed.roleplay == null) removed else removed.copy(roleplayMessages = removed.roleplayMessages.copy(
            links = removed.roleplayMessages.links.filterKeys { it !in removedIds },
            revisions = removed.roleplayMessages.revisions.filterKeys { it !in removedIds },
        ))
        if (revised.messages.isEmpty() && !revised.hasEarlierMessages) {
            conversationsById = conversationsById - conversationId
            conversationTitles = conversationTitles - conversationId
            conversationUpdatedAt = conversationUpdatedAt - conversationId
            fileAttachmentOwnerVersion += 1
            selectedConversationId = null
            homeState = emptyChatState(defaultThinkingEnabled)
            refreshConversationModel()
            conversationPaneState = conversationPaneState.copy(selectedConversationId = null)
            refreshConversationSummaries()
            persistConversations()
            return
        }
        updateConversation(conversationId, revised)
        refreshConversationSummaries()
        persistConversations()
    }

    fun regenerateMessage(messageId: String) {
        if (homeState.isStreaming || homeState.messageEdit != null) return
        val conversationId = selectedConversationId ?: return
        if (homeState.roleplay != null) {
            val target = homeState.messages.filterIsInstance<AgentMessageUi>().firstOrNull { it.id == messageId } ?: return
            val prefix = RoleplayConversationReducer.rewriteHistory(homeState, messageId) ?: return
            val runId = "run-${UUID.randomUUID()}"
            launchConversationRun(
                conversationId, runId, target.content, emptyList(), prefix, null, homeState.messages,
                homeState.copy(roleplayMessages = homeState.roleplayMessages.copy(
                    pendingRewrites = homeState.roleplayMessages.pendingRewrites + (runId to messageId),
                )), homeState.reasoningEffort, operation = AgentRuntimeWire.OP_REWRITE_REPLY,
                rewriteTargetMessageId = messageId,
            )
            return
        }
        val boundary = revisionBoundary(messageId) ?: return
        val images = boundary.userMessage.images.mapIndexed { index, dataUrl ->
            PendingImageUi(
                id = "regenerate-${boundary.userMessage.id}-$index",
                uri = dataUrl,
                dataUrl = dataUrl,
                mimeType = dataUrl.imageMimeType(),
            )
        }
        val runId = "run-${UUID.randomUUID()}"
        val userHistoryMessage = AgentModelClient.buildUserHistoryMessage(
            text = boundary.userMessage.content,
            images = images.toHistoryImages(),
        )
        if (boundary.contextWasCompacted) showCompactedRevisionNotice()
        launchConversationRun(
            conversationId = conversationId,
            runId = runId,
            prompt = boundary.userMessage.content,
            images = images,
            history = boundary.historyPrefix,
            userHistoryMessage = userHistoryMessage,
            messages = homeState.messages.take(boundary.userMessageIndex + 1),
            state = homeState.copy(journal = boundary.journalPrefix),
            reasoningEffort = homeState.reasoningEffort,
        )
    }

    fun compactCurrentContext() {
        val conversationId = selectedConversationId ?: return
        if (currentRunId != null || !homeState.canCompactContext || modelPickerState.isChanging) return
        launchConversationRun(
            conversationId = conversationId,
            runId = java.util.UUID.randomUUID().toString(),
            prompt = "", images = emptyList(), history = homeState.history,
            userHistoryMessage = null, messages = homeState.messages, state = homeState,
            reasoningEffort = homeState.reasoningEffort, operation = AgentRuntimeWire.OP_COMPACT,
        )
    }

    private fun launchConversationRun(
        conversationId: String,
        runId: String,
        prompt: String,
        images: List<PendingImageUi>,
        history: List<AgentModelClient.ConversationMessage>,
        userHistoryMessage: AgentModelClient.ConversationMessage?,
        messages: List<AgentChatMessageUi>,
        state: AgentChatHomeUiState,
        reasoningEffort: ReasoningEffort,
        operation: String = AgentRuntimeWire.OP_CHAT,
        rewriteTargetMessageId: String? = null,
    ) {
        runConversationIds[runId] = conversationId
        currentRunId = runId
        // 发送即绑定：会话记住本轮实际使用的模型，回落到默认模型时也随之更新。
        val runModelId = state.effectiveModelId

        updateConversation(
            conversationId,
            state.copy(
                modelId = runModelId,
                isStreaming = true,
                history = if (operation == AgentRuntimeWire.OP_REWRITE_REPLY) state.history else history + listOfNotNull(userHistoryMessage),
                journal = state.journal.ifEmpty { state.history } + listOfNotNull(userHistoryMessage),
                isCompacting = operation == AgentRuntimeWire.OP_COMPACT,
                messages = messages,
                messageEdit = null,
            )
        )
        refreshConversationSummaries()
        val initialPersistence = persistConversations()

        val preparationJob = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            // 本轮消耗 = 开跑前的余额 − 整轮跑完后的余额。起点快照立刻发起、不阻塞这一轮，
            // 到收尾时再 await；挂在 scope 上，不会随本协程被取消而丢掉。
            val roundStartBalance: Deferred<Double?>? = balanceStore?.let { store ->
                scope.async(Dispatchers.IO) { store.refreshAndAwaitTotal() }
            }
            // write-ahead：用户消息未提交前不把可能产生副作用的 run 交给 Runtime。
            if (!initialPersistence.await()) {
                withContext(Dispatchers.Main) {
                    applyRunResult(
                        runId,
                        AgentRuntimeWire.RunResult(
                            runId = runId,
                            ok = false,
                            content = "",
                            error = appContext.getString(R.string.conversation_persistence_failed),
                        )
                    )
                }
                return@launch
            }
            state.roleplay?.let { binding ->
                try {
                    CharacterRepository.initialize(appContext)
                    CharacterRepository.get(binding.characterId)?.let { profile ->
                        val updatedBinding = binding.copy(
                            cardSnapshotJson = CharacterCardCodec.encodeJson(profile.card),
                            characterName = profile.card.name, avatarPath = profile.avatarPath,
                        )
                        val saved = withContext(Dispatchers.Main) {
                            conversationsById[conversationId]?.let { current ->
                                updateConversation(conversationId, current.copy(roleplay = updatedBinding))
                            }
                            persistConversations()
                        }.await()
                        check(saved) { "无法保存角色会话设定" }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    AndroidAgentLogger.warnThrottled("character_run_prepare_failed") {
                        "Character preparation failed: type=${failure.safeLogType()}"
                    }
                    withContext(Dispatchers.Main) {
                        applyRunResult(runId, AgentRuntimeWire.RunResult(runId, false, "", "无法读取或保存角色设定，请重试。",
                            operation = operation, rewriteTargetMessageId = rewriteTargetMessageId))
                    }
                    return@launch
                }
            }
            val permittedReasoningEffort = if (
                agentBooleanForUi(Prefs.Keys.AGENT_THINKING_ENABLED)
            ) {
                reasoningEffort
            } else {
                ReasoningEffort.OFF
            }
            val config = RuntimeConfigRepository.runtimeConfigFor(runModelId)?.copy(
                terminalTools = agentBooleanForUi(Prefs.Keys.AGENT_TERMINAL_TOOLS),
                browserTools = agentBooleanForUi(Prefs.Keys.AGENT_BROWSER_TOOLS),
                deviceDirectTools = agentBooleanForUi(Prefs.Keys.AGENT_DEVICE_DIRECT_TOOLS),
                deviceSensitiveReadTools =
                    agentBooleanForUi(Prefs.Keys.AGENT_DEVICE_SENSITIVE_READ_TOOLS),
                deviceSensitiveActionTools =
                    agentBooleanForUi(Prefs.Keys.AGENT_DEVICE_SENSITIVE_ACTION_TOOLS),
                thinkingEnabled = permittedReasoningEffort.enablesReasoning,
                reasoningEffort = permittedReasoningEffort,
            )
            if (config == null) {
                withContext(Dispatchers.Main) {
                    applyRunResult(
                        runId,
                        AgentRuntimeWire.RunResult(
                            runId = runId,
                            ok = false,
                            content = "",
                            error = appContext.getString(R.string.state_ui_please_configure_the_model_provider_and_model_fi_a36e15),
                        )
                    )
                }
                return@launch
            }
            val modelImages = images.map { p ->
                AgentModelClient.ModelImage(
                    reference = p.uri,
                    mimeType = p.mimeType,
                    bytes = 0,
                    source = "user_attach",
                )
            }
            if (withContext(Dispatchers.Main) { runId in stopRequestedRunIds }) {
                withContext(Dispatchers.Main) {
                    applyRunResult(runId, AgentRuntimeWire.RunResult(runId, false, "", "已停止", operation = operation))
                }
                return@launch
            }
            val result = try {
                runInterruptible {
                    AgentRuntimeClient(appContext, AndroidAgentLogger).run(
                        request = AgentRuntimeWire.RunRequest(
                            operation = operation,
                            rewriteTargetMessageId = rewriteTargetMessageId,
                            runId = runId,
                            prompt = prompt,
                            config = config,
                            images = modelImages,
                            history = history,
                            handoff = AgentRuntimeWire.EntryHandoff(
                                id = runId,
                                source = AgentRuntimeWire.AGENT_UI_HANDOFF_SOURCE,
                                payload = conversationId,
                            ),
                        ),
                        onEvent = { event -> enqueueRunEvent(runId, event) },
                    )
                }
            } catch (cancelled: CancellationException) {
                // 界面被回收（切后台被系统回收 Activity/ViewModel）导致的协程取消，不再传染给 runtime：
                // 只清本地运行句柄，任务继续跑，下次进入界面由恢复流程把结果接管回会话。
                withContext(NonCancellable + Dispatchers.Main.immediate) { detachLocalRun(runId) }
                throw cancelled
            }
            if (result.errorCode == AgentRuntimeWire.ERROR_RUN_DETACHED) {
                // 客户端等待被中断、但任务并未取消：同样交回恢复流程，不能写成“已停止”。
                withContext(NonCancellable + Dispatchers.Main.immediate) { detachLocalRun(runId) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                applyRunResult(runId, result, acknowledgeRuntimeResult = true)
            }
            if (result.ok) {
                // 只有整轮完整结束（不是中途回答）才命名 + 结算这一轮的消耗。
                finishRound(conversationId, prompt, result.content, roundStartBalance)
            }
        }
        currentRunJob = preparationJob
        if (!RootAccess.isGranted) {
            val leaseId = "prepare:$runId"
            val acquired = AgentExecutionService.acquire(appContext, leaseId) {
                scope.launch(Dispatchers.Main.immediate) {
                    if (currentRunId == runId) stopCurrentRun()
                }
            }
            if (!acquired) {
                preparationJob.cancel()
                applyRunResult(runId, AgentRuntimeWire.RunResult(
                    runId = runId,
                    ok = false,
                    content = "",
                    error = appContext.getString(R.string.capability_background_failed),
                ))
                return
            }
            preparationJob.invokeOnCompletion { AgentExecutionService.release(leaseId) }
        }
        preparationJob.start()
    }

    private fun List<PendingImageUi>.toHistoryImages(): List<AgentModelClient.ModelImage> =
        map { image ->
            AgentModelClient.ModelImage(
                reference = image.dataUrl,
                mimeType = image.mimeType,
                bytes = image.dataUrl.length,
                source = image.uri,
            )
        }

    private fun String.imageMimeType(): String =
        takeIf { startsWith("data:") }
            ?.substringAfter("data:")
            ?.substringBefore(';')
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"

    private fun String.defaultConversationTitle(): String =
        lineSequence().firstOrNull().orEmpty().trim().take(MAX_TITLE_CHARS)

    private fun defaultConversationTitle(
        request: String,
        references: List<AgentFileReference>,
    ): String = AgentFileReferencePolicy
        .titleSource(request, references)
        .defaultConversationTitle()

    private fun String.defaultConversationTitleFromMessage(): String {
        val parsed = AgentFileReferencePromptCodec.parse(this)
        return defaultConversationTitle(parsed.request, parsed.references)
    }

    private fun showCompactedRevisionNotice() {
        Toast.makeText(
            appContext,
            appContext.getString(R.string.state_ui_the_earlier_context_has_been_compressed_and_will_cf6c86),
            Toast.LENGTH_LONG,
        ).show()
    }

    fun attachImage(uri: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val image = AgentImageCodec.fromReference(
                    context = appContext,
                    value = uri,
                    source = "user_attach",
                )
                if (image == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.state_ui_unable_to_read_this_image_please_try_again_or_us_d94978),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                val preview = AgentImageCodec.previewFromReference(appContext, image) ?: image
                val pending = PendingImageUi(
                    id = "img-${UUID.randomUUID()}",
                    // 后续发送使用首次读取后的稳定引用，不再依赖 ROM Photo Picker URI 的授权生命周期。
                    uri = image.reference,
                    dataUrl = preview.reference,
                    mimeType = image.mimeType,
                )
                withContext(Dispatchers.Main) {
                    updateCurrentConversation(homeState.copy(pendingImages = homeState.pendingImages + pending))
                }
            } finally {
                val selectedUri = Uri.parse(uri)
                if (selectedUri.scheme == ContentResolver.SCHEME_CONTENT) {
                    runCatching {
                        appContext.contentResolver.releasePersistableUriPermission(
                            selectedUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }
    }

    fun removePendingImage(id: String) {
        updateCurrentConversation(homeState.copy(pendingImages = homeState.pendingImages.filterNot { it.id == id }))
    }

    fun attachFiles(uris: List<String>) {
        if (uris.isEmpty()) return
        resolveAndAttachFileReferences {
            val gateway = AgentFileReferenceGateway(appContext, AndroidAgentLogger)
            uris.map { uri ->
                gateway.resolveDocumentUri(
                    uri = Uri.parse(uri),
                    expectedKind = AgentFileReferenceKind.File,
                )
            }
        }
    }

    fun attachFolder(uri: String) {
        resolveAndAttachFileReferences {
            val gateway = AgentFileReferenceGateway(appContext, AndroidAgentLogger)
            listOf(
                gateway.resolveDocumentUri(
                    uri = Uri.parse(uri),
                    expectedKind = AgentFileReferenceKind.Directory,
                )
            )
        }
    }

    fun attachFilePath(path: String) {
        resolveAndAttachFileReferences {
            listOf(AgentFileReferenceGateway(AndroidAgentLogger).resolveAbsolutePath(path))
        }
    }

    fun removePendingFileReference(id: String) {
        updateCurrentConversation(
            homeState.copy(
                pendingFileReferences = homeState.pendingFileReferences.filterNot { it.id == id }
            )
        )
    }

    private fun resolveAndAttachFileReferences(
        resolver: () -> List<AgentFileReferenceGateway.Resolution>,
    ) {
        val ownerVersion = fileAttachmentOwnerVersion
        scope.launch(Dispatchers.IO) {
            val resolutions = resolver()
            val references = resolutions.mapNotNull { resolution ->
                (resolution as? AgentFileReferenceGateway.Resolution.Success)?.reference
            }
            val failures = resolutions.mapNotNull { resolution ->
                (resolution as? AgentFileReferenceGateway.Resolution.Failure)?.error
            }
            withContext(Dispatchers.Main) {
                if (ownerVersion != fileAttachmentOwnerVersion) {
                    Toast.makeText(appContext, appContext.getString(R.string.state_ui_conversation_switched_selected_path_not_added_5bf91e), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val existingPaths = homeState.pendingFileReferences
                    .mapTo(mutableSetOf()) { it.reference.absolutePath }
                val additions = references
                    .distinctBy { it.absolutePath }
                    .filter { existingPaths.add(it.absolutePath) }
                    .map { reference ->
                        PendingFileReferenceUi(
                            id = "file-${UUID.randomUUID()}",
                            reference = reference,
                        )
                    }
                if (additions.isNotEmpty()) {
                    updateCurrentConversation(
                        homeState.copy(
                            pendingFileReferences = homeState.pendingFileReferences + additions
                        )
                    )
                }
                val message = when {
                    failures.size == 1 && references.isEmpty() -> failures.single().userMessage
                    failures.isNotEmpty() -> appContext.resources.getQuantityString(
                        R.plurals.file_references_added_with_failures,
                        failures.size,
                        additions.size,
                        failures.size,
                    )
                    additions.isEmpty() -> appContext.getString(R.string.state_ui_the_selected_path_has_been_added_42b432)
                    else -> null
                }
                if (message != null) {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val AgentFileReferenceGateway.Error.userMessage: String
        get() = when (this) {
            AgentFileReferenceGateway.Error.UnsupportedDocumentProvider ->
                appContext.getString(R.string.capability_import_denied)
            AgentFileReferenceGateway.Error.InvalidPath -> appContext.getString(R.string.state_ui_please_enter_a_valid_absolute_path_6afeb4)
            AgentFileReferenceGateway.Error.PathNotFound -> appContext.getString(R.string.state_ui_the_path_does_not_exist_or_is_no_longer_accessib_a9776e)
            AgentFileReferenceGateway.Error.UnsupportedFileType -> appContext.getString(R.string.state_ui_only_supports_normal_files_and_folders_4adea0)
            AgentFileReferenceGateway.Error.TypeMismatch -> appContext.getString(R.string.state_ui_the_selected_project_type_does_not_match_3a5c49)
            AgentFileReferenceGateway.Error.RootUnavailable -> appContext.getString(R.string.state_ui_root_is_not_available_and_the_path_cannot_be_ver_fc4c81)
            AgentFileReferenceGateway.Error.AccessDenied -> appContext.getString(R.string.capability_import_denied)
            AgentFileReferenceGateway.Error.ImportFailed -> appContext.getString(R.string.capability_import_failed)
            AgentFileReferenceGateway.Error.ImportTooLarge -> appContext.getString(R.string.capability_import_too_large)
            AgentFileReferenceGateway.Error.ValidationTimedOut -> appContext.getString(R.string.state_ui_path_verification_timed_out_please_try_again_703687)
        }

    private val stopRequestedRunIds = mutableSetOf<String>()
    private val userStoppedRunIds = mutableSetOf<String>()

    fun stopCurrentRun() {
        val runId = currentRunId ?: return
        userStoppedRunIds.add(runId)
        conversationIdForRun(runId)?.let { id ->
            conversationsById[id]?.let { state ->
                val cleared = AgentMessageQueueReducer.clearInterruptFlags(state)
                if (cleared !== state) {
                    updateConversation(id, cleared, updateTimestamp = false)
                }
            }
        }
        requestCancelRun(runId)
    }

    private fun requestInterruptCurrentRun() {
        val runId = currentRunId ?: return
        requestCancelRun(runId)
    }

    private fun requestCancelRun(runId: String) {
        if (!stopRequestedRunIds.add(runId)) return
        scope.launch(Dispatchers.IO) {
            AgentRuntimeClient(appContext, AndroidAgentLogger).cancelRun(runId)
        }
    }

    /**
     * 界面侧脱钩（不取消任务）。
     *
     * 切后台被系统回收、Activity/ViewModel 销毁等都会取消本界面协程；这不是用户要停任务。
     * 这里只清掉本地运行句柄，任务继续在 runtime 侧跑完，结果由下次进入界面的恢复流程接管。
     */
    private fun detachLocalRun(runId: String) {
        runConversationIds.remove(runId)
        if (currentRunId == runId) currentRunId = null
        if (currentRunJob?.isActive != true) currentRunJob = null
    }

    /** 顶栏标题：还没命名（第一轮未完成）时返回 null，由界面显示「新任务」。 */
    fun topBarConversationTitle(conversationId: String?): String? {
        val id = conversationId ?: return null
        if (id in autoNamingPending) return null
        return conversationTitles[id]?.takeIf { it.isNotBlank() }
    }

    /**
     * 整轮完整结束后的收尾：先按语义命名（它的花费也算进本轮），再结算本轮消耗。
     * 两步都在 IO 上跑，任何失败只影响这两项显示，不影响正文与落库。
     */
    private fun finishRound(
        conversationId: String,
        prompt: String,
        answer: String,
        startBalance: Deferred<Double?>?,
    ) {
        val store = balanceStore
        if (answer.isBlank() && store == null) return
        scope.launch(Dispatchers.IO) {
            // 命名的判据是「标题仍是第一轮占位原文」；autoNamingPending 只是快路径，
            // 界面重建或结果走恢复回填时集合会丢，这里仍然能补上。
            val pendingNaming = withContext(Dispatchers.Main.immediate) {
                conversationId in autoNamingPending
            }
            val needsNaming = answer.isNotBlank() &&
                (pendingNaming || isPlaceholderTitle(conversationId))
            if (!needsNaming) {
                if (answer.isBlank()) {
                    AndroidAgentLogger.info("Conversation naming skipped: empty answer")
                }
                withContext(Dispatchers.Main.immediate) { autoNamingPending.remove(conversationId) }
            } else {
                try {
                    val title = ConversationTitleGenerator().generate(prompt, answer)
                    if (!title.isNullOrBlank()) {
                        withContext(Dispatchers.Main.immediate) {
                            conversationTitles = conversationTitles + (conversationId to title)
                            // 侧栏读的是摘要列表；不主动刷新的话标题要等下一次别的刷新才同步。
                            refreshConversationSummaries()
                        }
                        withContext(Dispatchers.Main.immediate + NonCancellable) { persistConversations() }
                        AndroidAgentLogger.info("Conversation auto named by model: chars=${title.length}")
                    }
                } finally {
                    // 命名真的结束（成功或失败）才解除占位。提前解除会让顶栏在「新任务」和
                    // 最终标题之间闪一下本轮提问的原文——列表里的占位标题就是那句原文。
                    withContext(Dispatchers.Main.immediate + NonCancellable) {
                        autoNamingPending.remove(conversationId)
                    }
                }
            }
            if (store == null) return@launch
            // 起点用发消息时抓的快照；抓失败时退回最近一次已知余额，并记日志。
            val before = startBalance?.await() ?: store.state.total
            if (before == null) {
                AndroidAgentLogger.info("Round cost skipped: balance unavailable")
                return@launch
            }
            // DeepSeek 余额有几十秒延迟（实测 45s 内落地），立刻抓第二次只会读到同一个数，
            // 所以隔一会儿补抓几次（见 DeepSeekBalanceStore.SETTLE_RETRY_DELAYS_MS）。
            val after = store.settleBalanceChange(before)
            if (after == null) {
                AndroidAgentLogger.info("Round cost skipped: balance did not settle in time")
                return@launch
            }
            val cost = before - after
            AndroidAgentLogger.info("Round cost settled: before=$before after=$after cost=$cost")
            if (cost <= 0.0) return@launch
            // 结算期间又开了一轮的话，这次余额变化不再只属于本轮，放弃以免记错。
            val busy = withContext(Dispatchers.Main.immediate) {
                currentRunId != null || currentRunJob?.isActive == true
            }
            if (busy) {
                AndroidAgentLogger.info("Round cost skipped: another run is active")
                return@launch
            }
            withContext(Dispatchers.Main.immediate) { attachRoundCost(conversationId, cost) }
        }
    }

    /**
     * 标题是否仍是第一轮提问的占位原文；用户手动改过名字的会话返回 false。
     *
     * 占位必须用**会话首条用户消息**算：标题占位是第一轮提问生成的。拿本轮 prompt 去比，
     * 第二轮起永远对不上，就会把「还没命名」误判成「已有名字」，之后再也不会补命名。
     */
    private suspend fun isPlaceholderTitle(conversationId: String): Boolean {
        val firstUser = withContext(Dispatchers.Main.immediate) {
            conversationsById[conversationId]
                ?.messages
                ?.firstOrNull { it is UserMessageUi }
                ?.let { (it as UserMessageUi).content }
        } ?: return false
        val placeholder = firstUser.defaultConversationTitleFromMessage()
        return withContext(Dispatchers.Main.immediate) {
            val current = conversationTitles[conversationId]
            current.isNullOrBlank() || current == placeholder
        }
    }

    /** 把本轮消耗写到该会话最后一条回答上，随消息一起落库；老会话没有这一列就不显示。 */
    private fun attachRoundCost(conversationId: String, cost: Double) {
        val conversation = conversationsById[conversationId] ?: return
        val index = conversation.messages.indexOfLast { it is AgentMessageUi }
        if (index < 0) return
        val message = conversation.messages[index] as? AgentMessageUi ?: return
        if (message.roundCost != null) return
        val messages = conversation.messages.toMutableList().apply {
            this[index] = message.copy(roundCost = cost)
        }
        updateConversation(conversationId, conversation.copy(messages = messages))
        // 只精确更新这一行：整会话重写在超大会话里很慢，进程被杀会把记录一起丢掉。
        scope.launch(Dispatchers.IO) {
            runCatching {
                EtaDatabase.get(appContext).conversationDao()
                    .updateMessageRoundCost(conversationId, message.id, cost)
            }.onFailure { failure ->
                AndroidAgentLogger.warn("Round cost persist failed: type=${failure.safeLogType()}")
            }
        }
    }

    private fun isReplyRewrite(runId: String): Boolean =
        conversationsById[conversationIdForRun(runId)]?.roleplayMessages?.pendingRewrites?.containsKey(runId) == true

    private fun restoreRunEvents(runId: String, events: List<AgentEvent>) {
        if (isReplyRewrite(runId)) return
        // 恢复是完整快照：先清除同一 run 的旧投影，再一次发布，避免历史增量重复追加
        // 或中途的 Running 状态使已结束的思考重新展开、播放动画。
        Snapshot.withMutableSnapshot {
            flushPendingRunDelta(runId)
            updateMessages(runId, updateTimestamp = false) { messages ->
                runMessageProjector.resetForReplay(
                    runId = runId,
                    messages = messages,
                    replaySupplementIndexes = events.filterIsInstance<AgentEvent.UserSupplementReceived>()
                        .mapTo(mutableSetOf()) { it.index },
                )
            }
            events.forEach { event -> applyRunEvent(runId, event, persistSupplement = false) }
        }
    }

    private fun enqueueRunEvent(runId: String, event: AgentEvent) {
        if (event is AgentEvent.AssistantBlockDelta) {
            if (event.kind == AgentEvent.AssistantBlockKind.TOOL_CALL || event.delta.isEmpty()) return

            runEventCoalescer.append(runId, event)?.let { ready ->
                applyRunEvent(runId, ready)
            }
            scheduleRunDeltaFlush(runId)
            return
        }

        flushPendingRunDelta(runId)
        applyRunEvent(runId, event)
    }

    private fun scheduleRunDeltaFlush(runId: String) {
        if (runEventFlushJobs[runId]?.isActive == true) return
        runEventFlushJobs[runId] = scope.launch {
            delay(STREAM_UI_UPDATE_INTERVAL_MS)
            runEventFlushJobs.remove(runId)
            flushPendingRunDelta(runId)
        }
    }

    private fun flushPendingRunDelta(runId: String) {
        runEventFlushJobs.remove(runId)?.cancel()
        runEventCoalescer.flush(runId)?.let { event ->
            applyRunEvent(runId, event)
        }
    }

    private fun applyRunEvent(
        runId: String,
        event: AgentEvent,
        persistSupplement: Boolean = true,
    ) {
        if (isReplyRewrite(runId)) {
            if (event is AgentEvent.RunStarted && runId in stopRequestedRunIds) scope.launch(Dispatchers.IO) {
                AgentRuntimeClient(appContext, AndroidAgentLogger).cancelRun(runId)
            }
            return
        }
        when (event) {
            is AgentEvent.UserSupplementReceived ->
                insertSupplementMessage(runId, event.index, event.text, persist = persistSupplement)
            is AgentEvent.RunStarted -> if (runId in stopRequestedRunIds) scope.launch(Dispatchers.IO) {
                AgentRuntimeClient(appContext, AndroidAgentLogger).cancelRun(runId)
            }
            // 流式增量只刷新消息，不推进会话时间戳，也不重算侧栏摘要；其余可见变化两者都更新。
            is AgentEvent.AssistantBlockDelta -> updateMessages(runId, updateTimestamp = false) { messages ->
                runMessageProjector.applyEvent(runId, event, messages)
            }
            // 用量事件既写进消息统计，也记到会话上，供顶栏胶囊显示最近一次请求。
            is AgentEvent.UsageReceived -> {
                updateRunTrace(runId) { messages -> runMessageProjector.applyEvent(runId, event, messages) }
                updateAssistantUsage(runId, event.usage.toUi())
            }
            is AgentEvent.ContextCompaction -> {
                if (event.phase == AgentEvent.ContextCompaction.PHASE_COMPLETED) {
                    conversationIdForRun(runId)?.let { id ->
                        conversationsById[id]?.let { current ->
                            updateConversation(
                                id,
                                current.copy(lastModelUsage = current.lastModelUsage?.copy(contextTokens = null)),
                            )
                        }
                    }
                }
                updateRunTrace(runId) { messages -> runMessageProjector.applyEvent(runId, event, messages) }
            }
            else -> updateRunTrace(runId) { messages -> runMessageProjector.applyEvent(runId, event, messages) }
        }
    }

    private fun applyRunResult(
        runId: String,
        result: AgentRuntimeWire.RunResult,
        acknowledgeRuntimeResult: Boolean = false,
    ) {
        flushPendingRunDelta(runId)
        val completedConversationId = conversationIdForRun(runId)
        val explicitlyStoppedByUser = userStoppedRunIds.remove(runId)
        val rewriting = result.operation == AgentRuntimeWire.OP_REWRITE_REPLY || isReplyRewrite(runId)
        stopRequestedRunIds.remove(runId)
        if (runId == currentRunId) {
            currentRunId = null
            currentRunJob = null
        }
        if (rewriting) {
            val conversationId = conversationIdForRun(runId)
            val existing = conversationsById[conversationId]
            if (conversationId != null && existing != null && result.contextSnapshotRef.isBlank()) {
                updateConversation(conversationId, RoleplayConversationReducer.applyRewrite(existing, runId, result))
            } else setConversationStreaming(runId, false)
            runMessageProjector.clearRun(runId)
            runConversationIds.remove(runId)
            refreshConversationSummaries()
            persistConversations(onSaved = if (acknowledgeRuntimeResult && result.contextSnapshotRef.isBlank()) {
                { AgentRuntimeClient(appContext, AndroidAgentLogger).ackResult(runId) }
            } else null)
            return
        }
        updateRunTrace(runId) { messages ->
            runMessageProjector.finishContextCompaction(runId,
                runMessageProjector.finalizeRun(runId, messages),
                if (result.ok) "上下文压缩完成" else result.error ?: "上下文压缩已停止")
        }
        if (result.contextSnapshotRef.isBlank()) {
            applyConversationHistoryResult(runId, result.transcript, result.contextSnapshot, !result.ok || result.contextSnapshot != null)
        }
        when {
            result.operation == AgentRuntimeWire.OP_COMPACT ||
                conversationsById[conversationIdForRun(runId)]?.isCompacting == true -> updateMessages(runId) { messages ->
                    AgentRunMessageProjector.mergeCompactionResultNotice(
                        runId = runId,
                        messages = messages,
                        ok = result.ok,
                        detail = if (result.ok) "上下文压缩完成" else result.error ?: "上下文压缩失败",
                    )
                }
            else -> {
                val notice = when {
                    result.ok && result.content.isNotBlank() -> null
                    result.ok -> SystemNoticeCode.EmptyResult
                    result.errorCode == "UI_EXECUTION_PAUSED" -> SystemNoticeCode.UiPaused
                    result.error == LEGACY_STOPPED_ERROR || result.error == SYNTHETIC_STATUS_STOPPED ->
                        SystemNoticeCode.Stopped
                    else -> SystemNoticeCode.RuntimeFailed
                }
                updateMessages(runId) { messages ->
                    AgentRunMessageProjector.applyResult(
                        runId, messages, result.content, notice,
                        detail = result.error.takeIf {
                            notice == SystemNoticeCode.RuntimeFailed || notice == SystemNoticeCode.UiPaused
                        },
                    )
                }
            }
        }
        setConversationStreaming(runId, false)
        conversationIdForRun(runId)?.let { id -> conversationsById[id]?.let {
            updateConversation(id, RoleplayConversationReducer.linkRun(it, runId))
        } }
        runMessageProjector.clearRun(runId)
        runConversationIds.remove(runId)
        refreshConversationSummaries()
        persistConversations(
            onSaved = if (acknowledgeRuntimeResult && result.contextSnapshotRef.isBlank()) {
                {
                    AgentRuntimeClient(appContext, AndroidAgentLogger).ackResult(runId)
                }
            } else {
                null
            }
        )
        if (completedConversationId != null && currentRunId == null) {
            val finishedState = conversationsById[completedConversationId]
            if (finishedState != null && finishedState.queuedMessages.isNotEmpty()) {
                val stoppedByUser = explicitlyStoppedByUser ||
                    (!result.ok && (result.error == LEGACY_STOPPED_ERROR || result.error == SYNTHETIC_STATUS_STOPPED))
                val (afterPopState, nextQueued) = AgentMessageQueueReducer.pollNextToSendAfterRun(
                    state = finishedState,
                    stoppedByUser = stoppedByUser,
                )
                updateConversation(completedConversationId, afterPopState, updateTimestamp = false)
                if (nextQueued != null) {
                    dispatchQueuedMessage(completedConversationId, nextQueued)
                }
            }
        }
    }

    private fun updateRunTrace(
        runId: String,
        transform: (List<AgentChatMessageUi>) -> List<AgentChatMessageUi>,
    ) {
        if (updateMessages(runId, transform = transform)) refreshConversationSummaries()
    }

    /** 记录会话最近一次模型用量：顶栏胶囊显示 token 数与输出速度。 */
    private fun updateAssistantUsage(runId: String, usage: TokenUsageUi) {
        if (usage.isEmpty) return
        conversationIdForRun(runId)?.let { id ->
            conversationsById[id]?.let { current ->
                updateConversation(id, current.copy(lastModelUsage = usage))
            }
        }
    }

    private fun insertSupplementMessage(
        runId: String,
        index: Int,
        text: String,
        persist: Boolean = true,
    ) {
        updateMessages(runId) { messages ->
            AgentPendingResultRecovery.mergeSupplements(
                runId = runId,
                supplements = listOf(
                    AgentUiHandoffPayload.Supplement(
                        index = index,
                        text = text,
                        createdAt = System.currentTimeMillis(),
                    )
                ),
                messages = messages,
            )
        }
        refreshConversationSummaries()
        if (persist) persistConversations()
    }

    private fun updateMessages(
        runId: String,
        updateTimestamp: Boolean = true,
        transform: (List<AgentChatMessageUi>) -> List<AgentChatMessageUi>,
    ): Boolean {
        val conversationId = conversationIdForRun(runId) ?: return false
        val state = conversationsById[conversationId] ?: return false
        val messages = transform(state.messages)
        // 投影对无可见变化的事件返回原列表；此时不推进会话时间戳，避免请求开始、轮次开始等事件改变排序。
        if (messages === state.messages) return false
        updateConversation(
            conversationId = conversationId,
            state = state.copy(messages = messages),
            updateTimestamp = updateTimestamp,
        )
        return true
    }

    private fun applyConversationHistoryResult(
        runId: String,
        additions: List<AgentModelClient.ConversationMessage>,
        snapshot: AgentContextSnapshot? = null,
        retainPendingSupplements: Boolean = snapshot != null,
    ) {
        val conversationId = conversationIdForRun(runId) ?: return
        val state = conversationsById[conversationId] ?: return
        val outcome = AgentRuntimeHistoryReducer.apply(state, runId, additions, snapshot, retainPendingSupplements)
        if (!outcome.alreadyApplied) updateConversation(conversationId, outcome.state)
    }

    private fun updateCurrentConversation(
        state: AgentChatHomeUiState,
        updateTimestamp: Boolean = true,
    ) {
        val conversationId = selectedConversationId
        if (conversationId == null) {
            homeState = state
        } else {
            updateConversation(conversationId, state, updateTimestamp = updateTimestamp)
        }
    }

    private fun moveCurrentDraftToNewConversation() {
        val draft = homeState
        selectedConversationId = null
        homeState = emptyChatState(defaultThinkingEnabled).copy(
            input = draft.input,
            thinkingEnabled = draft.reasoningEffort.enablesReasoning,
            reasoningEffort = draft.reasoningEffort,
            availableReasoningEfforts = currentReasoningCapabilities?.selectableEfforts.orEmpty(),
            pendingImages = draft.pendingImages,
            pendingFileReferences = draft.pendingFileReferences,
            modelId = draft.modelId,
        )
        conversationPaneState = conversationPaneState.copy(selectedConversationId = null)
    }

    private fun updateConversation(
        conversationId: String,
        state: AgentChatHomeUiState,
        updateTimestamp: Boolean = true,
    ) {
        conversationsById = conversationsById + (conversationId to state)
        if (updateTimestamp) {
            conversationUpdatedAt = conversationUpdatedAt + (conversationId to System.currentTimeMillis())
        }
        if (conversationId == selectedConversationId) {
            homeState = state
        }
    }

    private fun setConversationStreaming(runId: String, isStreaming: Boolean) {
        val conversationId = conversationIdForRun(runId) ?: return
        val state = conversationsById[conversationId] ?: return
        updateConversation(conversationId, state.copy(isStreaming = isStreaming, isCompacting = state.isCompacting && isStreaming))
    }

    private fun conversationIdForRun(runId: String): String? = runConversationIds[runId]

    private fun conversationStateForRun(runId: String): AgentChatHomeUiState {
        val conversationId = conversationIdForRun(runId) ?: return emptyChatState(defaultThinkingEnabled)
        return conversationsById[conversationId] ?: emptyChatState(defaultThinkingEnabled)
    }

    private fun refreshConversationSummaries() {
        val summaries = conversationsById.entries
            .sortedByDescending { (id, _) ->
                conversationUpdatedAt[id] ?: 0L
            }
            .map { (id, state) ->
                val lastMessage = state.messages.lastOrNull()
                ConversationSummaryUi(
                    id = id,
                    title = conversationTitles[id].orEmpty().ifBlank {
                        appContext.getString(R.string.conversation_unnamed)
                    },
                    preview = when (lastMessage) {
                        is UserMessageUi -> AgentFileReferencePromptCodec
                            .parse(lastMessage.content)
                            .let { parsed ->
                                AgentFileReferencePolicy.titleSource(
                                    request = parsed.request,
                                    references = parsed.references,
                                )
                            }
                        is AgentMessageUi -> lastMessage.content.ifBlank {
                            appContext.getString(R.string.conversation_preview_reasoning)
                        }
                        is SystemNoticeMessageUi -> noticeText(lastMessage.code)
                        is ThinkingMessageUi -> appContext.getString(R.string.conversation_preview_reasoning)
                        is ToolActivityMessageUi -> appContext.getString(
                            R.string.conversation_preview_tool_call,
                            lastMessage.toolName,
                        )
                        else -> appContext.getString(R.string.conversation_preview_empty)
                    }.take(MAX_PREVIEW_CHARS),
                    timeLabel = if (state.isStreaming) {
                        appContext.getString(R.string.time_now)
                    } else {
                        conversationUpdatedAt[id]?.let { timestamp ->
                            ConversationTimeLabels.label(
                                timestampMillis = timestamp,
                                locale = appContext.resources.configuration.locales[0],
                                use24HourClock = DateFormat.is24HourFormat(appContext),
                                yesterdayLabel = appContext.getString(R.string.time_yesterday),
                                recentLabel = appContext.getString(R.string.time_recent),
                            )
                        } ?: appContext.getString(R.string.time_recent)
                    },
                    updatedAtMillis = conversationUpdatedAt[id] ?: 0L,
                    mode = ConversationModeUi.Chat,
                    characterName = state.roleplay?.characterName ?: state.conversationCharacterName,
                    characterAvatarPath = state.roleplay?.avatarPath ?: state.conversationCharacterAvatarPath,
                    isActiveRun = state.isStreaming,
                    isPinned = id in conversationPinned,
                    workspaceId = workspaceAssignments[id]
                        ?.takeIf { target -> workspaceRecords.any { it.id == target } },
                )
            }
            .sortedByDescending { it.isPinned }
        val query = conversationPaneState.searchQuery.trim()
        val buckets = summaries.groupForWorkspaces()
        conversationPaneState = conversationPaneState.copy(
            selectedConversationId = selectedConversationId,
            conversations = if (query.isBlank()) {
                buckets.loose
            } else {
                contentMatchCache.keys.retainAll(conversationsById.keys)
                buckets.loose.filter { summary ->
                    summary.title.contains(query, ignoreCase = true) ||
                        summary.preview.contains(query, ignoreCase = true) ||
                        summary.id in persistedSearchMatches || conversationContentMatches(summary.id, query)
                }
            },
            // 搜索时把结果平铺出来，避免命中项被折叠在文件夹里看不见。
            workspaces = if (query.isBlank()) buckets.workspaces else emptyList(),
        )
    }

    private data class WorkspaceBuckets(
        val workspaces: List<ConversationWorkspaceUi>,
        val loose: List<ConversationSummaryUi>,
    )

    /** 按工作区归拢摘要：已归入工作区的会话只在文件夹里出现，不在下方分组里重复。 */
    private fun List<ConversationSummaryUi>.groupForWorkspaces(): WorkspaceBuckets {
        if (workspaceRecords.isEmpty()) return WorkspaceBuckets(emptyList(), this)
        val byWorkspace = groupBy { it.workspaceId }
        val workspaces = workspaceRecords.map { record ->
            ConversationWorkspaceUi(
                id = record.id,
                name = record.name,
                collapsed = record.collapsed,
                conversations = byWorkspace[record.id].orEmpty(),
            )
        }
        return WorkspaceBuckets(workspaces = workspaces, loose = filter { it.workspaceId == null })
    }

    // 内容匹配按（查询词, 会话状态引用）缓存：刷新摘要时未变化的会话不重复全文扫描。
    private val contentMatchCache = mutableMapOf<String, ContentMatchCacheEntry>()

    private fun conversationContentMatches(conversationId: String, query: String): Boolean {
        val state = conversationsById[conversationId] ?: return false
        val cached = contentMatchCache[conversationId]
        if (cached != null && cached.query == query && cached.state === state) {
            return cached.matches
        }
        val matches = state.contentMatches(query) { code -> noticeText(code) }
        contentMatchCache[conversationId] = ContentMatchCacheEntry(query, state, matches)
        return matches
    }

    private fun persistConversations(onSaved: (() -> Unit)? = null): Deferred<Boolean> {
        val selected = selectedConversationId
        val conversations = conversationsById
        val titles = conversationTitles
        val timestamps = conversationUpdatedAt
        val pinned = conversationPinned
        return synchronized(persistenceLock) {
            // 未保存会阻止结果回执和 write-ahead 运行，由导入后的重载或失败后的补写收尾。
            if (persistencePaused) return CompletableDeferred(false)
            val previous = persistenceJob
            val persistence = conversationPersistence
            scope.async(Dispatchers.IO) {
                try {
                    previous?.join()
                    persistence.save(appContext,
                        AgentConversationStore.Snapshot(selected, conversations, titles, timestamps, pinned = pinned))
                    onSaved?.invoke()
                    scope.launch(Dispatchers.Main.immediate) { releaseInactiveConversations(conversations) }
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    AndroidAgentLogger.error(
                        "Agent conversation persistence failed: type=${throwable.safeLogType()}"
                    )
                    false
                }
            }.also { persistenceJob = it }
        }
    }

    private companion object {
        const val MAX_TITLE_CHARS = 24
        const val MAX_PREVIEW_CHARS = 48
        const val LEGACY_STOPPED_ERROR = "已停止"
        const val SYNTHETIC_STATUS_STOPPED = "eta_status:stopped"
        // 数据状态以较粗粒度发布，文字显现由独立的帧时钟连续推进。
        const val STREAM_UI_UPDATE_INTERVAL_MS = 80L

        fun emptyChatState(thinkingEnabled: Boolean): AgentChatHomeUiState =
            AgentChatHomeUiState(
                messages = emptyList(),
                history = emptyList(),
                journal = emptyList(),
                input = "",
                isStreaming = false,
                thinkingEnabled = thinkingEnabled,
            )

        fun newConversationId(): String = "conv-${UUID.randomUUID()}"
    }
}

internal data class MessageRevisionImpact(
    val laterTurnCount: Int,
)

private data class ContentMatchCacheEntry(
    val query: String,
    val state: AgentChatHomeUiState,
    val matches: Boolean,
)

private fun AgentChatHomeUiState.canReplaceMessageWindowOf(saved: AgentChatHomeUiState): Boolean =
    isConversationLoaded && !isStreaming && messageEdit == null &&
        messages === saved.messages && messageStartIndex == saved.messageStartIndex &&
        history === saved.history && journal === saved.journal && roleplayMessages == saved.roleplayMessages

private const val EXTERNAL_ARCHIVE_CONVERSATION_PREFIX = "archive-"

private fun String.isReadOnlyExternalArchiveConversation(): Boolean =
    startsWith(EXTERNAL_ARCHIVE_CONVERSATION_PREFIX)

private fun archiveConversationId(source: String, conversationKey: String): String {
    val prefix = if (source == AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE) {
        ASSISTANT_CONVERSATION_PREFIX
    } else {
        EXTERNAL_ARCHIVE_CONVERSATION_PREFIX
    }
    return prefix + stableArchiveId("$source:$conversationKey")
}

private const val ASSISTANT_CONVERSATION_PREFIX = "assistant-"

private fun stableArchiveId(value: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun agentBooleanForUi(key: String): Boolean {
    return Prefs.isEnabled(key)
}
