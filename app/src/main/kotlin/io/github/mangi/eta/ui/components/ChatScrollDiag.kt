package io.github.mangi.eta.ui.components

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * 会话正文「自动跟顶」诊断记录器。
 *
 * 只服务于开发期定位：把每一轮回答的停靠过程按帧记录下来，并给出一句结论
 * （跟顶成功 / 过冲后回拉 / 一路跟到底 / 中途放弃 / 收尾落点错）。
 * 日志写在应用私有目录 scroll-diag/ 下，开发侧用 root 直接读取，不需要用户导出。
 *
 * 关闭时所有入口立即返回，不分配、不写盘、不影响滚动时序。
 */
internal object ChatScrollDiag {

    /** 诊断开关。定版发版前必须置 false。 */
    const val ENABLED = true

    private const val TAG = "EtaScrollDiag"

    /** 保留的日志文件数上限：超出时删最旧的。 */
    private const val MAX_FILES = 8

    /** 单文件行数上限：超出后停止写入，避免长时间使用把存储写满。 */
    private const val MAX_LINES_PER_FILE = 30000


    /** 停靠残差小于这个值视为「已经贴住首行」。 */
    private const val DOCK_TOLERANCE_PX = 3f

    /** 首行越过目标线超过这个像素就判为过冲（约 13dp @3x）。 */
    private const val OVERSHOOT_THRESHOLD_PX = 40f

