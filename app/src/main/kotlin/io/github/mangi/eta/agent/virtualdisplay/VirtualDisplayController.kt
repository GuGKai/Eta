package io.github.mangi.eta.agent.virtualdisplay

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import io.github.mangi.eta.agent.device.DeviceToolContract
import io.github.mangi.eta.agent.device.RootAccess
import io.github.mangi.eta.agent.overlay.ui.StageCanvasHeight
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 虚拟屏会话与生命周期中枢。
 *
 * 画面通过常驻 [ImageReader] 接收：
 * - 收起态（仅胶囊微缩取景器可见）按 ~6fps 节流更新，兼顾真实动感与低功耗；
 * - 展开态（画廊装裱视窗 / 用户手动接管）提升至 ~30fps 实时更新；
 * - Agent 调用 observe_screen(include_screenshot=true) 时直接提取最新原尺寸 Bitmap，无需等待无障碍截图冷却。
 */
internal object VirtualDisplayController {

    private val lock = Any()
    private val creationLock = Any()
    private val sessionState = VirtualDisplaySessionState()

    /**
     * 当前虚拟屏 displayId；无会话时为 [Display.INVALID_DISPLAY]。
     *
     * 本地 fork 的 activeDisplayId 迁移到上游 VirtualDisplaySessionState 之后仍保留这个读法：
     * IME 策略下发与「接管/继续」都只针对当前这个 display，读写都必须落在同一把锁内。
     */
    private val activeDisplayId: Int
        get() = synchronized(lock) { sessionState.active?.displayId ?: Display.INVALID_DISPLAY }

    private val mutableSnapshots = MutableStateFlow(VirtualDisplaySnapshot.Inactive)
    val snapshots: StateFlow<VirtualDisplaySnapshot> = mutableSnapshots.asStateFlow()

    /** 顶部胶囊是否显示虚拟屏微缩镜/装裱视窗：由执行通知的第三个按钮开关。 */
    private val mutableCapsuleVisible = MutableStateFlow(true)
    val capsuleVisible: StateFlow<Boolean> = mutableCapsuleVisible.asStateFlow()

    private val frameSequenceGenerator = AtomicLong(0L)
    private val touchSequenceGenerator = AtomicLong(0L)

    @Volatile
    private var highFrequencyPreview: Boolean = false

    private var displayResources: VirtualDisplaySessionResources? = null
    private var latestFullBitmap: Bitmap? = null
    private var lastUiFrameAtElapsedMs: Long = 0L
    private val mutedPackages = linkedSetOf<String>()

    /** 创建虚拟屏时的应用上下文，供全屏外置后自动搬回使用。 */
    private var activeContext: Context? = null

    /** 主屏最近一次获得前台焦点的包名，由无障碍窗口事件回报。 */
    @Volatile
    private var lastMainDisplayForegroundPackage: String = ""

    private val restoreLock = Any()
    private var restoreWatcher: Thread? = null
    private var restoreFailureCount: Int = 0

    /** 外部化到主屏后，用户离开该应用时是否自动搬回虚拟屏；结果卡片「打开画面」传 false。 */
    @Volatile
    private var externalizedAutoRestore: Boolean = true

    /** 外部化到主屏的 RootTask id；销毁虚拟屏后靠它把应用按原任务提回前台。 */
    @Volatile
    private var externalizedTaskId: Int = 0

    /** 外部化完成的时刻。刚搬完的一小段窗口期主屏前台会抖动，这段期间不判断“用户离开了”。 */
    @Volatile
    private var externalizedAtElapsedMs: Long = 0L

    /** 延迟回收观察线程：用户离开外置应用（或等够上限）后销毁虚拟屏。 */
    private val retireLock = Any()
    private var retireWatcher: Thread? = null

    /** 虚拟屏 IME 策略最近一次成功下发的目标；displayId 不匹配或 policy<0 表示需要重新下发。 */
    @Volatile private var appliedImePolicyDisplayId: Int = Display.INVALID_DISPLAY
    @Volatile private var appliedImePolicy: Int = -1

