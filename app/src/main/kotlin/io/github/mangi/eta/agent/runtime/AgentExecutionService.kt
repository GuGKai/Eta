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
import io.github.mangi.eta.agent.overlay.toolDisplayNameResource
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

    /** agent 运行时上报步骤进度后，回到主线程刷新执行通知。 */
    private val progressListener: () -> Unit = {
        mainHandler.post { if (instance === this) refreshNotification() }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        leases.attachOwner(owner)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.execution_channel), NotificationManager.IMPORTANCE_LOW),
        )
        ExecutionLiveProgress.attachListener(progressListener)
        ensureForeground()
    }

    private fun ensureForeground() {
        if (foregroundActive || startRejected) return
        leases.attachOwner(owner)
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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
        ExecutionLiveProgress.detachListener(progressListener)
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
            ExecutionLiveProgress.reset()
            foregroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    /** 当前步骤的本地化名称（未本地化时退回原始工具名）。 */
    private fun currentStepLabel(): String? {
        val raw = ExecutionLiveProgress.current ?: return null
        return toolDisplayNameResource(raw)?.let { getString(it) } ?: raw
    }

    /** 胶囊右侧文字：固定 4 个中文字符宽度，超出直接截断，不做补齐、省略号或翻页。 */
    private fun liveTextLabel(): String {
        val label = currentStepLabel() ?: getString(R.string.work_analyzing)
        return if (label.length <= LIVE_TEXT_LIMIT) label else label.take(LIVE_TEXT_LIMIT)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val completed = ExecutionLiveProgress.completed
        val stepLabel = currentStepLabel()

        val iconRes = if (Build.VERSION.SDK_INT >= 36 && applicationInfo.icon != 0) {
            // 流体云胶囊直接按原样绘制这个小图标，用应用彩色图标而不是单色通知图标。
            applicationInfo.icon
        } else {
            R.drawable.ic_notification
        }

        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(Icon.createWithResource(this, iconRes))
            // 第一行：当前正在执行的环节名称。
            .setContentTitle(
                getString(R.string.execution_live_title, stepLabel ?: getString(R.string.work_analyzing)),
            )
            // 第二行：正向累计的已完成步骤数。
            .setContentText(
                resources.getQuantityString(R.plurals.work_completed_steps, completed, completed),
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        applyPromotedOngoing(builder)

        return builder.build()
    }

    /**
     * Android 16（API 36）起可把常驻执行通知升级为流体云实时活动：
     * 状态栏胶囊 + 通知栏实时活动卡片。
     * 任务总步数不可知，因此不渲染任何进度条，只保留文字进度。
     * 需要 manifest 声明 POST_PROMOTED_NOTIFICATIONS，并由用户在「流体云显示实时活动」中允许。
     */
    private fun applyPromotedOngoing(builder: Notification.Builder) {
        if (Build.VERSION.SDK_INT < 36) return
        builder.setShortCriticalText(liveTextLabel())
        builder.setRequestPromotedOngoing(true)
    }

    companion object {
        private const val CHANNEL = "eta_execution"
        private const val NOTIFICATION_ID = 1107
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_USER_EXECUTION"

        /** 胶囊右侧文字：固定 4 个中文字符宽度。 */
        private const val LIVE_TEXT_LIMIT = 4

        private val leases = ExecutionLeaseRegistry()
        private val ownerSequence = AtomicLong()
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var instance: AgentExecutionService? = null

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
