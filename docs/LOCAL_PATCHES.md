# Eta 本地改动清单（fork: GuGKai/Eta）

- 上游基线：**v3.3.5**（`Mangi-11/Eta` @ `6bedba98`，2026-10-28 合并）
- 本地工作分支：`feat/local`（单一工作分支）
- 最后更新：2026-10-28
- 出包版本：versionCode `2026102802`，versionName `3.3.5`
- 本次同步备份分支：`backup/pre-sync-20261028-1325-main`、`backup/pre-sync-20261028-1325-feat-local`（长期保留）

## 本次同步（2026-10-28，上游 3.3.4 → 3.3.5）

上游 6 个提交（`d1012a0a`…`6bedba98`）：Subagent 并发派发与独立模型配置、运行浮窗前台策略与结果卡自动收起、
后台任务 Live Updates 与 GUI 控制权解耦（API 36 通知提升）、工具结果摘要拆分与时间位置展示、Subagent 测试约定、3.3.5 版本。
118 个本地提交全部重放；本轮上游**没有数据库 schema 变更**，不需要写迁移。

### 冲突处理（9 个文件）

| 文件 | 处理 |
|---|---|
| `AgentExecutionService.kt` | 上游改用 `SDK_INT >= BAKLAVA` + `extras["android.requestPromotedOngoing"]`，彻底替代本地「36.1 → 36.0 门槛」补丁 → 丢弃该本地提交；短文本仍用本地 `liveTextLabel(...)` |
| `AgentRuntimeService.kt` | 本地 `instance` 统一为上游 `activeInstance`；保留本地「每个 run 一张通知卡」`updateRunStates(notices)` 与 `pauseFromNotification(runId)`，并入上游 GUI 解耦逻辑 |
| `AgentModelClient.kt` | 工具目录过滤改三段串联：`restrictedToolNames` → `AgentBatchToolCatalog.project` → 上游 `toolFilter`；`ToolResult` 同时保留上游 `command` 与本地 `stop` |
| `AgentLoop.kt` | 以本地 steering／批量／checkpoint 版 `executeToolBatch` 为骨架，合入上游 Subagent 并发：wave 全为 `subagent` 时走本地线程池并行；补回 `command = result.command` |
| `SettingsDataStore.kt` | 上游 `subagentSettings*` 与本地会话工作区并存 |
| `AgentRuntimeSession.kt` | 保留本地 `recoveryProjection()` 命名 + 上游 `SubagentProgress` 去重 |
| `AgentEventRecoveryProjection.kt` | 合并为单一 `recoveryProjection`；本地已删除网页建议事件，故不再过滤 `SearchSuggestions` |
| `AgentPromptBuilder.kt` | 保留本地 memory_write 措辞；补回上游 Subagent 系统提示、终端 `common_tools`／`profiles` 说明与「不要用 & 起后台进程」 |
| `app/build.gradle.kts` | versionCode 按约定先取上游再递增 |

丢弃的本地提交：`e3cd36b4`（流体云门槛 36.1 → 36.0），被上游 `2c648690` 覆盖。

### 编译与测试

- `:app:compileReleaseKotlin` 通过（仅既有 deprecation 警告）。
- 单测 1953 个：492 失败全部落在两类，均非本轮引入——① 本机 aarch64 跑不了 Robolectric（含上游新增的 `SubagentTest`、`AgentRuntimeScreenOnTest`）；
  ② `AgentBatchToolTest` 6 个既有失败（在 `backup/pre-sync-20261028-1325-feat-local` 上同样失败，已用 worktree 复核）。
- 为让上游新增测试可编译，`AgentExecutionServiceTest` 的 `updateRunStatus` 调用改为本地 `updateRunStates(notices)`。

### 注意

- 本地删除过 `supportsSearchSuggestions` 字段与 `AgentEvent.SearchSuggestions`，上游 `SubagentExecutor`／`RuntimeConfigRepository` 的对应传参已删除；下次上游再动这块要一起看。
- 本地裁剪 `MainActivity`（拆分版）曾是上游改动最容易丢的点：`startedInstances`／`isInForeground` 这次是手工补回的，`AgentRuntimeService` 依赖它判断 Eta 是否前台。
- 用整文件 `git checkout --theirs/--ours` 解冲突会丢该文件里其它已自动合并的上游改动，本轮 `AgentPromptBuilder`、`MainActivity` 都踩到，必须事后逐条比对。

## 本轮入库（2026-10-28，工作区遗留改动一并提交）

工作区里攒下的未提交改动（含 2026-10-25～27 出过的包）本轮全部入库，按主题拆成 6 个提交：

| 提交 | 内容 |
|---|---|
| `c8e8258` | 含 base64 内联图片的超长 `contentJson` 直接跳过解析（ColorOS 的 `org.json` 会抛 `ClassCastException`），解析链路整段兜异常 |
| `2a4775e` | 记忆/技能写入立即生效：`memory_write`、`skills_manage` 的提示词与工具描述去掉「待审批」措辞 |
| `b1e8fa1` | 通知按 run 分卡片：`ExecutionRunNotice`、`updateRunStates`、`drainIds`，动作携带 `EXTRA_RUN_ID` 只作用于本卡片；纯后台运行也提醒回复完成；插话回退运行时注册表 |
| `7dba01d` | `AgentAppState` 登记 `currentRunId`／`runConversationIds`，回合结束解绑 |
| `17d401f` | 朗读与高亮：代码块/行内代码照读、表格逐格断开、紧凑文本定位（窄空格不再吃掉句尾标点）、短残句不跨行合并、`MarkdownSpeechHighlight` 改多段 ranges、单句上限 120→80 |
| `e3bcff2` | 系统引擎逐句合成的领先量 `PREFETCH_CLIPS` 2→3 |
| `881485a` | versionCode `2026102614` |

朗读侧的调试要点（下次动它先看这段）：

- 朗读文本与渲染文本的唯一契约是「去掉所有空白后字序一致」，定位在紧凑文本上做；
- 表格单元格之间是换行（每格一句），跨格拼句的能力（多段 ranges）保留但默认不再触发；
- 切句合并只在同一行内进行，否则短标题会被并进上一段、整块拿不到高亮；
- 界面高亮用 `rememberSpeechSentences`，与播放侧共用 `SpeechText.sentences`，改切句必须两边一起。

## 本次同步（2026-10-26，上游 3.3.3 → 3.3.4）

上游 11 个提交（`ed022f5`…`de12fc3`）：Gemini 原生 Interactions API、多会话并行与 GUI 阶段互斥、DEX 压缩、
浏览器渲染与地址栏修复、系统提示词身份冲突修复、工具图标与多语言文案、Debian 安装中断恢复、README 与截图。

**按要求排除 Gemini**：对 `cf78436d` 执行 `git revert`（提交 `0555fb5`），不引入 Gemini 提供商、
Interactions 协议实现、Google 远端模型目录与思考档位，也不改备份 schema 的 Gemini 部分。
104 个本地提交全部重放（`git log main..feat/local` 仍为 104 + 2 个同步提交）。

