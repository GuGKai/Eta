package io.github.mangi.eta.data.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.widget.Toast
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 每次把 Eta 切到前台时，在后台静默检查一次更新。
 *
 * 触发点是 Application 级的「界面从后台回到前台」，不是进程启动，所以 App 常驻后台时
 * 再次打开同样会检查；没有新版本、网络失败、响应异常时全部保持静默，只有确实存在更新
 * 才用 Toast 提示「已有新版本」。
 *
 * 检查间隔由 [CHECK_INTERVAL_MS] 控制并持久化到 SharedPreferences：一小时内反复打开只查一次，
 * 进程被系统回收后重新打开也不会立刻重复请求。
 */
internal object LaunchUpdateNotice {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val checking = AtomicBoolean(false)

    /** 检查间隔：一小时内最多一次。 */
    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L

    /** 失败后的最小重试间隔，避免网络不通时每次开关都请求。 */
    private const val RETRY_INTERVAL_MS = 60 * 1000L

    private const val PREFS_NAME = "eta_update_check"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"

    @Volatile
    private var appContext: Context? = null

    /** 默认 display 上处于 started 的 Activity 数量，0→1 即「回到前台」。 */
    private var startedCount = 0

    @Volatile
    private var lastAttemptAt = 0L

    fun install(application: Application) {
        appContext = application.applicationContext
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (activity.display?.displayId != Display.DEFAULT_DISPLAY) return
                    val becameVisible = synchronized(this@LaunchUpdateNotice) {
                        startedCount += 1
                        startedCount == 1
                    }
                    if (becameVisible) checkIfDue()
                }

                override fun onActivityStopped(activity: Activity) {
                    if (activity.display?.displayId != Display.DEFAULT_DISPLAY) return
                    synchronized(this@LaunchUpdateNotice) {
                        if (startedCount > 0) startedCount -= 1
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun checkIfDue() {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        val lastSuccessAt = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SUCCESS_AT, 0L)
        if (now - lastSuccessAt < CHECK_INTERVAL_MS) return
        if (now - lastAttemptAt < RETRY_INTERVAL_MS) return
        if (!checking.compareAndSet(false, true)) return
        lastAttemptAt = now
        scope.launch {
            try {
                val release = runCatching { AppUpdateChecker.fetchLatest() }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    AndroidAgentLogger.warn(
                        "Launch update check unavailable: type=${error.javaClass.simpleName}"
                    )
                    null
                } ?: return@launch
                val current = currentVersionName(context)
                val newer = current.isNotEmpty() && AppUpdateChecker.isNewer(release.version, current)
                AndroidAgentLogger.info(
                    "Launch update check: latest=${release.version} current=$current newer=$newer"
                )
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_SUCCESS_AT, System.currentTimeMillis())
                    .apply()
                if (newer) {
                    mainHandler.post {
                        Toast.makeText(context, R.string.ui_update_available_toast, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } finally {
                checking.set(false)
            }
        }
    }

    private fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
