#!/usr/bin/env bash
# 发版脚本：自动推算下一个版本号 → 打 git tag → 推送到 GitHub，触发 CI 出包并发布 Release。
#
# 用法：
#   ./release.sh              补丁版 +0.0.1：v1.0.0 → v1.0.1（默认）
#   ./release.sh patch        同上面，显式指定补丁版
#   ./release.sh minor        次版本 +0.1.0：v1.0.1 → v1.1.0
#   ./release.sh major        主版本 +1.0.0：v1.1.0 → v2.0.0
#   ./release.sh 1.2.3        直接指定完整版本号 → v1.2.3（跳过自增）
#   ./release.sh minor --dry-run   只打印将打的版本，不做任何操作
#   ./release.sh --help       显示用法
#
# 注意事项：
#   - 必须在仓库根目录运行，且远端必须能 push（SSH 或 HTTPS 凭据）。
#   - 默认要求工作区干净（有未提交改动会中止），想无视就用 --force。
#   - tag 的 versionName / versionCode 由 doit/build.gradle.kts 从 tag 推导。

set -euo pipefail

usage() {
    sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
}

[[ $# -gt 0 && "$1" == "--help" || $# -gt 0 && "$1" == "-h" ]] && usage

BUMP="patch"
DRY=""
FORCE=""
EXPLICIT=""
ARGS=()
for a in "$@"; do
    case "$a" in
        patch|minor|major) BUMP="$a" ;;
        --dry-run) DRY=1 ;;
        --force|-f) FORCE=1 ;;
        --help|-h) usage ;;
        v[0-9]*.[0-9]*.[0-9]*|[0-9]*.[0-9]*.[0-9]*) EXPLICIT="$a" ;;
        *) ARGS+=("$a") ;;
    esac
done

if (( ${#ARGS[@]} > 0 )); then
    echo "❌ 无法识别的参数：${ARGS[*]}"
    usage
fi

# 1. 解析当前最新 tag；没有任何 tag 时从 v0.0.0 开始
CURRENT="$(git describe --tags --abbrev=0 2>/dev/null || echo v0.0.0)"
if [[ "$CURRENT" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    MAJOR="${BASH_REMATCH[1]}"
    MINOR="${BASH_REMATCH[2]}"
    PATCH="${BASH_REMATCH[3]}"
else
    echo "❌ 最近一个 tag（${CURRENT}）不是 vX.Y.Z 格式，无法自动自增"
    echo "   可手动指定：./release.sh 1.0.0"
    exit 1
fi

# 2. 计算新版本号
if [[ -n "$EXPLICIT" ]]; then
    NEW="${EXPLICIT#v}"
elif [[ "$BUMP" == "patch" ]]; then
    PATCH=$((PATCH + 1)); NEW="${MAJOR}.${MINOR}.${PATCH}"
elif [[ "$BUMP" == "minor" ]]; then
    MINOR=$((MINOR + 1)); NEW="${MAJOR}.${MINOR}.0"
else
    MAJOR=$((MAJOR + 1)); NEW="${MAJOR}.0.0"
fi

TAG="v${NEW}"

echo "📦 当前版本：${CURRENT}"
if [[ -n "$EXPLICIT" ]]; then
    echo "  下一版本：${TAG}（手动指定）"
else
    echo "  下一版本：${TAG}（${BUMP}）"
fi
if [[ -n "$DRY" ]]; then
    echo "  （dry-run，未做任何操作）"
    exit 0
fi

# 3. 前置检查
if [[ -z "$FORCE" ]]; then
    if [[ -n "$(git status --porcelain)" ]]; then
        echo "❌ 工作区有未提交的改动："
        git status --short
        echo "   先提交或 stash，或用 --force 强制发版。"
        exit 1
    fi
fi

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1; then
    echo "❌ 本地已存在 tag ${TAG}，删掉或用其它版本号："
    echo "   git tag -d ${TAG}"
    exit 1
fi

if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
    echo "❌ 远端已存在 tag ${TAG}，换一个版本号。"
    exit 1
fi

# 4. 打 tag 并推送
echo "🚀 创建并推送 ${TAG} ……"
git tag -a "$TAG" -m "release: $TAG"
git push origin "$TAG"

echo ""
echo "✅ 已推送 ${TAG}，GitHub Actions 会自动构建并发布 Release。"
echo "   期望产物：versionName=${NEW}，versionCode=$((10#$MAJOR * 10000 + 10#$MINOR * 100 + 10#$PATCH))"
echo "   查看进度：https://github.com/nabaonan/doit-apk/actions"
