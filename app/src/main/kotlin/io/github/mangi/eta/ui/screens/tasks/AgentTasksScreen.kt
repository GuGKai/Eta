package io.github.mangi.eta.ui.screens.tasks

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.shape.CouiShape
import com.itos.coui.switch.CouiSwitchDefaults
import com.itos.coui.switch.Switch as CouiSwitch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.automation.AgentTaskScheduler
import io.github.mangi.eta.agent.automation.AgentTaskTools
import io.github.mangi.eta.data.db.AgentTaskEntity
import io.github.mangi.eta.data.db.AgentTaskRunEntity
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.ItemDescriptionDialog
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz

private val TaskStatusSuccess = Color(0xFF34A853)
private val TaskStatusWarning = Color(0xFFF29900)

@Composable
internal fun AgentTasksScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val dao = remember { EtaDatabase.get(context).agentTaskDao() }
    val tasks by dao.observeTasks().collectAsState(initial = emptyList())
    val runs by dao.observeRuns().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var triggers by remember {
        mutableStateOf(TaskTriggerCatalogCache.triggers ?: emptyList())
    }
    var selectedTrigger by remember { mutableStateOf<TaskTriggerUi?>(null) }
    var describeTarget by remember { mutableStateOf<AgentTaskEntity?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var notice by remember { mutableStateOf<String?>(null) }
    fun execute(tool: String, args: JSONObject) {
        scope.launch(Dispatchers.IO) {
            val result = JSONObject(AgentTaskTools(context).execute(tool, args))
            withContext(Dispatchers.Main) {
                notice = if (result.optBoolean("ok")) null else result.optString("message")
            }
        }
    }
    val onToggle: (AgentTaskEntity, Boolean) -> Unit = { task, enabled ->
        execute(
            "tasks_update",
            JSONObject().put("taskId", task.id)
                .put("expectedUpdatedAt", task.updatedAt).put("enabled", enabled),
        )
    }
    val onRun: (AgentTaskEntity) -> Unit = { task ->
        execute("tasks_run", JSONObject().put("taskId", task.id))
    }
    val onCancel: (AgentTaskEntity) -> Unit = { task ->
        execute("tasks_cancel", JSONObject().put("taskId", task.id))
    }
    val onDelete: (AgentTaskEntity) -> Unit = { task ->
        execute("tasks_delete", JSONObject().put("taskId", task.id))
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 调度刷新只是副作用，放到后台单独跑，不要挡着触发器面板的数据。
            launch(Dispatchers.IO) { AgentTaskScheduler.refresh(context) }
            val loaded = withContext(Dispatchers.IO) { loadTaskTriggers(context) }
            TaskTriggerCatalogCache.update(loaded)
            triggers = loaded
            awaitCancellation()
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> CouiTasksPage(
            tasks = tasks,
            runs = runs,
            triggers = triggers,
            notice = notice,
            onBack = onBack,
            onToggle = onToggle,
            onRun = onRun,
            onCancel = onCancel,
            onDelete = onDelete,
            onDescribe = { describeTarget = it },
            onSelectTrigger = { selectedTrigger = it },
        )
        AppearanceUiStyle.MIUIX -> MiuixTasksPage(
            tasks = tasks,
            runs = runs,
            triggers = triggers,
            notice = notice,
            onBack = onBack,
            onToggle = onToggle,
            onRun = onRun,
            onCancel = onCancel,
            onDelete = onDelete,
            onDescribe = { describeTarget = it },
            onSelectTrigger = { selectedTrigger = it },
        )
    }

    selectedTrigger?.let { trigger -> TriggerDetails(trigger) { selectedTrigger = null } }

    describeTarget?.let { task ->
        ItemDescriptionDialog(
            show = true,
            title = task.name,
            description = describeTask(context, task, runs.filter { it.taskId == task.id }),
            onDismiss = { describeTarget = null },
        )
    }
}

