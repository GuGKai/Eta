#!/bin/sh
# Fork 改动规范自动检查（见 docs/FORK_RULES.md）
# 用法：
#   sh scripts/check-fork-spec.sh            检查工作区
#   sh scripts/check-fork-spec.sh --staged   检查暂存区（pre-commit 钩子）
# 退出码：0 通过（可含警告）；1 有错误被拦下；2 运行环境问题
set -u

MODE=worktree
[ "${1:-}" = "--staged" ] && MODE=staged

cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "不在 git 仓库内"; exit 2; }

errors=0
warns=0
err() { echo "  ✗ $1"; errors=$((errors + 1)); }
warn() { echo "  ⚠ $1"; warns=$((warns + 1)); }
info() { echo "  · $1"; }

# 本次要检查的文件列表
if [ "$MODE" = staged ]; then
    changed=$(git diff --cached --name-only)
else
    changed=$(git diff --name-only; git diff --cached --name-only)
    changed=$(printf '%s\n' "$changed" | sort -u)
fi

# 取要检查的文件内容：staged 用索引内容，worktree 用磁盘内容
show() {
    if [ "$MODE" = staged ]; then git show ":$1" 2>/dev/null; else cat "$1" 2>/dev/null; fi
}

echo "== 1. 上游 strings.xml 不得出现 local_ 前缀键"
for f in app/src/main/res/values/strings.xml app/src/main/res/values-*/strings.xml; do
    [ -f "$f" ] || continue
    case "$f" in *strings_local.xml) continue;; esac
    hits=$(show "$f" | grep -c 'name="local_' 2>/dev/null || true)
    [ "${hits:-0}" -gt 0 ] && err "$f 含 $hits 个 local_ 键，应移到 strings_local.xml"
done
[ "$errors" -eq 0 ] && info "上游 strings.xml 干净"

echo "== 2. strings_local.xml 的键必须带 local_ 前缀"
before=$errors
for f in app/src/main/res/values*/strings_local.xml; do
    [ -f "$f" ] || continue
    bad=$(show "$f" | grep -o 'name="[^"]*"' | grep -vc 'name="local_' 2>/dev/null || true)
    [ "${bad:-0}" -gt 0 ] && err "$f 有 $bad 个键缺少 local_ 前缀"
done
[ "$errors" -eq "$before" ] && info "strings_local 全部合规"

echo "== 3. 不得新增外部 com.itos.coui 依赖"
before=$errors
for f in app/build.gradle.kts gradle/libs.versions.toml build.gradle.kts; do
    [ -f "$f" ] || continue
    bad=$(show "$f" | grep -n "itos\.coui" | grep -vE '^[0-9]+:[[:space:]]*(//|\*)' || true)
    [ -n "$bad" ] && err "$f 引用了外部 COUI：$(printf '%s' "$bad" | head -1)"
done
[ "$errors" -eq "$before" ] && info "没有外部 COUI 依赖"

echo "== 4. DB 版本变更必须同时改迁移"
db_changed=$(printf '%s\n' "$changed" | grep -c 'data/db/EtaDatabase\.kt$' || true)
mig_changed=$(printf '%s\n' "$changed" | grep -c 'EtaDatabaseMigrations\.kt$' || true)
if [ "${db_changed:-0}" -gt 0 ] && [ "${mig_changed:-0}" -eq 0 ]; then
    err "EtaDatabase.kt 有改动但没有动 EtaDatabaseMigrations.kt"
else
    info "数据库变更检查通过"
fi

echo "== 5. 上游挂载源文件改动提醒"
MOUNTED="AgentModelPicker.kt AgentAppShell.kt AgentAppState.kt ConversationPanePanel.kt AgentChatBody.kt SettingsScreen.kt SettingsDataStore.kt MarkdownContent.kt AgentAccessibilityService.kt SpeechAudioLease.kt SpeechPlaybackController.kt ItemDescriptionDialog.kt OptionalModelPickerContent.kt SubagentActivitySheet.kt SubagentSettingsScreen.kt RemoteModelImportSheet.kt ScheduledTaskEditorScreen.kt AssistantOverlaySettingsScreen.kt ConversationTitleSettingsScreen.kt"
hit=""
for name in $MOUNTED; do
    printf '%s\n' "$changed" | grep -q "/$name$" && hit="$hit $name"
done
if [ -n "$hit" ]; then
    local_changed=$(printf '%s\n' "$changed" | grep -c 'Local\.kt$' || true)
    if [ "$MODE" = worktree ]; then
        local_untracked=$(git status --porcelain 2>/dev/null | grep '^??' | grep -c 'Local\.kt$' || true)
        local_changed=$(( ${local_changed:-0} + ${local_untracked:-0} ))
    fi
    if [ "${local_changed:-0}" -eq 0 ]; then
        warn "改了挂载源文件（$hit ）但没有对应 *Local.kt 改动，确认是否该搬到挂载点"
    else
        info "挂载源文件改动伴随 Local 文件改动：$hit"
    fi
else
    info "没有触碰挂载源文件"
fi

echo "== 6. 双主题联动提醒（仅提示，不拦截）"
coui_hit=$(printf '%s\n' "$changed" | grep -E 'vendor/coui/|ui/components/coui/|isCoui' | head -5 || true)
pane_hit=$(printf '%s\n' "$changed" | grep -E 'ConversationPaneChrome|AgentChatBody|AgentModelPicker|SettingsScreen|AppearanceSettingsScreen' | head -5 || true)
[ -n "$coui_hit" ] && info "涉及 COUI 侧：$(printf '%s' "$coui_hit" | tr '\n' ' ')"
[ -n "$pane_hit" ] && info "涉及双主题共享文件：$(printf '%s' "$pane_hit" | tr '\n' ' ')"
if [ -n "$coui_hit" ] || [ -n "$pane_hit" ]; then
    info "提交前确认：已问过用户另一侧要不要同步"
fi

echo "== 7. 未跟踪的 Kotlin 文件"
if [ "$MODE" = worktree ]; then
    untracked=$(git status --porcelain 2>/dev/null | grep '^??' | grep -E '\.kt$' | sed 's/^?? //' | head -10 || true)
    if [ -n "$untracked" ]; then
        warn "存在未跟踪的 Kotlin 文件，确认是否会漏提交："
        printf '%s\n' "$untracked" | while read -r p; do echo "      $p"; done
    else
        info "没有未跟踪的 Kotlin 文件"
    fi
fi

echo
if [ "$errors" -gt 0 ]; then
    echo "结果：$errors 个错误、$warns 个警告——请修正后再提交（见 docs/FORK_RULES.md）"
    exit 1
fi
echo "结果：通过（$warns 个警告）"
exit 0
