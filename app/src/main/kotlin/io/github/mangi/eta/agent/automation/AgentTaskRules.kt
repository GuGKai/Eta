package io.github.mangi.eta.agent.automation

import org.json.JSONObject

/** 固定结构的本地规则。匹配事件不调用模型。 */
internal object AgentTaskRules {
    /** 已下线的时间计划类触发器：时间驱动统一交给调度模块（scheduled_tasks）。 */
    val retiredTimeTypes = setOf("once", "interval", "daily")
    val eventTypes = setOf(
        "notification_posted",
        "notification_removed",
        "app_foreground",
        "device_unlocked",
        "screen_on",
        "screen_off",
        "charging_connected",
        "charging_disconnected",
        "battery_low",
        "battery_okay",
        "network_connected",
        "network_disconnected",
        "bluetooth_connected",
        "bluetooth_disconnected",
        "headset_connected",
        "headset_disconnected",
        "device_boot",
        "memory_updated",
        "task_completed",
        "task_failed"
    )
    private val filterFields =
        setOf("packageName", "titleContains", "textContains", "taskId", "transport")

    fun validate(raw: JSONObject): JSONObject {
        require(raw.toString().length <= 4000) { "触发规则过长" }
        val type = raw.getString("type")
        require(type !in retiredTimeTypes) {
            "时间计划类触发器已下线，请改用定时任务（scheduled_tasks 工具）"
        }
        require(type in eventTypes) { "触发器尚未实现，请先 tasks_triggers" }
        val fields = setOf("type", "filters", "conditions")
        require(raw.keys().asSequence().all { it in fields }) { "触发规则包含不支持的字段" }
        raw.optJSONObject("filters")?.let { filters ->
            require(
                filters.keys().asSequence().all { it in filterFields }) { "不支持此事件过滤字段" }
            filters.keys().forEach { name ->
                require(
                    filters.get(name) is String && filters.getString(name)
                        .isNotBlank() && filters.getString(name).length <= 200
                ) { "过滤值需要 1–200 字符" }
            }
            val allowed = when (type) {
                "notification_posted", "notification_removed" -> setOf(
                    "packageName",
                    "titleContains",
                    "textContains"
                )

                "app_foreground" -> setOf("packageName")
                "task_completed", "task_failed" -> setOf("taskId")
                "network_connected", "network_disconnected" -> setOf("transport")
                else -> emptySet()
            }
            require(
                filters.keys().asSequence().all { it in allowed }) { "该触发器不支持此过滤字段" }
        }
        require(!raw.has("filters") || raw.optJSONObject("filters") != null) { "filters 需要对象" }
        raw.optJSONObject("conditions")?.let { conditions ->
            require(
                conditions.keys().asSequence().all {
                    it in setOf(
                        "charging",
                        "unlocked",
                        "networkConnected"
                    )
                }) { "不支持此附加条件" }
            conditions.keys()
                .forEach { require(conditions.get(it) is Boolean) { "条件值需要 boolean" } }
        }
        require(!raw.has("conditions") || raw.optJSONObject("conditions") != null) { "conditions 需要对象" }
        return JSONObject(raw.toString())
    }

    fun matches(rule: JSONObject, type: String, event: JSONObject): Boolean {
        if (rule.optString("type") != type) return false
        val filters = rule.optJSONObject("filters") ?: JSONObject()
        return filters.keys().asSequence().all { field ->
            val value = filters.getString(field)
            when (field) {
                "titleContains" -> event.optString("title").contains(value, ignoreCase = true)
                "textContains" -> event.optString("text").contains(value, ignoreCase = true)
                else -> event.optString(field) == value
            }
        }
    }

    fun conditionsMatch(rule: JSONObject, state: Map<String, Boolean>): Boolean =
        rule.optJSONObject("conditions")?.let { conditions ->
            conditions.keys().asSequence()
                .all { name -> state[name] == conditions.getBoolean(name) }
        } ?: true

    fun integer(
        args: JSONObject,
        name: String,
        minimum: Long,
        maximum: Long,
        default: Long? = null
    ): Long {
        if (!args.has(name) && default != null) return default
        val value = args.get(name)
        require(
            value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong()
                .toDouble() && value.toLong() in minimum..maximum
        ) { "$name 需要 $minimum–$maximum 的整数" }
        return value.toLong()
    }

}
