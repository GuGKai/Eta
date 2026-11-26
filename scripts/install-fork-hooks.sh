#!/bin/sh
# 安装 fork 规范检查钩子（pre-commit）。重复执行安全。
set -e
cd "$(git rev-parse --show-toplevel)"
HOOK_DIR=$(git rev-parse --git-path hooks)
mkdir -p "$HOOK_DIR"
HOOK="$HOOK_DIR/pre-commit"

if [ -f "$HOOK" ] && ! grep -q "check-fork-spec" "$HOOK" 2>/dev/null; then
    cp "$HOOK" "$HOOK.bak.$(date +%Y%m%d%H%M%S)"
    echo "已备份原有 pre-commit"
fi

cat > "$HOOK" <<'EOF'
#!/bin/sh
# fork 改动规范检查（docs/FORK_RULES.md）；临时绕过用 git commit --no-verify
ROOT=$(git rev-parse --show-toplevel) || exit 0
[ -f "$ROOT/scripts/check-fork-spec.sh" ] || exit 0
cd "$ROOT" || exit 0
exec sh scripts/check-fork-spec.sh --staged
EOF
chmod +x "$HOOK"
echo "已安装：$HOOK"
