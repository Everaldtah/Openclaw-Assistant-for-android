#!/data/data/com.termux/files/usr/bin/bash
# =====================================================
# OpenClaw Assistant – Termux Build Environment Setup
# Run this ONCE before building the APK.
# =====================================================

set -e
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERR]${NC}  $1"; exit 1; }

# ── Fix /tmp so Claude Code and build tools work ───────────
info "Fixing /tmp symlink..."
mkdir -p /data/data/com.termux/files/usr/tmp
if [ ! -L /tmp ]; then
    ln -sf /data/data/com.termux/files/usr/tmp /tmp
    info "/tmp symlinked"
else
    info "/tmp already set"
fi
export TMPDIR=/data/data/com.termux/files/usr/tmp

# ── Add to shell profile ────────────────────────────────────
PROFILE="$HOME/.bashrc"
grep -q "TMPDIR" "$PROFILE" 2>/dev/null || {
    echo 'export TMPDIR=/data/data/com.termux/files/usr/tmp' >> "$PROFILE"
    echo 'export ANDROID_HOME=$HOME/android-sdk' >> "$PROFILE"
    echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH' >> "$PROFILE"
    info "Added env vars to $PROFILE"
}

# ── Update Termux packages ──────────────────────────────────
info "Updating package list..."
pkg update -y

# ── Install Java 17 ────────────────────────────────────────
info "Installing OpenJDK 17..."
pkg install -y openjdk-17

java -version 2>&1 | grep "17" || error "Java 17 install failed"
info "Java 17 OK"

# ── Install build tools ─────────────────────────────────────
info "Installing build tools..."
pkg install -y wget unzip zip curl git

# ── Download Android SDK command-line tools ─────────────────
ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME"

CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
CMDLINE_ZIP="$TMPDIR/cmdline-tools.zip"

if [ ! -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    info "Downloading Android SDK command-line tools (~130 MB)..."
    wget -q --show-progress -O "$CMDLINE_ZIP" "$CMDLINE_TOOLS_URL"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q "$CMDLINE_ZIP" -d "$ANDROID_HOME/cmdline-tools"
    # SDK expects tools at cmdline-tools/latest/
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -f "$CMDLINE_ZIP"
    info "SDK command-line tools extracted"
else
    info "SDK command-line tools already installed"
fi

export ANDROID_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ── Install SDK packages ─────────────────────────────────────
info "Installing SDK packages (accept licenses)..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

info "Installing platform-tools..."
sdkmanager "platform-tools"

info "Installing Android 15 platform (API 35)..."
sdkmanager "platforms;android-35"

info "Installing build-tools 35.0.0..."
sdkmanager "build-tools;35.0.0"

# ── Download Gradle wrapper ──────────────────────────────────
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
info "Making gradlew executable..."
chmod +x "$PROJECT_DIR/gradlew" 2>/dev/null || {
    info "Downloading gradle wrapper..."
    wget -q -O "$PROJECT_DIR/gradlew" \
        "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradlew"
    chmod +x "$PROJECT_DIR/gradlew"
}

# ── Install Python + server deps ─────────────────────────────
info "Installing Python and server dependencies..."
pkg install -y python
pip install -q fastapi uvicorn python-dotenv

info ""
info "=============================================="
info "  Setup complete! Next steps:"
info "  1. source ~/.bashrc"
info "  2. cd OpenClawAssistant"
info "  3. ./build_and_deploy.sh"
info "=============================================="
