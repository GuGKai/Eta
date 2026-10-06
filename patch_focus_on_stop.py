#!/usr/bin/env python3
"""退到后台时清掉聊天输入框焦点，避免回前台/从通知进会话页时键盘被系统恢复出来。"""
import sys
from pathlib import Path

ROOT = Path("/workspace/eta-work")
I = "app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatInputBar.kt"
G = "app/build.gradle.kts"
D = "docs/LOCAL_PATCHES.md"

EDITS = []
def E(rel, old, new, n=1):
    EDITS.append((rel, old, new, n))

# 1) import：LocalFocusManager + lifecycle 相关
E(I,
  "import androidx.compose.ui.platform.LocalDensity\n",
  "import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalFocusManager\n")
E(I,
  "import io.github.mangi.eta.R\n",
  "import androidx.lifecycle.Lifecycle\n"
  "import androidx.lifecycle.compose.LifecycleEventEffect\n"
  "import io.github.mangi.eta.R\n")

# 2) focusManager + 退后台清焦点
E(I,
  "    val keyboard = LocalSoftwareKeyboardController.current\n    val focusRequester = remember { FocusRequester() }\n",
  "    val keyboard = LocalSoftwareKeyboardController.current\n"
  "    val focusManager = LocalFocusManager.current\n"
  "    val focusRequester = remember { FocusRequester() }\n")
E(I,
  "    LaunchedEffect(isStreaming, isCompacting) {\n",
  "    // 退到后台时丢掉输入框焦点：Android 会在窗口重新拿到焦点时把键盘按原样恢复，\n"
  "    // 从「回复完成」通知点进会话页就会莫名带着键盘。清掉焦点后，回前台要打字自己点输入框。\n"
  "    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {\n"
  "        focusManager.clearFocus()\n"
  "    }\n"
  "\n"
  "    LaunchedEffect(isStreaming, isCompacting) {\n")

# 3) 版本号
E(G, "        versionCode = 2026100702\n", "        versionCode = 2026100703\n")

# 4) 清单
E(D,
  "- 出包版本：versionCode `2026100702`，versionName 跟随上游 `3.2.0`",
  "- 出包版本：versionCode `2026100703`，versionName 跟随上游 `3.2.0`")
E(D,
  "| 28 | 输入栏加号按钮改用主题色 | `feat(ui): 输入栏附件加号按钮改用主题色` | `AgentChatFileAttachments` 的 `AgentAttachmentPickerButton` 图标 `tint`：`onSurface` → `primary`（常亮主题色，与旁边思考强度按钮激活态同色） |\n",
  "| 28 | 输入栏加号按钮改用主题色 | `feat(ui): 输入栏附件加号按钮改用主题色` | `AgentChatFileAttachments` 的 `AgentAttachmentPickerButton` 图标 `tint`：`onSurface` → `primary`（常亮主题色，与旁边思考强度按钮激活态同色） |\n"
  "| 30 | 退后台清掉输入框焦点（点通知进会话页不再自带键盘） | `fix(chat): 退到后台时清掉输入框焦点，避免回前台被系统恢复键盘` | `AgentChatInputBar` 加 `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { focusManager.clearFocus() }`。实测：聊天输入框在发送/拖动收键盘时只调 `keyboard?.hide()`、从不清理焦点，于是「离开时键盘开着」→ 回前台（含点「回复完成」通知进会话页）系统把键盘原样恢复（`dumpsys input_method` 的 `mInputShown` 从 false 变 true，无需任何点击）；离开时键盘是收起的就不会恢复。清焦点后回前台一律干净，草稿文字不受影响 |\n")
E(D,
  "| 29 | 出包版本号 | （随本轮改动一并提交） | 精简输入栏／移除浮窗提示（#26～#28）的出包号，产物 `eta-3.2.0-2026100702-clean-input.apk` |\n",
  "| 29 | 出包版本号 | （随本轮改动一并提交） | 精简输入栏／移除浮窗提示（#26～#28）的出包号，产物 `eta-3.2.0-2026100702-clean-input.apk` |\n"
  "| 31 | 出包版本号 | （随本轮改动一并提交） | 退后台清输入焦点（#30）的出包号，产物 `eta-3.2.0-2026100703-blur-focus.apk` |\n")

plans = {}
for rel, old, new, n in EDITS:
    plans.setdefault(rel, []).append((old, new, n))

fail = False
for rel, items in plans.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for old, new, n in items:
        got = text.count(old)
        if got != n:
            print(f"[FAIL] {rel}: expect {n}, got {got} for {old.strip()[:70]!r}")
            fail = True
if fail:
    sys.exit(1)

for rel, items in plans.items():
    p = ROOT / rel
    text = p.read_text(encoding="utf-8")
    for old, new, n in items:
        text = text.replace(old, new, n)
    p.write_text(text, encoding="utf-8")
    print(f"[OK] {rel}")