关键取舍：

| 区域 | 处理 |
|---|---|
| 运行时 | 一律取上游多 run 架构（`AgentRuntimeService`/`RunExecutor`/`Session`）；本地单 session 时代的 `activeSession`、`entrySurfaceGuard`、原子岛 `islandBridge` 调用点丢弃（字段保留但不再被调用） |
| 虚拟屏 | 12 个虚拟屏提交全部取上游侧（本地末尾 `7f2395af` 本就移除虚拟屏，上游 3.3.4 也无此功能），相关符号不再进入产物 |
| 通知 | 取上游 `executionText` + 任务数；本地「已完成 N 步」「暂停中」文案、`runPaused`、步骤计数丢弃；保留彩色小图标、固定 `when`、400ms 发布节流、`BAKLAVA` 门槛（本机 ColorOS 16.1 必需） |
| 插话 | 取上游 `requestSupplement(runId, text)`，补回 Boolean 返回值供 `MSG_STEER_RESPONSE` 的 ack 使用 |
| 自动任务 | 保留：`admitRun` 内补回 `AgentTaskScheduler.isRegistered(request.runId)` 的 `automated` 判断 |
| 默认提示词 | 保留本地 `buildRuntimeConfig(defaultAssistantSystemPrompt)` 分支 |
| vivo | 本地 `01b86a0c`「剔除 vivo 一套」生效，README 里 vivo 两条同时移除 |
| 文档 | `docs/*.md` 冲突一率取上游，本文件在同步后重写 |
| 数据库 | 上游 3.3.4 未改 Room schema，本地 23→30 迁移链保持现行为 v30 |

被上游多 run 架构覆盖的本地补丁：通知「已完成 N 步」「暂停中」文案、`entrySurfaceGuard` 入口浮层关闭、
原子岛通知集成（`islandBridge` 字段保留但不再调用）。

**PR #142 遗留（提交作者 乐嘉伟，非原作者、非本地自研）**：虚拟屏全链路、经验学习触发点、通知中心。
本 fork 早已分步清理——`bc4a9e30`（移除经验学习与通知中心）、`01b86a0c`（剔除 vivo 一套）、
`7f2395af`（移除虚拟屏）。本次 rebase 时这些提交的冲突一律取上游侧，验证后代码内已无残留
（`*VirtualScreen*`、`*Experience*`、`VirtualScreenUiTools`、`AgentExperienceReview` 均为 0 处）。

编译修复（`5c67071`）：移除 Gemini 后清掉 `recoveryProjection`/`requestSupplement`/`activeSession` 残留；
恢复 `currentRunId`/`currentRunJob`/`runConversationIds` 字段；通知服务改回 `runStatus == Paused` 判断；
`config.copy(supportsSearchSuggestions)` 回退为 `config`；`app/build.gradle.kts` 去掉冲突标记。

## 本次同步（2026-10-22，上游 3.3.1 → 3.3.3）

上游 14 个提交（`ef0a1b3`…`5e9d3a7`）：消息排队与插话发送、历史会话内存优化与分页恢复、Gemini/MiniMax 语音播报、
可关闭语音识别、自动朗读开关移入更多菜单、网页搜索/读取可切 DeepSeek 与自定义接口、Codex/Grok 订阅登录与远端模型同步、
回复与 GUI 执行期间屏幕常亮、新图标与移除自定义启动动画、仅打包 ARM64。

本地 92 个提交重放，**丢弃 2 个**（`61f9975`、`4b4aa9a` 启动/会话加载优化——上游已用索引行 + 分页 + 启动请求重构覆盖）。

关键取舍：

| 区域 | 处理 |
|---|---|
| 插话 | **保留本地运行时插话**（`MSG_STEER_RUN` → `AgentLoop` 注入补充文本、跳过未开始动作）。上游「排队 + 长按插话」的队列条 UI 已移除（`AgentChatInputBar` 不再渲染 `AgentChatQueueStrip`）；点击发送在流式中仍走本地 steer，不会排队 |
| 通知插话入口 | 保留：`ACTION_INTERJECT` 经 `MainActivityLaunchRequests.interject` → `AgentAppRoot.interjectRequested` → `requestComposerFocus()` |
| 语音 | 上游 Gemini / MiniMax 与本地系统 TTS（MultiTTS）**共存**：`TtsProvider` = NONE/QWEN/DOUBAO/GEMINI/MINIMAX/SYSTEM，各 when 分支补齐 SYSTEM；朗读暂停/继续、聆听压音量保留 |
| 网页 | 上游可配置后端（BUILTIN / DEEPSEEK / CUSTOM）保留；本地内置搜索的 DuckDuckGo → Bing RSS 回退与单次 8 秒超时并进 `searchBuiltInWithFallback` |
| 启动/会话加载 | 全取上游（索引行、分页、`MainActivityLaunchRequests`） |
| 运行服务 | 合并：上游 `updateKeepScreenOn` / `setKeepScreenOn` 常亮 + 本地 `refreshGlowFor` 边缘光、`pauseFromNotification`/`resumeFromNotification`、`CAPSULE_OVERLAY_ENABLED` |
| 会话存储 | 上游分页 + 本地 `pinned`、`lastModelUsage`（顶栏胶囊）：`ConversationIndexRow` 与 `conversationMetadataRow` 补 `pinned`、`last_model_usage_json` 列 |
| 数据库 | 上游仍是 v23、无新迁移；本地 23→29 链保留，`MIGRATION_22_23` 额外补 `model_providers.auth_mode`（上游 3.3.2 同版本号新增），`version = 29` |
| 输入栏 | 采用上游结构（含语音/模型按钮、长按插话），并补回本地 `focusInputRequest` / `onFocusInputHandled`（新建对话自动聚焦）；队列条移除 |
| 侧栏/工作区/顶栏胶囊 | 保留本地：置顶、工作区（会话文件夹）、顶栏余额/累计胶囊、侧栏原作者版式 |

编译修复：`AgentChatBody` 与 `AgentChatInputBar` 参数对齐、`EtaVoicePanel` 键盘判定、三处语音设置 when 穷尽性、
`EtaDatabase` 重复 `MIGRATION_21_22/22_23` 去重、`AgentAppState` 多余的类闭合大括号。


## 零之四、侧栏回归原作者版式 + 新增工作区（2026-10-15，出包 `2026101501`）

需求：把侧栏里 PR #142 的版式改动全部剔除、只保留本地自己的改动；并在「置顶」上方加一条工作区栏
（右侧加号新建；工作区即文件夹，可重命名 / 删除，可把会话放进去，点击标题行展开或折叠）。

做法：