/** COUI 版：创建提示置顶，任务紧随其后，触发器目录在最后。 */
@Composable
private fun CouiTasksPage(
    tasks: List<AgentTaskEntity>,
    runs: List<AgentTaskRunEntity>,
    triggers: List<TaskTriggerUi>,
    notice: String?,
    onBack: () -> Unit,
    onToggle: (AgentTaskEntity, Boolean) -> Unit,
    onRun: (AgentTaskEntity) -> Unit,
    onCancel: (AgentTaskEntity) -> Unit,
    onDelete: (AgentTaskEntity) -> Unit,
    onDescribe: (AgentTaskEntity) -> Unit,
    onSelectTrigger: (TaskTriggerUi) -> Unit,
) {
    val dark = CouiTheme.isDark
    CouiScaffoldPage(
        title = stringResource(R.string.automation_title),
        onBack = onBack,
        // 任务列表/触发器目录会很长，标题固定成紧凑顶栏，上滑时不再跟着折叠移动。
        compactTitle = true,
    ) {
        Group {
            CouiPreference(
                title = stringResource(R.string.automation_create_hint),
                summary = stringResource(R.string.automation_create_summary),
                showChevron = false,
            )
        }
        if (tasks.isNotEmpty()) {
            Group(title = stringResource(R.string.automation_tasks_section)) {
                tasks.forEachIndexed { index, task ->
                    val rule = remember(task.triggerJson) { JSONObject(task.triggerJson) }
                    CouiTaskRow(
                        task = task,
                        rule = rule,
                        onToggle = { onToggle(task, it) },
                        onRun = { onRun(task) },
                        onCancel = { onCancel(task) },
                        onDelete = { onDelete(task) },
                        onDescribe = { onDescribe(task) },
                    )
                    if (index < tasks.lastIndex) {
                        Divider()
                    }
                }
            }
        }
        if (triggers.isNotEmpty()) {
            Group(title = stringResource(R.string.automation_triggers_title)) {
                TriggerCatalogHeading(triggers)
                TaskTriggerGroup.entries.forEach { group ->
                    val groupTriggers = triggers.filter { it.group == group }
                    if (groupTriggers.isNotEmpty()) {
                        TriggerCatalogGroup(group, groupTriggers, onSelectTrigger)
                    }
                }
            }
        }
        notice?.let { message ->
            CouiText(
                text = message,
                kind = CouiTextKind.ItemSummary,
                color = CouiDialogDefaults.themeColors(dark).destructive,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        CouiPageBottomSpacer()
    }
}

/** 任务行：和 Skills 页一致的「标题 + 摘要 + 三点菜单 + 开关」，详情全收进菜单。 */
@Composable
private fun CouiTaskRow(
    task: AgentTaskEntity,
    rule: JSONObject,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDescribe: () -> Unit,
) {
    val context = LocalContext.current
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val destructiveColor = CouiDialogDefaults.themeColors(dark).destructive
    var showMenu by remember { mutableStateOf(false) }
    val anchor = rememberCouiMenuAnchor()
    val moreSource = remember { MutableInteractionSource() }
    val viewDescText = stringResource(R.string.ui_view_description)
    val runText = stringResource(R.string.automation_run)
    val cancelText = stringResource(R.string.automation_cancel)
    val deleteText = stringResource(R.string.automation_delete)
    val menuItems = remember(viewDescText, runText, cancelText, deleteText, destructiveColor) {
        listOf(
            CouiMenuItem(value = 0, text = viewDescText),
            CouiMenuItem(value = 1, text = runText),
            CouiMenuItem(value = 2, text = cancelText),
            CouiMenuItem(value = 3, text = deleteText, iconTint = destructiveColor, dividerBefore = true),
        )
    }
    val statusColor = couiStatusColor(task.lastStatus, colors.accent, colors.summary, destructiveColor)
    val statusLine = statusLabel(context, task.lastStatus) + " · " +
        context.getString(R.string.automation_run_count, task.runCount, task.maxRuns)

    Item(
        // 与 Skills 页一致：点整行就是切开关，完整说明只从三点菜单的「查看说明」进入。
        onClick = { onToggle(!task.enabled) },
        title = {
            CouiText(
                text = task.name,
                kind = CouiTextKind.ItemTitle,
                color = if (task.enabled) colors.title else colors.disabled,
            )
        },
        summary = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CouiText(
                    text = task.prompt,
                    kind = CouiTextKind.ItemSummary,
                    color = if (task.enabled) colors.summary else colors.disabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CouiText(
                    text = statusLine,
                    kind = CouiTextKind.GroupFooter,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing = {
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CouiShape(18.dp, 2f))
                        .couiButtonFeedback(dark, moreSource, true)
                        .couiMenuAnchor(anchor)
                        .clickable(
                            interactionSource = moreSource,
                            indication = null,
                            role = Role.Button,
                        ) {
                            anchor.open { showMenu = true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CouiIcon(
                        imageVector = CouiIcons.Outlined.More,
                        contentDescription = stringResource(R.string.automation_tasks_more_named, task.name),
                        modifier = Modifier.size(20.dp),
                        tint = colors.summary,
                    )
                }
                Menu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    items = menuItems,
                    onItemClick = { item ->
                        showMenu = false
                        when (item.value) {
                            0 -> onDescribe()
                            1 -> onRun()
                            2 -> onCancel()
                            3 -> onDelete()
                        }
                    },
                    anchor = anchor,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CouiSwitch(
                checked = task.enabled,
                onCheckedChange = onToggle,
                dark = dark,
                colors = CouiSwitchDefaults.colors(colors.accent, dark),
            )
        },
    )
}

/** MIUIX 版：同样的信息层级，样式走 Miuix 组件。 */
@Composable
private fun MiuixTasksPage(
    tasks: List<AgentTaskEntity>,
    runs: List<AgentTaskRunEntity>,
    triggers: List<TaskTriggerUi>,
    notice: String?,
    onBack: () -> Unit,
    onToggle: (AgentTaskEntity, Boolean) -> Unit,
    onRun: (AgentTaskEntity) -> Unit,
    onCancel: (AgentTaskEntity) -> Unit,
    onDelete: (AgentTaskEntity) -> Unit,
    onDescribe: (AgentTaskEntity) -> Unit,
    onSelectTrigger: (TaskTriggerUi) -> Unit,
) {
    MiuixScaffoldPage(
        title = stringResource(R.string.automation_title),
        onBack = onBack,
        compactTitle = true,
    ) {
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.automation_create_hint),
                    summary = stringResource(R.string.automation_create_summary),
                )
            }
        }
        if (tasks.isNotEmpty()) {
            item(key = "tasks") {
                SmallTitle(stringResource(R.string.automation_tasks_section))
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    tasks.forEach { task ->
                        val rule = remember(task.triggerJson) { JSONObject(task.triggerJson) }
                        MiuixTaskRow(
                            task = task,
                            rule = rule,
                            onToggle = { onToggle(task, it) },
                            onRun = { onRun(task) },
                            onCancel = { onCancel(task) },
                            onDelete = { onDelete(task) },
                            onDescribe = { onDescribe(task) },
                        )
                    }
                }
            }
        }
        item(key = "trigger-catalog-heading") {
            TriggerCatalogHeading(triggers)
        }
        TaskTriggerGroup.entries.forEach { group ->
            val groupTriggers = triggers.filter { it.group == group }
            if (groupTriggers.isNotEmpty()) item(key = "trigger-group-" + group.name) {
                TriggerCatalogGroup(group, groupTriggers, onSelectTrigger)
            }
        }
        notice?.let { message ->
            item {
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun MiuixTaskRow(
    task: AgentTaskEntity,
    rule: JSONObject,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDescribe: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val viewDescText = stringResource(R.string.ui_view_description)
    val statusLine = statusLabel(context, task.lastStatus) + " · " +
        context.getString(R.string.automation_run_count, task.runCount, task.maxRuns)
    val statusColor = when (task.lastStatus) {
        "completed" -> TaskStatusSuccess
        "failed", "interrupted" -> colors.error
        "queued", "running" -> colors.primary
        "configuration_required" -> TaskStatusWarning
        else -> colors.onSurfaceVariantSummary
    }
    BasicComponent(
        onClick = { onToggle(!task.enabled) },
        endActions = {
            OverlayIconDropdownMenu(
                modifier = Modifier.align(Alignment.CenterVertically),
                entry = DropdownEntry(
                    items = listOf(
                        DropdownItem(text = viewDescText, onClick = onDescribe),
                        DropdownItem(text = stringResource(R.string.automation_run), onClick = onRun),
                        DropdownItem(text = stringResource(R.string.automation_cancel), onClick = onCancel),
                        DropdownItem(text = stringResource(R.string.automation_delete), onClick = onDelete),
                    ),
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.automation_tasks_more_named, task.name),
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariantActions,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = task.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        },
    ) {
        Text(
            text = task.name,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = if (task.enabled) colors.onBackground else colors.disabledOnSecondaryVariant,
        )
        Text(
            text = task.prompt,
            style = MiuixTheme.textStyles.body2,
            color = if (task.enabled) colors.onSurfaceVariantSummary else colors.disabledOnSecondaryVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = statusLine,
            style = MiuixTheme.textStyles.footnote1,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun couiStatusColor(status: String, accent: Color, summary: Color, destructive: Color): Color =
    when (status) {
        "completed" -> TaskStatusSuccess
        "failed", "interrupted" -> destructive
        "queued", "running" -> accent
        "configuration_required" -> TaskStatusWarning
        else -> summary
    }

/** 「查看说明」的正文：触发方式、筛选、条件、下次运行与最近运行都在这里。 */
private fun describeTask(context: Context, task: AgentTaskEntity, runs: List<AgentTaskRunEntity>): String {
    val rule = runCatching { JSONObject(task.triggerJson) }.getOrElse { JSONObject() }
    val lines = buildList {
        add(task.prompt)
        add("")
        add(context.getString(R.string.automation_trigger_label) + ": " + triggerSummary(context, rule))
        triggerFilters(context, rule).takeIf { it.isNotBlank() }?.let {
            add(context.getString(R.string.automation_filters_label) + ": " + it)
        }
        triggerConditions(context, rule).takeIf { it.isNotBlank() }?.let {
            add(context.getString(R.string.automation_conditions_label) + ": " + it)
        }
        task.nextRunAt?.let {
            add(context.getString(R.string.automation_next_run_label) + ": " + formatTime(context, it))
        }
        add(context.getString(R.string.automation_run_count, task.runCount, task.maxRuns))
        if (runs.isNotEmpty()) {
            add("")
            add(context.getString(R.string.automation_recent_runs_label) + ":")
            runs.take(3).forEach { run ->
                val time = formatTime(context, run.startedAt ?: run.queuedAt)
                add("· " + statusLabel(context, run.status) + " · " + time)
                if (run.resultPreview.isNotBlank()) {
                    add("  " + run.resultPreview.take(300))
                }
            }
        }
    }
    return lines.joinToString("\n")
}

private fun triggerSummary(context: Context, rule: JSONObject): String =
    when (rule.optString("type")) {
        "once" -> runCatching {
            formatTime(context, OffsetDateTime.parse(rule.optString("at")).toInstant().toEpochMilli())
        }.getOrDefault(rule.optString("at"))
        "daily" -> {
            val weekdays = rule.optJSONArray("weekdays")
            val days = if (weekdays == null) emptyList() else {
                (0 until weekdays.length()).mapNotNull { index ->
                    weekdays.optInt(index).takeIf { it in 1..7 }?.let {
                        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, context.resources.configuration.locales[0])
                    }
                }
            }
            context.getString(R.string.automation_daily, rule.optString("time"), rule.optString("timeZone")) +
                if (days.isEmpty()) "" else " · ${days.joinToString(", ")}"
        }
        "interval" -> context.getString(R.string.automation_interval, rule.optLong("seconds") / 60)
        else -> eventLabel(context, rule.optString("type"))
    }

internal fun eventLabel(context: Context, type: String): String {
    val label = when (type) {
        "notification_posted" -> R.string.automation_event_notification_posted
        "notification_removed" -> R.string.automation_event_notification_removed
        "app_foreground" -> R.string.automation_event_app_foreground
        "device_unlocked" -> R.string.automation_event_device_unlocked
        "screen_on" -> R.string.automation_event_screen_on
        "screen_off" -> R.string.automation_event_screen_off
        "charging_connected" -> R.string.automation_event_charging_connected
        "charging_disconnected" -> R.string.automation_event_charging_disconnected
        "battery_low" -> R.string.automation_event_battery_low
        "battery_okay" -> R.string.automation_event_battery_okay
        "network_connected" -> R.string.automation_event_network_connected
        "network_disconnected" -> R.string.automation_event_network_disconnected
        "bluetooth_connected" -> R.string.automation_event_bluetooth_connected
        "bluetooth_disconnected" -> R.string.automation_event_bluetooth_disconnected
        "headset_connected" -> R.string.automation_event_headset_connected
        "headset_disconnected" -> R.string.automation_event_headset_disconnected
        "device_boot" -> R.string.automation_event_device_boot
        "memory_updated" -> R.string.automation_event_memory_updated
        "task_completed" -> R.string.automation_event_task_completed
        "task_failed" -> R.string.automation_event_task_failed
        else -> return context.getString(R.string.automation_event, type)
    }
    return context.getString(label)
}

private fun triggerFilters(context: Context, rule: JSONObject): String {
    val filters = rule.optJSONObject("filters") ?: return ""
    return listOf(
        "packageName" to R.string.automation_filter_app,
        "titleContains" to R.string.automation_filter_title,
        "textContains" to R.string.automation_filter_text,
        "taskId" to R.string.automation_filter_task,
        "transport" to R.string.automation_filter_transport,
    ).mapNotNull { (key, label) ->
        filters.optString(key).takeIf { it.isNotBlank() }?.let { context.getString(label, it) }
    }.joinToString("\n")
}

private fun triggerConditions(context: Context, rule: JSONObject): String {
    val conditions = rule.optJSONObject("conditions") ?: return ""
    return listOf(
        Triple("charging", R.string.automation_condition_charging, R.string.automation_condition_not_charging),
        Triple("unlocked", R.string.automation_condition_unlocked, R.string.automation_condition_locked),
        Triple("networkConnected", R.string.automation_condition_online, R.string.automation_condition_offline),
    ).mapNotNull { (key, yes, no) ->
        if (conditions.has(key)) context.getString(if (conditions.optBoolean(key)) yes else no) else null
    }.joinToString(" · ")
}

private fun formatTime(context: Context, time: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(context.resources.configuration.locales[0])
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

private fun statusLabel(context: Context, status: String): String =
    context.getString(
        when (status) {
            "ready" -> R.string.automation_ready
            "queued" -> R.string.automation_queued
            "running" -> R.string.automation_running
            "completed" -> R.string.automation_completed
            "failed" -> R.string.automation_failed
            "cancelled" -> R.string.automation_cancelled
            "interrupted" -> R.string.automation_interrupted
            "configuration_required" -> R.string.automation_config_required
            else -> R.string.automation_ready
        }
    )
