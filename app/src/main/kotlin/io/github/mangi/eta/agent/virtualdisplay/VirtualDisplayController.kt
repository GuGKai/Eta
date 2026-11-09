package io.github.mangi.eta.agent.virtualdisplay

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import io.github.mangi.eta.agent.device.DeviceToolContract
import io.github.mangi.eta.agent.device.RootAccess
import io.github.mangi.eta.agent.overlay.StageCanvasHeight
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
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
    private val mutableSnapshots = MutableStateFlow(VirtualDisplaySnapshot.Inactive)
    val snapshots: StateFlow<VirtualDisplaySnapshot> = mutableSnapshots.asStateFlow()

    /** 顶部胶囊是否显示虚拟屏微缩镜/装裱视窗：由执行通知的第三个按钮开关。 */
    private val mutableCapsuleVisible = MutableStateFlow(true)
    val capsuleVisible: StateFlow<Boolean> = mutableCapsuleVisible.asStateFlow()

    private val frameSequenceGenerator = AtomicLong(0L)
    private val touchSequenceGenerator = AtomicLong(0L)

    @Volatile
    private var highFrequencyPreview: Boolean = false

    private var frameThread: HandlerThread? = null
    private var frameHandler: Handler? = null
    private var imageReader: ImageReader? = null
    private var inProcessVirtualDisplay: VirtualDisplay? = null
    private var rootBridgeProcess: Process? = null
    private var processSupervisor: ShellProcessSupervisor? = null

    private var activeDisplayId: Int = Display.DEFAULT_DISPLAY
    private var displayWidth: Int = 1080
    private var displayHeight: Int = 2400
    private var displayDensityDpi: Int = 420
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
        activeDisplayId.takeIf { CanvasRoutingPolicy.isVirtualDisplay(it) } ?: Display.DEFAULT_DISPLAY
    }

    /**
     * 确保受信任虚拟屏已就绪并返回其 displayId；不可用时返回 null，由调用方平滑回退至物理主屏。
     */
    fun ensureVirtualDisplay(
        context: Context,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ): Int? {
        synchronized(lock) {
            if (CanvasRoutingPolicy.isVirtualDisplay(activeDisplayId) && imageReader != null) {
                val existingId = activeDisplayId
                if (!mutableSnapshots.value.userControlling) {
                    hideImeOnVirtualDisplay(existingId, rootAvailable)
                }
                return existingId
            }
        }
        val appContext = context.applicationContext
        activeContext = appContext
        // 建屏前先屏蔽系统的 size-compat（letterbox）判定：
        // 打开“强制可调整大小”后，任务迁到主屏会按目标 display 的 app bounds 直接重排，
        // 而不是被缩放塞进 letterbox 并弹出“全屏显示”兼容提示。
        SizeCompatBypass.ensure(appContext, rootAvailable)
        val (width, height, densityDpi) = resolvePhysicalDisplayMetrics(appContext)
        synchronized(lock) {
            if (CanvasRoutingPolicy.isVirtualDisplay(activeDisplayId) && imageReader != null) {
                return activeDisplayId
            }
            releaseLocked(appContext, rootAvailable)

            val thread = HandlerThread("eta-virtual-display-frame").apply { start() }
            val handler = Handler(thread.looper)
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader.setOnImageAvailableListener(::onFrameAvailable, handler)

            frameThread = thread
            frameHandler = handler
            imageReader = reader
            displayWidth = width
            displayHeight = height
            displayDensityDpi = densityDpi

            val createdInProcess = tryCreateInProcessVirtualDisplay(
                appContext = appContext,
                surface = reader.surface,
                width = width,
                height = height,
                densityDpi = densityDpi,
            )
            if (createdInProcess != null) {
                val id = createdInProcess.display?.displayId ?: Display.INVALID_DISPLAY
                if (CanvasRoutingPolicy.isVirtualDisplay(id)) {
                    inProcessVirtualDisplay = createdInProcess
                    activeDisplayId = id
                    mutableSnapshots.value = VirtualDisplaySnapshot(
                        active = true,
                        displayId = id,
                        width = width,
                        height = height,
                    )
                    AndroidAgentLogger.info("Virtual display created in-process: displayId=$id size=${width}x$height")
                    hideImeOnVirtualDisplay(id, rootAvailable)
                    return id
                }
                runCatching { createdInProcess.release() }
            }

            if (rootAvailable()) {
                val rootId = tryCreateRootBridgedVirtualDisplay(
                    appContext = appContext,
                    surface = reader.surface,
                    width = width,
                    height = height,
                    densityDpi = densityDpi,
                    rootAvailable = rootAvailable,
                )
                if (rootId != null && CanvasRoutingPolicy.isVirtualDisplay(rootId)) {
                    activeDisplayId = rootId
                    mutableSnapshots.value = VirtualDisplaySnapshot(
                        active = true,
                        displayId = rootId,
                        width = width,
                        height = height,
                    )
                    AndroidAgentLogger.info("Virtual display created via root bridge: displayId=$rootId size=${width}x$height")
                    hideImeOnVirtualDisplay(rootId, rootAvailable)
                    return rootId
                }
            }

            releaseLocked(appContext, rootAvailable)
            return null
        }
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
    ): Int? {
        // 若上一轮仍停留在全屏外置状态，先把原任务搬回虚拟屏，再启动新应用。
        restoreExternalizedToVirtualDisplay(rootAvailable)
        val displayId = ensureVirtualDisplay(context, rootAvailable) ?: return null
        val appContext = context.applicationContext
        mutePackageAudioFocusIfPossible(appContext, packageName, rootAvailable)

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

        if (!startedInProcess || rootAvailable()) {
            // Root 下补发一次指定 displayId 的 am start，确保后台启动限制或跨屏栈迁移生效
            if (rootAvailable()) {
                val userId = DeviceToolContract.appUserId(appContext)
                val component = launchIntent.component?.flattenToShortString()
                val cmd = if (!component.isNullOrBlank()) {
                    "am start --user $userId --display $displayId -n ${DeviceToolContract.quote(component)}"
                } else {
                    "monkey -p ${DeviceToolContract.quote(packageName)} -c android.intent.category.LAUNCHER 1"
                }
                if (!startedInProcess) {
                    val exec = BoundedRootCommandExecutor(AndroidAgentLogger, rootAvailable = rootAvailable)
                    try {
                        val res = exec.execute(cmd, timeoutMillis = 6_000L)
                        if (res.exitCode != 0 && !startedInProcess) return null
                    } finally {
                        exec.close()
                    }
                }
            } else if (!startedInProcess) {
                return null
            }
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
    ): Int? {
        // 若上一轮仍停留在全屏外置状态，先把原任务搬回虚拟屏，再打开新 URI。
        restoreExternalizedToVirtualDisplay(rootAvailable)
        val displayId = ensureVirtualDisplay(context, rootAvailable) ?: return null
        val appContext = context.applicationContext
        val started = runCatching {
            val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            appContext.startActivity(launch, options.toBundle())
            true
        }.getOrDefault(false)

        if (!started && rootAvailable()) {
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
        val moved = moveVirtualTaskToDisplay(
            context = appContext,
            sourceDisplayId = snapshot.displayId,
            targetDisplayId = Display.DEFAULT_DISPLAY,
            packageName = snapshot.packageName,
            rootAvailable = rootAvailable,
        )

        if (!moved) {
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
            if (!current.active) return moved
            mutableSnapshots.value = current.copy(materializeFading = true)
        }
        sleepQuietly(MaterializeFadeOutMs + MaterializeFadeTailMs)

        // C 方案：全屏后不销毁虚拟屏，只把任务挪到物理主屏；
        // 用户离开该应用时由主屏窗口事件把同一个 RootTask 搬回，胶囊小窗立即恢复。
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active) return moved
            mutableSnapshots.value = current.copy(
                userControlling = false,
                materializingToMain = false,
                materializeFading = false,
                materializeFrom = null,
                externalizedToMain = true,
            )
            highFrequencyPreview = false
            restoreFailureCount = 0
        }
        return moved
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return fallback
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
                                    releaseLocked(activeContext, { RootAccess.isGranted })
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
        val moved = moveVirtualTaskToDisplay(
            context = appContext,
            sourceDisplayId = Display.DEFAULT_DISPLAY,
            targetDisplayId = snapshot.displayId,
            packageName = snapshot.packageName,
            rootAvailable = rootAvailable,
        )
        if (!moved) return false
        synchronized(lock) {
            val current = mutableSnapshots.value
            if (!current.active || !current.externalizedToMain) return moved
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
        return moved
    }

    /**
     * 提取虚拟屏最新一帧的完整 Bitmap 副本（供 observe_screen 使用，调用方负责 recycle）。
     */
    fun captureScreenshotBitmap(waitUpToMs: Long = 450L): Bitmap? {
        val deadline = SystemClock.elapsedRealtime() + waitUpToMs.coerceAtLeast(0L)
        while (true) {
            synchronized(lock) {
                val bmp = latestFullBitmap
                if (bmp != null && !bmp.isRecycled) {
                    return runCatching { bmp.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                }
                if (!CanvasRoutingPolicy.isVirtualDisplay(activeDisplayId)) return null
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

    fun release(
        context: Context? = null,
        rootAvailable: () -> Boolean = { RootAccess.isGranted },
    ) {
        synchronized(lock) {
            releaseLocked(context, rootAvailable)
        }
        // 虚拟屏彻底销毁后把“强制可调整大小”恢复成接管前的值，避免污染全局。
        context?.let { SizeCompatBypass.restore(it.applicationContext, rootAvailable) }
    }

    private fun releaseLocked(
        context: Context?,
        rootAvailable: () -> Boolean,
    ) {
        highFrequencyPreview = false
        activeDisplayId = Display.DEFAULT_DISPLAY
        activeContext = null
        lastMainDisplayForegroundPackage = ""
        mutableSnapshots.value = VirtualDisplaySnapshot.Inactive
        appliedImePolicyDisplayId = Display.INVALID_DISPLAY
        appliedImePolicy = -1

        if (context != null && mutedPackages.isNotEmpty()) {
            restoreMutedPackages(context.applicationContext, rootAvailable)
        } else {
            mutedPackages.clear()
        }

        inProcessVirtualDisplay?.let { vd ->
            runCatching { vd.release() }
        }
        inProcessVirtualDisplay = null

        val bridge = rootBridgeProcess
        val supervisor = processSupervisor
        rootBridgeProcess = null
        processSupervisor = null
        if (bridge != null) {
            thread(name = "eta-vd-bridge-cleanup", isDaemon = true) {
                runCatching {
                    bridge.outputStream.write("release\n".toByteArray(Charsets.UTF_8))
                    bridge.outputStream.flush()
                    bridge.outputStream.close()
                }
                if (supervisor != null) {
                    supervisor.beginClosing()
                    if (bridge.isAlive) supervisor.terminateAndReap(bridge) else supervisor.reapProcess(bridge)
                    supervisor.unregisterProcess(bridge)
                } else {
                    runCatching { bridge.destroyForcibly() }
                }
            }
        }

        imageReader?.let { reader ->
            runCatching { reader.setOnImageAvailableListener(null, null) }
            runCatching { reader.close() }
        }
        imageReader = null

        frameThread?.let { thread ->
            runCatching { thread.quitSafely() }
        }
        frameThread = null
        frameHandler = null

        latestFullBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        latestFullBitmap = null
        lastUiFrameAtElapsedMs = 0L
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
                if (!CanvasRoutingPolicy.isVirtualDisplay(activeDisplayId)) {
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
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null
        buffer.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        if (paddedWidth < width) return null

        val rawBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == width) {
            rawBitmap
        } else {
            val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("Landroid/hardware/display/") }
        }
        val dm = appContext.getSystemService(DisplayManager::class.java) ?: return@runCatching null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val builder = VirtualDisplayConfig.Builder(
                CanvasRoutingPolicy.VIRTUAL_DISPLAY_NAME,
                width,
                height,
                densityDpi,
            )
                .setSurface(surface)
                .setFlags(CanvasRoutingPolicy.TRUSTED_INTERACTIVE_FLAGS)
            val config = builder.build()
            AndroidAgentLogger.info("[vd] in-process 虚拟屏创建：size=${width}x$height")
            dm.createVirtualDisplay(config)
        } else {
            dm.createVirtualDisplay(
                CanvasRoutingPolicy.VIRTUAL_DISPLAY_NAME,
                width,
                height,
                densityDpi,
                surface,
                CanvasRoutingPolicy.TRUSTED_INTERACTIVE_FLAGS,
            )
        }
    }.getOrNull()

    /** 破壁过渡：位图放大到全屏所需时间，与 overlay 的弹簧时长同量级。 */
    private const val MaterializeExpandMs = 260L

    /** 破壁过渡：迁移任务后给主屏窗口留的首帧时间，之后才开始淡出位图。 */
    private const val MaterializeMainFrameWaitMs = 200L

    /** 破壁过渡：位图淡出时长，必须与 VirtualDisplayMaterializeOverlay 的 tween 一致。 */
    private const val MaterializeFadeOutMs = 220L

    /** 破壁过渡：淡出结束到下线的余量，避免最后一帧硬切。 */
    private const val MaterializeFadeTailMs = 40L

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

    private fun tryCreateRootBridgedVirtualDisplay(
        appContext: Context,
        surface: android.view.Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        rootAvailable: () -> Boolean,
    ): Int? {
        val token = VirtualDisplaySurfaceRegistry.register(surface)
        val supervisor = ShellProcessSupervisor(rootAvailable = rootAvailable)
        val apkPath = appContext.applicationInfo.sourceDir
        val cmd = "CLASSPATH=${DeviceToolContract.quote(apkPath)} app_process /system/bin " +
            "io.github.mangi.eta.agent.virtualdisplay.VirtualDisplayCommandMain"
        val process = supervisor.startShellProcess("root", cmd, mergeStderr = true)
        if (process == null) {
            VirtualDisplaySurfaceRegistry.unregister(token)
            return null
        }

        val readyLatch = CountDownLatch(1)
        var resolvedDisplayId: Int? = null
        val payload = JSONObject()
            .put("action", "hold_display")
            .put("user_id", DeviceToolContract.appUserId(appContext))
            .put("surface_token", token)
            .put("width", width)
            .put("height", height)
            .put("density_dpi", densityDpi)
            .toString() + "\n"

        thread(name = "eta-vd-bridge-stdout", isDaemon = true) {
            runCatching {
                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith(VirtualDisplayCommandMain.READY_PREFIX)) {
                            resolvedDisplayId = line
                                .removePrefix(VirtualDisplayCommandMain.READY_PREFIX)
                                .trim()
                                .toIntOrNull()
                            readyLatch.countDown()
                        } else if (line.startsWith(VirtualDisplayCommandMain.RESULT_PREFIX)) {
                            readyLatch.countDown()
                        }
                    }
                }
            }
            readyLatch.countDown()
        }

        val written = runCatching {
            process.outputStream.write(payload.toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
            true
        }.getOrDefault(false)

        if (!written) {
            VirtualDisplaySurfaceRegistry.unregister(token)
            supervisor.beginClosing()
            supervisor.terminateAndReap(process)
            supervisor.unregisterProcess(process)
            return null
        }

        val awaited = runCatching { readyLatch.await(4, TimeUnit.SECONDS) }.getOrDefault(false)
        val id = resolvedDisplayId
        if (!awaited || id == null || !CanvasRoutingPolicy.isVirtualDisplay(id)) {
            VirtualDisplaySurfaceRegistry.unregister(token)
            supervisor.beginClosing()
            if (process.isAlive) supervisor.terminateAndReap(process) else supervisor.reapProcess(process)
            supervisor.unregisterProcess(process)
            return null
        }

        rootBridgeProcess = process
        processSupervisor = supervisor
        return id
    }

    private fun moveVirtualTaskToDisplay(
        context: Context,
        sourceDisplayId: Int,
        targetDisplayId: Int,
        packageName: String,
        rootAvailable: () -> Boolean,
    ): Boolean {
        // 1. 尝试进程内通过 system_server Hook 放行的 moveRootTaskToDisplay 迁移
        val movedInProcess = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityTaskManager", "Landroid/app/IActivityTaskManager")
            }
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val service = atmClass.getMethod("getService").invoke(null) ?: return@runCatching false
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
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (movedInProcess) return true

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
                val movedByRoot = try {
                    proc.outputStream.write(req.toByteArray(Charsets.UTF_8))
                    proc.outputStream.flush()
                    proc.outputStream.close()
                    val out = proc.inputStream.bufferedReader().readText()
                    proc.waitFor(4, TimeUnit.SECONDS)
                    out.contains("\"ok\":true")
                } catch (_: Throwable) {
                    false
                } finally {
                    supervisor.beginClosing()
                    if (proc.isAlive) supervisor.terminateAndReap(proc) else supervisor.reapProcess(proc)
                    supervisor.unregisterProcess(proc)
                }
                if (movedByRoot) return true
            }
        }

        // 3. 兜底：以 SINGLE_TOP + NEW_TASK 在目标显示上唤起该应用
        if (packageName.isNotBlank()) {
            return runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    ?: return@runCatching false
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
                context.startActivity(intent, options.toBundle())
                true
            }.getOrDefault(false)
        }
        return false
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
