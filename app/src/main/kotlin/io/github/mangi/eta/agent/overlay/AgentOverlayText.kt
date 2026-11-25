package io.github.mangi.eta.agent.overlay

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import io.github.mangi.eta.R

/** 浮窗只保存语义状态，文案在渲染时根据当前系统语言解析。 */
internal sealed interface AgentOverlayStatus {
    data object Reasoning : AgentOverlayStatus
    data object SupplementReceived : AgentOverlayStatus
    data class RunningTool(val name: String) : AgentOverlayStatus
    data class HostedToolRunning(val name: String) : AgentOverlayStatus
    data object ResultReady : AgentOverlayStatus
    data object RunFailed : AgentOverlayStatus
    data object GeneratingAnswer : AgentOverlayStatus
    data object Stopping : AgentOverlayStatus
    data object Paused : AgentOverlayStatus
    data object Continuing : AgentOverlayStatus
    data object Finishing : AgentOverlayStatus
    data object ContinuationUnavailable : AgentOverlayStatus
    data object Stopped : AgentOverlayStatus
}

@Composable
internal fun AgentOverlayStatus.localizedText(): String = localizedText(LocalResources.current)

/** 通知等非 Compose 表面与浮层共用同一套状态文案。 */
internal fun AgentOverlayStatus.localizedText(resources: Resources): String = when (this) {
    AgentOverlayStatus.Reasoning -> resources.getString(R.string.overlay_reasoning)
    AgentOverlayStatus.SupplementReceived -> resources.getString(R.string.overlay_supplement_received)
    // 工具名本身就是动作（点击元素、读取文件），不再加"执行："前缀。
    is AgentOverlayStatus.RunningTool -> toolDisplayName(resources, name)
    is AgentOverlayStatus.HostedToolRunning -> resources.getString(R.string.overlay_hosted_tool_running, name)
    AgentOverlayStatus.ResultReady -> resources.getString(R.string.overlay_result_ready)
    AgentOverlayStatus.RunFailed -> resources.getString(R.string.overlay_run_failed)
    AgentOverlayStatus.GeneratingAnswer -> resources.getString(R.string.overlay_generating_answer)
    AgentOverlayStatus.Stopping -> resources.getString(R.string.overlay_stopping)
    AgentOverlayStatus.Paused -> resources.getString(R.string.overlay_paused)
    AgentOverlayStatus.Continuing -> resources.getString(R.string.overlay_continuing)
    AgentOverlayStatus.Finishing -> resources.getString(R.string.overlay_finishing)
    AgentOverlayStatus.ContinuationUnavailable -> resources.getString(R.string.overlay_continuation_unavailable)
    AgentOverlayStatus.Stopped -> resources.getString(R.string.overlay_stopped)
}

@Composable
internal fun toolDisplayName(name: String): String = toolDisplayName(LocalResources.current, name)

internal fun toolDisplayName(resources: Resources, name: String): String {
    val resource = toolDisplayNameResource(name) ?: return io.github.mangi.eta.agent.model.AgentPhoneToolCatalog.entries.firstOrNull { it.name == name }?.title
        ?: io.github.mangi.eta.agent.context.PersonalSearchTools.searches.firstOrNull { it.name == name }?.title ?: name
    return resources.getString(resource)
}

