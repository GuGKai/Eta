package io.github.mangi.eta.agent.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.AgentOverlayStatus
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.agent.overlay.toolDisplayName
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.ui.MainActivity
import java.util.concurrent.atomic.AtomicLong

/** 只在用户任务存活期间持有前台执行生命周期；进程被系统停止后不重放任务。 */
internal class AgentExecutionService : Service() {
    private val stopQueue = ExecutionStopQueue { failure ->
        AndroidAgentLogger.warn("Execution task stop failed: type=${failure.safeLogType()}")
    }
    private val owner = ownerSequence.incrementAndGet()
    private var foregroundActive = false
    @Volatile private var startRejected = false
    private var progress: String = ""
    private var progressOwner: String = ""

    /** 每张卡片的已发布内容，key 为通知 ID；只在主线程读写。 */
    private var publishedCards: Map<Int, RenderedText> = emptyMap()
    private var pendingPublish: Runnable? = null
    private var lastPublishAt = 0L

    /** 通知时间戳锚点：本次前台期开始的那一刻，避免每次更新都带新时间戳。 */
    private var anchorAt = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        leases.attachOwner(owner)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.execution_channel), NotificationManager.IMPORTANCE_LOW),
        )
        ensureForeground()
    }

    private fun ensureForeground() {
        if (foregroundActive || startRejected) return
        leases.attachOwner(owner)
        try {
            // 前台服务通知即第一张卡片（有 Agent run 时）或汇总文案（只有终端等任务时）。
            val cards = expectedCards(runNotices)
            val head = cards.entries.first()
            val headNotice = runNotices.firstOrNull()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(head.key, notification(head.value, headNotice, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(head.key, notification(head.value, headNotice, 0))
            }
            foregroundActive = true
            lastPublishAt = SystemClock.uptimeMillis()
            publishedCards = cards
        } catch (failure: RuntimeException) {
            startRejected = true
            AndroidAgentLogger.warn("Execution service foreground failed: type=${failure.safeLogType()}")
            stopTasks(startFailed = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知动作按卡片绑定到具体 Agent run：同一个按钮在不同卡片上只作用于该任务。
        val targetRunId = intent?.getStringExtra(EXTRA_RUN_ID)
        when (intent?.action) {
            ACTION_STOP -> stopTasks(ids = targetRunId?.let { setOf("run:$it") })
            // 通知上的「暂停/恢复」：直接落到 runtime 的 run controller。
            // runtime 不在或任务已结束时什么都不做，通知保持原样。
            ACTION_PAUSE -> AgentRuntimeService.pauseFromNotification(targetRunId)
            ACTION_RESUME -> AgentRuntimeService.resumeFromNotification()
            else -> {
                ensureForeground()
                refreshNotification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (instance === this) instance = null
        // 服务被停不等于用户取消任务：用户任务租约只解除归属，runtime 侧继续跑完，
        // 下次进入界面由恢复流程接管；保活/终端租约照旧回收。回收在独立工作线程上完成，不阻塞 Main。
        stopQueue.close(leases.detachOwner(owner))
        super.onDestroy()
    }

    private fun stopTasks(startFailed: Boolean = false, ids: Set<String>? = null) {
        val callbacks = if (ids == null) leases.drain(startFailed) else leases.drainIds(ids)
        stopQueue.submit(callbacks) {
            mainHandler.post { if (instance === this) refreshNotification() }
        }
    }

    private fun refreshNotification() {
        if (leases.closeOwnerIfIdle(owner)) {
            cancelPendingPublish()
            anchorAt = 0L
            foregroundActive = false
            // 先摘掉前台通知，再清掉其余卡片，避免对前台正在使用的那条调用 cancel。
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(NotificationManager::class.java)
            publishedCards.keys.forEach { id -> manager.cancel(id) }
            publishedCards = emptyMap()
            stopSelf()
        } else {
            requestPublish()
        }
    }

    /** 通知的两行文本。用于去重：内容没变就不重发。 */
    private data class RenderedText(val title: String, val text: String)

    /** 没有任何 Agent run 时的汇总卡片（前端保活、终端任务等）。 */
    private fun summaryContent(): RenderedText {
        val count = if (taskCount > 0) taskCount else leases.count()
        return RenderedText(
            title = getString(R.string.execution_title),
            text = resources.getQuantityString(R.plurals.execution_summary, count, count),
        )
    }

    /** 单个 Agent run 的卡片内容：第一行环节名（暂停时改成「暂停中」），第二行已完成步骤数。 */
    private fun cardContent(notice: ExecutionRunNotice, multi: Boolean): RenderedText {
        val title = if (notice.paused) getString(R.string.execution_paused) else currentStepLabel(notice.status)
        val steps = resources.getQuantityString(R.plurals.work_completed_steps, notice.completedSteps, notice.completedSteps)
        // 并行跑多个任务时，卡片之间只剩环节名往往无法区分，前面补上任务摘要。
        val text = if (multi && notice.taskLabel.isNotBlank()) "${notice.taskLabel} · $steps" else steps
        return RenderedText(title = title, text = text)
    }

    /** 期望的通知卡片：ID 从 [NOTIFICATION_ID] 起按活跃顺序连续分配，第一张兼任前台服务通知。 */
    private fun expectedCards(notices: List<ExecutionRunNotice>): LinkedHashMap<Int, RenderedText> {
        val cards = LinkedHashMap<Int, RenderedText>()
        if (notices.isEmpty()) {
            cards[NOTIFICATION_ID] = summaryContent()
            return cards
        }
        val multi = notices.size > 1
        notices.forEachIndexed { index, notice ->
            cards[NOTIFICATION_ID + index] = cardContent(notice, multi)
        }
        return cards
    }

    /** 把 elapsedRealtime 意义上的开始时刻换算成墙钟 HH:mm，用于「从几点开始跑」。 */
    private fun formatClock(elapsedStartedAt: Long): String {
        val wallClock = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsedStartedAt)
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(wallClock))
    }

    /**
     * 通知去重 + 节流后再发。
     *
     * 系统把每一次通知更新都当成一次全新的流体云"发布"交给插件重建胶囊/卡片
     * （SystemUI 日志：`notifyLiveAlerts: PostAction` + `NotificationLiveAlertProvider-->post sbn`），
     * 而一次工具调用会带来工具开始、工具结束、前台租约收放等多处刷新，短时间连发就会看到文字闪跳。
     * 这里：内容没变不发；距上次发布不足 [MIN_PUBLISH_INTERVAL_MS] 的变化合并成一次尾发。
     */
    private fun requestPublish() {
        val cards = expectedCards(runNotices)
        if (cards == publishedCards) return
        val elapsed = SystemClock.uptimeMillis() - lastPublishAt
        if (elapsed >= MIN_PUBLISH_INTERVAL_MS) {
            publish(cards)
            return
        }
        if (pendingPublish != null) return
        val trailing = Runnable {
            pendingPublish = null
            val latest = expectedCards(runNotices)
            if (latest != publishedCards) publish(latest)
        }
        pendingPublish = trailing
        mainHandler.postDelayed(trailing, MIN_PUBLISH_INTERVAL_MS - elapsed)
    }

    /** 逐卡片 diff 发布：内容没变的卡片不重发，已结束任务的卡片单独撤销。 */
    private fun publish(cards: Map<Int, RenderedText>) {
        cancelPendingPublish()
        lastPublishAt = SystemClock.uptimeMillis()
        val manager = getSystemService(NotificationManager::class.java)
        for ((id, content) in cards) {
            if (publishedCards[id] == content) continue
            val index = id - NOTIFICATION_ID
            manager.notify(id, notification(content, runNotices.getOrNull(index), index))
        }
        for (stale in publishedCards.keys - cards.keys) manager.cancel(stale)
        publishedCards = cards
    }

    private fun cancelPendingPublish() {
        pendingPublish?.let { mainHandler.removeCallbacks(it) }
        pendingPublish = null
    }

    /**
     * 单张卡片的通知。[notice] 为 null 表示汇总卡片（只有保活会话或终端任务，没有 Agent run）。
     * [index] 是卡片序号：不同卡片的 PendingIntent 用不同 request code，避免互相覆盖。
     */
    private fun notification(content: RenderedText, notice: ExecutionRunNotice?, index: Int): Notification {
        // 只有 Agent run 正在跑时才指向虚拟屏查看页；任务跑完或只剩保活会话时指向对话界面，
        // 否则用户点通知会先落在查看页、还要按返回才回到对话。
        val open = PendingIntent.getActivity(
            this, OPEN_REQUEST_BASE + index, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopState = Intent(this, AgentExecutionService::class.java).setAction(ACTION_STOP)
        notice?.let { stopState.putExtra(EXTRA_RUN_ID, it.runId) }
        val stop = PendingIntent.getService(
            this, ACTION_REQUEST_BASE + index * 4 + 1, stopState,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 暂停/恢复用不同 request code 各自持有，避免同一码复用同一个 PendingIntent。
        val paused = notice?.paused == true
        val toggleState = Intent(this, AgentExecutionService::class.java)
            .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE)
        notice?.let { toggleState.putExtra(EXTRA_RUN_ID, it.runId) }
        val togglePause = PendingIntent.getService(
            this, ACTION_REQUEST_BASE + index * 4 + if (paused) 3 else 2, toggleState,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 通知与流体云胶囊按原样绘制这个小图标：用应用彩色图标，而不是单色通知图标。
        val iconRes = if (Build.VERSION.SDK_INT >= 36 && applicationInfo.icon != 0) {
            applicationInfo.icon
        } else {
            R.drawable.ic_notification
        }
        // 时间戳固定在本次任务开始那一刻并不显示：每次更新都带新 when 会让系统按"新通知"处理，加剧重建。
        if (anchorAt == 0L) anchorAt = System.currentTimeMillis()
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(Icon.createWithResource(this, iconRes))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setWhen(anchorAt)
            .setShowWhen(false)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // 只有 Agent run 在跑时才申请提升为 Live Update；终端会话、Linux 启动等长驻任务不属于"正在进行、需要持续关注"的活动。
        if (notice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // 流体云 / 实时活动卡片只保留标题、副标题与两个按钮。
            // ProgressStyle 仍是申请 promoted ongoing 的必备样式（去掉就连提升资格都没有、只剩普通前台通知），
            // 所以保留样式、但不再设置 indeterminate 或分段进度：卡片不绘制进度条。
            builder
                .setStyle(Notification.ProgressStyle())
                .setShortCriticalText(liveTextLabel(content.title))
            // 请求方法从 API 36.1 才公开；标准 extra 也可供支持该协议的 API 36 系统读取。
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        if (notice != null) {
            // Agent 回答中：只保留 暂停(恢复) 与 停止任务 两个入口，各自绑定本卡片的 run。
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    getString(if (paused) R.string.execution_resume else R.string.execution_pause),
                    togglePause,
                ).build(),
            )
            builder.addAction(Notification.Action.Builder(null, getString(R.string.execution_stop), stop).build())
        } else if (leases.taskCount() > 0) {
            // 后台命令没有「插话」「暂停」的概念，只保留一个停止入口。
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.execution_stop), stop).build(),
            )
        }
        return builder.build()
    }

    /** 第一行「当前正在做的事」：执行工具时用本地化工具名，其余阶段用状态文案。 */
    private fun currentStepLabel(status: AgentOverlayStatus?): String = when (status) {
        null -> getString(R.string.overlay_reasoning)
        is AgentOverlayStatus.RunningTool -> toolDisplayName(resources, status.name)
        is AgentOverlayStatus.HostedToolRunning -> toolDisplayName(resources, status.name)
        else -> status.localizedText(resources)
    }

    /** 状态栏胶囊右侧文字：固定 4 个中文字符宽度，超出直接截断，不做补齐、省略号或翻页。 */
    private fun liveTextLabel(label: String): String =
        if (label.length <= LIVE_TEXT_LIMIT) label else label.take(LIVE_TEXT_LIMIT)

    companion object {
        private const val CHANNEL = "eta_execution"
        /** 第一张卡片（同时承载前台服务）；后续卡片按序号顺延：1108、1109…… */
        private const val NOTIFICATION_ID = 1107
        private const val OPEN_REQUEST_BASE = 1200
        private const val ACTION_REQUEST_BASE = 1300
        private const val EXTRA_RUN_ID = "io.github.mangi.eta.extra.EXECUTION_RUN_ID"
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_USER_EXECUTION"
        private const val ACTION_PAUSE = "io.github.mangi.eta.action.PAUSE_USER_EXECUTION"
        private const val ACTION_RESUME = "io.github.mangi.eta.action.RESUME_USER_EXECUTION"

        /** 状态栏胶囊右侧文字：固定 4 个中文字符宽度。 */
        private const val LIVE_TEXT_LIMIT = 4

        /** 通知合并窗口：距上次发布不足该时长的新变化合并成一次尾发，避免流体云胶囊反复重建。 */
        private const val MIN_PUBLISH_INTERVAL_MS = 400L
        private val leases = ExecutionLeaseRegistry()
        private val ownerSequence = AtomicLong()
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var instance: AgentExecutionService? = null
        private var runNotices: List<ExecutionRunNotice> = emptyList()
        private var taskCount = 0
        private var publishedStatusText: String? = null

        /**
         * Agent run 的当前状态与已完成步骤数，供通知与状态栏胶囊展示；status 为 null 表示没有 Agent run 在跑。
         * 只在标题或正文变化时刷新通知，避免流式事件把通知更新频率推到系统限流。
         */
        fun updateRunStates(context: Context, notices: List<ExecutionRunNotice>, taskCount: Int) {
            mainHandler.post {
                this.taskCount = taskCount
                // 去重键覆盖卡片的全部可见内容：步数、暂停态、任务名在变而文字没变时也要刷新。
                val key = notices.joinToString("|") {
                    "${it.runId},${it.status},${it.completedSteps},${it.paused},${it.taskLabel}"
                } + "#$taskCount"
                if (key == publishedStatusText) return@post
                publishedStatusText = key
                runNotices = notices
                instance?.takeIf { it.foregroundActive }?.refreshNotification()
            }
        }

        /** 必须从有效的用户入口取得引用，再创建会话或子进程；失败时调用方不启动任务。 */
        fun acquire(
            context: Context,
            id: String,
            allowBoundFallback: Boolean = false,
            kind: ExecutionLeaseRegistry.Kind = ExecutionLeaseRegistry.Kind.TASK,
            label: String? = null,
            onStop: () -> Unit,
        ): Boolean {
            if (instance?.startRejected == true) return false
            if (!leases.acquire(id, kind, allowBoundFallback, label, onStop)) return true
            return try {
                context.applicationContext.startForegroundService(Intent(context, AgentExecutionService::class.java))
                true
            } catch (failure: RuntimeException) {
                leases.release(id)
                AndroidAgentLogger.warn("Execution service start rejected: type=${failure.safeLogType()}")
                false
            }
        }

        fun release(id: String) {
            leases.release(id)
            mainHandler.post { instance?.let { service -> if (service.progressOwner == id) { service.progress = ""; service.progressOwner = "" }; service.refreshNotification() } }
        }

        /** 后台命令换了描述时更新租约上的名字，下一帧通知就会显示新的任务名。 */
        fun updateLeaseLabel(id: String, label: String) {
            leases.updateLabel(id, label)
            mainHandler.post { instance?.let { it.refreshNotification() } }
        }

        fun updateProgress(id: String, event: AgentEvent) {
            val tool = when (event) {
                is AgentEvent.ToolStarted -> event.name
                is AgentEvent.ToolFinished -> ""
                else -> return
            }
            mainHandler.post { instance?.let { service ->
                if (tool.isNotBlank() || service.progressOwner == id) {
                    service.progressOwner = id
                    service.progress = tool.take(80)
                    service.refreshNotification()
                }
            } }
        }
    }
}

/** 一个 Agent run 在通知栏里的独立卡片：并行任务各自一张，互不覆盖。 */
internal data class ExecutionRunNotice(
    val runId: String,
    val taskLabel: String = "",
    val status: AgentOverlayStatus? = null,
    val completedSteps: Int = 0,
    val paused: Boolean = false,
)
