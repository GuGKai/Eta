package io.github.mangi.eta.agent.automation

import android.Manifest
import android.app.KeyguardManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.runtime.ipc.AgentRuntimeClient
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.db.AgentTaskEntity
import io.github.mangi.eta.data.db.AgentTaskRunEntity
import io.github.mangi.eta.data.db.EtaDatabase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

internal object AgentTaskScheduler {
    private const val JOB_ID = 1110
    private val eventWorker = ThreadPoolExecutor(
        1,
        1,
        0,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(100),
        { runnable -> Thread(runnable, "eta-trigger-events").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy()
    )
    private val cancellationWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            runnable,
            "eta-task-cancel"
        ).apply { isDaemon = true }
    }
    private val activeRuns = ConcurrentHashMap<String, String>()
    private val jobRunning = AtomicBoolean()
    private val jobLock = Any()

    fun jobStarted(): Boolean = synchronized(jobLock) { jobRunning.compareAndSet(false, true) }
    fun jobFinished() = synchronized(jobLock) {
        jobRunning.set(false)
    }

    fun isRegistered(runId: String): Boolean = activeRuns.containsKey(runId)
    fun register(runId: String, taskId: String) {
        activeRuns[runId] = taskId
    }

    fun unregister(runId: String) {
        activeRuns.remove(runId)
    }

    fun cancelRunning(context: Context, taskId: String) {
        activeRuns.filterValues { it == taskId }.keys.forEach { id ->
            cancellationWorker.execute {
                AgentRuntimeClient(
                    context,
                    AndroidAgentLogger
                ).cancelRun(id)
            }
        }
    }

    fun refresh(context: Context) = runBlocking {
        val dao = EtaDatabase.get(context).agentTaskDao()
        val tasks = dao.tasks().filter { it.enabled && it.runCount < it.maxRuns }
        val queued = dao.nextQueued() != null
        synchronized(jobLock) {
            if (!jobRunning.get()) {
                if (queued) requestJob(context)
                else context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            }
        }
        if (tasks.any { JSONObject(it.triggerJson).optString("type") in monitoredEventTypes }) {
            runCatching {
                context.startForegroundService(
                    Intent(
                        context,
                        AgentTriggerMonitorService::class.java
                    )
                )
            }
                .onFailure { AndroidAgentLogger.warn("Trigger monitor requires a foreground user entry") }
        } else context.stopService(Intent(context, AgentTriggerMonitorService::class.java))
    }

    fun requestJob(context: Context, delayMs: Long = 0): Unit = synchronized(jobLock) {
        if (jobRunning.get()) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val delay = delayMs.coerceIn(0, TimeUnit.DAYS.toMillis(365))
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AgentTaskJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay)
            .setOverrideDeadline(delay + 60_000)
            .setPersisted(true)
            .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) AndroidAgentLogger.warn("Automation job scheduling rejected")
    }

    fun state(context: Context): Map<String, Boolean> {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val network = context.getSystemService(ConnectivityManager::class.java)
        return mapOf(
            "charging" to ((battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0),
            "unlocked" to !context.getSystemService(KeyguardManager::class.java).isDeviceLocked,
            "networkConnected" to (network.activeNetwork != null)
        )
    }

    /**
     * 存量时间计划类任务只保留记录：关闭并清空下次时间。
     * 时间驱动已统一由调度模块（scheduled_tasks）负责，避免两套机制重复触发。
     */
    fun retireTimeTasks(context: Context) = runBlocking {
        val dao = EtaDatabase.get(context).agentTaskDao()
        dao.tasks()
            .filter { JSONObject(it.triggerJson).optString("type") in AgentTaskRules.retiredTimeTypes }
            .forEach { task ->
                runCatching {
                    dao.updateTask(
                        task.copy(
                            enabled = false,
                            nextRunAt = null,
                            updatedAt = maxOf(System.currentTimeMillis(), task.updatedAt + 1)
                        )
                    )
                }.onFailure { AndroidAgentLogger.warn("Retire legacy time task failed") }
            }
    }

    fun enqueueManual(context: Context, task: AgentTaskEntity): Boolean = runBlocking {
        val run = AgentTaskRunEntity(
            UUID.randomUUID().toString(),
            task.id,
            "manual:${UUID.randomUUID()}",
            queuedAt = System.currentTimeMillis()
        )
        EtaDatabase.get(context).agentTaskDao().enqueue(task, run, task.nextRunAt, manual = true)
            .also { if (it) requestJob(context) }
    }

    fun publish(context: Context, type: String, key: String, data: JSONObject = JSONObject()) {
        if (type !in AgentTaskRules.eventTypes) return
        val appContext = context.applicationContext
        val copy = JSONObject(data.toString())
        eventWorker.execute {
            runCatching {
                runBlocking {
                    val dao = EtaDatabase.get(appContext).agentTaskDao()
                    val now = System.currentTimeMillis()
                    val state = state(appContext)
                    var queued = false
                    dao.tasks().filter { it.enabled }.forEach { task ->
                        val rule = JSONObject(task.triggerJson)
                        if (task.id == copy.optString("taskId")) return@forEach
                        // 防止全局任务完成事件形成自动循环；要求精确指定来源任务。
                        if (type in setOf(
                                "task_completed",
                                "task_failed"
                            ) && rule.optJSONObject("filters")?.optString("taskId").isNullOrBlank()
                        ) return@forEach
                        if (AgentTaskRules.matches(
                                rule,
                                type,
                                copy
                            ) && AgentTaskRules.conditionsMatch(rule, state)
                        ) {
                            val event = JSONObject().put("type", type)
                            for (field in listOf(
                                "packageName",
                                "taskId",
                                "transport"
                            )) if (copy.has(field)) event.put(
                                field,
                                copy.optString(field).take(200)
                            )
                            queued = dao.enqueue(
                                task, AgentTaskRunEntity(
                                    UUID.randomUUID().toString(),
                                    task.id,
                                    "$type:${key.take(200)}",
                                    eventJson = event.toString(),
                                    queuedAt = now
                                ), null
                            ) || queued
                        }
                    }
                    if (queued) requestJob(appContext)
                }
            }.onFailure { AndroidAgentLogger.warn("Trigger event could not be queued") }
        }
    }

    fun capabilities(context: Context): JSONObject {
        val triggers = JSONArray()
        AgentTaskRules.eventTypes.forEach { type ->
            val available = available(context, type)
            val scope = when (type) {
                "notification_posted", "notification_removed" -> "需要系统通知读取授权；不回放连接前通知，忽略 Eta 自身通知"
                "app_foreground" -> "需要 Eta 无障碍服务连接；按窗口事件触发"
                "bluetooth_connected", "bluetooth_disconnected" -> "需要蓝牙连接授权"
                "memory_updated", "task_completed", "task_failed" -> "Eta 本地事件；任务结果触发需要 filters.taskId 防止循环"
                else -> "事件监控前台服务运行期间接收；系统结束服务时不补发"
            }
            triggers.put(
                JSONObject().put("type", type).put("available", available).put("scope", scope)
            )
        }
        listOf(
            "location_enter",
            "location_exit",
            "calendar_near",
            "file_changed",
            "shared_content"
        ).forEach { type ->
            triggers.put(
                JSONObject().put("type", type).put("available", false)
                    .put("scope", "待适配，当前不能创建此类任务")
            )
        }
        return JSONObject().put("triggers", triggers)
            .put("monitorRunning", AgentTriggerMonitorService.isRunning)
            .put("conditions", JSONArray(listOf("charging", "unlocked", "networkConnected")))
    }

    fun requireTriggerAvailable(context: Context, rule: JSONObject) {
        val type = rule.getString("type")
        require(type !in AgentTaskRules.retiredTimeTypes) {
            "时间计划类触发器已下线，请改用定时任务（scheduled_tasks 工具）"
        }
        require(available(context, type)) {
            "触发来源未授权，请先 tasks_triggers；也可 enabled=false 保存"
        }
        if (rule.getString("type") in setOf(
                "task_completed",
                "task_failed"
            )
        ) require(
            !rule.optJSONObject("filters")?.optString("taskId").isNullOrBlank()
        ) { "任务结果触发必须指定 filters.taskId" }
    }

    private fun available(context: Context, type: String): Boolean = when (type) {
        "notification_posted", "notification_removed" -> AgentNotificationHistoryService.isEnabled(
            context
        )

        "app_foreground" -> AgentAccessibilityService.isAvailable()
        "bluetooth_connected", "bluetooth_disconnected" -> context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else -> type in AgentTaskRules.eventTypes
    }

    private val monitoredEventTypes = AgentTaskRules.eventTypes - setOf(
        "notification_posted",
        "notification_removed",
        "app_foreground",
        "memory_updated",
        "task_completed",
        "task_failed",
        "device_boot",
    )
}