@StringRes
internal fun toolDisplayNameResource(name: String): Int? = when (name) {
    "batch" -> R.string.tool_batch
    "observe_screen" -> R.string.tool_observe_screen
    "release_gui" -> R.string.tool_release_gui
    "present_to_user" -> R.string.tool_present_to_user
    "ask_user_question" -> R.string.tool_ask_user_question
    "tap" -> R.string.tool_tap
    "tap_element" -> R.string.tool_tap_element
    "tap_area" -> R.string.tool_tap_area
    "long_press" -> R.string.tool_long_press
    "long_press_element" -> R.string.tool_long_press_element
    "swipe" -> R.string.tool_swipe
    "scroll" -> R.string.tool_scroll
    "scroll_element" -> R.string.tool_scroll_element
    "type_text", "input_text" -> R.string.tool_input_text
    "replace_text" -> R.string.tool_replace_text
    "clear_text" -> R.string.tool_clear_text
    "set_clipboard" -> R.string.tool_set_clipboard
    "get_clipboard" -> R.string.tool_get_clipboard
    "paste_text" -> R.string.tool_paste_text
    "press_key" -> R.string.tool_press_key
    "wait" -> R.string.tool_wait
    "wait_for_text" -> R.string.tool_wait_for_text
    "wait_for_package" -> R.string.tool_wait_for_package
    "get_current_context" -> R.string.tool_current_context
    "open_system_panel" -> R.string.tool_open_system_panel
    "search_apps" -> R.string.tool_search_apps
    "launch_app" -> R.string.tool_launch_app
    "open_uri" -> R.string.tool_open_uri
    "browser_use" -> R.string.tool_browser_use
    "web_search" -> R.string.tool_web_search
    "fetch_url" -> R.string.tool_fetch_url
    "generate_image" -> R.string.tool_generate_image
    "terminal" -> R.string.tool_terminal
    "terminal_job" -> R.string.tool_terminal_job
    "terminal_daemon" -> R.string.tool_terminal_daemon
    "run_command" -> R.string.tool_run_command
    "inspect_app" -> R.string.tool_inspect_app
    "edit_file" -> R.string.tool_edit_file
    "stat_file" -> R.string.tool_stat_file
    "glob_files" -> R.string.tool_glob_files
    "grep_files" -> R.string.tool_grep_files
    "read_file" -> R.string.tool_read_file
    "write_file" -> R.string.tool_write_file
    "list_directory" -> R.string.tool_list_directory
    "memory_get" -> R.string.tool_memory_get
    "memory_write" -> R.string.tool_memory_write
    "subagent" -> R.string.tool_subagent
    "subagent_profile" -> R.string.tool_subagent_profile
    "set_alarm" -> R.string.tool_set_alarm
    "set_timer" -> R.string.tool_set_timer
    "device_status" -> R.string.tool_device_status
    "network_info" -> R.string.tool_network_info
    "media_control" -> R.string.tool_media_control
    "set_volume" -> R.string.tool_set_volume
    "top_memory_apps" -> R.string.tool_top_memory_apps
    "top_storage_apps" -> R.string.tool_top_storage_apps
    "read_sms_code" -> R.string.tool_read_sms_code
    "recent_notifications" -> R.string.tool_recent_notifications
    "wifi_credentials" -> R.string.tool_wifi_credentials
    "get_setting" -> R.string.tool_get_setting
    "set_setting" -> R.string.tool_set_setting
    "set_device_state" -> R.string.tool_set_device_state
    "app_state_control" -> R.string.tool_app_state_control
    "get_logcat" -> R.string.tool_get_logcat
    // 个人数据、媒体与系统查询
    "read_image" -> R.string.tool_read_image
    "read_personal_item" -> R.string.tool_read_personal_item
    "summarize_bills" -> R.string.tool_summarize_bills
    "search_media" -> R.string.tool_search_media
    "search_audio" -> R.string.tool_search_audio
    "search_recordings" -> R.string.tool_search_recordings
    "search_coloros_recordings" -> R.string.tool_search_coloros_recordings
    "search_recording_summaries" -> R.string.tool_search_recording_summaries
    "search_files" -> R.string.tool_search_files
    "search_calendar_events" -> R.string.tool_search_calendar_events
    "search_contacts" -> R.string.tool_search_contacts
    "search_call_history" -> R.string.tool_search_call_history
    "search_messages" -> R.string.tool_search_messages
    "search_downloads" -> R.string.tool_search_downloads
    "search_notes" -> R.string.tool_search_notes
    "search_system_memories" -> R.string.tool_search_system_memories
    "search_saved_places" -> R.string.tool_search_saved_places
    "search_personal_orders" -> R.string.tool_search_personal_orders
    "search_qq_chat_images" -> R.string.tool_search_qq_chat_images
    "search_wechat_chat_images" -> R.string.tool_search_wechat_chat_images
    "search_notification_history" -> R.string.tool_search_notification_history
    "recent_app_activity" -> R.string.tool_recent_app_activity
    "app_usage_summary" -> R.string.tool_app_usage_summary
    "get_current_location" -> R.string.tool_get_current_location
    "get_device_environment" -> R.string.tool_get_device_environment
    "list_alarms" -> R.string.tool_list_alarms
    "list_active_timers" -> R.string.tool_list_active_timers
    "search_clipboard_history" -> R.string.tool_search_clipboard_history
    "get_health_summary" -> R.string.tool_get_health_summary
    // 技能
    "skills_list" -> R.string.tool_skills_list
    "skills_read" -> R.string.tool_skills_read
    "skills_read_resource" -> R.string.tool_skills_read_resource
    "skills_manage" -> R.string.tool_skills_manage
    "skills_list_curated" -> R.string.tool_skills_list_curated
    "skills_inspect_github" -> R.string.tool_skills_inspect_github
    "skills_install_from_github" -> R.string.tool_skills_install_from_github
    // 自动任务与会话历史
    "tasks_triggers" -> R.string.tool_tasks_triggers
    "tasks_list" -> R.string.tool_tasks_list
    "tasks_create" -> R.string.tool_tasks_create
    "tasks_update" -> R.string.tool_tasks_update
    "tasks_delete" -> R.string.tool_tasks_delete
    "tasks_run" -> R.string.tool_tasks_run
    "tasks_cancel" -> R.string.tool_tasks_cancel
    "tasks_history" -> R.string.tool_tasks_history
    "scheduled_tasks" -> R.string.tool_scheduled_tasks
    "conversation_history" -> R.string.tool_conversation_history
    else -> null
}
