package io.github.mangi.eta.agent.runtime

import android.os.Handler
import android.os.Looper

/**
 * 进程内执行进度快照。
 *
 * 前台执行通知（Android 16 的流体云实时活动）读取它渲染任务名与步骤进度；agent 运行时与执行服务
 * 位于同一进程，因此这里只做进程内可见的状态，不做任何跨进程同步或持久化。
 */
internal object ExecutionLiveProgress {

    /** 任务名在通知标题里的最大长度，超出用省略号收起。 */
    private const val TASK_NAME_LIMIT = 30

    private val whitespace = Regex("\\s+")

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var changeListener: (() -> Unit)? = null

    @Volatile
    private var taskName = ""

    @Volatile
    private var completedSteps = 0

    @Volatile
    private var currentStep: String? = null

    /** 当前任务的名称（用户本轮请求的单行化截断文本）。 */
    val currentTaskName: String
        get() = taskName

    /** 已完成的工具步骤数。 */
    val completed: Int
        get() = completedSteps

    /** 当前正在执行的工具名（未本地化）。 */
    val current: String?
        get() = currentStep

    fun attachListener(listener: () -> Unit) {
        changeListener = listener
    }

    fun detachListener(listener: () -> Unit) {
        if (changeListener === listener) changeListener = null
    }

    /** 新一轮任务开始：记录任务名，并重置步骤进度。 */
    fun onRunStarted(prompt: String) {
        val normalized = prompt.trim().replace(whitespace, " ")
        taskName = if (normalized.length > TASK_NAME_LIMIT) {
            normalized.take(TASK_NAME_LIMIT) + "…"
        } else {
            normalized
        }
        completedSteps = 0
        currentStep = null
        notifyChanged()
    }

    /** 一次工具调用即将开始：把上一个步骤计入完成，并把当前步骤替换为该工具。 */
    fun onStepStarted(toolName: String) {
        if (currentStep != null) completedSteps += 1
        currentStep = toolName
        notifyChanged()
    }

    /** 任务全部结束后复位，避免下一次任务继承旧进度。 */
    fun reset() {
        if (completedSteps == 0 && currentStep == null && taskName.isEmpty()) return
        completedSteps = 0
        currentStep = null
        taskName = ""
        notifyChanged()
    }

    private fun notifyChanged() {
        val listener = changeListener ?: return
        if (Looper.myLooper() === Looper.getMainLooper()) {
            listener()
        } else {
            mainHandler.post(listener)
        }
    }
}
