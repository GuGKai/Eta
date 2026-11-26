package io.github.mangi.eta.data.db

// fork 本地功能抽离，避免与上游冲突：EtaDatabase.kt 只保留 version = 45 与注册入口。
// 本文件承载 fork 新增的迁移链 30 → 45 以及列探测辅助函数；迁移逻辑与搬迁前逐字一致。

import androidx.room.migration.Migration

/** fork 新增的迁移链：上游停在 29→30，本地从 30→31 一路续接到 44→45。 */
internal object EtaDatabaseMigrations {

    /**
     * 31 → 32：归档表加「本轮耗时」列。
     *
     * 浮窗/语音入口的会话是 Runtime 先跑完再导入，界面这边不知道起点，
     * 只能靠结果里带的耗时；不落这一列，导入时就丢了。
     */
    internal val MIGRATION_31_32 = Migration(31, 32) { database ->
        if (!database.columnExists("runtime_archive_runs", "elapsed_ms")) {
            database.execSQL("ALTER TABLE runtime_archive_runs ADD COLUMN elapsed_ms INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 30 → 31：消息表加「本轮用时」列。
     *
     * 只加列、不回填：老会话该列为 null，界面据此不显示这一项。
     */
    internal val MIGRATION_30_31 = Migration(30, 31) { database ->
        if (!database.columnExists("conversation_messages", "round_duration_ms")) {
            database.execSQL("ALTER TABLE conversation_messages ADD COLUMN round_duration_ms INTEGER")
        }
    }

    /**
     * 32 → 33：把本地置顶列 pinned 并入上游的 is_pinned。
     *
     * 上游 3.4.0 与本地 fork 都占过 v24 这个版本号：上游在这里加 is_pinned，本地在这里建
     * 自动任务表，本地一路用 pinned 表示置顶。设备上的库现在有三种来源：
     *  - 本地旧库（pinned，无 is_pinned）—— 直接改名，置顶数据原地保留；
     *  - 纯上游库（is_pinned，无 pinned）—— 已经对齐，什么都不做；
     *  - 两列都在的混合库 —— 先把值并过去，再重建表去掉旧列，因为 Room 对整表列集合
     *    做严格相等校验，多一列就抛 Migration didn't properly handle。
     *
     * 重建分支必须绕开外键级联：Room 的迁移跑在事务里，改不了 foreign_keys，
     * 只能靠 legacy_alter_table 让改名不改写子表的引用（实测 FK 打开时直接 DROP
     * 父表会连带删光 79361 条消息）。
     */
    internal val MIGRATION_32_33 = Migration(32, 33) { database ->
        val hasPinned = database.conversationColumnExists("pinned")
        val hasIsPinned = database.conversationColumnExists("is_pinned")
        when {
            hasPinned && !hasIsPinned -> database.execSQL(
                "ALTER TABLE conversations RENAME COLUMN pinned TO is_pinned",
            )

            hasPinned && hasIsPinned -> {
                database.execSQL("UPDATE conversations SET is_pinned = pinned")
                database.execSQL("PRAGMA legacy_alter_table = ON")
                database.execSQL(
                    "CREATE TABLE conversations_sync_new (" +
                        "id TEXT NOT NULL, title TEXT NOT NULL, thinking_enabled INTEGER NOT NULL, " +
                        "reasoning_effort TEXT NOT NULL DEFAULT 'default', history_json TEXT NOT NULL, " +
                        "applied_runtime_run_ids_json TEXT NOT NULL, roleplay_json TEXT NOT NULL DEFAULT '', " +
                        "revisions_json TEXT NOT NULL DEFAULT '', last_model_usage_json TEXT NOT NULL DEFAULT '', " +
                        "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, model_id TEXT, " +
                        "is_pinned INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))",
                )
                database.execSQL(
                    "INSERT INTO conversations_sync_new " +
                        "SELECT id, title, thinking_enabled, reasoning_effort, history_json, " +
                        "applied_runtime_run_ids_json, roleplay_json, revisions_json, last_model_usage_json, " +
                        "created_at, updated_at, model_id, is_pinned FROM conversations",
                )
                database.execSQL("ALTER TABLE conversations RENAME TO conversations_sync_old")
                database.execSQL("ALTER TABLE conversations_sync_new RENAME TO conversations")
                database.execSQL("DROP TABLE conversations_sync_old")
                database.execSQL("PRAGMA legacy_alter_table = OFF")
            }
        }
    }

    /** 上游 3.4.1 新增本轮性能统计列（步数、模型/工具耗时、首字与解码指标）。 */
    internal val MIGRATION_33_34 = Migration(33, 34) { database ->
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN step_count INTEGER")
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN llm_ms INTEGER")
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN tool_ms INTEGER")
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN ttft_ms INTEGER")
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN ttft_steps INTEGER")
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN decode_ms INTEGER")
        database.execSQL("ALTER TABLE conversation_messages ADD COLUMN decode_tokens INTEGER")
    }

    /**
     * 上游 3.4.4 新增：拆分缓存写入与压缩归档标识。
     *
     * 上游把它放在 25 → 26 的槽位，本地这条链已被自己的迁移占用，
     * 因此顺延成本地链末尾的一步；旧库升级时同样幂等。
     */
    internal val MIGRATION_34_35 = Migration(34, 35) { database ->
        if (!database.columnExists("conversation_messages", "cache_write_tokens")) {
            database.execSQL("ALTER TABLE conversation_messages ADD COLUMN cache_write_tokens INTEGER")
        }
        if (!database.columnExists("conversation_messages", "compaction_archive_id")) {
            database.execSQL("ALTER TABLE conversation_messages ADD COLUMN compaction_archive_id TEXT")
        }
    }

    /** 35 → 36：MCP 工具目录记录截断原因（上游 #216；本地库版本号高于上游，故改名续接）。 */
    internal val MIGRATION_35_36 = Migration(35, 36) { database ->
        database.execSQL("ALTER TABLE mcp_servers ADD COLUMN tools_truncation_reason TEXT")
    }

    internal val MIGRATION_36_37 = Migration(36, 37) { database ->
        if (!database.columnExists("conversation_messages", "prompt_suggestions_json")) {
            database.execSQL(
                "ALTER TABLE conversation_messages ADD COLUMN prompt_suggestions_json TEXT NOT NULL DEFAULT '[]'",
            )
        }
    }

    /**
     * 37 → 38：Gemini Vertex AI 接入配置（上游 #265；上游编为 27→28，本地版本号更高，改名续接）。
     *
     * 只加列、幂等：若列已存在（例如从上游包直接升级）则跳过，避免重复 ALTER 报错。
     */
    internal val MIGRATION_37_38 = Migration(37, 38) { database ->
        if (!database.columnExists("model_providers", "vertex_project")) {
            database.execSQL("ALTER TABLE model_providers ADD COLUMN vertex_project TEXT NOT NULL DEFAULT ''")
        }
        if (!database.columnExists("model_providers", "vertex_location")) {
            database.execSQL("ALTER TABLE model_providers ADD COLUMN vertex_location TEXT NOT NULL DEFAULT 'global'")
        }
    }

    /**
     * 38 → 39：定时任务两张表（上游 #265 scheduler；上游编为 28→29，本地版本号更高，改名续接）。
     *
     * CREATE TABLE/INDEX 均为 IF NOT EXISTS，天然幂等；与本地 automation 的 agent_tasks 表互不相同。
     */
    internal val MIGRATION_38_39 = Migration(38, 39) { database ->
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS scheduled_tasks (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "prompt TEXT NOT NULL, " +
                "enabled INTEGER NOT NULL, " +
                "trigger_kind TEXT NOT NULL, " +
                "schedule_json TEXT NOT NULL, " +
                "misfire_policy TEXT NOT NULL, " +
                "misfire_window_minutes INTEGER NOT NULL, " +
                "quiet_hours_json TEXT NOT NULL, " +
                "delivery_mode TEXT NOT NULL, " +
                "session_mode TEXT NOT NULL, " +
                "dedicated_conversation_key TEXT, " +
                "model_id TEXT, " +
                "reasoning_effort TEXT, " +
                "capabilities_json TEXT NOT NULL, " +
                "timeout_seconds INTEGER NOT NULL, " +
                "memo TEXT NOT NULL, " +
                "memo_updated_at INTEGER NOT NULL, " +
                "next_run_at INTEGER, " +
                "last_run_at INTEGER, " +
                "last_status TEXT, " +
                "last_summary TEXT NOT NULL, " +
                "consecutive_failures INTEGER NOT NULL, " +
                "auto_disabled_reason TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scheduled_tasks_enabled_next_run_at " +
                "ON scheduled_tasks (enabled, next_run_at)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS scheduled_task_runs (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "task_id TEXT NOT NULL, " +
                "trigger_source TEXT NOT NULL, " +
                "scheduled_for_at INTEGER, " +
                "started_at INTEGER NOT NULL, " +
                "finished_at INTEGER, " +
                "status TEXT NOT NULL, " +
                "summary TEXT NOT NULL, " +
                "notified INTEGER NOT NULL, " +
                "archive_conversation_key TEXT, " +
                "model_label TEXT NOT NULL, " +
                "FOREIGN KEY(task_id) REFERENCES scheduled_tasks(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scheduled_task_runs_task_id_started_at " +
                "ON scheduled_task_runs (task_id, started_at)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scheduled_task_runs_status " +
                "ON scheduled_task_runs (status)"
        )
    }

    /**
     * 39 → 40：定时任务「运行时显示进度通知」开关列（上游 #265；上游编为 29→30，本地版本号更高，改名续接）。
     * 幂等：若列已存在（例如从上游包直接升级）则跳过。
     */
    internal val MIGRATION_39_40 = Migration(39, 40) { database ->
        if (!database.columnExists("scheduled_tasks", "show_running_notification")) {
            database.execSQL(
                "ALTER TABLE scheduled_tasks ADD COLUMN show_running_notification INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * 40 → 41 → 42 → 43：空迁移链。
     *
     * 41、42 是此前「回滚 / 恢复」实验为了改写 Room 身份哈希抬上去的纯版本号，DDL 为空，
     * 实体与 40 完全一致。手机上现装的库已经是 43，若还出 40 的包，Room 会判定为降级并
     * 直接抛 IllegalStateException（3.5.0 已刻意去掉 destructive fallback，不会清数据也不会自愈）。
     * 因此这里把版本抬到 43 并保留空迁移，保证 40 / 41 / 42 的库都能沿空链升上来。
     */
    internal val MIGRATION_40_41 = Migration(40, 41) { _ -> }

    internal val MIGRATION_41_42 = Migration(41, 42) { _ -> }

    internal val MIGRATION_42_43 = Migration(42, 43) { _ -> }

    /**
     * 44 → 45：清理一次早前实验包留下的 `plan_json`。
     *
     * 手机上现装的库可能是「计划卡片」实验版建的 44（带 plan_json、没有 ask_*），
     * Room 只认同版本的 identity hash，直接开 44 会报 schema 校验失败。
     * 这里把版本抬到 45，删掉那一列、补齐新列，迁移后可继续沿用旧会话。
     */
    internal val MIGRATION_44_45 = Migration(44, 45) { database ->
        if (database.columnExists("conversation_messages", "plan_json")) {
            database.execSQL("ALTER TABLE conversation_messages DROP COLUMN plan_json")
        }
        if (!database.columnExists("conversation_messages", "ask_json")) {
            database.execSQL(
                "ALTER TABLE conversation_messages ADD COLUMN ask_json TEXT NOT NULL DEFAULT ''"
            )
        }
        if (!database.columnExists("conversation_messages", "ask_answer")) {
            database.execSQL(
                "ALTER TABLE conversation_messages ADD COLUMN ask_answer TEXT"
            )
        }
    }

    /** 43 → 44：加两列承载「模型提问卡片」及其已答内容。旧库逐列补齐，可重复执行。 */
    internal val MIGRATION_43_44 = Migration(43, 44) { database ->
        if (!database.columnExists("conversation_messages", "ask_json")) {
            database.execSQL(
                "ALTER TABLE conversation_messages ADD COLUMN ask_json TEXT NOT NULL DEFAULT ''"
            )
        }
        if (!database.columnExists("conversation_messages", "ask_answer")) {
            database.execSQL(
                "ALTER TABLE conversation_messages ADD COLUMN ask_answer TEXT"
            )
        }
    }


    /** 按版本顺序排列的全部 fork 新增迁移，注册时一次性挂上。 */
    internal val ALL: List<Migration> = listOf(
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44,
        MIGRATION_44_45,
    )
}

// ── 列探测辅助函数 ────────────────────────────────────────────────────────
// 上游迁移与 fork 迁移都要用；放在包级，EtaDatabase.kt 里保留的迁移可直接调用。

internal fun androidx.sqlite.db.SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info($table)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        var found = false
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                found = true
                break
            }
        }
        found
    }

internal fun androidx.sqlite.db.SupportSQLiteDatabase.columnExists(table: String, column: String): Boolean =
    query("PRAGMA table_info($table)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        var found = false
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                found = true
                break
            }
        }
        found
    }

internal fun androidx.sqlite.db.SupportSQLiteDatabase.conversationColumnExists(column: String): Boolean =
    columnExists("conversations", column)

/**
 * 补齐置顶列，兼容两条迁移链。
 *
 * 上游在 v24 加的是 is_pinned，本地在 v22→23 加的是 pinned；谁都没有时才补本地旧名，
 * 具体叫法留到最后由 MIGRATION_32_33 统一。
 */
internal fun androidx.sqlite.db.SupportSQLiteDatabase.ensurePinnedColumn() {
    if (!columnExists("conversations", "is_pinned") && !columnExists("conversations", "pinned")) {
        execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

