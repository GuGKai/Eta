# Eta 本地改动清单（fork: GuGKai/Eta）

- 上游基线：**v3.2.0**（`Mangi-11/Eta` @ `f4c853a`）
- 本地工作分支：`feat/local`（单一工作分支，原 `feat/system-tts` 更名而来）
- 最后更新：2026-10-05
- 出包版本：versionCode `2026100502`，versionName 跟随上游 `3.2.0`

> 本文件记录 fork 相对上游原版的全部改动。上游每发新版、本地提交重放（rebase）之后
> 提交号会变化，**请以「提交标题」为准**；SHA 只对应当前基线。
> 自查：`git log --oneline origin/main..HEAD`、`git diff --stat origin/main..HEAD`

## 一、功能改动

| # | 功能 | 提交标题 | 具体改动 |
|---|---|---|---|
| 1 | 回复完成通知 + 执行中流体云实时活动 | `feat(notification): 回复完成通知与执行中流体云实时活动` | 新增 `AppForegroundState`、`ExecutionLiveProgress`、`MarkdownTextStripper`；改造 `AgentExecutionService`/`AgentRuntimeService`；胶囊图标经 `Icon.createWithResource`，反射 `OplusUxIconManager` |
| 2 | 实时活动/前台通知显示当前环节名 | `feat(notification): 实时活动与前台通知改为显示当前环节名称` | 进度快照拆出 `onThinking()`/`onStepFinished()`：思考阶段回落「正在思考」，工具结束才累加步骤数；通知首行由任务名改为环节名 |
| 3 | 会话置顶与侧栏面板操作 | `feat(conversation): 会话置顶与侧栏面板操作` | `ConversationEntity`/`ConversationDao`/`EtaDatabase` 加 `pinned` 列；`Snapshot` 加 `pinned`；侧栏面板操作增强 |
| 4 | 氛围光贴合真实屏幕圆角 | `fix(overlay): 氛围光贴合真实屏幕圆角` | 读 `getRoundedCorner` 真实圆角（1440 宽约 133px），遮罩 `dimAlpha` 0.31→0 |
| 5 | 氛围光只在执行中且前台可见时显示 | `fix(overlay): 氛围光只在执行中且界面处于前台时显示` | 新增 `AgentOverlayVisibilityPolicy`，要求 `phase==RUNNING && foregroundOperationActive`；淡入 180ms / 保留 5s / 淡出 320ms |
| 6 | 最终正文停靠 | `feat(chat): 最终正文停靠` | `AgentChatBody`：最终回复结束后正文停靠在顶部 |
| 7 | 后台完成回复也保证停靠 | `fix(chat): 后台完成回复时也保证最终正文停靠` | 应用不在前台时完成的那轮回复同样停靠 |
| 8 | 系统 TTS 播报后端 | `feat(speech): 新增系统 TTS 播报后端，可选用 MultiTTS 等设备语音引擎` | 新增 `SystemTtsSpeaker`、`SpeechSystemTtsSection`；`SpeechSettings` 增加系统引擎/音色字段；播报走 `speakWithSystemEngine` 分支（非系统引擎仍走上游 `playChunks`） |
| 9 | 新建对话提到顶栏 + 自动聚焦 | `feat(ui): 新建对话入口提到顶栏，并新建后自动聚焦输入框` | 顶栏自绘加号图标取代溢出菜单项；`composerFocusPending` 一次性信号触发聚焦并拉起键盘 |
| 10 | 空首页精简 | `feat(ui): 精简空首页，移除建议卡片并放大问候语` | 移除建议卡片，问候语放大到 `headline1 × 1.3f` |

## 二、工程与出包相关

| # | 事项 | 提交标题 | 说明 |
|---|---|---|---|
| 11 | 同步脚本入库 | `chore(scripts): 纳入上游同步脚本与实时活动补丁脚本` | `sync-upstream.sh`、`patch_v12.py` 纳入仓库，rebase 时不再是未跟踪文件 |
| 12 | 工作分支统一 | `chore(sync): 工作分支统一为 feat/system-tts` | `feat/raise-backup-limit`、`fix/agent-overlay-glow-corner` 改名 `archive/*` 归档，脚本不再重放 |
| 13 | 工作分支更名 | `chore(sync): 工作分支更名为 feat/local` | `feat/system-tts` → `feat/local`，脚本 `WORK_BRANCH` 默认值同步更新 |
| 14 | 出包版本号 | `chore(release): versionCode 2026100502` | 上游 3.2.0 已占用 2026100501，本地出包用当日 02 |

## 三、上游已覆盖、合并时丢弃的本地提交

| 原提交标题 | 内容 | 丢弃原因 |
|---|---|---|
| `feat(backup): 备份上限提高到 256 MiB 并改为流式导出` | 上限 64→256 MiB、流式导出、`android:largeHeap="true"` | 上游 3.2.0 自己实现得更彻底：分页 64/页 + `EtaBackupJsonStreams` 流式、上限 512 MiB，largeHeap 已无必要 |
| `chore(release): 本地上游合并版本 versionCode 2026100202` | 版本号中间值 | 上游 3.2.0 即为 2026100501，中间号无意义 |
| `chore(release): versionCode 2026100501` | 与上游撞号 | 由 `2026100502` 取代 |

## 四、仓库外、设备侧的配套（不属于本仓库改动）

- `pin-patch/build2.sh`：ARM64 手机上的出包脚本；自签 keystore `eta-fork.jks`（CN=GuGKai Eta Mod，SHA-256 `F0:DD:C5:E7…CF:86:A4:82`），与设备已装包同签名，可覆盖安装保数据
- Debian 工具环境中的 Android 构建链（JDK、SDK 37、ARM64 aapt2 运行时）
- 通知补丁线 v1→v12 的迭代过程（成品即上表第 1、2 项，`patch_v12.py` 为当时的一键脚本）

## 五、同步后的维护

1. 同步流程见 skill `eta-upstream-sync`；脚本会自动创建 `backup/pre-sync-*` 备份分支，用户要求长期保留，不要删
2. 归档分支 `archive/*` 只作历史快照，脚本不重放、**不得用于出包**（`archive/agent-overlay-glow-corner` 仍是 `dimAlpha = 0.31f` 的旧状态）
3. 发版前递增 `app/build.gradle.kts` 的 versionCode（规则 `yyyyMMdd` + 两位当日序号），并回到本文件更新基线版本、更新日期与上述表格
