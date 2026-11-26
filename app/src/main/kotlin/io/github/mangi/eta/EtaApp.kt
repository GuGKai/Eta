package io.github.mangi.eta

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.mangi.eta.agent.browser.BrowserSessionRegistry
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.AppStrings
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.update.LaunchUpdateNotice
import io.github.mangi.eta.ui.app.PredictiveBackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 模块 UI 进程的 Application。
 *
 * 在进程启动时注册 [XposedServiceHelper] 监听器，框架会通过 XposedProvider 推送 binder，
 * 随后 UI 即可拿到 [XposedService] 写入 RemotePreferences，跨进程同步到各 hook 进程。
 */
class EtaApp : Application(), XposedServiceHelper.OnServiceListener {

    /** 仅主进程创建；`:voice` / `:recognition` 等辅助进程保持为 null。 */
    internal var container: AppContainer? = null
        private set

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)
        Prefs.initLocal(this)
        if (!AppProcessPolicy.shouldInitializeFullRuntime(Application.getProcessName(), packageName)) {
            applyPredictiveBackForAuxiliaryProcess()
            return
        }
        // 主进程存储、仓库与设备运行时统一由 AppContainer 按依赖顺序装配。
        val appContainer = AppContainer(this).also(AppContainer::initialize)
        container = appContainer
        AppForegroundState.install(this)
        // 挂起只在进程内存在：新进程起来时把上一进程遗留的提问通知清掉。
        io.github.mangi.eta.agent.runtime.AskNotificationManager.clearStale(this)
        io.github.mangi.eta.ui.components.ChatScrollDiag.install(java.io.File(filesDir, "scroll-diag"))
        LaunchUpdateNotice.install(this)
        // 本地 automation（事件触发）恢复
        appContainer.applicationScope.launch(Dispatchers.IO) {
            runCatching {
                io.github.mangi.eta.data.db.EtaDatabase.get(this@EtaApp).agentTaskDao().recoverInterrupted(System.currentTimeMillis())
                io.github.mangi.eta.agent.automation.AgentTaskScheduler.retireTimeTasks(this@EtaApp)
                io.github.mangi.eta.agent.automation.AgentTaskScheduler.refresh(this@EtaApp)
            }.onFailure { AndroidAgentLogger.warn("Automation recovery unavailable") }
        }
        XposedServiceHelper.registerListener(this)
    }

    /**
     * :voice / :recognition 等子进程里也有 Activity（例如语音助手界面），
     * 它们的窗口同样要在创建前读到 ApplicationInfo 上的预测性返回标志，
     * 所以这些进程不初始化完整 runtime，也要单独应用一次设置。
     */
    private fun applyPredictiveBackForAuxiliaryProcess() {
        SettingsDataStore.init(this, prewarm = false)
        val enabled = runBlocking(Dispatchers.IO) {
            runCatching { AppearanceSettingsRepository.settings().predictiveBackEnabled }
                .getOrDefault(true)
        }
        PredictiveBackController.apply(applicationInfo, enabled)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            BrowserSessionRegistry.releaseIdlePages()
        }
    }

    override fun onServiceBind(service: XposedService) {
        serviceInstance = service
        Prefs.reconcileAgentPreferences(service)
        dispatch(service)
    }

    override fun onServiceDied(service: XposedService) {
        // 只有当前持有的 service 死亡时才清空并派发 null；
        // 多 framework 场景下死掉的可能是已被替换的旧实例，无需影响 UI。
        if (serviceInstance === service) {
            serviceInstance = null
            dispatch(null)
        }
    }

    companion object {
        @Volatile
        var serviceInstance: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<ServiceStateListener>()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            listeners.add(listener)
            if (notifyImmediately) {
                dispatchTo(listener, serviceInstance)
            }
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            listeners.remove(listener)
        }

        private fun dispatch(service: XposedService?) {
            listeners.forEach { dispatchTo(it, service) }
        }

        private fun dispatchTo(listener: ServiceStateListener, service: XposedService?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                listener.onServiceStateChanged(service)
            } else {
                mainHandler.post {
                    if (listeners.contains(listener)) {
                        listener.onServiceStateChanged(service)
                    }
                }
            }
        }
    }
}