- `ConversationPanePanel.kt` 回退到上游 `main` 的版式（顶部搜索栏 `PaneActionBar` + 底部图标条 `PaneDock`），
  在基线之上重放本地自己的两处改动：置顶分组（置顶行底色、长按菜单里的置顶项、置顶分组可折叠）。
  PR 的品牌头、快捷入口（角色 / 权限 / 更多选项）、底部搜索 + 设置按钮、模式徽标随之不复存在。
- 新增工作区：`WorkspacesSection` 渲染在置顶分组之上，标题行右侧 `+` 新建；每个工作区一行
  （文件夹图标 + 名称 + 会话数 + 折叠箭头），点击标题行折叠 / 展开，长按弹菜单重命名 / 删除；
  会话行的长按菜单新增「移动到工作区」，弹窗里选工作区或「移出工作区」。
- 数据持久化走 `SettingsDataStore` 的 `conversation_workspaces_v1`（`ConversationWorkspaceState`，JSON）：
  存工作区列表、会话归属与折叠状态。**不占 Room 表、不做数据库迁移**。
- 已归入工作区的会话只在文件夹里出现，不在下方日期分组重复；搜索时把结果平铺，避免命中项被折叠藏起来。
- 删除 PR 遗留且已无引用的字符串 `sidebar_more_expanded` / `sidebar_more_collapsed`。
- 参数链 `AgentAppRoot → AgentAppShell → ConversationSidePaneScaffold → ConversationPanePanel` 新增
  `onCreateWorkspace` / `onRenameWorkspace` / `onDeleteWorkspace` / `onWorkspaceCollapsedChange` /
  `onConversationMoveToWorkspace`；移除只服务于 PR 徽标的 `onOpenRootSettings` / `onOpenAccessibilitySettings`。

出包：versionCode `2026101501`，产物 `/workspace/eta-3.3.1-2026101501-sidebar-workspaces.apk`。

## 零之三、侧栏清掉 PR #142 入口 + 顶栏余额胶囊合一（2026-10-14，出包 `2026101403`）

用户要求：侧边栏里 PR #142 引入的东西一律移除，通知中心与经验学习也一并清掉；顶栏两枚胶囊合并为一枚、
只显示「余额/累计消费」，长按可改累计消费。

| 位置 | 处理 |
|---|---|
| 侧栏「通知中心」入口 | 删除。该入口在 PR 部分合并时被带回，但 `ConversationSidePaneScaffold` 从未传 `onOpenNotifications`，落到默认 `{}`，点了没反应，是死链 |
| 侧栏「任务」入口 | 删除（PR 的自动任务入口）；自动任务页本体保留，仍可从**设置 → 自动任务**进入 |
| 侧栏 Root / 无障碍 / 基础 模式徽标 | 删除（`PaneModeBadges`、`PaneModeBadge` 及 `sidebar_mode_*` 字符串） |
| 参数链 | `onOpenTasks` / `onOpenNotifications` / `notificationCount` 从 `ConversationPanePanel`、`ConversationSidePaneScaffold`、`AgentAppShell`、`AgentAppRoot` 一并删除；`PaneShortcut` 去掉角标参数 |
| 经验学习 / 通知中心残留 | 本轮再核对：`LearningProposal*`、`AgentExperienceReview`、`NotificationCenter*`、`AppRoute.Notifications` 均已无引用；剩余字符串 `inbox_title` 一并删除 |
| 顶栏账号胶囊 | `AccountBalanceCapsules` 由「余额、累计」两枚改为**一枚**，文本为 `余额/累计消费`（如 `43.83/306.56`，两位小数、不带货币符号与文字标签）；点击刷新，**长按**弹出校准累计消费；无障碍描述仍说明两个数字的含义 |

出包：versionCode `2026101403`，产物 `/workspace/eta-3.3.1-2026101403-sidebar-clean-capsule.apk`。

**2026-10-15 追加修复（出包 `2026101404`）**：合并上游 3.3.1 时顶栏被重写，`conversationStatus` 只剩参数，
`AgentTopBar` 里**没有任何渲染处**，胶囊整块消失（这就是用户说的「余额胶囊没有了」，与 Key、快照无关：
`builtin-deepseek` 有 35 字符 Key、DataStore 里有 `deepseek_balance_snapshot_v1` 快照）。
已在首页顶栏 `actions` 首位补回 `conversationStatus?.invoke()`，出包
`/workspace/eta-3.3.1-2026101404-topbar-capsule-fix.apk`（versionCode `2026101404`），已覆盖安装并实测顶栏显示。

## 零之一、本次同步（2026-10-14，上游 3.3.0 → 3.3.1）

上游新增 16 个提交（`3b21d9f`…`213e8ed`）：终端 PTY 重构与持久后台命令、工具执行简化、模型选择器与提供商弹窗重做、
聊天自动朗读与整篇流式语音合成、角色头像与酒馆界面重构、外部入口结果卡片回到对应会话、手势经过状态胶囊自动让路、
公式预处理死循环修复等；`v3.3.1` 标签指向 `a7fdfc2`。

本地 38 个提交（PR #142 剩余部分 + 本地特性）重放到新 `main` 上，逐条解决冲突，**没有丢弃任何本地提交**。

关键取舍：

| 区域 | 处理 |
|---|---|
| 运行浮层 / 通知（`AgentRuntimeService`、`AgentExecutionService`） | 以上游 3.3.1 为新基线（胶囊 + 边缘流光重写），本地只并回「虚拟屏任务不在手机上显示叠层」的判定与本地阈值放宽（36.1 → 36.0） |
| 终端 / 工具目录 | 全取上游（`runMessageProjector` 统一事件投影、`AgentToolCallValidator` 已删、批量执行保留） |
| `AgentLoop` | 本地插话与虚拟屏恢复保留；工具校验改成「是否在本轮能力目录中」；虚拟屏恢复改走 `executeToolBatch` |
| `AgentPromptBuilder` / `AgentModelClient` | 上游新增 `executionContext`（终端执行上下文）与 PR 的 `virtualScreenEnabled` / `virtualScreenFallbackEnabled` 撞同一参数位 → 三者并存、调用点一并串上 |
| 记忆页 | 上游重做为 `MemorySettingsGroup` + `AgentMemoryStore`，本地的自动记忆 / 自动技能开关并进该结构，`setAutomaticReview` 落在 store |
| 会话状态 | 上游 `modelId` / `contextDeferred` 与 PR 的 `lastModelUsage`（顶栏胶囊）并存；记忆页不再搬回 `AgentAppState` |
| 输入栏 | 上游结构为准，只并回插话提示与停止按钮；语音 / 模型按钮按补丁 #21 保持移除 |
| 侧栏 / 工具页 | 本地折叠分组、模式徽标保留；`AgentToolsScreen` 取上游版本 |
| 数据库 | PR 迁移重排到本地 v23 之后：23→24 自动任务表、24→25 请求耗时、25→26 模型用量、26→27 `error_code`；新增 **27→28 兼容迁移**（逐列 / 逐表补齐 `pinned`、`model_id`、`last_model_usage_json`、`request_duration_ms`、`error_code`、自动任务表），兼容装过 PR 自带包（`user_version` 25 / 27）的库。`EtaDatabase.version = 28` |
| 经验学习 / 通知中心 | PR 那部分本地不保留，随本地 `chore(pr142)` 提交移除；合并 3.3.1 后清掉残留引用（`LearningProposalEntity` 注册、`learningProposalDao`） |
| 语音 | 上游整篇流式合成保留；本地补丁 #5 的系统 TTS 分支在 `streamSpeech` 里补出 `TtsProvider.SYSTEM`（系统引擎不走流式合成） |