    /**
     * 残差大于这个值才用它判定「来向」。比 [DOCK_TOLERANCE_PX] 大得多：
     * 收口阶段残差本来就在 ±几像素抖，用它判方向会来回跳；也不能太大，
     * 短距离停靠（残差本来就只有几十像素）也要求能判出方向。
     */
    private const val DOCK_APPROACH_PX = 24f

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "eta-scroll-diag").apply { isDaemon = true }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val lock = Any()
    private val buffer = StringBuilder()
    private var writer: FileWriter? = null
    private var lineCount = 0
    private var installed = false

    /** 当前轮次的状态累积。 */
    private var roundOpen = false
    private var roundAnswerId: String? = null
    private var roundStartedAt = 0L
    private var dockAttempts = 0
    private var dockFrames = 0
    private var maxOvershootPx = 0f
    /** 本轮第一次停靠帧的残差：那是「离落点还有多远」的起点距离，不是过冲，单独记。 */
    private var firstDockDeltaPx: Float? = null
    /** 起点距离那一帧是不是真实文本测量；false 表示是退化基准（条目顶边）。 */
    private var firstDockDeltaFromText = false
    /** 上一帧的测量基准，用来识别 text / 退化之间的切换；null 表示本轮还没有帧。 */
    private var lastDockBasis: Boolean? = null
    /**
     * 本轮的「来向」：+1 表示从下方靠近落点（残差明显为正，首行还在落点下面），
     * -1 表示从上方靠近（残差为负，首行已经在落点上面），0 表示还没拉开过距离。
     * 过冲只在「越过落点到相对的一侧」时才计，否则从远处跳过来的起点距离会被当成冲过头。
     */
    private var overshootApproachSign = 0
    private var reachedTop = false
    private var sawDockSuccess = false
    private var snapOk = false
    private var snapRounds = 0
    private var snapExitReason: String? = null
    private var snapExitFirstLineY: Float? = null
    private var snapExitItemOffset: Int? = null
    private var snapExitDelta: Float? = null
    private var sawDockFailure: String? = null
    private var tailSettleBranch: String? = null
    private var finalDeltaPx: Float? = null
    private var interruptedByUser = false
    private var lastInterruptAt = 0L
    private var dockSkipCount = 0
    private var guardCancelCount = 0
    private var frozenByJitter = false
    private var keyboardLiftDuringRound = false

    val enabled: Boolean get() = ENABLED

    /** 由 EtaApp 在进程启动时调用；dir 一般是 filesDir/scroll-diag。 */
    fun install(dir: File) {
        if (!ENABLED || installed) return
        installed = true
        executor.execute {
            runCatching {
                dir.mkdirs()
                pruneOldFiles(dir)
                val name = "scroll-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"
                val file = File(dir, name)
                synchronized(lock) {
                    writer = FileWriter(file, true)
                    lineCount = 0
                }
                append("==== Eta 滚动诊断开始 " + name + " ====")
            }.onFailure { error ->
                Log.w(TAG, "install failed: " + error.javaClass.simpleName)
            }
        }
    }

    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles { file -> file.isFile && file.name.startsWith("scroll-") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_FILES).forEach { stale -> stale.delete() }
    }

    /**
     * 一轮回答开始。同一 answerId 已在进行中时不重置累积状态：
     * 停靠 effect 会因时间线末尾条目变化而重启，重启不该把前面测到的过冲等证据清掉。
     */
    fun beginRound(answerId: String?, streaming: Boolean, topInsetPx: Int, targetY: Int) {
        if (!ENABLED) return
        val alreadyOpen = synchronized(lock) { roundOpen && roundAnswerId != null && roundAnswerId == answerId }
        if (alreadyOpen) return
        synchronized(lock) {
            roundOpen = true
            roundAnswerId = answerId
            roundStartedAt = System.currentTimeMillis()
            dockAttempts = 0
            dockFrames = 0
            maxOvershootPx = 0f
            firstDockDeltaPx = null
            firstDockDeltaFromText = false
            lastDockBasis = null
            overshootApproachSign = 0
            reachedTop = false
            sawDockSuccess = false
            snapOk = false
            snapRounds = 0
            snapExitReason = null
            snapExitFirstLineY = null
            snapExitItemOffset = null
            snapExitDelta = null
            sawDockFailure = null
            tailSettleBranch = null
            finalDeltaPx = null
            interruptedByUser = false
            // lastInterruptAt 刻意不在这里清零：它记录「最后一次用户接管」的时刻，必须跨轮保留。
            // 以前每轮开头清 0，而 DOCK_SKIP 恰恰紧跟在 ROUND_BEGIN 之后，于是
            // sinceLastInterruptMs 永远读到 -1，这条最关键的量化线索一直是空的。
            dockSkipCount = 0
            guardCancelCount = 0
            frozenByJitter = false
            keyboardLiftDuringRound = false
        }
        append(
            "ROUND_BEGIN id=" + answerId + " streaming=" + streaming +
                " topInset=" + topInsetPx + " target=" + targetY,
        )
    }

    /** 通用事件：状态位变化、分支走向、守卫放行等。 */
    fun event(name: String, detail: String = "") {
        if (!ENABLED) return
        append(name + if (detail.isEmpty()) "" else " " + detail)
    }

    /** 停靠尝试开始（外层重试的一次）。 */
    fun dockAttemptStart(attempt: Int, itemIndex: Int) {
        if (!ENABLED) return
        synchronized(lock) { dockAttempts = attempt }
        append("DOCK_ATTEMPT n=" + attempt + " itemIndex=" + itemIndex)
    }

    /** 守卫结论：首行是否已经逼近落点。 */
    fun reachTopResult(reached: Boolean, waitedFrames: Int, firstLineY: Float?) {
        if (!ENABLED) return
        synchronized(lock) { if (reached) reachedTop = true }
        append(
            "REACH_TOP reached=" + reached + " waited=" + waitedFrames +
                " firstLineY=" + (firstLineY?.toInt()?.toString() ?: "null"),
        )
    }

    /**
     * 守卫等待期间的周期性采样（纯观测）：把「首行卡在 400 / 卡在 threshold 上方不动」
     * 这类现象量化出来，用于区分「正文没长高」与「跟底把首行推出去了」。
     *
     * @param firstLineY 该帧首行字顶（拿不到可信测量时为 null）
     * @param itemOffset 该帧条目实测顶边
     * @param canForward 该帧是否还能往下滚
     * @param atBottom 该帧是否已经贴在底部
     */
    fun guardSample(
        waited: Int,
        firstLineY: Float?,
        itemOffset: Int?,
        canForward: Boolean,
        atBottom: Boolean,
        streaming: Boolean,
    ) {
        if (!ENABLED) return
        // 每 20 帧记一次，避免刷屏；第 0 帧必记，便于看起点。
        if (waited % 20 != 0) return
        append(
            "GUARD_SAMPLE waited=" + waited +
                " firstLineY=" + (firstLineY?.toInt()?.toString() ?: "null") +
                " itemOffset=" + (itemOffset?.toString() ?: "null") +
                " fwd=" + canForward + " atBottom=" + atBottom +
                " streaming=" + streaming,
        )
    }

    /**
     * 停靠闭环的一帧。
     *
     * @param delta 首行字顶与落点的残差：正数表示首行还在落点下面（要继续往前滚），
     *   负数表示首行已经在落点上面。是否算过冲取决于来向，不能只看符号。
     * @param fromText 首行是否来自真实文本测量；false 表示退化用条目顶边。
     */
    fun dockFrame(
        frame: Int,
        delta: Float,
        firstLineY: Float?,
        itemOffset: Int,
        canForward: Boolean,
        canBackward: Boolean,
        fromText: Boolean,
        streaming: Boolean,
    ) {
        if (!ENABLED) return
        synchronized(lock) {
            dockFrames++
            // 测量基准不能混着比：text=true 量的是首行字顶，false 退化量条目顶边，
            // 两者差着条目内边距（实测常见 45px）。基准一换，上一帧的「来向」就作废，
            // 本帧也不参与判定 —— 否则 32 → -13 这种基准切换会被当成越过落点。
            val basisChanged = lastDockBasis != null && lastDockBasis != fromText
            lastDockBasis = fromText
            if (basisChanged) overshootApproachSign = 0
            // 过冲 = 「越过落点、跑到来向的对侧」的最大距离，判据必须分方向：
            // 1) 残余几千像素的一帧是从远处过来时的起点距离，不是冲过头 —— 以前不分方向
            //    一律计入负残差，把一次 5619px 的跳转误报成「两屏半的过冲」；
            // 2) 只认「先出现正残差」的写法同样不对：长回答停在底部时首行本来就在视口
            //    上方，残差一开始是负的，那种最常见的停靠永远判不出过冲（恒报 0）。
            // 所以先记来向，再只看对侧：从下方靠近只会往负残差冲过头，反之同理。
            if (!basisChanged) {
                if (overshootApproachSign > 0 && delta < 0f) {
                    if (-delta > maxOvershootPx) maxOvershootPx = -delta
                } else if (overshootApproachSign < 0 && delta > 0f) {
                    if (delta > maxOvershootPx) maxOvershootPx = delta
                }
                // 用本帧的残差更新来向（放在记过冲之后，避免本帧就把判据的方向改掉）。
                if (delta > DOCK_APPROACH_PX) {
                    overshootApproachSign = 1
                } else if (delta < -DOCK_APPROACH_PX) {
                    overshootApproachSign = -1
                }
            }
            finalDeltaPx = delta
            // 起点距离单独记：优先取真实文本测量那一帧（退化基准的差值偏一个内边距）。
            if (firstDockDeltaPx == null || (fromText && !firstDockDeltaFromText)) {
                firstDockDeltaPx = delta
                firstDockDeltaFromText = fromText
            }
        }
        append(
            "DOCK_FRAME f=" + frame + " delta=" + delta.toInt() +
                " firstLineY=" + (firstLineY?.toInt()?.toString() ?: "null") +
                " itemOffset=" + itemOffset +
                " fwd=" + canForward + " back=" + canBackward +
                " text=" + fromText + " streaming=" + streaming,
        )
    }

    /** 停靠闭环结束。success=false 时 reason 说明是哪条守卫放弃的。 */
    fun dockResult(success: Boolean, reason: String, frames: Int) {
        if (!ENABLED) return
        synchronized(lock) {
            if (success) sawDockSuccess = true else if (sawDockFailure == null) sawDockFailure = reason
        }
        append("DOCK_RESULT success=" + success + " reason=" + reason + " frames=" + frames)
    }

    /** 历史会话首帧落点（snapAnswerToTop）的结果。 */
    fun snapResult(ok: Boolean, itemIndex: Int) {
        if (!ENABLED) return
        synchronized(lock) { snapOk = ok }
        append("SNAP_RESULT ok=" + ok + " itemIndex=" + itemIndex)
    }

    /**
     * snapAnswerToTop 内部某一帧仍在等待结构落点稳定时的原因（纯观测）。
     *
     * @param label no_item = 条目还没进可视区；settling = 条目在但位置仍在变化
     * @param rawFirstLineY 未过滤的原始首行字顶（仅诊断，不参与成功判定）
     * @param ownerMatched 首行归属是否就是本轮 answerId
     * @param itemOffset 该帧条目实测顶边
     */
    fun snapProbe(
        label: String = "no_measure",
        rounds: Int,
        rawFirstLineY: Float?,
        ownerMatched: Boolean,
        itemOffset: Int?,
    ) {
        if (!ENABLED) return
        synchronized(lock) { snapRounds = rounds }
        append(
            "SNAP_PROBE what=" + label + " rounds=" + rounds +
                " rawFirstLineY=" + (rawFirstLineY?.toInt()?.toString() ?: "null") +
                " ownerMatched=" + ownerMatched +
                " itemOffset=" + (itemOffset?.toString() ?: "null"),
        )
    }

    /**
     * snapAnswerToTop 的出口原因（纯观测）。
     *
     * @param reason structural_stable / structural_timeout / item_missing / dragging / tail_changed
     * @param rounds 循环实际跑了几轮
     * @param firstLineY 出口这一帧的首行字顶（拿不到可信测量时为 null）
     * @param itemOffset 出口这一帧条目顶边
     * @param delta 出口这一帧的残差（firstLineY - target）
     */
    fun snapExit(
        reason: String,
        rounds: Int,
        firstLineY: Float?,
        itemOffset: Int?,
        delta: Float?,
    ) {
        if (!ENABLED) return
        synchronized(lock) {
            snapRounds = rounds
            snapExitReason = reason
            snapExitFirstLineY = firstLineY
            snapExitItemOffset = itemOffset
            snapExitDelta = delta
        }
        append(
            "SNAP_EXIT reason=" + reason + " rounds=" + rounds +
                " firstLineY=" + (firstLineY?.toInt()?.toString() ?: "null") +
                " itemOffset=" + (itemOffset?.toString() ?: "null") +
                " delta=" + (delta?.toInt()?.toString() ?: "null"),
        )
    }

    /** 收尾落点判定的分支。 */
    fun tailSettle(branch: String, detail: String = "") {
        if (!ENABLED) return
        synchronized(lock) { tailSettleBranch = branch }
        append("TAIL_SETTLE branch=" + branch + if (detail.isEmpty()) "" else " " + detail)
    }

    /** 用户接管（拖动 / 键盘抬升置位）。 */
    fun userInterrupted(by: String) {
        if (!ENABLED) return
        synchronized(lock) {
            interruptedByUser = true
            lastInterruptAt = System.currentTimeMillis()
            if (by == "keyboard_lift") {
                // 键盘 inset 逐帧变化会让这里每帧触发一次；只记第一次，避免刷屏。
                if (keyboardLiftDuringRound) return
                keyboardLiftDuringRound = true
            }
        }
        append("USER_INTERRUPTED by=" + by)
    }

    /**
     * 停靠入口因为上一轮遗留的 dockUserInterrupted 直接跳过（纯观测）。
     *
     * @param msSinceLastInterrupt 距离最近一次用户接管过去了多少毫秒；-1 表示本轮没记录到接管
     */
    fun dockSkipped(reason: String) {
        if (!ENABLED) return
        val since = synchronized(lock) {
            dockSkipCount++
            if (lastInterruptAt == 0L) -1L else System.currentTimeMillis() - lastInterruptAt
        }
        append("DOCK_SKIP reason=" + reason + " sinceLastInterruptMs=" + since +
            " skipN=" + synchronized(lock) { dockSkipCount })
    }

    /** 守卫被协程取消（不是判定失败）导致的退出（纯观测）。 */
    fun guardCancelled(waitedFrames: Int, lastFirstLineY: Float?) {
        if (!ENABLED) return
        synchronized(lock) { guardCancelCount++ }
        append(
            "GUARD_CANCELLED waited=" + waitedFrames +
                " lastFirstLineY=" + (lastFirstLineY?.toInt()?.toString() ?: "null"),
        )
    }

    /** 抖动熔断。 */
    fun jitterFrozen(index: Int, offset: Int) {
        if (!ENABLED) return
        synchronized(lock) { frozenByJitter = true }
        append("JITTER_FROZEN index=" + index + " offset=" + offset)
    }

    /**
     * 一轮结束：给出结论并汇总本轮全部关键事实。
     * verdict 由累积状态推导，不依赖调用方传值，避免各处口径不一致。
     */
    fun endRound(answerId: String?, note: String = "") {
        if (!ENABLED) return
        // 幂等：同一轮可能从「流式结束」与「收尾判定」两处到达，只认第一次。
        if (synchronized(lock) { !roundOpen }) return
        val detail: String
        synchronized(lock) {
            val delta = finalDeltaPx
            val elapsed = System.currentTimeMillis() - roundStartedAt
            val verdict = when {
                snapOk && (snapExitReason == "delta_ok" || snapExitReason == "structural_stable") -> "OK_SNAP"
                snapOk -> "SNAP_FALLBACK_" + (snapExitReason ?: "unknown")
                roundAnswerId == null -> "NO_ANSWER"
                sawDockSuccess && delta != null && abs(delta) <= DOCK_TOLERANCE_PX &&
                    maxOvershootPx <= OVERSHOOT_THRESHOLD_PX -> "OK_DOCKED"
                sawDockSuccess && delta != null && abs(delta) <= DOCK_TOLERANCE_PX -> "OK_BUT_OVERSHOOT"
                sawDockSuccess -> "DOCKED_NOT_AT_TARGET"
                sawDockFailure != null -> "DOCK_FAILED_" + sawDockFailure
                reachedTop && !sawDockSuccess -> "REACHED_BUT_NOT_DOCKED"
                dockAttempts > 0 -> "DOCK_ABANDONED"
                else -> "NEVER_DOCKED"
            }
            detail = "verdict=" + verdict +
                " id=" + roundAnswerId +
                " attempts=" + dockAttempts +
                " frames=" + dockFrames +
                " reachedTop=" + reachedTop +
                " overshootMax=" + maxOvershootPx.toInt() +
                " osStart=" + (firstDockDeltaPx?.toInt()?.toString() ?: "null") +
                " osFrom=" + when (overshootApproachSign) {
                    1 -> "below"
                    -1 -> "above"
                    else -> "none"
                } +
                " finalDelta=" + (delta?.toInt()?.toString() ?: "null") +
                " tailSettle=" + (tailSettleBranch ?: "-") +
                " userInterrupt=" + interruptedByUser +
                " keyboardLift=" + keyboardLiftDuringRound +
                " jitter=" + frozenByJitter +
                " dockSkipN=" + dockSkipCount +
                " guardCancelN=" + guardCancelCount +
                " ms=" + elapsed +
                if (snapExitReason == null) "" else
                    " snapExit=" + snapExitReason +
                        " snapRounds=" + snapRounds +
                        " snapFirstLineY=" + (snapExitFirstLineY?.toInt()?.toString() ?: "null") +
                        " snapItemOffset=" + (snapExitItemOffset?.toString() ?: "null") +
                        " snapDelta=" + (snapExitDelta?.toInt()?.toString() ?: "null") +
                if (note.isEmpty()) "" else " note=" + note
            roundOpen = false
            roundAnswerId = null
        }
        append("ROUND_END " + detail)
        flushNow()
    }

    private fun append(line: String) {
        if (!ENABLED) return
        val stamp = timeFormat.format(Date())
        var toWrite: String? = null
        synchronized(lock) {
            if (lineCount >= MAX_LINES_PER_FILE) return
            lineCount++
            buffer.append(stamp).append(' ').append(line).append('\n')
            // 逐条立即落盘：诊断数据最怕进程被回收时还在内存里，
            // 之前攒到 8KB 才写，中途一变就整轮丢失（实测出现过 0 字节文件）。
            toWrite = buffer.toString()
            buffer.setLength(0)
        }
        toWrite?.let(::submitWrite)
    }

    private fun flushNow() {
        if (!ENABLED) return
        val toWrite: String?
        synchronized(lock) {
            toWrite = if (buffer.isEmpty()) null else buffer.toString().also { buffer.setLength(0) }
        }
        toWrite?.let(::submitWrite)
    }

    private fun submitWrite(chunk: String) {
        executor.execute {
            runCatching {
                val active = synchronized(lock) { writer }
                active?.apply {
                    write(chunk)
                    flush()
                }
            }.onFailure { error ->
                Log.w(TAG, "write failed: " + error.javaClass.simpleName)
            }
        }
    }
}
