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
 * 播放本身仍由聊天页的控制器持有；通知上的“停止”经 [onStopRequested] 交回控制器。
 */
internal class ReadAloudPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequested?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (failure: RuntimeException) {
            // 前台资格被拒时朗读仍在前台继续，只是退到后台可能被系统暂停。
            AndroidAgentLogger.warn("Read-aloud foreground failed: type=${failure.safeLogType()}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.read_aloud_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, ReadAloudPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 通知与实时活动卡片都按原样绘制这个小图标：用应用彩色图标，而不是单色通知图标。
        val iconRes = if (Build.VERSION.SDK_INT >= 36 && applicationInfo.icon != 0) {
            applicationInfo.icon
        } else {
            R.drawable.ic_notification
        }
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(Icon.createWithResource(this, iconRes))
            .setContentTitle(getString(R.string.read_aloud_notification_title))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(Notification.Action.Builder(null, getString(R.string.speech_read_stop), stop).build())
        // 抬成 ColorOS 的实时活动（流体云）：系统只认「ongoing + 主动申请 promoted」的通知，
        // 与执行通知同一条判据——上游用的 BAKLAVA_1(36.1) 在本机（sdk_full=36.0）恒假，必须收到 36.0。
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA) {
            builder
                .setShortCriticalText(getString(R.string.read_aloud_live_short_text))
                .setRequestPromotedOngoing(true)
        }
        return builder.build()
    }

    companion object {
        private const val ACTION_STOP = "io.github.mangi.eta.action.STOP_READ_ALOUD"
        private const val CHANNEL = "eta_read_aloud"
        private const val NOTIFICATION_ID = 4204

        /** 当前朗读的停止入口；同一时间只有一个朗读，后开始的会替换前一个。 */
        @Volatile private var onStopRequested: (() -> Unit)? = null

        /** 在用户点击朗读后的出声时刻调用，此时 App 仍在前台，可以启动前台服务。 */
        fun start(context: Context, onStop: () -> Unit) {
            onStopRequested = onStop
            try {
                context.startForegroundService(Intent(context, ReadAloudPlaybackService::class.java))
            } catch (failure: RuntimeException) {
                AndroidAgentLogger.warn("Read-aloud service start rejected: type=${failure.safeLogType()}")
            }
        }

        fun stop(context: Context) {
            onStopRequested = null
            context.stopService(Intent(context, ReadAloudPlaybackService::class.java))
        }
    }
}