出包：versionCode `2026101402`（上游 3.3.1 已占用 `2026101401`），versionName `3.3.1`。

> 本文件记录 fork 相对上游原版的全部改动。上游每发新版、本地提交重放（rebase）之后
> 提交号会变化，**请以「提交标题」为准**；SHA 只对应当前基线。
> 自查：`git log --oneline main..HEAD`、`git diff --stat main..HEAD`

## 零之二、PR #142 部分合并（2026-10-11，分支 `feat/pr142-merge`）

来源：`Mangi-11/Eta` PR #142（`lejw0925:feat/virtual-screen-automation-vivo`，head `716caf0`，38 提交，
+22180/−607、288 文件）。**只取三块功能**，其余（vivo 接管、经验学习/审批、批量执行）不纳入或已剔除：

| 纳入 | 说明 |
|---|---|
| Root 虚拟屏 | 新增 `agent/display/` 整包（会话、截图、输入注入、查看页、悬浮预览、模式徽标），UI 工具按 display 路由；需 Root + Android 14+，opt-in |
| 自动任务 | 新增 `agent/automation/`（Room 表 `agent_tasks`/`agent_task_runs`、AlarmManager/JobScheduler、触发器、运行历史）+ 侧栏「任务」入口 |
| 实时插话 | `AgentLoop` 中断与跳过未开始动作、输入框插话/停止按钮、`LaunchedEffect(input)` 延迟回填 |

剔除/未纳入：

- **经验学习与通知中心**（PR 的 #106）：删除 `LearningProposal*`、`AgentExperienceReview`、
  `NotificationCenterStore/Screen`、`AppRoute.Notifications`/`LearningProposalDetail`、侧栏通知计数、
  `LearningProposalRepositoryTest`/`ExperienceReviewPolicyTest`；`memory_write`/`skills_manage` 恢复直写。
- **vivo 接管 / 小 V 原子岛 / 原子笔记上下文**：已全部剔除（2026-10-11 第二轮）：删除 `hook/vivo/*` 12 个文件、
  `VivoPersonalContextQuery`、`AgentRuntimeIslandBridge`、Xposed 作用域里的 `com.vivo.ai.copilot`、
  `ModuleConfig.VIVO_COPILOT_PACKAGE`、`Prefs` 的 vivo 旧键别名、工具能力里的 `vivo`/`vivoAlternative`、
  `PhoneCommandMain` 的 `vivo_personal` 后端、注入端 16 条 `injected_vivo_*` 文案与相关测试；
  厂商助手接管仍保留小布（Breeno）与小爱通道。
- **批量执行**（`AgentToolBatchPlanner` 等）：随 GUI 效率改动一并保留，未单独接入说明。

冲突取舍（12 处）：

| 文件 | 取舍 |
|---|---|
| 侧栏 `ConversationPanePanel` | 保留本地设计（置顶折叠、分组标题、底部 Dock、精简输入栏）；PR 的模式徽标 + 「任务」入口当时补入，**2026-10-14 已全部移除**（见零之三） |
| `AgentChatInputBar` | 保留本地的聚焦/退后台清焦点效果，补入 PR 的插话停止按钮；**不恢复**语音/模型按钮（本地补丁 #21） |
| `AgentAppShell` | 新建对话按钮（本地）与运行状态胶囊（PR）并存 |
| `AgentExecutionService` | 保留本地通知样式（去重/节流/固定 when） |
| `*ConversationPersistence/Store/Dao/Entities` | `pinned`（本地）与 `lastModelUsage`/`request_duration_ms`（PR）并存 |
| `AgentAppState` | 归档会话 id 改用 `AgentExternalArchivePayload.conversationId`（与本地 private 实现语义一致，去重） |

**数据库升到 v28**（本地 23 保持不变，PR 的迁移重排到其后）：

```
21→22 model_id（上游）       22→23 pinned + model_id（本地，逐列判断）
23→24 agent_tasks/agent_task_runs 表        24→25 conversation_messages.request_duration_ms
25→26 conversations.last_model_usage_json   26→27 runtime_results/archive_runs.error_code
27→28 兼容装过 PR 自带包（3.3.1-beta，user_version=27）的库：逐列/逐表补齐 pinned、model_id、
      last_model_usage_json、request_duration_ms、error_code、自动任务表
```

## 零、本次同步（2026-10-07，上游 3.2.0 → 3.3.0）

上游 3.3.0 自己重写了「运行浮层 + 通知」整块（顶部状态胶囊、边缘流光、状态栏/锁屏实时活动、
结果卡片回到会话），与本 fork 早期的同类改动正面重叠。按既有约定（上游已覆盖 → 丢弃本地提交），
本次 rebase 中**丢弃 24 个本地提交、改写 1 个**：

| 本地原提交 | 处理 | 原因 |
|---|---|---|
| `feat(notification): 回复完成通知与执行中流体云实时活动` | **改写**（现标题 `feat(notification): 回复完成通知`） | 只保留「回复完成通知」；其中的执行中实时活动、胶囊文案部分由上游 `eb5fada`、`31cbdcc` 自行实现 |
| `feat(notification): 实时活动与前台通知改为显示当前环节名称` | 丢弃 | 上游胶囊自己显示「操作中/回答中」（`execution_chip_running` 等） |
| `fix(overlay): 氛围光贴合真实屏幕圆角`、`fix(overlay): 氛围光只在执行中且界面处于前台时显示` | 丢弃 | 上游 `31cbdcc`/`f1a8ed1`/`3b81492` 重做浮层：状态胶囊 + 边缘流光 + `getRoundedCorner` 圆角 |
| `feat(notification): 实时活动加「停止」按钮…` + 其 `revert` | 丢弃 | 净零改动，且上游卡片已换新实现 |
| `feat(notification): 实时活动卡片改用自定义内容视图…` + 其 `revert` | 丢弃 | 净零改动（该方案与 promoted ongoing 互斥，已实测撤回） |
| `fix(agent): 补齐 buildRuntimeConfig 的工具开关，浮窗不再丢终端工具` | 丢弃 | 上游 `20cec7d` 在 `AgentRuntimeRequestConfigResolver` 层修好了同一问题（保留入口工具开关） |
| `fix(runtime): 入口侧断开不再连带取消任务` + 其 `revert` | 丢弃 | 净零改动 |
| `chore(ci): 临时 workflow 增加签名密钥导出` + 其移除提交 | 丢弃 | 净零改动（临时 CI 方案） |
| 11 个 `chore(release): versionCode …` 出包号提交 | 丢弃 | 版本号由本次同步末尾统一递增为 `2026100708` |

