package io.github.mangi.eta.agent.accessibility

import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.ConcurrentHashMap

/*
 * fork 本地：无障碍动作之后"界面是否真的变了"的判定（挂载点化抽离，原文件只留调用）。
 *
 * AgentAccessibilityService 里上游没有这块能力，本地用它替代"动作后固定 sleep 一下"：
 * 每个 display 记一枚内容事件代数，动作前记基准、动作后等"代数涨了且事件安静"再返回，
 * 让屏幕观察/点击链路能拿到真实的界面状态，而不是靠猜等待时长。
 */

/** 动作后判定界面已稳定要求内容事件停满的时间窗口。 */
internal const val SETTLE_QUIET_MS = 400L

/** 界面完全不产生内容事件时的提前放弃时间，避免把不报事件的页面拖慢。 */
internal const val SETTLE_IDLE_GIVE_UP_MS = 500L

/** 动作后等待的硬上限，避免持续动画的页面把动作卡住。 */
internal const val SETTLE_MAX_MS = 1_200L

/** 等待期间的轮询间隔。 */
internal const val SETTLE_POLL_MS = 120L

/** 动作起点超过这个时长即视为过期，改用固定等待，避免拿旧基准误判。 */
internal const val SETTLE_BASELINE_TTL_MS = 3_000L

/**
 * 把"内容事件 → 是否变化"的记账从服务里挪出来单独持有。
 *
 * [windows] 用于事件没带 displayId 时按 windowId 反查（传入服务的 `windows`）。
 */
internal class AccessibilitySettleTracker(
    private val windows: () -> List<AccessibilityWindowInfo>,
) {
    /** 各 display 的内容事件代数：动作前后比较它就能判断界面是否真的变了。 */
    private val displayContentGenerations = ConcurrentHashMap<Int, Long>()

    /** 各 display 最近一次内容事件的时刻，用来判断界面是否已安静。 */
    private val displayLastContentEventUptime = ConcurrentHashMap<Int, Long>()

    /** 最近一次交互动作开始时各 display 的代数，作为"是否变了"的基准。 */
    private val actionBaselineGenerations = ConcurrentHashMap<Int, Long>()

    /** 记录动作起点的时刻，用于丢弃过期基准。 */
    private val actionStartUptime = ConcurrentHashMap<Int, Long>()

    /** 记录动作起点，必须在真正派发动作之前调用。 */
    fun markActionStart(displayId: Int = Display.DEFAULT_DISPLAY) {
        actionBaselineGenerations[displayId] = contentGenerationOf(displayId)
        actionStartUptime[displayId] = SystemClock.uptimeMillis()
    }

    fun contentGenerationOf(displayId: Int): Long = displayContentGenerations[displayId] ?: 0L

    /**
     * 等界面"确实变了且事件安静"，最多等 [maxMs]。
     *
     * 返回 changed：已变化且安静；changed_busy：变了但事件还在刷，到点返回；
     * unchanged：[idleGiveUpMs] 内没有任何内容事件，说明该界面不产生内容事件；
     * timeout：始终没等到变化；no_baseline：动作没经过无障碍，调用方应回退固定等待。
     */
    fun awaitActionSettled(
        displayId: Int,
        quietMs: Long = SETTLE_QUIET_MS,
        idleGiveUpMs: Long = SETTLE_IDLE_GIVE_UP_MS,
        maxMs: Long = SETTLE_MAX_MS,
    ): String {
        val baseline = actionBaselineGenerations[displayId] ?: return "no_baseline"
        val startedAt = actionStartUptime[displayId] ?: return "no_baseline"
        if (SystemClock.uptimeMillis() - startedAt > SETTLE_BASELINE_TTL_MS) return "no_baseline"
        while (true) {
            SystemClock.sleep(SETTLE_POLL_MS)
            val now = SystemClock.uptimeMillis()
            val changed = contentGenerationOf(displayId) > baseline
            val quiet = now - (displayLastContentEventUptime[displayId] ?: 0L) >= quietMs
            if (changed && quiet) return "changed"
            if (!changed && now - startedAt >= idleGiveUpMs) return "unchanged"
            if (now - startedAt >= maxMs) return if (changed) "changed_busy" else "timeout"
        }
    }

    fun recordContentEvent(event: AccessibilityEvent) {
        val displayId = resolveEventDisplayId(event)
        displayContentGenerations.merge(displayId, 1L) { current, delta -> current + delta }
        displayLastContentEventUptime[displayId] = SystemClock.uptimeMillis()
    }

    private fun resolveEventDisplayId(event: AccessibilityEvent): Int {
        val direct = event.displayId
        if (direct >= 0) return direct
        return windows().firstOrNull { window -> window.id == event.windowId }?.displayId
            ?: Display.DEFAULT_DISPLAY
    }
}
