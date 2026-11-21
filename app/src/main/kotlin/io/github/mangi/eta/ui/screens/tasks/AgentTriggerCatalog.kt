package io.github.mangi.eta.ui.screens.tasks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.automation.AgentTaskRules
import io.github.mangi.eta.agent.automation.AgentTaskScheduler
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal enum class TaskTriggerGroup(val label: Int) {
    TIME(R.string.automation_triggers_time), DEVICE(R.string.automation_triggers_device),
    CONNECTION(R.string.automation_triggers_connection), APPS(R.string.automation_triggers_apps),
    ETA(R.string.automation_triggers_eta),
}

internal data class TaskTriggerUi(val type: String, val available: Boolean) {
    val group: TaskTriggerGroup get() = when (type) {
        "network_connected", "network_disconnected", "bluetooth_connected", "bluetooth_disconnected", "headset_connected", "headset_disconnected" -> TaskTriggerGroup.CONNECTION
        "notification_posted", "notification_removed", "app_foreground" -> TaskTriggerGroup.APPS
        "memory_updated", "task_completed", "task_failed" -> TaskTriggerGroup.ETA
        else -> TaskTriggerGroup.DEVICE
    }
}

internal fun loadTaskTriggers(context: Context): List<TaskTriggerUi> {
    val array = AgentTaskScheduler.capabilities(context).getJSONArray("triggers")
    val supported = AgentTaskRules.eventTypes
    return (0 until array.length()).map { array.getJSONObject(it) }.filter { it.optString("type") in supported }
        .map { TaskTriggerUi(it.getString("type"), it.optBoolean("available")) }
}

/**
 * 触发器目录的进程内缓存。
 * capabilities() 要跑一遍权限检查与调度状态汇总，首次进入页面时耗时明显；
 * 缓存后再次打开自动任务页可以直接渲染，避免下方面板"先缺一块再补上"。
 */
internal object TaskTriggerCatalogCache {
    @Volatile
    var triggers: List<TaskTriggerUi>? = null
        private set

    fun update(value: List<TaskTriggerUi>) {
        triggers = value
    }
}

/** 进入自动任务页之前预热触发器目录，让面板首帧就位。 */
internal fun warmUpTaskTriggers(context: Context, scope: CoroutineScope) {
    if (TaskTriggerCatalogCache.triggers != null) return
    scope.launch(Dispatchers.IO) {
        val loaded = runCatching { loadTaskTriggers(context) }.getOrNull() ?: return@launch
        TaskTriggerCatalogCache.update(loaded)
    }
}

@Composable
internal fun TriggerCatalogHeading(triggers: List<TaskTriggerUi>) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
            CouiText(
                text = stringResource(
                    R.string.automation_triggers_count,
                    triggers.count { it.available },
                    triggers.size,
                ),
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AppearanceUiStyle.MIUIX -> {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.automation_triggers_title),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.automation_triggers_count, triggers.count { it.available }, triggers.size),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
internal fun TriggerCatalogGroup(group: TaskTriggerGroup, triggers: List<TaskTriggerUi>, onClick: (TaskTriggerUi) -> Unit) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> CouiTriggerChips(group, triggers, onClick)
        AppearanceUiStyle.MIUIX -> MiuixTriggerChips(group, triggers, onClick)
    }
}

@Composable
private fun CouiTriggerChips(
    group: TaskTriggerGroup,
    triggers: List<TaskTriggerUi>,
    onClick: (TaskTriggerUi) -> Unit,
) {
    val context = LocalContext.current
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CouiText(
            text = stringResource(group.label),
            kind = CouiTextKind.ItemSummary,
            color = colors.summary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            triggers.forEach { trigger ->
                val title = triggerTitle(context, trigger.type)
                val tint = if (trigger.available) colors.accent else colors.summary
                val source = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .clip(CouiCapsuleShape)
                        .background(tint.copy(alpha = 0.12f))
                        .couiButtonFeedback(dark, source, true)
                        .clickable(
                            interactionSource = source,
                            indication = null,
                            onClickLabel = title,
                        ) { onClick(trigger) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CouiIcon(
                        imageVector = if (trigger.available) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = stringResource(
                            if (trigger.available) R.string.automation_trigger_available
                            else R.string.automation_trigger_permission,
                        ),
                        modifier = Modifier.size(14.dp),
                        tint = tint,
                    )
                    CouiText(text = title, kind = CouiTextKind.ItemSummary, color = tint)
                }
            }
        }
    }
}

@Composable
private fun MiuixTriggerChips(
    group: TaskTriggerGroup,
    triggers: List<TaskTriggerUi>,
    onClick: (TaskTriggerUi) -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(group.label), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                triggers.forEach { trigger ->
                    val title = triggerTitle(context, trigger.type)
                    val color = if (trigger.available) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f))
                        .clickable(onClickLabel = title, onClick = { onClick(trigger) }).padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (trigger.available) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, modifier = Modifier.size(14.dp),
                            tint = color, contentDescription = stringResource(if (trigger.available) R.string.automation_trigger_available else R.string.automation_trigger_permission))
                        Text(title, style = MiuixTheme.textStyles.footnote1, color = color)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TriggerDetails(trigger: TaskTriggerUi, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val description = stringResource(triggerDescriptionRes(trigger.type))
    val stateText = stringResource(
        if (trigger.available) R.string.automation_trigger_available else R.string.automation_trigger_permission,
    )
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiNoticeDialog(
                visible = true,
                title = triggerTitle(context, trigger.type),
                message = stateText + "\n\n" + description,
                confirmText = stringResource(R.string.action_close),
                onDismissRequest = onDismiss,
            )
        }
        AppearanceUiStyle.MIUIX -> {
            WindowDialog(show = true, title = triggerTitle(context, trigger.type), onDismissRequest = onDismiss) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stateText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (trigger.available) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(description, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface)
                    TextButton(
                        text = stringResource(R.string.action_close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun triggerDescriptionRes(type: String): Int = when (type) {
    "notification_posted", "notification_removed" -> R.string.automation_trigger_scope_notification
    "app_foreground" -> R.string.automation_trigger_scope_accessibility
    "bluetooth_connected", "bluetooth_disconnected" -> R.string.automation_trigger_scope_bluetooth
    "task_completed", "task_failed" -> R.string.automation_trigger_scope_task
    "memory_updated" -> R.string.automation_trigger_scope_memory
    "device_boot" -> R.string.automation_trigger_scope_boot
    else -> R.string.automation_trigger_scope_monitor
}

private fun triggerTitle(context: Context, type: String): String = when (type) {
    "once" -> context.getString(R.string.automation_trigger_once)
    "daily" -> context.getString(R.string.automation_trigger_daily)
    "interval" -> context.getString(R.string.automation_trigger_interval)
    else -> eventLabel(context, type)
}