随之落地两处必要改动：

1. **数据库升到 v23 + 条件迁移**：上游 3.3.0 的「21→22」补 `model_id`（会话记住上次模型），本 fork 的
   「21→22」补 `pinned`（置顶），版本号相同而内容不同；设备上已装的库只带 `pinned`。现在
   `MIGRATION_21_22` 用上游语义（`model_id`），新增 `MIGRATION_22_23` **逐列判断后再补**（缺
   `pinned` 补 `pinned`、缺 `model_id` 补 `model_id`），三种来源（本地旧库 / 纯上游库 / 混合）都不会
   撞 `duplicate column name`。`EtaDatabase.version = 23`。
2. **versionCode 取大**：上游 3.3.0 用 `2026100701`，本机已装 `2026100706`，本次出包取 `2026100708`。

## 一、功能改动

| # | 功能 | 提交标题 | 具体改动 |
|---|---|---|---|
| 1 | 回复完成通知 | `feat(notification): 回复完成通知` | 新增 `AppForegroundState`（本应用 UI 是否可见）、`MarkdownTextStripper`；`AgentRuntimeService` 在 run 成功后发独立渠道 `eta_reply_done` 通知（id 1108、正文 60 字预览、BigText），用户就在 Eta 界面里时不打扰 |
| 2 | 会话置顶与侧栏面板操作 | `feat(conversation): 会话置顶与侧栏面板操作` | `ConversationEntity`/`ConversationDao`/`EtaDatabase` 加 `pinned` 列（现为 v23 条件迁移）；`Snapshot` 加 `pinned`；存储层维护置顶集合并按置顶优先排序；侧栏面板支持置顶/取消置顶 |
| 3 | 最终正文停靠 | `feat(chat): 最终正文停靠` | `AgentChatBody`：最终回复结束后正文停靠在顶部 |
| 4 | 后台完成回复也保证停靠 | `fix(chat): 后台完成回复时也保证最终正文停靠` | 应用不在前台时完成的那轮回复同样停靠 |
| 5 | 系统 TTS 播报后端 | `feat(speech): 新增系统 TTS 播报后端，可选用 MultiTTS 等设备语音引擎` | 新增 `SystemTtsSpeaker`、`SpeechSystemTtsSection`；`SpeechSettings` 增加系统引擎/音色字段；播报走 `speakWithSystemEngine` 分支（非系统引擎仍走上游 `playChunks`） |
| 6 | 新建对话提到顶栏 + 自动聚焦 | `feat(ui): 新建对话入口提到顶栏，并新建后自动聚焦输入框` | 顶栏自绘加号图标取代溢出菜单项；`composerFocusPending` 一次性信号触发聚焦并拉起键盘 |
| 7 | 空首页精简 | `feat(ui): 精简空首页，移除建议卡片并放大问候语` | 移除建议卡片，问候语放大到 `headline1 × 1.3f` |
| 8 | 关窗后回答在后台续跑 | `fix(assistant): 退出浮窗后回答在后台继续，回答途中可随时接管到本体` | 关窗只收回窗口并置 `runOutlivesEntrySurface`，run 归 Runtime 在后台跑完（结果照常归档 +「回复完成」通知），这轮结束服务才 `stopSelf`；`cancelCurrentRun(abortRun)` 对这类 run 不下发取消。**接管部分按第 9 项已退回上游** |
| 9 | 浮窗交回本体恢复上游 | `revert(assistant): 浮窗交回本体的门槛与接管实现恢复上游` | 直接改回上游写法：`canOpenConversation` 要求 `activeRunId == null` 且有正文，`openConversation` 门禁与 Intent 只带 `EXTRA_CONVERSATION_KEY`，`MainActivity`/`AgentAppRoot`/`AgentAppState` 去掉 runId/prompt 透传，`HANDOFF_TIMEOUT_MS` 回 5s。行为：只有这一轮跑完才能交回本体 |
| 10 | 回复完成通知直达助理会话 | `feat(notification): 回复完成通知直达助理会话` | 通知的 `PendingIntent` 带上 handoff 里的 `conversationKey`，点通知进入该助理会话 |
| 11 | 聆听期间压媒体音量 | `feat(speech): 聆听期间压媒体音量，识别结束还原` | `SpeechAudioLease.acquire(muteMedia)`：在播放且媒体音量 > 0 时压到 0，识别结束/取消/出错还原；用户手动调过音量则不覆盖 |
| 12 | 聆听静音改用瞬态焦点 + 盯守 | `fix(speech): 聆听静音改用瞬态焦点并盯守音量` | 申请 `AUDIOFOCUS_GAIN_TRANSIENT`（`setWillPauseWhenDucked(true)`）请播放器让音；仍压 `STREAM_MUSIC`；每 400ms 复检被写回的音量并再压；通道日志 debug → info（release 会剥 debug） |
| 13 | 语音浮窗展示的回答不再弹通知 | `fix(notification): 语音唤醒浮窗展示的回答不再弹完成通知` | `AppForegroundState` 增加 `assistantOverlayVisible`/`hasVisibleSurface`；`EtaAssistantOverlayService` 挂/摘窗口时维护；`notifyReplyCompleted` 改判 `hasVisibleSurface`，关窗后台续跑的 run 仍照常通知 |
| 14 | 聆听静音绕开音量 API 硬化 | `fix(speech): 聆听静音绕开音量 API 硬化` | 新增 `SystemVolumeControl`：公开 API 读回确认，没生效且 Root 已授权时把 `cmd media_session volume --stream <n> --set <i>` 交后台线程执行（Root 未授权则保持原样）。背景：Android 15 起普通应用 `setStreamVolume`/`adjustStreamVolume` 被静默忽略 |
| 15 | 首帧不再等会话全量加载 | `perf(startup): 首帧不再等待会话全量加载` | `AgentAppState` 构造不再同步等全库（`initialConversations` 可空 + `CompletableDeferred` 就绪门）；`AgentConversationStore` 拆出 `loadFromDisk` 与分片快照；`applyConversationSnapshot` 统一应用快照 |
| 16 | 会话预加载提前到 onCreate | `perf(startup): 会话预加载提前到 onCreate，首帧不再闪空会话页` | 新增 `AgentConversationLoader`：`MainActivity.onCreate` 起跑、`await()` 就绪后才 `setContent`。点通知进界面先闪 4 秒空会话页的根因即这条同步加载 |
| 17 | 置顶分组可折叠 | `feat(ui): 置顶分组支持点击标题行折叠` | 侧栏「置顶」分组标题行点击收起/展开（`rememberSaveable`），右侧旋转箭头 180ms；折叠时连分割线一起隐藏；搜索期间强制展开 |
| 18 | 滑动收键盘 + 键盘弹起正文跟抬 | `feat(chat): 滑动消息列表收起键盘，键盘弹起时正文跟着抬升` | 列表拖动且 IME inset 非 0 时 `keyboard?.hide()`（不消费手势）；键盘弹起时正文跟抬：`bottomInset` 变大且尾部哨兵可见时重新锚到底部，翻在历史中间/流式中跳过 |
| 19 | 键盘抬升改平滑位移 | `fix(chat): 键盘抬升改用跟底引擎平滑位移，后台完成的停靠不再补播动画` | 新增 `keyboardLiftSettling`，抬升交给现有跟底引擎（`smoothBottomFollowStep` 指数收敛）；后台完成的停靠直接 `scrollToItem` 不补播动画（新增 `appResumed`） |
| 20 | 键盘抬升提速 | `perf(chat): 键盘抬升改用更快的位移曲线（对齐键盘弹起速度）` | `smoothBottomFollowStep` 开放 `responseSeconds`/`maxSpeedDpPerSecond`（默认仍 0.085s/720dp·s⁻¹）；键盘抬升期间走 0.06s/2000dp·s⁻¹，约 0.2s 走完，对齐系统键盘（约 0.25s） |
| 21 | 精简输入栏 + 移除浮窗三条提示 | `feat(chat): 输入栏移除语音/模型按钮、加号改用主题色，语音浮窗移除三条提示` | 删掉 `SpeechDictationButton`、`AgentModelPickerButton` 与 `EtaAssistantSuggestions.kt`；加号图标 `tint` 改 `primary`。**副作用：应用内不再有切换模型入口**，改模型走设置 → 模型提供商 → 目标模型「设为当前模型」 |
| 22 | 退后台清掉输入框焦点 | `fix(chat): 退到后台时清掉输入框焦点，避免回前台被系统恢复键盘` | `AgentChatInputBar` 加 `LifecycleEventEffect(ON_STOP) { focusManager.clearFocus() }`。装机实测（2026100703）：点输入框 `mInputShown=true` → 回桌面 → 再回 Eta `mInputShown=false`（旧包为 true） |
| 23 | 输入框外边距与屏幕圆角同心 | `feat(chat): 输入框外边距与屏幕圆角同心` | `ChatInputOuterMargin`（左右下三边共用，现 8dp）取代写死的 14/12dp；输入框圆角按「屏幕底角半径 − 边距」推导（读 `Display.getRoundedCorner`，回退 20dp），miuix squircle 会乘 1.1 故先除回去 |
| 24 | 会话页顶栏毛玻璃统一 | `feat(chat): 会话页顶栏统一为设置页的毛玻璃样式` | `AgentAppRoot.RoutedShell` 对 `AppRoute.Home` 不再把顶栏高度算成外边距，顶栏高度传成消息列表 `topInset` 作顶部 `contentPadding`，列表视口铺满整屏，正文能滑到毛玻璃下方 |
| 25 | 会话文字大小滑块 | `feat(ui): 外观和主题新增会话文字大小滑块` | `AppearanceSettings.chatTextScale`（0.8–1.4，默认 1.0）持久化到 `appearance_chat_text_scale`；设置页「界面」分组加滑块（90/100/110/120/130%）；只缩放助手正文（`MarkdownTone.Answer`）与我发出的消息文本 |

