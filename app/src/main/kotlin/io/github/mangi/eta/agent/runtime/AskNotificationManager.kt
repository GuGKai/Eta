package io.github.mangi.eta.agent.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AskQuestionCard
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.ui.MainActivity
import java.util.concurrent.ConcurrentHashMap

/**
 * 「等你回答」通知。
 *
 * 提问挂起时，如果用户眼前没有那个会话页（切到了别的应用，或停在 Eta 内的其它页面），
 * 就把这张卡片推到通知栏；点一下直达那轮正在跑的会话，而不是只把 Eta 本体拉到前台。
 *
 * 撤回只看「有没有真的作答」：回答完、运行被停止、或等待超时才撤。
 * 点开通知不自动消失、回到会话页也不撤——没答之前一直留在通知栏里提醒。
 */
internal object AskNotificationManager {
    private const val CHANNEL_ASK = "eta_ask_questions"
    private const val BRAND_COLOR = 0xFF4285F4.toInt()
    private const val MAX_QUESTION_PREVIEW_CHARS = 80

    /** 会话 ID → 当前挂着的提问消息 ID，用于回答后精确撤回。 */
    private val posted = ConcurrentHashMap<String, String>()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ASK,
                context.getString(R.string.local_ask_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.local_ask_notification_channel_desc)
            },
        )
    }

    fun notifyPendingAsk(
        context: Context,
        conversationId: String?,
        card: AskQuestionCard,
        messageId: String,
    ): Boolean {
        val key = conversationId?.takeIf { it.isNotBlank() } ?: return false
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false

        val firstQuestion = card.questions.firstOrNull()?.question.orEmpty().trim()
        val preview = firstQuestion.take(MAX_QUESTION_PREVIEW_CHARS).ifBlank {
            context.getString(R.string.local_ask_notification_fallback)
        }
        val text = if (card.questions.size > 1) {
            context.getString(R.string.local_ask_notification_text_multi, preview, card.questions.size)
        } else {
            context.getString(R.string.local_ask_notification_text, preview)
        }
        val notification = Notification.Builder(context, CHANNEL_ASK)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(context.getString(R.string.local_ask_notification_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openConversationIntent(context, key))
            // 点开不自动消失：只有真的作答（或运行时收尾）才撤，没答就一直挂着提醒。
            .setAutoCancel(false)
            .build()

        return runCatching {
            manager.notify(notificationIdFor(key), notification)
            posted[key] = messageId
            true
        }.getOrElse { failure ->
            AndroidAgentLogger.warn("Ask notify failed: type=${failure.safeLogType()}")
            false
        }
    }

    /** 回答完了或提问作废：只撤回属于这条消息的通知。 */
    fun dismiss(context: Context, conversationId: String?, messageId: String) {
        val key = conversationId?.takeIf { it.isNotBlank() } ?: return
        if (posted[key] != messageId) return
        posted.remove(key)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(notificationIdFor(key)) }
    }

    /**
     * 新进程启动时清掉上一进程遗留的提问通知。
     *
     * 挂起只在进程内存在，新进程里不可能还有在等答案的提问；不清就会留下一条
     * 点了也没用的常驻提醒。
     */
    fun clearStale(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        posted.clear()
        runCatching {
            manager.activeNotifications
                .filter { it.notification?.channelId == CHANNEL_ASK }
                .forEach { manager.cancel(it.id) }
        }
    }

    private fun notificationIdFor(conversationId: String): Int =
        0x41530000 or (conversationId.hashCode() and 0xFFFF)

    private fun openConversationIntent(context: Context, conversationId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CONVERSATION_ID)
            .putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
