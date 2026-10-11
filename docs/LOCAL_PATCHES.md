# Eta 本地改动清单（fork: GuGKai/Eta）

- 上游基线：**v3.3.0**（`Mangi-11/Eta` @ `4d760b8`，2026-10-07 合并；2026-10-11 快进到 `9de3a3f`）
- 本地工作分支：`feat/local`（单一工作分支，原 `feat/system-tts` 更名而来）
- 最后更新：2026-10-11
- 出包版本：versionCode `2026101101`，versionName `3.3.1-beta.1`（未出包，待验证后定）
- 本次同步备份分支：`backup/pre-sync-20261007-0635-feat-local`（同步前的 `feat/local`＝`e61cce4`，按用户要求长期保留）
- 本次合并备份分支：`backup/pre-pr142-20261011-feat-local`（`141f9dd`）、`backup/pre-pr142-20261011-main`（`4d760b8`）

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
| 侧栏 `ConversationPanePanel` | 保留本地设计（置顶折叠、分组标题、底部 Dock、精简输入栏），补入 PR 的模式徽标 + 「任务」入口 |
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