## 二、工程与出包相关

| # | 事项 | 提交标题 | 说明 |
|---|---|---|---|
| 26 | 同步脚本入库 | `chore(scripts): 纳入上游同步脚本与实时活动补丁脚本` | `sync-upstream.sh`、`patch_v12.py` 纳入仓库，rebase 时不再是未跟踪文件 |
| 27 | 流体云门槛放宽 | `fix(notification): 流体云/Live Update 门槛从 36.1 放宽到 36.0` | 上游用 `SDK_INT_FULL >= VERSION_CODES_FULL.BAKLAVA_1`（3600001）判断，本机 `ro.build.version.sdk_full=36.0`（3600000）恒假 → 永不申请 promoted。收紧到 `BAKLAVA`（3600000） |
| 28 | 执行通知恢复 fork 旧样式 | `feat(notification): 执行通知恢复「当前环节 + 已完成 N 个步骤」样式` | 上游 3.3.0 覆盖后重做：`AgentOverlayState` 增加 `completedSteps`（`ToolFinished`/`HostedToolFinished` +1、`RunStarted` 归零）；`AgentExecutionService` 标题改为当前环节名（工具本地化名，其余状态用各自文案，不再加 `ETA 👉` 前缀）、正文改为 `work_completed_steps`（已完成 N 个步骤）、小图标改回应用彩色图标、去掉 `ProgressStyle` 进度条、`setShortCriticalText` 取环节名限 4 字；`updateRunStatus` 多带步数，按「状态+步数」变化刷新 |
| 29 | 通知去重 + 节流 + 固定时间戳 | `fix(notification): 执行通知去重与节流，缓解流体云胶囊闪跳` | 实测 SystemUI 把每次通知更新都当一次流体云"发布"交给插件重建（`notifyLiveAlerts: PostAction` + `NotificationLiveAlertProvider-->post sbn`），而一次工具调用会产生 3–4 次刷新。改为 `renderedText()` 比对去重、距上次发布 <400ms 合并成尾发（`MIN_PUBLISH_INTERVAL_MS`），`when` 固定为前台期起点并 `setShowWhen(false)`，停止按钮文案改「停止任务」 |
| 30 | 虚拟屏会话通知语义修正 | `fix(notification): 虚拟屏会话不再冒充运行中的任务，通知点击直达对话` | ① `ExecutionLeaseRegistry` 加租约类型（`TASK`／`KEEP_ALIVE`）与 `taskCount()`：虚拟屏会话登记为保活租约，前台服务继续保活，但不再计入“N 项任务正在运行”；② `AgentExecutionService.renderedText()` 在只剩保活租约时改用新文案「虚拟屏保持中／点按返回 Eta 对话」（`execution_keepalive_title`／`execution_keepalive_summary`，三语）；③ 同处 `notification()` 的 contentIntent 改为只在有 Agent run 时指向 `VirtualScreenViewerActivity`，任务跑完或空闲会话指回 `MainActivity`，不再“按返回才回到对话”；④ `AppForegroundState` 只统计默认 display 上 started 的 Activity：虚拟屏 display 上的 `VirtualScreenHomeActivity`／查看页此前让 `hasVisibleSurface` 恒为真，导致 `notifyReplyCompleted` 把「Eta 已完成回复」通知静默丢弃 |
| 31 | 后台任务被自己取消（进程活着却「已停止」） | `fix(runtime): 执行服务销毁不再取消 run，结果交回会话` | 证据：`runtime_results` 115 条里只有 8 条 `ok=1`，其余全是 `ok=0 + error="已停止" + 空正文`，而进程 PID 一直活着。根因：`AgentExecutionService.onDestroy()` → `leases.drainOwner()` → 前台 run 租约 `onStop` → `session.controller.cancel()`；「已停止」唯一产生点是 `AgentRuntimeRunExecutor` 的 cancelled 分支。改法：`ExecutionLeaseRegistry.detachOwner()`（只摘属主不取消），`onDestroy` 改用 detachOwner；`AgentRuntimeClient` 等待被中断时不再发 `MSG_CANCEL`，改返回 `ERROR_RUN_DETACHED`；`AgentAppState` 取消只 `detachLocalRun()`（NonCancellable），结果由下次进入界面时恢复 |
| 32 | 前台偶发崩溃（OOM） | `fix(permission): 应用列表探测改为逐包，避免 getInstalledPackages OOM` | dropbox `data_app_crash` 指向 `PermissionHealthStore.hasAppListAccess()` 的 `pm.getInstalledPackages(0)` 抛 `OutOfMemoryError`（外层 `catch (Exception)` 抓不住）。改为逐包探测 + `catch (Throwable)` |
| 33 | 余额胶囊居中 | `feat(chat): 金额胶囊移到输入栏正中，长按明细加留白` | `AccountBalanceCapsules` 加 `maxWidth`，`Box.align(Center)` 绝对居中；长按弹窗顶部加 `Spacer(12.dp)` |
| 34 | 记忆页卡顿 | `perf(memory): 记忆页改为分块懒加载` | 整份 MEMORY.md（约 138KB）此前组合进单个 `Column` 一次性测量。新增 `MarkdownBlockPage`（`MarkdownContent.kt`）+ `LazyStaticMarkdown`（`ChatMarkdown.kt`，每页 20 块的 LazyColumn） |
| 35 | 侧栏去底色 + 搜索按钮化 | `feat(sidebar): 置顶\/工作区条目去掉底色，搜索改按钮触发` | `ConversationTextRow` 去掉 `isPinned` 的 `surfaceContainerHigh`；`PaneActionBar` 用本地 `draft` + 右侧搜索图标（新增 `conversation_search_action`）；搜索框叉号清空时一并提交空查询，否则过滤状态退不出去 |
| 36 | 会话自动命名 | `feat(chat): 会话按首轮语义自动命名` | 新增 `agent/model/ConversationTitleGenerator.kt`（关思考、12s 超时、≤13 字）与 `ProviderRequestPurpose.TITLE`；整轮成功后命名并 `refreshConversationSummaries()`；顶栏自绘 `Text`（`body1`、硬截断 11 字、`widthIn(max = 192.dp)`）排在侧栏按钮右侧，未命名时显示「新任务」；命名真正结束前不解除占位，避免顶栏闪回提问原文 |
| 37 | 外部入口会话也命名 | `feat(chat): 语音浮窗\/小布\/小爱\/定时任务的会话导入时补命名` | 这些 run 由 Runtime 直接归档，不经过 `finishRound`，导入时标题直接取 `payload.title`（提问原文）→ 永远显示原文。改为导入归档 run 时，对「标题仍是占位原文且回答非空」的会话补一次模型命名（一次导入最多 8 条，去重 + 在途标记），命名后刷新侧栏并落库 |
| 38 | 「本轮消耗」 | `feat(billing): 每轮回复结束后显示本轮消耗` | 新增 DB 列 `conversation_messages.round_cost`（v29，`MIGRATION_28_29` 只加列）与新 DAO `updateMessageRoundCost`（精确 UPDATE 单行）；整轮结束后 +19s\/+45s\/+70s 各抓一次 DeepSeek 余额（实测约 45s 才结算），一变即结算，三次不变则不显示，避免 90s 轮询把请求数打上去 |
| 39 | 执行通知三按钮 | `feat(notification): 执行通知提供补充\/暂停\/停止任务` | ① 补充（`ACTION_INTERJECT` → `MainActivity` 收起侧栏并拉起键盘，走 steering 通道插话不打断）；② 暂停\/恢复（`AgentRunController.pause()\/resume()`，暂停时通知首行「暂停中」、按钮变「恢复」，由 `AgentOverlayState.phase == PAUSED` 上报）；③ 停止任务；文案三语补 `execution_supplement\/pause\/resume\/paused` |
| 40 | 常驻胶囊停用 + 彩虹光按操作点亮 | `feat(overlay): 停用屏幕常驻胶囊，边缘光只在操作屏幕时亮` | `showOverlay()` 加 `CAPSULE_OVERLAY_ENABLED = false`（窗口辅助函数保留便于回退），终止入口由执行通知承担；彩虹边改由 `glowActive` 驱动，沿用 `AgentOverlayVisibilityPolicy.shouldRevealFor` 的工具集，动作结束后停 `GLOW_LINGER_MS = 10s` 淡出、新操作取消淡出重新点亮（淡入 320ms\/淡出 620ms），虚拟屏保持熄灭 |
| 41 | 彩虹边框越叠越多 + 卡顿 | `fix(overlay): 叠层判据改为边缘光窗口，不再重复挂全屏窗口` | 胶囊停用后 `capsuleView` 恒为 null，`showOverlay()` 的「已挂出」判据失效 → 每次屏幕操作都重新 new 一个全屏 ComposeView 并 `addView`，`glowView` 只记住最后一个。实测 `dumpsys window` 里 Eta 的 `CREATE_ACCESSIBILITY_OVERLAY` 窗口有 **28 个**（正常 1 个）：多份 14dp 光带 + 12dp 模糊叠加看着更宽，多份无限旋转动画拖垮帧率，任务结束也不回收。改为 `if (glowView != null) return`，触感同样只在首次挂出时震一次（修复后实测窗口数回到 1） |
| 42 | 朗读通知抬成实时活动 | `feat(notification): 朗读通知申请 promoted，与执行通知同一条判据` | `ReadAloudPlaybackService` 加 `setShortCriticalText` + `setRequestPromotedOngoing(true)`（判据 `SDK_INT_FULL >= BAKLAVA`，本机 `sdk_full=36.0`）、小图标改应用彩色图标；三语新增 `read_aloud_live_short_text`。appop `POST_PROMOTED_NOTIFICATIONS` 已 allow，权限已在 manifest 声明 |
| 43 | 浮窗语音后台播报（已回滚） | `revert(voice): 撤销浮窗语音会话的完成后台播报` | 曾让「浮窗已收起也把回答读完、读完再收服务」（`backgroundSpeech` + `removeWindow(keepPlayback)` + 等播放结束再 `stopSelf`），实测能播但体验不好，按用户要求连同 `AssistantSpeechForeground` 的通知外观一起还原成上游行为（标题「Eta 正在朗读」、正文「仅在助手浮窗可见时运行」、按钮「停止」、不申请 promoted）。聊天朗读的通知仍走实时活动 |
| 44 | 实测结论：流体云卡片布局不可由应用控制 | `docs: 记录流体云通知卡片布局的实测结论` | 日志 `PluginSeedling--Base` 对应的渲染方是 `SystemUIPlugin.apk`（`com.oplus.systemui.livealert.notification.source.NotificationLiveAlertProvider`）+ `SeedlingSdk.apk`（`com.oplus.seedling.remoteviewsengine`）；卡片尺寸\/模板来自系统下发的卡包（upk）声明的 `support: notification_sm\|notification_md\|notification_lg`，卡包位于 SystemUI 数据目录、用 OPPO 私钥签名并加密（`config.json` 为 hash + RSA-OAEP，`META-INF\/HELLOBIK.RSA`），本地无法伪造；应用侧唯一可传的自定义字段是通知 extras 的 `oplusLiveAlertCustomUi`（模型含 `card.template`、`card.endActionBtns`），但本机这版插件里它只映射到唯一取值 `NORMAL_STYLE`，等于没接线。结论：promoted 通知的按钮只能排在下方；要「左图标＋中文字＋右按钮」只能不申请 promoted |
| 45 | 顶栏会话标题按中文字宽截断 | `fix(topbar): 标题宽度上限按 11 个中文字符实测宽度` | 原来按「字符个数」截断（`TOP_BAR_TITLE_MAX_CHARS = 11` + `topBarTitleText()` 手工截断）：英文、数字、空格本来就比中文窄，长英文标题会被白白砍掉一半。改为常量 `TOP_BAR_TITLE_CJK_WIDTH = 11`，用 `rememberTextMeasurer()` + `LocalDensity` 在当前 `MiuixTheme.textStyles.body1` 下实测 `"中".repeat(11)` 的宽度，`Text` 用 `widthIn(max = 实测宽度)` + `TextOverflow.Ellipsis` 交给 Compose 省略，并删除手工截断函数 |
| 27 | 工作分支统一 | `chore(sync): 工作分支统一为 feat/system-tts` | `feat/raise-backup-limit`、`fix/agent-overlay-glow-corner` 改名 `archive/*` 归档，脚本不再重放 |
| 28 | 工作分支更名 | `chore(sync): 工作分支更名为 feat/local` | `feat/system-tts` → `feat/local`，脚本 `WORK_BRANCH` 默认值同步更新 |
| 29 | 改动清单 | `docs: 新增本地改动清单` | 即本文件 |
| 30 | 本次出包号 | `chore(release): versionCode 2026100708（合并上游 3.3.0 后出包）` | 上游 3.3.0 已占用 2026100701，本机已装 2026100706，取 2026100708 |

