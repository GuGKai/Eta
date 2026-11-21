package io.github.mangi.eta.agent.automation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentTaskRulesTest {
    @Test
    fun timeTriggersAreRetiredAndRejected() {
        listOf(
            JSONObject().put("type", "once").put("at", "2026-10-10T09:00:00+08:00"),
            JSONObject().put("type", "interval").put("seconds", 3600),
            JSONObject().put("type", "daily").put("time", "09:00").put("timeZone", "Asia/Shanghai"),
        ).forEach { raw ->
            val error = assertThrows(Exception::class.java) { AgentTaskRules.validate(raw) }
            assertTrue(error.message?.contains("已下线") == true)
        }
    }

    @Test
    fun eventFiltersAndConditionsAreLocallyMatched() {
        val rule = AgentTaskRules.validate(
            JSONObject().put("type", "notification_posted")
                .put(
                    "filters",
                    JSONObject().put("packageName", "com.example.app")
                        .put("titleContains", "Delivery")
                )
                .put("conditions", JSONObject().put("charging", true).put("unlocked", false))
        )
        assertTrue(
            AgentTaskRules.matches(
                rule,
                "notification_posted",
                JSONObject().put("packageName", "com.example.app").put("title", "DELIVERY arrived")
            )
        )
        assertFalse(
            AgentTaskRules.matches(
                rule,
                "notification_removed",
                JSONObject().put("packageName", "com.example.app").put("title", "Delivery")
            )
        )
        assertTrue(
            AgentTaskRules.conditionsMatch(
                rule,
                mapOf("charging" to true, "unlocked" to false)
            )
        )
        assertFalse(
            AgentTaskRules.conditionsMatch(
                rule,
                mapOf("charging" to false, "unlocked" to false)
            )
        )
        assertFalse(AgentTaskRules.conditionsMatch(rule, emptyMap()))
    }

    @Test
    fun unsupportedTriggersAndUnknownFiltersAreRejected() {
        val invalid = listOf(
            JSONObject().put("type", "location_enter"),
            JSONObject().put("type", "app_foreground")
                .put("filters", JSONObject().put("textContains", "text")),
            JSONObject().put("type", "screen_off")
                .put("conditions", JSONObject().put("unlocked", "true"))
        )
        invalid.forEach { raw -> assertThrows(Exception::class.java) { AgentTaskRules.validate(raw) } }
    }
}
