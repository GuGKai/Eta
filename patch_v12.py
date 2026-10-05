from pathlib import Path

# 1) 新增：应用 UI 前台可见性状态
p = Path('app/src/main/kotlin/io/github/mangi/eta/AppForegroundState.kt')
assert not p.exists(), 'exists'
p.write_text('''package io.github.mangi.eta

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

    val isUiVisible: Boolean
        get() = startedActivities > 0

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
''')
print('state ok')

# 2) EtaApp 注册回调
p = Path('app/src/main/kotlin/io/github/mangi/eta/EtaApp.kt')
s = p.read_text()
anchor = """        if (!AppProcessPolicy.shouldInitializeFullRuntime(Application.getProcessName(), packageName)) {
            return
        }
"""
assert s.count(anchor) == 1, s.count(anchor)
s = s.replace(anchor, anchor + "        AppForegroundState.install(this)\n")
p.write_text(s)
print('app ok')

# 3) 回复完成通知：UI 在前台时不发
p = Path('app/src/main/kotlin/io/github/mangi/eta/agent/runtime/AgentRuntimeService.kt')
s = p.read_text()

imp = 'import io.github.mangi.eta.EtaApp\n'
assert s.count(imp) == 1, s.count(imp)
s = s.replace(imp, imp + 'import io.github.mangi.eta.AppForegroundState\n')

trigger = """    private fun notifyReplyCompleted(content: String) {
        runCatching {
"""
assert s.count(trigger) == 1, s.count(trigger)
s = s.replace(
    trigger,
    """    private fun notifyReplyCompleted(content: String) {
        // 用户此刻就在 Eta 界面里，回复已直接可见，不再发通知打扰。
        if (AppForegroundState.isUiVisible) return
        runCatching {
""",
)
p.write_text(s)
print('service ok')