## 三、历史丢弃记录（上游已覆盖的本地提交）

| 原提交标题 | 内容 | 丢弃原因 |
|---|---|---|
| `feat(backup): 备份上限提高到 256 MiB 并改为流式导出` | 上限 64→256 MiB、流式导出、`android:largeHeap="true"` | 上游 3.2.0 实现得更彻底：分页 64/页 + `EtaBackupJsonStreams` 流式、上限 512 MiB |
| `chore(release): 本地上游合并版本 versionCode 2026100202`、`chore(release): versionCode 2026100501` | 版本号中间值与撞号 | 由 `2026100502` 取代 |
| 本次同步的 24 个提交（见「零」节表格） | 实时活动/胶囊/氛围光/浮窗工具开关/出包号等 | 上游 3.3.0 已覆盖或本地净零改动 |

## 四、环境事实（执行同步前先核对）

- 源码目录：**Debian Linux 工具环境 `/workspace/eta-work`**（`/workspace/eta-src` 已不存在；`build2.sh` 两个都试，自动挑有 `app/` 的那个）
- 远端：仓库里**只有 `origin` = fork `git@github.com:GuGKai/Eta.git`**，没有配置上游 remote；
  skill 里写的 `origin=上游 / fork=fork` 是旧布局，实际取上游用：
  `git fetch https://github.com/Mangi-11/Eta.git main:refs/remotes/upstream/main`
