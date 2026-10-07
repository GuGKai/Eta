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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            foregroundActive = true
        } catch (failure: RuntimeException) {
            startRejected = true
            AndroidAgentLogger.warn("Execution service foreground failed: type=${failure.safeLogType()}")
            stopTasks(startFailed = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTasks()
        } else {
            ensureForeground()
            refreshNotification()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (instance === this) instance = null
        // 销毁时同样收回本服务拥有的任务。回收在独立有界工作线程上完成，不阻塞 Main。
        stopQueue.close(leases.drainOwner(owner))
        super.onDestroy()
    }

    private fun stopTasks(startFailed: Boolean = false) {
        val callbacks = leases.drain(startFailed)
        stopQueue.submit(callbacks) {
            mainHandler.post { if (instance === this) refreshNotification() }
        }
    }

    private fun refreshNotification() {
        if (leases.closeOwnerIfIdle(owner)) {
            foregroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun notification(): Notification {
        val status = runStatus
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AgentExecutionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val taskCount = leases.count()
        val stepLabel = currentStepLabel(status)
        // 通知与流体云胶囊按原样绘制这个小图标：用应用彩色图标，而不是单色通知图标。
        val iconRes = if (Build.VERSION.SDK_INT >= 36 && applicationInfo.icon != 0) {
            applicationInfo.icon
        } else {
            R.drawable.ic_notification
        }
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(Icon.createWithResource(this, iconRes))
            // 第一行：当前正在做的事（环节名）。
            .setContentTitle(status?.let { stepLabel } ?: getString(R.string.execution_title))
            // 第二行：正向累计的已完成步骤数。
            .setContentText(
                if (status != null) {
                    resources.getQuantityString(R.plurals.work_completed_steps, runSteps, runSteps)
                } else {
                    resources.getQuantityString(R.plurals.execution_summary, taskCount, taskCount)
                },
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.execution_stop), stop).build())
        // 只有 Agent run 在跑时才申请提升为 Live Update；终端会话、Linux 启动等长驻任务不属于"正在进行、需要持续关注"的活动。
        // 上游用 SDK_INT_FULL >= BAKLAVA_1(36.1) 判断；本机 ColorOS 16.1 上报 ro.build.version.sdk_full=36.0
        // （SDK_INT_FULL = 3600000 < 3600001），条件恒假 → 永不申请，流体云/实时活动不出现。
        // 收紧到 BAKLAVA(36.0)，已在本机实测系统接受 promoted 请求（dumpsys 出现 PROMOTED_ONGOING）。
        if (status != null && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA) {
            builder
                .setShortCriticalText(liveTextLabel(stepLabel))
                .setRequestPromotedOngoing(true)
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
        private const val NOTIFICATION_ID = 1107
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_USER_EXECUTION"

        /** 状态栏胶囊右侧文字：固定 4 个中文字符宽度。 */
        private const val LIVE_TEXT_LIMIT = 4
        private val leases = ExecutionLeaseRegistry()
        private val ownerSequence = AtomicLong()
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var instance: AgentExecutionService? = null
        private var runStatus: AgentOverlayStatus? = null
        private var runSteps = 0
        private var publishedKey: String? = null

        /**
         * Agent run 的当前状态与已完成步骤数，供通知与状态栏胶囊展示；status 为 null 表示没有 Agent run 在跑。
         * 只在标题或正文变化时刷新通知，避免流式事件把通知更新频率推到系统限流。
         */
        fun updateRunStatus(context: Context, status: AgentOverlayStatus?, completedSteps: Int = 0) {
            mainHandler.post {
                runStatus = status
                runSteps = completedSteps
                val key = status?.let { "$it|$completedSteps" } ?: ""
                if (key == publishedKey) return@post
                publishedKey = key
                instance?.takeIf { it.foregroundActive }?.refreshNotification()
            }
        }

        /** 必须从有效的用户入口取得引用，再创建会话或子进程；失败时调用方不启动任务。 */
        fun acquire(
            context: Context,
            id: String,
            allowBoundFallback: Boolean = false,
            onStop: () -> Unit,
        ): Boolean {
            if (instance?.startRejected == true) return false
            if (!leases.acquire(id, allowBoundFallback, onStop)) return true
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
            mainHandler.post { instance?.refreshNotification() }
        }
    }
}
