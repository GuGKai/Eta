package io.github.mangi.eta.agent.voice

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
import android.os.IBinder
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.ui.MainActivity

/**
 * 聊天朗读的前台播放服务：只在实际出声期间存在，让 App 退到后台或锁屏后朗读不被系统冻结。
 * 播放本身仍由聊天页的控制器持有；通知上的「暂停」「停止」经回调交回控制器。
 * 暂停时服务保持前台、通知不撤，只把按钮换成「继续」，再点回到播放并从断点接着读。
 */
internal class ReadAloudPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                onStopRequested?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                // 暂停/继续由控制器切换，这里只按切换后的状态换按钮文案。
                publish(this, onTogglePauseRequested?.invoke() ?: !pausedState)
                return START_NOT_STICKY
            }
        }
        try {
            startForeground(NOTIFICATION_ID, notification(this, pausedState), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            running = true
        } catch (failure: RuntimeException) {
            // 前台资格被拒时朗读仍在前台继续，只是退到后台可能被系统暂停。
            AndroidAgentLogger.warn("Read-aloud foreground failed: type=${failure.safeLogType()}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_READ_ALOUD"
        private const val ACTION_TOGGLE_PAUSE = "io.github.mangi.eta.action.TOGGLE_READ_ALOUD_PAUSE"
        private const val CHANNEL = "eta_read_aloud"
        private const val NOTIFICATION_ID = 4204

        /** 当前朗读的停止入口；同一时间只有一个朗读，后开始的会替换前一个。 */
        @Volatile private var onStopRequested: (() -> Unit)? = null

        /** 暂停/继续切换入口，返回切换后的暂停状态。 */
        @Volatile private var onTogglePauseRequested: (() -> Boolean)? = null

        @Volatile private var pausedState = false
        @Volatile private var running = false

        /** 在用户点击朗读后的出声时刻调用，此时 App 仍在前台，可以启动前台服务。 */
        fun start(context: Context, onStop: () -> Unit, onTogglePause: () -> Boolean) {
            onStopRequested = onStop
            onTogglePauseRequested = onTogglePause
            pausedState = false
            try {
                context.startForegroundService(Intent(context, ReadAloudPlaybackService::class.java))
            } catch (failure: RuntimeException) {
                AndroidAgentLogger.warn("Read-aloud service start rejected: type=${failure.safeLogType()}")
            }
        }

        /** 控制器侧暂停或继续后同步按钮文案；服务不在前台时只记状态。 */
        fun setPaused(context: Context, paused: Boolean) = publish(context, paused)

        fun stop(context: Context) {
            onStopRequested = null
            onTogglePauseRequested = null
            pausedState = false
            context.stopService(Intent(context, ReadAloudPlaybackService::class.java))
        }

        private fun publish(context: Context, paused: Boolean) {
            pausedState = paused
            if (!running) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching { manager.notify(NOTIFICATION_ID, notification(context, paused)) }
        }

        private fun notification(context: Context, paused: Boolean): Notification {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.read_aloud_channel), NotificationManager.IMPORTANCE_LOW),
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val toggle = PendingIntent.getService(
                context, 1,
                Intent(context, ReadAloudPlaybackService::class.java).setAction(ACTION_TOGGLE_PAUSE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val stop = PendingIntent.getService(
                context, 0,
                Intent(context, ReadAloudPlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // 通知与实时活动卡片都按原样绘制这个小图标：用应用彩色图标，而不是单色通知图标。
            val iconRes = if (Build.VERSION.SDK_INT >= 36 && context.applicationInfo.icon != 0) {
                context.applicationInfo.icon
            } else {
                R.drawable.ic_notification
            }
            val builder = Notification.Builder(context, CHANNEL)
                .setSmallIcon(Icon.createWithResource(context, iconRes))
                .setContentTitle(
                    context.getString(
                        if (paused) R.string.read_aloud_paused_title else R.string.read_aloud_notification_title,
                    ),
                )
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(
                    Notification.Action.Builder(
                        null,
                        context.getString(if (paused) R.string.speech_read_resume else R.string.speech_read_pause),
                        toggle,
                    ).build(),
                )
                .addAction(Notification.Action.Builder(null, context.getString(R.string.speech_read_stop), stop).build())
            // 抬成 ColorOS 的实时活动（流体云）：系统只认「ongoing + 主动申请 promoted」的通知，
            // 与执行通知同一条判据——上游用的 BAKLAVA_1(36.1) 在本机（sdk_full=36.0）恒假，必须收到 36.0。
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA) {
                builder
                    .setShortCriticalText(
                        context.getString(
                            if (paused) R.string.read_aloud_paused_short_text else R.string.read_aloud_live_short_text,
                        ),
                    )
                    .setRequestPromotedOngoing(true)
            }
            return builder.build()
        }
    }
}
