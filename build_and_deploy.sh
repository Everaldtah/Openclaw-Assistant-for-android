#!/data/data/com.termux/files/usr/bin/bash
# =====================================================
# OpenClaw Assistant – Build, Sign & Deploy Script
# =====================================================

set -e
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${GREEN}[BUILD]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error() { echo -e "${RED}[ERR]${NC}   $1"; exit 1; }
step()  { echo -e "\n${CYAN}━━━ $1 ━━━${NC}"; }

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEYSTORE="$PROJECT_DIR/keystore.jks"
APK_DEBUG="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APK_RELEASE="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"

# ── Environment ──────────────────────────────────────────────
export TMPDIR=/data/data/com.termux/files/usr/tmp
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
export JAVA_HOME="$(dirname $(dirname $(readlink -f $(which java))))"

# ── Preflight checks ─────────────────────────────────────────
step "Preflight Checks"
java -version 2>&1 | grep -q "17\|21" || error "Java 17/21 required. Run setup_termux.sh first."
info "Java: OK"

[ -f "$ANDROID_HOME/platforms/android-35/android.jar" ] || \
    error "Android SDK API 35 not found. Run setup_termux.sh first."
info "Android SDK: OK"

# ── Generate keystore if missing ─────────────────────────────
step "Signing Key"
if [ ! -f "$KEYSTORE" ]; then
    info "Generating debug keystore..."
    export KEYSTORE_PASS="openclaw_keystore"
    export KEY_PASS="openclaw_key"
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias openclaw \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$KEYSTORE_PASS" \
        -keypass "$KEY_PASS" \
        -dname "CN=OpenClaw,OU=Dev,O=OpenClaw,L=Local,S=Local,C=US" \
        2>/dev/null
    info "Keystore created: keystore.jks"
    warn "This is a DEVELOPMENT keystore – generate a new one before Play Store upload"
else
    info "Keystore: exists"
fi

# Set default passwords if not in env
export KEYSTORE_PASS="${KEYSTORE_PASS:-openclaw_keystore}"
export KEY_PASS="${KEY_PASS:-openclaw_key}"

# ── Select build type ─────────────────────────────────────────
step "Build Type"
BUILD_TYPE="${1:-debug}"  # Pass "release" as argument for release build
info "Building: $BUILD_TYPE"

# ── Build ────────────────────────────────────────────────────
step "Gradle Build"
cd "$PROJECT_DIR"
chmod +x ./gradlew

if [ "$BUILD_TYPE" = "release" ]; then
    info "Running assembleRelease..."
    ./gradlew :app:assembleRelease \
        -Pandroid.injected.signing.store.file="$KEYSTORE" \
        -Pandroid.injected.signing.store.password="$KEYSTORE_PASS" \
        -Pandroid.injected.signing.key.alias="openclaw" \
        -Pandroid.injected.signing.key.password="$KEY_PASS" \
        --no-daemon 2>&1 | tee /tmp/build.log
    APK="$APK_RELEASE"
else
    info "Running assembleDebug..."
    ./gradlew :app:assembleDebug --no-daemon 2>&1 | tee /tmp/build.log
    APK="$APK_DEBUG"
fi

[ -f "$APK" ] || error "Build failed. Check /tmp/build.log"
info "APK size: $(du -h "$APK" | cut -f1)"
info "APK path: $APK"

# ── Install ──────────────────────────────────────────────────
step "Install"
if command -v adb &>/dev/null; then
    ADB_DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    if [ "$ADB_DEVICES" -gt 0 ]; then
        info "Installing via ADB..."
        adb install -r "$APK"
        info "Installed successfully"

        info "Launching MainActivity..."
        adb shell am start -n com.openclaw.assistant/.MainActivity
    else
        warn "No ADB device connected. Copy APK manually:"
        warn "  $APK"
    fi
else
    info "ADB not found. APK ready to install:"
    info "  $APK"
    info "Transfer to device and open with a file manager to install."
fi

step "Done"
info "Build complete: $APK"
echo ""
echo "  To start the mock server:"
echo "    python $PROJECT_DIR/mock_server.py"
echo ""
echo "  Default server URL: ws://10.0.2.2:8000/ws (Android emulator)"
echo "  Device on local WiFi: ws://<your-phone-ip>:8000/ws"
echo ""
