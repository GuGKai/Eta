package io.github.mangi.eta

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 本应用 UI 是否正在前台可见。
 *
 * AgentRuntimeService 用它判断用户此刻是否正看着 Eta 界面：人就在界面里时回复已经直接
 * 可见，不再额外发通知；离开了 Eta 才发通知提醒。
 */
internal object AppForegroundState {

    @Volatile
    private var startedActivities = 0

    /**
     * 语音助理浮窗（全屏 `TYPE_APPLICATION_OVERLAY`）是否正在展示。
     *
     * 浮窗不是 Activity，[isUiVisible] 观察不到它；语音唤醒的回答就落在浮窗里，
     * 用户已经能直接读到，同样不该再弹「回复完成」通知。
     */
    @Volatile
    private var assistantOverlayVisible = false

    val isUiVisible: Boolean
        get() = startedActivities > 0

    /** 用户此刻能从屏幕上直接读到内容：本体界面可见，或语音助理浮窗仍在展示。 */
    val hasVisibleSurface: Boolean
        get() = isUiVisible || assistantOverlayVisible

    /** 只由 EtaAssistantOverlayService 在窗口挂上／摘掉时调用。 */
    fun setAssistantOverlayVisible(visible: Boolean) {
        assistantOverlayVisible = visible
    }

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities += 1
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