- 推送：`git push origin main`、`git push --force-with-lease origin feat/local`（历史被 rebase 重写，推之前先 `git fetch origin`）
- 出包脚本：`/workspace/pin-patch/build2.sh`（自签 keystore CN=GuGKai Eta Mod，可覆盖安装保数据）
- Skill `eta-upstream-sync` 正文里的路径与 remote 命名需要按本节更正

## 五、仓库外、设备侧的配套（不属于本仓库改动）

- `pin-patch/build2.sh`：ARM64 手机上的出包脚本；自签 keystore `eta-fork.jks`（CN=GuGKai Eta Mod，SHA-256 `F0:DD:C5:E7…CF:86:A4:82`），与设备已装包同签名，可覆盖安装保数据
- Debian 工具环境中的 Android 构建链（JDK 25、SDK 37、ARM64 aapt2 运行时）
- 设备侧脚本约定：拉起应用/页面用 `am start`，**不要用 `monkey`**（会打开自动旋转）

## 六、同步后的维护

1. 同步流程见 skill `eta-upstream-sync`；脚本会自动创建 `backup/pre-sync-*` 备份分支，用户要求长期保留，不要删
2. 归档分支 `archive/*` 只作历史快照，脚本不重放、**不得用于出包**
3. 发版前递增 `app/build.gradle.kts` 的 versionCode（规则 `yyyyMMdd` + 两位当日序号，且必须大于设备已装值），并回到本文件更新基线版本、更新日期与上述表格
4. 数据库版本冲突时**不要**让两边共用同一条迁移：上游与本地各改一次 `version` 时，升版本号并写「逐列判断再补」的条件迁移

## 构建约定：release 关闭 R8 优化（2026-10-26 追加）

`app/proguard-rules.pro` 末尾固定保留 `-dontoptimize`。原因：release 包在工具调用路径抛
`Attempt to invoke virtual method 'boolean java.lang.Boolean.booleanValue()' on a null object reference`，
定位为 R8 把多个 Kotlin lambda 合并成「单类 + selector 字节」后，`rootAvailable` 这类 `Function0<Boolean>`
分支返回 null 被外层拆箱；debug（不优化）正常，关优化后正常。

- 已验证无效的手段：换 JDK 21 重建（产物与 JDK 25 几乎逐字节相同：同 5 个类、11 处调用、同行号，体积差 8 字节）。
- 代价：APK 6.58 MB → 8.29 MB，运行性能略降；shrink 与混淆不受影响。
- **同步上游时若 `app/proguard-rules.pro` 冲突，必须保留这段 `-dontoptimize` 及其注释**，否则会重新崩溃。
- 回退条件：升级 AGP/R8 或换工具链后实测工具调用正常，可删除该行并重新验证。

versionCode 约定：2026102601（首次同步）、2026102602（外部 noopt 交付包）、2026102603（本仓库入库优化开关），
后续自增必须大于设备已装值。
