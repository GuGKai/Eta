# Fork 改动规范（本地 fork 专用）

Eta 的 fork 采用「上游一间、fork 一间、COUI 自己一栋」的落位方式。目的是让每次同步上游时冲突面最小。
本文件是 fork 自有的，上游不会改动它；`AGENTS.md` 里只有一处指针。

## 🧱 落位规则

1. **文案**：fork 专属字符串一律写进 `app/src/main/res/values*/strings_local.xml`，键名以 `local_` 开头。
   上游的 `strings.xml` / `strings_*.xml` 保持与上游逐字一致，不得加入 `local_` 键。
2. **fork 逻辑**：写进对应的 `*Local.kt` 挂载点，不塞回上游同名文件。
   已建立的挂载点：`SettingsScreenLocal`、`SettingsDataStoreLocal`、`MarkdownContentLocal`、
   `EtaDatabaseMigrations`、`AgentAccessibilityServiceLocal`、`SpeechAudioLeaseLocal`、
   `SpeechPlaybackControllerLocal`、`ConversationPanePanelLocal`、`AgentChatBodyLocal`、
   `AgentAppShellLocal`、`AgentAppStateLocal`、`AgentModelPickerLocal`、`AgentBrowserLocals`、
   `AgentLocalTools`、`LocalFileToolBackend`、`LocalNetworkPermission`、`AppearanceThemeLocals`、
   `AssistantOverlaySettingsScreenLocal`、`ConversationTitleSettingsScreenLocal`、
   `ItemDescriptionDialogLocal`、`SubagentSettingsScreenLocal`、`SubagentActivitySheetLocal`、
   `RemoteModelImportSheetLocal`、`ScheduledTaskEditorScreenLocal`、`OptionalModelPickerContentLocal`。
   新增挂载点时同步更新本清单。
3. **数据库**：迁移只写 `EtaDatabaseMigrations.kt`；`EtaDatabase.version` 只升不降；
   升版本必须同时提供对应 `MIGRATION_x_y`，并在出包前核对已装机 versionCode。
4. **COUI**：组件改动落在 `vendor/coui/` 源码 fork 内；Eta 侧封装放 `ui/components/coui/`；
   不得新增外部 `com.itos.coui` 依赖（会与 vendor 源冲突）。
5. **双主题**：改 COUI 侧必须问用户是否同步 Miuix 侧，反之亦然。
   带 `isCoui` 分支的共享文件（`AgentChatBody`、`ConversationPaneChrome`、`SettingsScreen`、
   `AgentModelPicker`、`AppearanceSettingsScreen` 等）改动时要明确告知用户两边都会生效。
6. **上游同步**：先在 `eta-plan`（`feat/plan-card`）合并并验证，再搬回 `eta-work`（`feat/local`）；
   单测对照基线——2237 用例、44 个环境性失败，多出任何一个失败即视为回归。
7. **出包**：正式包走 release + R8；`proguard-rules.pro` 中的 `-dontoptimize` 与
   `AgentFileReference*` 的 keep 规则不得删除；versionCode 用日期编码并通过 `-Peta.versionCode` 覆盖。
   验证阶段可出 debug（release 签名），用户最终要求 release。

## 🧊 冻结区（不跟上游）

- 主界面顶栏：`AgentTopBarChatHeader`（现居 `AgentModelPickerLocal.kt`）及其在 `AgentAppShell.kt`
  的调用点只保留标题，Miuix / COUI 双主题定稿（2026-11-27 用户决定）。
  上游日后在此区域加新功能一律不跟，合并时直接保留本地版本；
  `modelCount` 的删除与顶栏三参数瘦身都是有意为之，不要再当问题提出。

## 🚫 提交红线

- `git commit` / `git push` 每次都要当次明确同意，报告内容、分支、提交信息。
- 装 APK 每次单独同意；覆盖安装 Eta 自身必须延期到回复呈现之后。
- `backup/pre-*` 分支不得删除；`main` 只跟随上游。

## ✅ 自动检查

```sh
sh scripts/check-fork-spec.sh          # 检查工作区
sh scripts/check-fork-spec.sh --staged # 检查暂存区（pre-commit 钩子用）
```

建议每个克隆都装一次钩子：

```sh
sh scripts/install-fork-hooks.sh
```