    /** 最新下发意图的代际号：排队期间被新意图取代的旧任务直接作废，避免慢路径盖掉新状态。 */
    private val imePolicyGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /** 串行执行 WMS 策略调用：用户快速点「接管/继续」时不会并发乱序。 */
    private val imePolicyExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "eta-vd-ime-policy").apply { isDaemon = true }
    }

    fun isBackendAvailable(rootAvailable: Boolean, lsposedAvailable: Boolean): Boolean =
        rootAvailable || lsposedAvailable

    fun setStageExpanded(expanded: Boolean) {
        highFrequencyPreview = expanded
    }

    /** 开关顶部胶囊的虚拟屏微缩镜显示；供执行通知第三个按钮调用。 */
    fun toggleCapsuleVisible() {
        mutableCapsuleVisible.value = !mutableCapsuleVisible.value
    }

    fun setUserControlling(
        controlling: Boolean,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ) {
        val displayId = synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active || current.userControlling == controlling) return
            mutableSnapshots.value = current.copy(userControlling = controlling)
            if (controlling) highFrequencyPreview = true
            activeDisplayId
        }
        // 接管：用户要亲手打字，把虚拟屏 IME 策略放回 FALLBACK，软键盘才能弹出来。
        // 继续托管：收回键盘，避免 Agent 自己聚焦输入框时键盘反复冒到物理主屏。
        updateVirtualDisplayImePolicy(
            displayId = displayId,
            hide = !controlling,
            rootAvailable = rootAvailable,
        )
    }

    fun currentDisplayId(): Int = synchronized(lock) {
        // 全屏破壁期间任务在物理主屏：Agent 的观测与手势必须跟随主屏，
        // 但虚拟屏仍保留，搬回后 currentDisplayId 会自动回到原 displayId。
        if (mutableSnapshots.value.externalizedToMain) return@synchronized Display.DEFAULT_DISPLAY
        sessionState.active?.displayId ?: Display.DEFAULT_DISPLAY
    }

    /**
     * 确保受信任虚拟屏已就绪并返回其 displayId；不可用时返回 null，由调用方平滑回退至物理主屏。
     */
    fun ensureVirtualDisplay(
        context: Context,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ): Int? {
        val appContext = context.applicationContext
        activeContext = appContext
        // 建屏前先屏蔽系统的 size-compat（letterbox）判定：
        // 打开“强制可调整大小”后，任务迁到主屏会按目标 display 的 app bounds 直接重排，
        // 而不是被缩放塞进 letterbox 并弹出“全屏显示”兼容提示。
        SizeCompatBypass.ensure(appContext, rootAvailable)
        val manager = appContext.getSystemService(DisplayManager::class.java) ?: return null
        // 创建可包含 Binder 与 Root 等待；发布状态和释放不等待这把创建锁。
        synchronized(creationLock) {
            val existing = synchronized(lock) { sessionState.active }
            if (existing != null) {
                val bridge = synchronized(lock) { displayResources?.bridge }
                if ((bridge == null || bridge.isAlive) && isDisplayUsable(manager, existing.displayId)) {
                    synchronized(lock) {
                        if (sessionState.isCurrent(existing.token)) {
                            // 复用既有虚拟屏：上一轮排下的延迟回收必须取消，否则会把这轮正在用的屏销毁掉。
                            cancelExternalizedRetire()
                            // 复用既有虚拟屏：本地 IME 策略可能已被「接管」改成 FALLBACK，托管态补回 HIDE。
                            if (!mutableSnapshots.value.userControlling) {
                                hideImeOnVirtualDisplay(existing.displayId, rootAvailable)
                            }
                            // 上一轮可能把应用外置到主屏后保留了这块屏，复用时要清掉外置态。
                            synchronized(lock) {
                                val reused = mutableSnapshots.value
                                if (reused.externalizedToMain || reused.materializingToMain) {
                                    mutableSnapshots.value = reused.copy(
                                        externalizedToMain = false,
                                        materializingToMain = false,
                                        materializeFading = false,
                                        materializeFrom = null,
                                    )
                                    externalizedTaskId = 0
                                }
                            }
                            return existing.displayId
                        }
                    }
                }
                releaseSession(appContext, rootAvailable, existing.token)
            }
            val token = synchronized(lock) { sessionState.begin() }
            val frameWorker = HandlerThread("eta-virtual-display-frame")
            var reader: ImageReader? = null
            var inProcessDisplay: VirtualDisplay? = null
            var rootBridge: RootVirtualDisplayBridge? = null
            var listener: DisplayManager.DisplayListener? = null
            var accepted = false
            try {
                val (width, height, densityDpi) = resolvePhysicalDisplayMetrics(appContext)
                frameWorker.start()
                val frames = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
                reader = frames
                val handler = Handler(frameWorker.looper)
                frames.setOnImageAvailableListener(::onFrameAvailable, handler)
                if (rootAvailable()) {
                    rootBridge = RootVirtualDisplayBridge.create(
                        appContext, frames.surface, width, height, densityDpi, rootAvailable,
                    ) { process ->
                        val ownsSession = synchronized(lock) {
                            sessionState.isCurrent(token) && displayResources?.bridge?.process === process
                        }
                        if (ownsSession) releaseSession(appContext, rootAvailable, token)
                    }
                }
                val id = rootBridge?.displayId ?: run {
                    if (EtaApp.serviceInstance == null) return null
                    inProcessDisplay = tryCreateInProcessVirtualDisplay(
                        appContext, frames.surface, width, height, densityDpi,
                    )
                    inProcessDisplay?.display?.displayId ?: return null
                }
                listener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit
                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == id && !isDisplayUsable(manager, id)) {
                            releaseSession(appContext, rootAvailable, token)
                        }
                    }
                    override fun onDisplayRemoved(displayId: Int) {
                        if (displayId == id) releaseSession(appContext, rootAvailable, token)
                    }
                }
                manager.registerDisplayListener(listener, handler)
                if (!isDisplayUsable(manager, id)) {
                    val display = runCatching { manager.getDisplay(id) }.getOrNull()
                    val flags = display?.flags ?: 0
                    AndroidAgentLogger.warn(
                        "Virtual display rejected: displayId=$id reason=DISPLAY_NOT_USABLE " +
                            "present=${display != null} valid=${display?.isValid == true} " +
                            "flags=0x${Integer.toHexString(flags)} " +
                            "missing=${CanvasRoutingPolicy.missingDisplayFlagsDescription(flags)}",
                    )
                    return null
                }
                synchronized(lock) {
                    if (rootBridge?.isAlive == false || !sessionState.activate(token, id)) return null
                    displayResources = VirtualDisplaySessionResources(
                        frames, frameWorker, inProcessDisplay, rootBridge, manager, listener,
                    )
                    mutableSnapshots.value = VirtualDisplaySnapshot(
                        active = true,
                        displayId = id,
                        width = width,
                        height = height,
                    )
                    accepted = true
                }
                AndroidAgentLogger.info("Virtual display ready: displayId=$id size=${width}x$height")
                if (rootAvailable() && !mutableSnapshots.value.userControlling) {
                    hideImeOnVirtualDisplay(id, rootAvailable)
                }
                return id
            } catch (exception: Exception) {
                AndroidAgentLogger.warn("Virtual display creation failed: type=${exception.safeLogType()}")
                return null
            } finally {
                if (!accepted) {
                    synchronized(lock) { sessionState.invalidate(token) }
                    VirtualDisplaySessionResources(
                        reader, frameWorker, inProcessDisplay, rootBridge, manager, listener,
                    ).close()
                }
            }
        }
    }

    private fun isDisplayUsable(manager: DisplayManager, displayId: Int): Boolean = try {
        val display = manager.getDisplay(displayId)
        display != null && display.isValid && CanvasRoutingPolicy.hasRequiredDisplayFlags(display.flags)
    } catch (exception: Exception) {
        AndroidAgentLogger.warnThrottled("virtual_display_validation_failed") {
            "Virtual display validation failed: type=${exception.safeLogType()}"
        }
        false
    }

    /**
     * 在虚拟屏上启动指定应用，并对其施加后台音频焦点静默保护。
     */
    fun launchAppOnVirtualDisplay(
        context: Context,
        packageName: String,
        appName: String,
        launchIntent: Intent,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
        checkCancellation: () -> Unit = {},
    ): Int? {
        checkCancellation()
        // 若上一轮仍停留在全屏外置状态，先把原任务搬回虚拟屏，再启动新应用。
        restoreExternalizedToVirtualDisplay(rootAvailable)
        val displayId = ensureVirtualDisplay(context, rootAvailable) ?: return null
        checkCancellation()
        val appContext = context.applicationContext
        mutePackageAudioFocusIfPossible(appContext, packageName, rootAvailable)

        var launchedByRoot = false
        if (rootAvailable()) {
            val userId = DeviceToolContract.appUserId(appContext)
            val component = launchIntent.component?.flattenToShortString()
            if (!component.isNullOrBlank()) {
                val cmd = "am start --user $userId --display $displayId -n ${DeviceToolContract.quote(component)}"
                val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
                try {
                    launchedByRoot = exec.execute(cmd, timeoutMillis = 6_000L).exitCode == 0
                } finally {
                    exec.close()
                }
            }
        }

        if (!launchedByRoot) {
            checkCancellation()
            val startedInProcess = runCatching {
                val intent = Intent(launchIntent).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                appContext.startActivity(intent, options.toBundle())
                true
            }.getOrElse { throwable ->
                AndroidAgentLogger.debug {
                    "Virtual display startActivity in-process fallback: type=${throwable.safeLogType()}"
                }
                false
            }
            if (!startedInProcess) return null
        }

        synchronized(lock) {
            val current = mutableSnapshots.value
            if (current.active && current.displayId == displayId) {
                mutableSnapshots.value = current.copy(
                    appName = appName,
                    packageName = packageName,
                    materializingToMain = false,
                    externalizedToMain = false,
                )
            }
        }
        return displayId
    }

    /**
     * 在虚拟屏上打开外部 URI。
     */
    fun openUriOnVirtualDisplay(
        context: Context,
        intent: Intent,
        targetLabel: String,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
        checkCancellation: () -> Unit = {},
    ): Int? {
        checkCancellation()
        // 若上一轮仍停留在全屏外置状态，先把原任务搬回虚拟屏，再打开新 URI。
        restoreExternalizedToVirtualDisplay(rootAvailable)
        val displayId = ensureVirtualDisplay(context, rootAvailable) ?: return null
        checkCancellation()
        val appContext = context.applicationContext
        val started = runCatching {
            val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            appContext.startActivity(launch, options.toBundle())
            true
        }.getOrDefault(false)

        if (!started && rootAvailable()) {
            checkCancellation()
            val uriString = intent.dataString ?: return null
            val userId = DeviceToolContract.appUserId(appContext)
            val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
            val ok = try {
                exec.execute(
                    "am start --user $userId --display $displayId -a android.intent.action.VIEW -d ${DeviceToolContract.quote(uriString)}",
                    timeoutMillis = 6_000L,
                ).exitCode == 0
            } finally {
                exec.close()
            }
            if (!ok) return null
        } else if (!started) {
            return null
        }

        synchronized(lock) {
            val current = mutableSnapshots.value
            if (current.active && current.displayId == displayId) {
                mutableSnapshots.value = current.copy(
                    appName = targetLabel.ifBlank { current.appName },
                    materializingToMain = false,
                    externalizedToMain = false,
                )
            }
        }
        return displayId
    }

    /**
     * 将虚拟屏中正在运行的应用无缝破壁移交到物理主屏（DEFAULT_DISPLAY）。
     *
     * 顺序按「任何一帧都有画面盖住屏幕」排列，闪烁就出在顺序上：
     * 1. 过渡层先用画板最后一帧、按画板原位尺寸起手，放大到物理全屏（画板消失与位图出现是同一帧，
     *    位图全程不透明，屏幕不留空白）；
     * 2. 整屏被同一帧盖住之后才迁移 RootTask —— 迁移瞬间虚拟屏会变空，空画面被位图挡住；
     * 3. 等主屏应用画出首帧再淡出位图，露出来的已经是活的窗口。
     * 过渡期间帧采集冻结，否则虚拟屏空帧会把位图替换成黑屏，那就是用户看到的「闪一下」。
     */
    fun handoffToMainDisplay(
        context: Context,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
        /**
         * 搬到主屏后，用户切走时是否自动把任务搬回虚拟屏。
         *
         * 结果卡片的「打开画面」是“我就要在主屏用它”，卡片随后就会被移除、胶囊也没了，
         * 自动搬回只会把用户刚打开的应用抢回去，所以那条路径传 false。
         */
        autoRestoreOnLeave: Boolean = true,
        /**
         * 用户离开外置应用时是否直接释放虚拟屏。
         *
         * 虚拟屏一销毁，系统就会把它搬出去的任务挤回后台，所以选「留在主屏」的路径
         * 必须把释放推迟到用户真的离开之后。
         */
        releaseAfterLeave: Boolean = false,
    ): Boolean {
        val snapshot = synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active || !CanvasRoutingPolicy.isVirtualDisplay(current.displayId)) {
                return false
            }
            mutableSnapshots.value = current.copy(
                userControlling = false,
                materializingToMain = true,
                materializeFading = false,
                materializeFrom = resolveStageBoundsPx(context, current),
                externalizedToMain = false,
            )
            // 过渡用冻结帧，不需要高频预览刷新。
            highFrequencyPreview = false
            current
        }

        val appContext = context.applicationContext
        // 迁移前确保兼容检测已关闭，否则搬过去会立刻被按 letterbox 缩放。
        SizeCompatBypass.ensure(appContext, rootAvailable)
        restoreMutedPackages(appContext, rootAvailable)

        // 1) 先把位图放大到物理全屏，屏幕全程被画面覆盖。
        sleepQuietly(MaterializeExpandMs)

        // 2) 盖满之后才搬任务：虚拟屏变空的那一帧发生在这张不透明位图底下。
        val movedTaskId = moveVirtualTaskToDisplay(
            context = appContext,
            sourceDisplayId = snapshot.displayId,
            targetDisplayId = Display.DEFAULT_DISPLAY,
            packageName = snapshot.packageName,
            rootAvailable = rootAvailable,
        )

        if (movedTaskId < 0) {
            // 搬运失败：撤掉过渡层并释放虚拟屏，避免留下看不见的空画布。
            synchronized(lock) {
                val current = mutableSnapshots.value
                if (current.active) {
                    mutableSnapshots.value = current.copy(
                        materializingToMain = false,
                        materializeFading = false,
                        materializeFrom = null,
                    )
                }
            }
            release(appContext, rootAvailable)
            return false
        }

        // 3) 给主屏窗口留出首帧时间，再让位图淡出：淡出是渐变，底下的真实画面接得上。
        sleepQuietly(MaterializeMainFrameWaitMs)
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active) return movedTaskId > 0
            mutableSnapshots.value = current.copy(materializeFading = true)
        }
        sleepQuietly(MaterializeFadeOutMs + MaterializeFadeTailMs)

        // C 方案：全屏后不销毁虚拟屏，只把任务挪到物理主屏；
        // 用户离开该应用时由主屏窗口事件把同一个 RootTask 搬回，胶囊小窗立即恢复。
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active) return movedTaskId > 0
            mutableSnapshots.value = current.copy(
                userControlling = false,
                materializingToMain = false,
                materializeFading = false,
                materializeFrom = null,
                externalizedToMain = true,
            )
            highFrequencyPreview = false
            restoreFailureCount = 0
            externalizedAutoRestore = autoRestoreOnLeave
            externalizedAtElapsedMs = SystemClock.elapsedRealtime()
            // 记下这个 RootTask：销毁虚拟屏时系统会把主屏焦点改派给 Eta，
            // 只有按 taskId 直接把它提回前台，才能让用户继续用原来的页面。
            externalizedTaskId = movedTaskId
        }
        // 已经排程的“搬回虚拟屏”观察线程必须立刻作废，否则它会在几百毫秒后把刚搬到主屏的应用抢回去。
        if (!autoRestoreOnLeave) cancelRestoreWatcher()
        AndroidAgentLogger.info(
            "Virtual display handed off: task=$movedTaskId package=${snapshot.packageName} " +
                "display=${snapshot.displayId}",
        )
        return movedTaskId > 0
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 画板（画廊装裱视窗里的画布）此刻在物理屏幕上的矩形。
     *
     * 装裱窗口横向居中、纵向贴着状态栏，画布又是窗口内水平居中的定高画框，
     * 所以画布中心即屏幕横向中心，顶边 = 状态栏下沿 + 装裱内边距。
     */
    private fun resolveStageBoundsPx(
        context: Context,
        snapshot: VirtualDisplaySnapshot,
    ): VirtualStageBounds? {
        val density = context.resources.displayMetrics.density.takeIf { it > 0.1f } ?: return null
        val screenWidth = runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@runCatching 0
            wm.currentWindowMetrics.bounds.width()
        }.getOrDefault(0)
        if (screenWidth <= 0) return null
        val canvasHeight = StageCanvasHeight.value * density
        val canvasWidth = canvasHeight * snapshot.aspectRatio
        if (canvasWidth <= 1f || canvasHeight <= 1f) return null
        val top = statusBarInsetTopPx(context, density) + StageCanvasTopInsetDp * density
        return VirtualStageBounds(
            left = (screenWidth - canvasWidth) / 2f,
            top = top,
            width = canvasWidth,
            height = canvasHeight,
        )
    }

    private fun statusBarInsetTopPx(context: Context, density: Float): Float {
        val fallback = 28f * density
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@runCatching fallback
            wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.displayCutout(),
                )
                .top
                .toFloat()
                .takeIf { it > 0f } ?: fallback
        }.getOrDefault(fallback)
    }

    /**
     * 无障碍服务回报主屏前台包名。全屏外置期间，只要前台不再是原应用，
     * 就把同一个 RootTask 搬回原虚拟屏，用户随时能回到胶囊小窗继续看。
     */
    fun onMainDisplayForegroundPackage(packageName: String) {
        if (packageName.isBlank()) return
        lastMainDisplayForegroundPackage = packageName
        val snapshot = mutableSnapshots.value
        if (!snapshot.active || !snapshot.externalizedToMain) return
        // 全屏位图还在淡出的这段时间，主屏前台会来回抖，此时的“前台变化”全是假信号。
        if (snapshot.materializingToMain) return
        // 用户从结果卡片点了「打开画面」：明确要在主屏用，别自动搬回去。
        if (!externalizedAutoRestore) return
        // 刚搬完的稳定期内同样不做判断，把时间留给窗口焦点落定。
        if (SystemClock.elapsedRealtime() - externalizedAtElapsedMs < ExternalizedRestoreGraceMs) return
        // 系统窗口（下拉通知、锁屏、音量条）抢占焦点不代表用户离开了应用。
        if (packageName == snapshot.packageName ||
            packageName == "android" ||
            packageName == "com.android.systemui"
        ) {
            return
        }
        scheduleRestoreToVirtualDisplay(packageName)
    }

    /**
     * 手动把全屏外置的任务搬回虚拟屏（供胶囊/结果卡片等入口复用）。
     */
    fun restoreExternalizedToVirtualDisplay(
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ): Boolean {
        val snapshot = mutableSnapshots.value
        if (!snapshot.active || !snapshot.externalizedToMain) return false
        return restoreToVirtualDisplayInternal(snapshot, rootAvailable)
    }

    /** 取消已排程的「搬回虚拟屏」观察线程；线程在 sleep 被中断后自行退出。 */
    private fun cancelRestoreWatcher() {
        val watcher = synchronized(restoreLock) {
            restoreWatcher.also { restoreWatcher = null }
        }
        watcher?.interrupt()
    }

    private fun scheduleRestoreToVirtualDisplay(leftForPackage: String) {
        synchronized(restoreLock) {
            if (restoreWatcher?.isAlive == true) return
            restoreWatcher = thread(name = "eta-vd-restore-watch", isDaemon = true) {
                try {
                    // 600ms 确认窗：系统弹窗、音量条等短暂抢占焦点时不要立刻搬回。
                    try {
                        Thread.sleep(600L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@thread
                    }
                    val snapshot = mutableSnapshots.value
                    if (!snapshot.active || !snapshot.externalizedToMain) return@thread
                    if (!externalizedAutoRestore || snapshot.materializingToMain) return@thread
                    val foreground = lastMainDisplayForegroundPackage
                    if (foreground.isNotBlank() && foreground == snapshot.packageName) return@thread
                    AndroidAgentLogger.info(
                        "Virtual display restore: $leftForPackage left foreground, " +
                            "moving ${snapshot.packageName} back to display=${snapshot.displayId}",
                    )
                    val restored = restoreToVirtualDisplayInternal(
                        snapshot = snapshot,
                        rootAvailable = { RootAccess.isGranted },
                    )
                    if (!restored) {
                        restoreFailureCount += 1
                        AndroidAgentLogger.warn(
                            "Virtual display restore failed: package=${snapshot.packageName} " +
                                "attempt=$restoreFailureCount",
                        )
                        if (restoreFailureCount >= 3) {
                            // 任务已被划掉或系统连续拒绝搬移：释放虚拟屏，避免留下不可见的后台画布。
                            synchronized(lock) {
                                if (mutableSnapshots.value.externalizedToMain) {
                                    AndroidAgentLogger.info(
                                        "Virtual display released after repeated restore failures",
                                    )
                                    releaseSession(activeContext, { RootAccess.isGranted })
                                }
                            }
                            restoreFailureCount = 0
                        }
                    } else {
                        restoreFailureCount = 0
                    }
                } finally {
                    synchronized(restoreLock) {
                        if (restoreWatcher === Thread.currentThread()) restoreWatcher = null
                    }
                }
            }
        }
    }

    private fun restoreToVirtualDisplayInternal(
        snapshot: VirtualDisplaySnapshot,
        rootAvailable: () -> Boolean,
    ): Boolean {
        val appContext = activeContext ?: return false
        val movedTaskId = moveVirtualTaskToDisplay(
            context = appContext,
            sourceDisplayId = Display.DEFAULT_DISPLAY,
            targetDisplayId = snapshot.displayId,
            packageName = snapshot.packageName,
            rootAvailable = rootAvailable,
        )
        if (movedTaskId < 0) return false
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active || !current.externalizedToMain) return movedTaskId >= 0
            mutableSnapshots.value = current.copy(
                userControlling = false,
                materializingToMain = false,
                externalizedToMain = false,
            )
            highFrequencyPreview = false
        }
        AndroidAgentLogger.info(
            "Virtual display restored: package=${snapshot.packageName} display=${snapshot.displayId}",
        )
        // 回到虚拟屏后按当前归属重设 IME 策略：用户控制态保持 FALLBACK，托管态恢复 HIDE。
        updateVirtualDisplayImePolicy(
            displayId = snapshot.displayId,
            hide = !mutableSnapshots.value.userControlling,
            rootAvailable = rootAvailable,
        )
        return true
    }

    /**
     * 当虚拟屏内部发生跨应用跳转（如从应用 A 跳往应用 B 或拉起授权页）时，同步更新当前虚拟屏承载的包名与应用名。
     */
    fun onTopPackageObserved(
        context: Context,
        observedPackage: String,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ) {
        val pkg = observedPackage.trim()
        if (pkg.isBlank() || pkg == "android" || pkg == "com.android.systemui" || pkg == context.packageName) return
        val current = synchronized(lock) { mutableSnapshots.value }
        if (!current.active || current.packageName == pkg) return
        val appContext = context.applicationContext
        val resolvedName = runCatching {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString().trim().takeIf { it.isNotBlank() }
        }.getOrNull() ?: pkg
        mutePackageAudioFocusIfPossible(appContext, pkg, rootAvailable)
        synchronized(lock) {
            val latest = mutableSnapshots.value
            if (latest.active && latest.packageName != pkg) {
                mutableSnapshots.value = latest.copy(
                    packageName = pkg,
                    appName = resolvedName,
                )
            }
        }
    }
    fun captureScreenshotBitmap(waitUpToMs: Long = 450L): Bitmap? {
        val deadline = SystemClock.elapsedRealtime() + waitUpToMs.coerceAtLeast(0L)
        while (true) {
            synchronized(lock) {
                val bmp = latestFullBitmap
                if (bmp != null && !bmp.isRecycled) {
                    return runCatching { bmp.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                }
                if (sessionState.active == null) return null
            }
            if (SystemClock.elapsedRealtime() >= deadline) return null
            try {
                Thread.sleep(40L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
    }

    fun emitTapEffect(x: Int, y: Int, longPress: Boolean = false) {
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active || current.width <= 0 || current.height <= 0) return
            val nx = (x.toFloat() / current.width).coerceIn(0f, 1f)
            val ny = (y.toFloat() / current.height).coerceIn(0f, 1f)
            val effect = VirtualTouchEffect.Tap(
                sequence = touchSequenceGenerator.incrementAndGet(),
                createdAtMs = SystemClock.uptimeMillis(),
                normalizedX = nx,
                normalizedY = ny,
                longPress = longPress,
            )
            mutableSnapshots.value = current.copy(touchEffect = effect)
        }
    }

    fun emitSwipeEffect(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 420) {
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active || current.width <= 0 || current.height <= 0) return
            val effect = VirtualTouchEffect.Swipe(
                sequence = touchSequenceGenerator.incrementAndGet(),
                createdAtMs = SystemClock.uptimeMillis(),
                startX = (x1.toFloat() / current.width).coerceIn(0f, 1f),
                startY = (y1.toFloat() / current.height).coerceIn(0f, 1f),
                endX = (x2.toFloat() / current.width).coerceIn(0f, 1f),
                endY = (y2.toFloat() / current.height).coerceIn(0f, 1f),
                durationMs = durationMs.coerceIn(160, 1_500),
            )
            mutableSnapshots.value = current.copy(touchEffect = effect)
        }
    }

    /**
     * 用户在画廊视窗画布上直接触摸时，将归一化坐标注入虚拟屏。
     */
    fun dispatchUserTapOnCanvas(
        normalizedX: Float,
        normalizedY: Float,
        longPress: Boolean = false,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ) {
        val snapshot = mutableSnapshots.value
        if (!snapshot.active || !CanvasRoutingPolicy.isVirtualDisplay(snapshot.displayId)) return
        val x = (normalizedX.coerceIn(0f, 1f) * (snapshot.width - 1)).roundToInt()
        val y = (normalizedY.coerceIn(0f, 1f) * (snapshot.height - 1)).roundToInt()
        emitTapEffect(x, y, longPress = longPress)
        thread(name = "eta-vd-user-tap", isDaemon = true) {
            val duration = if (longPress) 650L else 55L
            val dispatched = AgentAccessibilityService.current()
                ?.gestureTap(x.toFloat(), y.toFloat(), durationMs = duration, displayId = snapshot.displayId)
                ?.ok == true
            if (!dispatched && rootAvailable()) {
                val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
                try {
                    val cmd = if (longPress) {
                        "input -d ${snapshot.displayId} swipe $x $y $x $y 650"
                    } else {
                        "input -d ${snapshot.displayId} tap $x $y"
                    }
                    exec.execute(cmd, timeoutMillis = 5_000L)
                } finally {
                    exec.close()
                }
            }
        }
    }

    fun dispatchUserSwipeOnCanvas(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Int = 320,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ) {
        val snapshot = mutableSnapshots.value
        if (!snapshot.active || !CanvasRoutingPolicy.isVirtualDisplay(snapshot.displayId)) return
        val x1 = (startX.coerceIn(0f, 1f) * (snapshot.width - 1)).roundToInt()
        val y1 = (startY.coerceIn(0f, 1f) * (snapshot.height - 1)).roundToInt()
        val x2 = (endX.coerceIn(0f, 1f) * (snapshot.width - 1)).roundToInt()
        val y2 = (endY.coerceIn(0f, 1f) * (snapshot.height - 1)).roundToInt()
        val safeDuration = durationMs.coerceIn(140, 1_000)
        emitSwipeEffect(x1, y1, x2, y2, safeDuration)
        thread(name = "eta-vd-user-swipe", isDaemon = true) {
            val dispatched = AgentAccessibilityService.current()
                ?.gestureSwipe(
                    x1.toFloat(),
                    y1.toFloat(),
                    x2.toFloat(),
                    y2.toFloat(),
                    durationMs = safeDuration.toLong(),
                    displayId = snapshot.displayId,
                )?.ok == true
            if (!dispatched && rootAvailable()) {
                val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
                try {
                    exec.execute(
                        "input -d ${snapshot.displayId} swipe $x1 $y1 $x2 $y2 $safeDuration",
                        timeoutMillis = 5_000L,
                    )
                } finally {
                    exec.close()
                }
            }
        }
    }

    /**
     * 销毁虚拟屏。
     *
     * 系统移除 display 时会把主屏焦点交还给它记住的旧任务（通常就是 Eta 自己），
     * 刚从句外搬到主屏、正占着屏幕的应用会被顶到后面 —— 用户看到的就是
     * 「点了打开画面，应用跟着结果卡片一起消失」。
     * [restoreExternalizedForeground] 为真时，销毁前后把那个外置包按原 RootTask 提回前台。
     */
    fun release(
        context: Context? = null,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
        restoreExternalizedForeground: Boolean = false,
    ) {
        // 真正销毁了就不需要再排「延迟回收」，否则会在下次任务里对同一块屏重复动手。
        cancelExternalizedRetire()
        val appContext = context?.applicationContext
        val externalizedPackage = if (restoreExternalizedForeground && appContext != null) {
            mutableSnapshots.value.let { snapshot ->
                snapshot.packageName.takeIf { snapshot.active && snapshot.externalizedToMain && it.isNotBlank() }
            }
        } else {
            null
        }
        val externalizedTask = if (externalizedPackage != null) externalizedTaskId else 0
        // 先把「带回前台」的线程排好再销毁：销毁是异步的，焦点改派紧跟在它后面。
        if (externalizedPackage != null && appContext != null) {
            scheduleBringExternalizedToForeground(appContext, externalizedPackage, externalizedTask, rootAvailable)
        }
        releaseSession(context, rootAvailable)
        // 虚拟屏彻底销毁后把“强制可调整大小”恢复成接管前的值，避免污染全局。
        context?.let { SizeCompatBypass.restore(it.applicationContext, rootAvailable) }
    }

    /**
     * 服务停止时的收尾。
     *
     * 此刻如果正有应用被外置到主屏（用户点了「打开画面」），**不能立刻销毁虚拟屏**：
     * 系统移除 display 的那一瞬会把主屏焦点抢回 Eta，用户正在用的应用就被盖掉，
     * 看起来就是「跟着浮窗一起消失」。所以改成**延迟回收**：先按 [ExternalizedRetireDelayMs]
     * 让用户安安静静用完，之后一旦发现主屏前台不再是那个应用（或等够
     * [ExternalizedRetireMaxWaitMs] 上限），再把虚拟屏连同采集资源一起销毁，
     * 既不打断使用，也不长期占着那块 1440x3168 的画布。
     */
    fun releaseForServiceStop(
        context: Context? = null,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ) {
        val snapshot = mutableSnapshots.value
        if (snapshot.active && snapshot.externalizedToMain) {
            AndroidAgentLogger.info(
                "Virtual display retire scheduled: ${snapshot.packageName} still externalized on main display, " +
                    "delay=${ExternalizedRetireDelayMs}ms",
            )
            scheduleExternalizedRetire(context, rootAvailable)
            return
        }
        release(context, rootAvailable)
    }

    /**
     * 排一个后台观察线程做延迟回收：先等 [ExternalizedRetireDelayMs]，之后每
     * [ExternalizedRetireRetryMs] 复查一次主屏前台；用户已经离开那个应用就销毁虚拟屏。
     * 期间只要虚拟屏被回收/搬回/新任务复用，线程自行退出。
     */
    private fun scheduleExternalizedRetire(
        context: Context?,
        rootAvailable: () -> Boolean,
    ) {
        synchronized(retireLock) {
            if (retireWatcher?.isAlive == true) return
            retireWatcher = thread(name = "eta-vd-retire-watch", isDaemon = true) {
                try {
                    val started = SystemClock.elapsedRealtime()
                    var waited = 0L
                    while (true) {
                        sleepQuietly(
                            if (waited == 0L) ExternalizedRetireDelayMs else ExternalizedRetireRetryMs,
                        )
                        waited = SystemClock.elapsedRealtime() - started
                        val snapshot = mutableSnapshots.value
                        // 已经释放、已搬回虚拟屏、或被新一轮任务复用：无需再动手。
                        if (!snapshot.active || !snapshot.externalizedToMain) return@thread
                        // 直接问无障碍服务“主屏当前窗口是谁”。
                        // 不能用 lastMainDisplayForegroundPackage：它唯一的写入口
                        // onMainDisplayForegroundPackage 在本模块里没有任何调用者，
                        // 读出来恒为空，会让判定永远落到「用户已经离开」。
                        val foreground = AgentAccessibilityService.current()
                            ?.currentPackageName(Display.DEFAULT_DISPLAY)
                            .orEmpty()
                        if (foreground.isBlank()) {
                            // 无障碍答不上来：宁可多占一会儿画布，也不误杀正在用的应用。
                            if (waited < ExternalizedRetireMaxWaitMs) {
                                AndroidAgentLogger.info(
                                    "Virtual display retire pending: foreground unknown after ${waited}ms",
                                )
                                continue
                            }
                            AndroidAgentLogger.info(
                                "Virtual display retire forced: foreground still unknown after ${waited}ms",
                            )
                            runCatching { release(activeContext ?: context, rootAvailable) }
                            return@thread
                        }
                        val stillUsing = foreground == snapshot.packageName
                        if (stillUsing && waited < ExternalizedRetireMaxWaitMs) {
                            AndroidAgentLogger.info(
                                "Virtual display retire postponed: ${snapshot.packageName} still in foreground " +
                                    "after ${waited}ms",
                            )
                            continue
                        }
                        AndroidAgentLogger.info(
                            "Virtual display retired: ${snapshot.packageName} after ${waited}ms " +
                                "foreground=$foreground",
                        )
                        runCatching { release(activeContext ?: context, rootAvailable) }
                        return@thread
                    }
                } finally {
                    synchronized(retireLock) {
                        if (retireWatcher === Thread.currentThread()) retireWatcher = null
                    }
                }
            }
        }
    }

    /** 取消待执行的延迟回收；虚拟屏被重新使用时调用，避免新任务被回收线程打断。 */
    private fun cancelExternalizedRetire() {
        val watcher = synchronized(retireLock) {
            retireWatcher.also { retireWatcher = null }
        } ?: return
        // 回收线程自己调用 release 时不要打断自己。
        if (watcher === Thread.currentThread()) return
        watcher.interrupt()
    }

    /**
     * 虚拟屏销毁后把外置到主屏的应用带回前台。
     *
     * 销毁是异步的，焦点改派在几十到几百毫秒之后才发生，所以在短窗口里反复提一次：
     * 目标本来就在前台时调用 moveTaskToFront 没有副作用，真正生效的是焦点被抢走之后那一次。
     * 进程内没有权限时退回 root 桥，由桥自己等「主屏栈顶变回 Eta」再动手。
     */
    private fun scheduleBringExternalizedToForeground(
        context: Context,
        packageName: String,
        taskId: Int,
        rootAvailable: () -> Boolean,
    ) {
        thread(name = "eta-vd-bring-front", isDaemon = true) {
            // 只走特权桥：进程内的 ActivityManager.moveTaskToFront 在这台机器上没有可用的
            // ATMS 入口，调用会静默失败，反而让兜底永远不触发。桥内部自己轮询等焦点改派。
            repeat(ExternalizedBringAttempts) { attempt ->
                if (attempt > 0) sleepQuietly(250L)
                val brought = bringPackageToFront(
                    context = context,
                    packageName = packageName,
                    expectedTopPackage = context.packageName,
                    rootAvailable = rootAvailable,
                )
                if (brought) {
                    AndroidAgentLogger.info(
                        "Virtual display released, brought $packageName back (task=$taskId)",
                    )
                    return@thread
                }
            }
        }
    }

    private fun bringPackageToFront(
        context: Context,
        packageName: String,
        expectedTopPackage: String,
        rootAvailable: () -> Boolean,
    ): Boolean {
        if (!rootAvailable() || !DeviceToolContract.validPackageName(packageName)) return false
        return runCatching {
            val apkPath = context.applicationInfo.sourceDir
            val cmd = "CLASSPATH=${DeviceToolContract.quote(apkPath)} app_process /system/bin " +
                "io.github.mangi.eta.agent.virtualdisplay.VirtualDisplayCommandMain"
            val req = JSONObject()
                .put("action", "bring_to_front")
                .put("package_name", packageName)
                .put("expected_top_package", expectedTopPackage)
                .put("target_display_id", Display.DEFAULT_DISPLAY)
                .put("timeout_ms", 3_000)
                .toString() + "\n"
            val supervisor = ShellProcessSupervisor(rootAvailable = rootAvailable)
            val proc = supervisor.startShellProcess("root", cmd, mergeStderr = true) ?: return false
            try {
                proc.outputStream.write(req.toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
                proc.outputStream.close()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor(6, TimeUnit.SECONDS)
                out.contains("\"ok\":true")
            } catch (_: Throwable) {
                false
            } finally {
                supervisor.beginClosing()
                if (proc.isAlive) supervisor.terminateAndReap(proc) else supervisor.reapProcess(proc)
                supervisor.unregisterProcess(proc)
            }
        }.getOrDefault(false)
    }

    private fun releaseSession(
        context: Context?,
        rootAvailable: () -> Boolean,
        expected: VirtualDisplaySessionState.Token? = null,
    ) {
        val resources = synchronized(lock) {
            if (expected != null && !sessionState.isCurrent(expected)) return
            sessionState.invalidate()
            // 本地状态复位：会话结束后不再指代任何 display 或前台包。
            activeContext = null
            lastMainDisplayForegroundPackage = ""
            appliedImePolicyDisplayId = Display.INVALID_DISPLAY
            appliedImePolicy = -1
            highFrequencyPreview = false
            externalizedAutoRestore = true
            externalizedAtElapsedMs = 0L
            externalizedTaskId = 0
            mutableSnapshots.value = VirtualDisplaySnapshot.Inactive
            if (context != null && mutedPackages.isNotEmpty()) {
                restoreMutedPackages(context.applicationContext, rootAvailable)
            } else {
                mutedPackages.clear()
            }
            val detached = displayResources
            displayResources = null
            latestFullBitmap?.let { if (!it.isRecycled) it.recycle() }
            latestFullBitmap = null
            lastUiFrameAtElapsedMs = 0L
            detached
        }
        if (resources != null) {
            thread(name = "eta-vd-cleanup", isDaemon = true) { resources.close() }
        }
        // 会话已结束：还在睡的观察线程再醒来也不该做任何搬运。
        cancelRestoreWatcher()
    }

    private fun onFrameAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            val now = SystemClock.elapsedRealtime()
            val minIntervalMs = if (highFrequencyPreview) 30L else 160L
            val shouldUpdateUi = now - lastUiFrameAtElapsedMs >= minIntervalMs || latestFullBitmap == null

            val bitmap = decodeImageToBitmap(image) ?: return
            var oldBitmap: Bitmap? = null
            synchronized(lock) {
                if (sessionState.active == null || displayResources?.reader !== reader) {
                    bitmap.recycle()
                    return
                }
                oldBitmap = latestFullBitmap
                latestFullBitmap = bitmap
                if (shouldUpdateUi) {
                    lastUiFrameAtElapsedMs = now
                    val previewBitmap = createUiPreviewBitmap(bitmap)
                    val current = mutableSnapshots.value
                    // 破壁过渡期间冻结预览帧：任务搬走后虚拟屏是空画面，若继续刷新会把过渡层
                    // 正在放大的那张位图替换成黑屏 —— 用户看到的「切全屏闪一下」就是这么来的。
                    if (current.active && !current.materializingToMain) {
                        mutableSnapshots.value = current.copy(
                            frame = previewBitmap.asImageBitmap(),
                            frameSequence = frameSequenceGenerator.incrementAndGet(),
                        )
                    }
                }
            }
            oldBitmap?.let { old ->
                if (!old.isRecycled) old.recycle()
            }
        } catch (_: Throwable) {
            // 单帧解码异常不影响后续帧流
        } finally {
            runCatching { image.close() }
        }
    }

    private fun decodeImageToBitmap(image: Image): Bitmap? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        // 优先直接通过 HardwareBuffer 映射，避免不同 ROM Gralloc 行尾 padding 差异导致的 BufferUnderflowException
        runCatching {
            image.hardwareBuffer?.use { hb ->
                val wrapped = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
                if (wrapped != null) {
                    try {
                        return wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        if (!wrapped.isRecycled) wrapped.recycle()
                    }
                }
            }
        }

        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null
        buffer.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        if (paddedWidth < width) return null

        val requiredBytes = paddedWidth * height * 4
        val safeHeight = if (buffer.remaining() >= requiredBytes) {
            height
        } else {
            (buffer.remaining() / (paddedWidth * 4)).coerceAtMost(height)
        }
        if (safeHeight <= 0) return null

        val rawBitmap = Bitmap.createBitmap(paddedWidth, safeHeight, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == width && safeHeight == height) {
            rawBitmap
        } else {
            val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, width, safeHeight)
            rawBitmap.recycle()
            cropped
        }
    }

    private fun createUiPreviewBitmap(source: Bitmap): Bitmap {
        val maxTargetHeight = if (highFrequencyPreview) 1080 else 480
        if (source.height <= maxTargetHeight) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxTargetHeight.toFloat() / source.height.toFloat()
        val targetW = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = maxTargetHeight
        return source.scale(targetW, targetH)
    }

    @android.annotation.SuppressLint("WrongConstant")
    private fun tryCreateInProcessVirtualDisplay(
        appContext: Context,
        surface: android.view.Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
    ): VirtualDisplay? = runCatching {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("Landroid/hardware/display/") }
        val dm = appContext.getSystemService(DisplayManager::class.java) ?: return@runCatching null
        val config = VirtualDisplayConfig.Builder(
            CanvasRoutingPolicy.VIRTUAL_DISPLAY_NAME,
            width,
            height,
            densityDpi,
        )
            .setSurface(surface)
            .setFlags(CanvasRoutingPolicy.TRUSTED_INTERACTIVE_FLAGS)
            .build()
        dm.createVirtualDisplay(config)
    }.getOrNull()

    /** 破壁过渡：位图放大到全屏所需时间，与 overlay 的弹簧时长同量级。 */
    private const val MaterializeExpandMs = 260L

    /** 破壁过渡：迁移任务后给主屏窗口留的首帧时间，之后才开始淡出位图。 */
    private const val MaterializeMainFrameWaitMs = 200L

    /** 破壁过渡：位图淡出时长，必须与 VirtualDisplayMaterializeOverlay 的 tween 一致。 */
    private const val MaterializeFadeOutMs = 220L

    /** 破壁过渡：淡出结束到下线的余量，避免最后一帧硬切。 */
    private const val MaterializeFadeTailMs = 40L

    /**
     * 外置到主屏后的稳定期：这段时间内主屏前台报什么包名都不据此把任务搬回虚拟屏。
     * 覆盖系统重组任务栈、焦点回落等瞬时抖动，避免刚打开的应用又被抢回后台。
     */
    private const val ExternalizedRestoreGraceMs = 1500L

    /** 销毁虚拟屏后把外置任务提回前台的尝试次数；桥内部还会自己轮询焦点改派。 */
    private const val ExternalizedBringAttempts = 2

    /** 外置到主屏后的静默期：这段时间完全不考虑回收，先让用户安心用完。 */
    private const val ExternalizedRetireDelayMs = 40_000L

    /** 静默期之后的复查间隔：每次复查主屏前台是否还是那个应用。 */
    private const val ExternalizedRetireRetryMs = 20_000L

    /** 用户一直不离开时，最长保留多久强制回收，避免虚拟屏无限期占着内存。 */
    private const val ExternalizedRetireMaxWaitMs = 15 * 60_000L

    /** 画布顶边相对状态栏下沿的内边距（画廊装裱 Column 的垂直 padding）。 */
    private const val StageCanvasTopInsetDp = 4f

    /** IWindowManager 事务码：本机 framework.jar 中 IWindowManager$Stub.TRANSACTION_setDisplayImePolicy。 */
    private const val WMS_TRANSACTION_SET_DISPLAY_IME_POLICY = 108

    /** IWindowManager 事务码：本机 framework.jar 中 IWindowManager$Stub.TRANSACTION_getDisplayImePolicy。 */
    private const val WMS_TRANSACTION_GET_DISPLAY_IME_POLICY = 107

    /** android.view.WindowManager.DISPLAY_IME_POLICY_FALLBACK：IME 回落到物理主屏显示。 */
    private const val DISPLAY_IME_POLICY_FALLBACK = 1

    /** android.view.WindowManager.DISPLAY_IME_POLICY_HIDE：不在该 display 上显示 IME。 */
    private const val DISPLAY_IME_POLICY_HIDE = 2

    /**
     * 把虚拟屏的 IME 策略改成「隐藏」。
     *
     * 该机型的 VirtualDisplayConfig.Builder 没有 setDisplayImePolicy，只能在 display 注册进
     * WindowManagerService 之后调用 WMS#setDisplayImePolicy(HIDE)。策略生效后
     * InputMethodManagerService 的 computeImeDisplayIdForTargetInner 会返回 -1，虚拟屏里的
     * 输入框不会再让软键盘冒到物理主屏。display 注册有延迟，失败时短暂重试。
     */
    private fun hideImeOnVirtualDisplay(displayId: Int, rootAvailable: () -> Boolean) {
        updateVirtualDisplayImePolicy(
            displayId = displayId,
            hide = true,
            rootAvailable = rootAvailable,
        )
    }

    /**
     * 下发虚拟屏的 IME 策略。
     *
     * HIDE：Agent 自动运行期间使用，虚拟屏里的输入框不会再把软键盘冒到物理主屏；
     * FALLBACK：用户点「接管」后使用，软键盘可以正常弹出（挂在物理主屏）。
     *
     * 该机型的 VirtualDisplayConfig.Builder 没有 setDisplayImePolicy，只能在 display 注册进
     * WindowManagerService 之后调用 WMS#setDisplayImePolicy。display 注册有延迟，失败时短暂重试。
     * 策略调用串行执行，保证「接管/继续」快速切换时最终状态正确。
     */
    private fun updateVirtualDisplayImePolicy(
        displayId: Int,
        hide: Boolean,
        rootAvailable: () -> Boolean,
    ) {
        if (!CanvasRoutingPolicy.isVirtualDisplay(displayId)) return
        val policy = if (hide) DISPLAY_IME_POLICY_HIDE else DISPLAY_IME_POLICY_FALLBACK
        val generation = imePolicyGeneration.incrementAndGet()
        imePolicyExecutor.execute {
            // 用户连点「接管 / 继续」时队列里可能压着旧意图：只执行最新那一条。
            if (generation != imePolicyGeneration.get()) return@execute
            if (appliedImePolicyDisplayId == displayId && appliedImePolicy == policy) return@execute
            // 排队期间虚拟屏可能已经换人或释放：只对当前这个 display 生效。
            if (displayId != activeDisplayId) return@execute
            if (!rootAvailable()) {
                AndroidAgentLogger.warn("[vd] 无 Root，跳过虚拟屏 IME 策略 displayId=$displayId policy=$policy")
                return@execute
            }
            val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
            try {
                // HIDE 只拦新请求，已经冒出来的软键盘要主动收掉。
                if (hide) dismissSoftKeyboardIfVisible(exec)
                val applied = applyDisplayImePolicy(exec, displayId, policy)
                // 写回成功不代表真的生效：读回一次，日志里留下 WMS 的实际策略，方便对账。
                val readBack = readDisplayImePolicy(exec, displayId)
                if (applied && (readBack == null || readBack == policy)) {
                    appliedImePolicyDisplayId = displayId
                    appliedImePolicy = policy
                    AndroidAgentLogger.info(
                        "[vd] 虚拟屏 IME 策略已下发 displayId=$displayId policy=$policy readBack=$readBack",
                    )
                } else {
                    appliedImePolicy = -1
                    AndroidAgentLogger.warn(
                        "[vd] 虚拟屏 IME 策略下发失败 displayId=$displayId policy=$policy " +
                            "readBack=$readBack",
                    )
                }
            } finally {
                runCatching { exec.close() }
            }
        }
    }

    private fun applyDisplayImePolicy(
        exec: BoundedRootCommandExecutor,
        displayId: Int,
        policy: Int,
    ): Boolean {
        repeat(3) { attempt ->
            val result = runCatching {
                exec.execute(
                    command = "service call window $WMS_TRANSACTION_SET_DISPLAY_IME_POLICY " +
                        "i32 $displayId i32 $policy",
                    timeoutMillis = 3_000L,
                )
            }.getOrNull()
            val applied = result != null && result.ok &&
                result.stdout.contains("Result: Parcel") &&
                !result.stdout.contains("Exception")
            if (applied) return true
            try {
                Thread.sleep(300L * (attempt + 1))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    /**
     * 读回 displayId 当前实际生效的 IME 策略；解析失败返回 null。
     *
     * 本机 service call 的输出有两种形态：`Result: Parcel(\t00000000 00000000   \'........\')`
     * （异常码 + 返回值）与 AIDL 的 `0x00000000: 00000000 00000002 \'....\'`（列偏移 + 异常码 + 返回值）。
     * 只有异常码为 0 时返回值才可信。
     */
    private fun readDisplayImePolicy(exec: BoundedRootCommandExecutor, displayId: Int): Int? {
        val result = runCatching {
            exec.execute(
                command = "service call window $WMS_TRANSACTION_GET_DISPLAY_IME_POLICY i32 $displayId",
                timeoutMillis = 3_000L,
            )
        }.getOrNull() ?: return null
        if (!result.ok) return null
        val body = result.stdout.substringAfter("Result: Parcel(", "").substringBefore("\'")
        val words = body.split(Regex("[^0-9a-fA-F]+")).filter { it.isNotEmpty() }
        // 带列偏移时多一个前导字，异常码与返回值整体后移一位。
        val offset = if (body.contains(":")) 1 else 0
        if (words.getOrNull(offset)?.toLongOrNull(16) != 0L) return null
        return words.getOrNull(offset + 1)?.toLongOrNull(16)?.toInt()
    }

    /** 接管结束后把还留在物理主屏的软键盘收掉。 */
    private fun dismissSoftKeyboardIfVisible(exec: BoundedRootCommandExecutor) {
        val probe = runCatching {
            exec.execute(
                command = "dumpsys input_method | grep -m1 'mImeWindowVis='",
                timeoutMillis = 2_000L,
            )
        }.getOrNull() ?: return
        if (!probe.ok) return
        val flag = probe.stdout.substringAfter("mImeWindowVis=", "").trim().take(1)
        if (flag.isEmpty() || flag == "0") return
        runCatching {
            exec.execute(
                command = "input -d ${Display.DEFAULT_DISPLAY} keyevent 111",
                timeoutMillis = 2_000L,
            )
        }
        AndroidAgentLogger.info("[vd] 收回接管期间弹出的软键盘")
    }

    /**
     * 把 [sourceDisplayId] 上该包的任务搬到 [targetDisplayId]。
     *
     * 返回：>0 搬过去的 RootTask id；0 任务已启动但拿不到 id；-1 失败。
     * 调用方需要这个 id，因为虚拟屏销毁后要靠它把应用按原任务提回前台。
     */
    private fun moveVirtualTaskToDisplay(
        context: Context,
        sourceDisplayId: Int,
        targetDisplayId: Int,
        packageName: String,
        rootAvailable: () -> Boolean,
    ): Int {
        // 1. 尝试进程内通过 system_server Hook 放行的 moveRootTaskToDisplay 迁移
        val movedTaskIdInProcess = runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityTaskManager", "Landroid/app/IActivityTaskManager")
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val service = atmClass.getMethod("getService").invoke(null) ?: return@runCatching -1
            val getTasks = service.javaClass.methods.firstOrNull { it.name == "getTasks" && it.parameterCount >= 1 }
            val tasks = when (getTasks?.parameterCount) {
                1 -> getTasks.invoke(service, 40)
                3 -> getTasks.invoke(service, 40, false, false)
                4 -> getTasks.invoke(service, 40, false, false, Display.INVALID_DISPLAY)
                else -> null
            } as? List<*> ?: emptyList<Any?>()
            var onSourceDisplayTaskId = -1
            var samePackageTaskId = -1
            for (item in tasks) {
                val info = item as? android.app.TaskInfo ?: continue
                val taskDisplay = runCatching {
                    android.app.TaskInfo::class.java.getField("displayId").getInt(info)
                }.getOrDefault(Display.INVALID_DISPLAY)
                // 已在目标屏的任务不需要再搬，避免把主屏复用任务误判为本次搬运对象。
                if (taskDisplay == targetDisplayId) continue
                val topPkg = info.topActivity?.packageName ?: info.baseActivity?.packageName
                val packageMatches = packageName.isEmpty() || topPkg == packageName
                if (taskDisplay == sourceDisplayId && packageMatches) {
                    onSourceDisplayTaskId = info.taskId
                    break
                }
                if (packageName.isNotEmpty() && topPkg == packageName && samePackageTaskId <= 0) {
                    samePackageTaskId = info.taskId
                }
            }
            val targetTaskId = if (onSourceDisplayTaskId > 0) onSourceDisplayTaskId else samePackageTaskId
            if (targetTaskId > 0) {
                val moveMethod = service.javaClass.getMethod(
                    "moveRootTaskToDisplay",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                moveMethod.invoke(service, targetTaskId, targetDisplayId)
                targetTaskId
            } else {
                -1
            }
        }.getOrDefault(-1)

        if (movedTaskIdInProcess > 0) return movedTaskIdInProcess

        // 2. Root 特权桥调用 moveRootTaskToDisplay
        if (rootAvailable()) {
            val apkPath = context.applicationInfo.sourceDir
            val cmd = "CLASSPATH=${DeviceToolContract.quote(apkPath)} app_process /system/bin " +
                "io.github.mangi.eta.agent.virtualdisplay.VirtualDisplayCommandMain"
            val req = JSONObject()
                .put("action", "move_to_display")
                .put("display_id", sourceDisplayId)
                .put("target_display_id", targetDisplayId)
                .put("package_name", packageName)
                .toString() + "\n"
            val supervisor = ShellProcessSupervisor(rootAvailable = rootAvailable)
            val proc = supervisor.startShellProcess("root", cmd, mergeStderr = true)
            if (proc != null) {
                val movedByRootTaskId = try {
                    proc.outputStream.write(req.toByteArray(Charsets.UTF_8))
                    proc.outputStream.flush()
                    proc.outputStream.close()
                    val out = proc.inputStream.bufferedReader().readText()
                    proc.waitFor(4, TimeUnit.SECONDS)
                    parseBridgeTaskId(out)
                } catch (_: Throwable) {
                    -1
                } finally {
                    supervisor.beginClosing()
                    if (proc.isAlive) supervisor.terminateAndReap(proc) else supervisor.reapProcess(proc)
                    supervisor.unregisterProcess(proc)
                }
                if (movedByRootTaskId >= 0) return movedByRootTaskId
            }
        }

        // 3. 兜底：以 SINGLE_TOP + NEW_TASK 在目标显示上唤起该应用（能起来，但拿不到 taskId）
        if (packageName.isNotBlank()) {
            val launched = runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    ?: return@runCatching false
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
                context.startActivity(intent, options.toBundle())
                true
            }.getOrDefault(false)
            if (launched) return 0
        }
        return -1
    }

    /** 从特权桥的 ETA_VD_RESULT JSON 里取 task_id；ok=false 或解析失败都返回 -1。 */
    private fun parseBridgeTaskId(output: String): Int {
        val raw = output.substringAfter(VirtualDisplayCommandMain.RESULT_PREFIX, "")
            .lineSequence().firstOrNull()?.trim().orEmpty()
        if (raw.isEmpty()) return -1
        return runCatching {
            val json = JSONObject(raw)
            if (!json.optBoolean("ok")) -1 else json.optInt("task_id", 0)
        }.getOrDefault(-1)
    }

    private fun mutePackageAudioFocusIfPossible(
        context: Context,
        packageName: String,
        rootAvailable: () -> Boolean,
    ) {
        if (!rootAvailable() || !DeviceToolContract.validPackageName(packageName)) return
        val added = synchronized(lock) { mutedPackages.add(packageName) }
        if (!added) return
        val userId = DeviceToolContract.appUserId(context)
        thread(name = "eta-vd-audio-shield", isDaemon = true) {
            val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
            try {
                exec.execute(
                    "cmd appops set --user $userId ${DeviceToolContract.quote(packageName)} TAKE_AUDIO_FOCUS ignore",
                    timeoutMillis = 3_000L,
                )
            } catch (_: Throwable) {
                // 忽略部分 ROM appops 差异
            } finally {
                exec.close()
            }
        }
    }

    private fun restoreMutedPackages(
        context: Context,
        rootAvailable: () -> Boolean,
    ) {
        val toRestore = synchronized(lock) {
            val copy = mutedPackages.toList()
            mutedPackages.clear()
            copy
        }
        if (toRestore.isEmpty() || !rootAvailable()) return
        val userId = DeviceToolContract.appUserId(context)
        thread(name = "eta-vd-audio-restore", isDaemon = true) {
            val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
            try {
                for (pkg in toRestore) {
                    if (!DeviceToolContract.validPackageName(pkg)) continue
                    exec.execute(
                        "cmd appops set --user $userId ${DeviceToolContract.quote(pkg)} TAKE_AUDIO_FOCUS allow",
                        timeoutMillis = 3_000L,
                    )
                }
            } catch (_: Throwable) {
            } finally {
                exec.close()
            }
        }
    }

    /**
     * 虚拟屏尺寸必须与主屏「应用可布局区域」完全一致，并且取默认显示屏（主屏）的真实尺寸。
     *
     * 网易云这类 resizeMode=unresizable 的应用被 moveRootTaskToDisplay 搬回主屏时，
     * 系统不会重新计算它的 activity 配置，只能沿用虚拟屏上的 mBounds：
     * - 虚拟屏比主屏小 → size-compat 缩放/letterbox，出现左右黑边与「需要重新打开才能全屏」；
     * - 虚拟屏只差一条 inset（本机 1440x3008 vs 主屏 1440x3168）→ 窗口铺到全屏，
     *   但应用内容仍按 3008 排版，底部空出一条 160px。
     * 本机主屏 mAppBounds 本身就是 1440x3168（状态栏/挖孔区域允许应用铺满），
     * 所以这里直接取默认显示屏的真实尺寸，不再扣减 system bar insets。
     */
    @Suppress("DEPRECATION")
    private fun resolvePhysicalDisplayMetrics(context: Context): Triple<Int, Int, Int> {
        val dm = context.resources.displayMetrics
        var width = dm.widthPixels.coerceAtLeast(720)
        var height = dm.heightPixels.coerceAtLeast(1280)
        val density = dm.densityDpi.coerceIn(160, 640)
        runCatching {
            val displayManager = context.getSystemService(DisplayManager::class.java)
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            if (display != null) {
                val point = Point()
                display.getRealSize(point)
                if (point.x > 0 && point.y > 0) {
                    width = point.x
                    height = point.y
                }
            }
            AndroidAgentLogger.info("Virtual display metrics: main display=" + width + "x" + height)
        }
        return Triple(width, height, density)
    }
}

/**
 * 屏蔽系统的 size-compat（letterbox）判定。
 *
 * 网易云这类 targetSdk 较低、声明不可缩放的应用，在虚拟屏（1440x3168）里是满屏的；
 * 一旦把任务搬到主屏，系统会按主屏 app bounds（扣掉 160 挖孔）重新判定尺寸，
 * 判定失败即进入 size-compat：窗口被 scaleFactor 缩放、左右出现 Letterbox 黑边，
 * 并弹出“全屏显示”兼容提示。
 *
 * 打开 Settings.Global.force_resizable_activities 后，系统不再把这些应用当作
 * “不可缩放”，迁移时直接按目标 display 的 app bounds 重排，从源头避免缩放和黑边。
 * 原值记录在 SharedPreferences，虚拟屏销毁时恢复，避免永久污染全局设置。
 */
internal object SizeCompatBypass {
    private const val PREFS = "eta_size_compat_bypass"
    private const val KEY_ORIGINAL = "original"
    private const val KEY_APPLIED = "applied"
    private const val SETTING = "force_resizable_activities"

    fun ensure(context: Context, rootAvailable: () -> Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_APPLIED, false)) return true
        if (!rootAvailable()) return false
        return runCatching {
            BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable).use { exec ->
                val current = exec.execute("settings get global $SETTING", timeoutMillis = 4_000L)
                val original = current.stdout.trim().ifBlank { "null" }
                if (original == "1") {
                    // 用户本来就开着（开发者选项），不需要接管，也不要在销毁时改回去。
                    AndroidAgentLogger.info("Size compat bypass already enabled by user")
                    return true
                }
                val put = exec.execute("settings put global $SETTING 1", timeoutMillis = 4_000L)
                if (put.exitCode == 0) {
                    prefs.edit()
                        .putString(KEY_ORIGINAL, original)
                        .putBoolean(KEY_APPLIED, true)
                        .apply()
                    AndroidAgentLogger.info("Size compat bypass enabled original=$original")
                    true
                } else {
                    AndroidAgentLogger.warn("Size compat bypass enable failed exit=${put.exitCode}")
                    false
                }
            }
        }.getOrDefault(false)
    }

    fun restore(context: Context, rootAvailable: () -> Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_APPLIED, false)) return
        val original = prefs.getString(KEY_ORIGINAL, "null") ?: "null"
        val target = if (original == "1") "1" else "0"
        prefs.edit().putBoolean(KEY_APPLIED, false).apply()
        if (!rootAvailable()) return
        runCatching {
            BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable).use { exec ->
                val put = exec.execute("settings put global $SETTING $target", timeoutMillis = 4_000L)
                AndroidAgentLogger.info("Size compat bypass restored target=$target exit=${put.exitCode}")
            }
        }
    }
}
