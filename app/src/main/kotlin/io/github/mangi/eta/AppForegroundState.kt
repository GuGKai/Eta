package io.github.mangi.eta

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Display
import java.util.concurrent.ConcurrentHashMap

/**
 * 本应用 UI 是否正在前台可见。
 *
 * AgentRuntimeService 用它判断用户此刻是否正看着 Eta 界面：人就在界面里时回复已经直接
 * 可见，不再额外发通知；离开了 Eta 才发通知提醒。
 *
 * 只统计默认 display 上的 Eta 页面：虚拟屏是 Root 独立 display，那里的查看页与导航页同样会触发
 * Activity 回调，但用户在主屏看不到其中的内容，不能算作“用户正看着 Eta”，否则「回复完成」通知
 * 会被静默丢掉。
 */
internal object AppForegroundState {

    /** 只在默认 display 上 started 的 Eta Activity。 */
    private val startedActivities = ConcurrentHashMap.newKeySet<Activity>()

    /**
     * 语音助理浮窗（全屏 `TYPE_APPLICATION_OVERLAY`）是否正在展示。
     *
     * 浮窗不是 Activity，[isUiVisible] 观察不到它；语音唤醒的回答就落在浮窗里，
     * 用户已经能直接读到，同样不该再弹「回复完成」通知。
     */
    @Volatile
    private var assistantOverlayVisible = false

    val isUiVisible: Boolean
        get() = startedActivities.isNotEmpty()

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
                    if (activity.display?.displayId == Display.DEFAULT_DISPLAY) startedActivities += activity
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities -= activity
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
