#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# Push OpenClawAssistant to GitHub
# Usage: bash git_push.sh <github-username> <personal-access-token>
# Example: bash git_push.sh Everaldtah ghp_xxxxxxxxxxxx
# ============================================================

set -e
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[GIT]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERR]${NC} $1"; exit 1; }

# ── Fix /tmp if not already done ────────────────────────────
mkdir -p /data/data/com.termux/files/usr/tmp
[ -L /tmp ] || ln -sf /data/data/com.termux/files/usr/tmp /tmp
export TMPDIR=/data/data/com.termux/files/usr/tmp

# ── Args ────────────────────────────────────────────────────
GITHUB_USER="${1:-}"
GITHUB_TOKEN="${2:-}"

if [ -z "$GITHUB_USER" ] || [ -z "$GITHUB_TOKEN" ]; then
    echo ""
    read -p "GitHub username: " GITHUB_USER
    read -s -p "GitHub Personal Access Token: " GITHUB_TOKEN
    echo ""
fi

[ -z "$GITHUB_USER" ]  && error "Username required"
[ -z "$GITHUB_TOKEN" ] && error "Token required"

REPO_URL="https://${GITHUB_USER}:${GITHUB_TOKEN}@github.com/Everaldtah/Openclaw-Assistant-for-android.git"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

info "Project: $PROJECT_DIR"
info "Remote:  https://github.com/Everaldtah/Openclaw-Assistant-for-android.git"

cd "$PROJECT_DIR"

# ── Git config ───────────────────────────────────────────────
git config --global user.email "${GITHUB_USER}@users.noreply.github.com" 2>/dev/null || true
git config --global user.name "$GITHUB_USER" 2>/dev/null || true

# ── Init or re-use existing repo ────────────────────────────
if [ ! -d ".git" ]; then
    info "Initialising git repository..."
    git init -b main
else
    info "Git repo already initialised"
    git checkout -B main 2>/dev/null || true
fi

# ── Stage all files ──────────────────────────────────────────
info "Staging all project files..."
git add \
  README.md .gitignore \
  build.gradle.kts settings.gradle.kts \
  gradle/libs.versions.toml \
  gradle/wrapper/gradle-wrapper.properties \
  app/build.gradle.kts \
  app/proguard-rules.pro \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/ \
  app/src/main/res/ \
  mock_server.py \
  setup_termux.sh \
  build_and_deploy.sh \
  local.properties.template \
  .env.template

info "Files staged: $(git diff --cached --name-only | wc -l)"

# ── Commit ───────────────────────────────────────────────────
if git diff --cached --quiet; then
    info "Nothing to commit – already up to date"
else
    git commit -m "feat: production-ready OpenClaw Assistant Android app

- Kotlin 2.1.0 / AGP 8.7.3 / KSP (replaces kapt)
- Native MediaCodec Opus encoder (API 34+, no external dep)
- 4-state animated avatar: IDLE/LISTENING/SPEAKING/THINKING
- SecureWebSocketClient with exponential-backoff reconnection
- Encrypted credential storage (EncryptedSharedPreferences)
- kotlinx.serialization for safe JSON handling
- Fixed CameraFrameManager Context/LifecycleOwner cast bug
- FastAPI mock server (mock / Anthropic / proxy modes)
- Termux setup + build + deploy scripts
- Full README with deploy & OpenClaw agent connection guide"
    info "Committed"
fi

# ── Push ─────────────────────────────────────────────────────
info "Setting remote origin..."
git remote remove origin 2>/dev/null || true
git remote add origin "$REPO_URL"

info "Pushing to main..."
git push -u origin main --force

info ""
info "======================================================="
info " Successfully pushed to:"
info " https://github.com/Everaldtah/Openclaw-Assistant-for-android"
info "======================================================="
