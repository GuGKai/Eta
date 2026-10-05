#!/bin/sh
# Eta 上游同步：把原作者（上游）的新提交并入 main，再把本地特性分支重放到新 main 之上
#
# 用法：
#   sh sync-upstream.sh                 同步 + 推送 fork + 编译验证
#   SKIP_BUILD=1 sh sync-upstream.sh    只同步和推送，不编译
#   NO_PUSH=1    sh sync-upstream.sh    只同步，不推送
#   WORK_BRANCH=feat/xxx sh sync-upstream.sh   指定结束时停留的分支
#
# 说明：脚本自动同步 refs/heads/feat/* 与 refs/heads/fix/* 下的所有分支，
#       并在动手前为每个分支建立 backup/pre-sync-<时间>-<分支> 备份。
set -e
cd /workspace/eta-src

WORK_BRANCH=${WORK_BRANCH:-feat/system-tts}
STAMP=$(date +%Y%m%d-%H%M)

echo "== 0/5 备份现有特性分支 =="
BRANCHES=$(git for-each-ref --format='%(refname:short)' refs/heads/feat refs/heads/fix)
for b in $BRANCHES; do
  tag="backup/pre-sync-$STAMP-$(echo "$b" | tr '/' '-')"
  if git rev-parse --verify -q "$tag" >/dev/null; then
    echo "已存在 $tag"
  else
    git branch "$tag" "$b"
    echo "$b -> $tag"
  fi
done

echo "== 1/5 拉取上游 + fork =="
git fetch origin
git fetch fork

echo "== 2/5 main 快进到上游 =="
git checkout main
git merge --ff-only origin/main
git log --oneline -3

echo "== 3/5 重放本地特性分支 =="
for b in $BRANCHES; do
  echo "-- $b"
  git checkout "$b"
  if ! git rebase main; then
    echo "!! $b 出现冲突：解决后执行 git add -A && git rebase --continue，再重跑本脚本"
    echo "!! 放弃本次重放：git rebase --abort"
    exit 1
  fi
done

echo "== 4/5 推送 fork =="
if [ -n "$NO_PUSH" ]; then
  echo "NO_PUSH 已设置，跳过推送"
else
  git checkout main && git push fork main
  for b in $BRANCHES; do
    git checkout "$b" && git push --force-with-lease fork "$b"
  done
fi

echo "== 5/5 编译验证 =="
echo "当前版本号（合并上游后通常需要递增 versionCode）："
grep -n "versionCode\|versionName" app/build.gradle.kts | head -3
if [ -n "$SKIP_BUILD" ]; then
  echo "SKIP_BUILD 已设置，跳过编译"
else
  sh /workspace/pin-patch/build2.sh
fi

git checkout "$WORK_BRANCH"
echo "SYNC_DONE 当前分支：$(git rev-parse --abbrev-ref HEAD)  备份前缀：backup/pre-sync-$STAMP-"
