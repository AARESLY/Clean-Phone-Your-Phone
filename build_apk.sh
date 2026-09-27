#!/bin/bash
set -e

# Detect script directory (repository root)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$SCRIPT_DIR"

# Locate Android SDK
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    SDK_ROOT="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    SDK_ROOT="$ANDROID_SDK_ROOT"
elif [ -d "/opt/android-sdk" ]; then
    SDK_ROOT="/opt/android-sdk"
elif [ -d "$HOME/.android-sdk" ]; then
    SDK_ROOT="$HOME/.android-sdk"
elif [ -d "$HOME/Library/Android/sdk" ]; then
    SDK_ROOT="$HOME/Library/Android/sdk"
else
    echo "ERROR: Android SDK not found. Set ANDROID_HOME." >&2
    exit 1
fi

# Locate android.jar (platform 34 preferred, or highest available)
PLATFORM=""
if [ -f "$SDK_ROOT/platforms/android-34/android.jar" ]; then
    PLATFORM="$SDK_ROOT/platforms/android-34/android.jar"
else
    PLATFORM=$(find "$SDK_ROOT/platforms" -name "android.jar" 2>/dev/null | sort -V | tail -n 1)
fi

if [ -z "$PLATFORM" ] || [ ! -f "$PLATFORM" ]; then
    echo "ERROR: android.jar not found in $SDK_ROOT/platforms" >&2
    exit 1
fi

# Locate build-tools
BUILD_TOOLS_DIR=""
if [ -d "/usr/local/share/android-commandlinetools/build-tools/34.0.0" ]; then
    BUILD_TOOLS_DIR="/usr/local/share/android-commandlinetools/build-tools/34.0.0"
elif [ -d "$SDK_ROOT/build-tools" ]; then
    BUILD_TOOLS_DIR=$(find "$SDK_ROOT/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
fi

AAPT2=$(command -v aapt2 2>/dev/null || echo "$BUILD_TOOLS_DIR/aapt2")
D8=$(command -v d8 2>/dev/null || echo "$BUILD_TOOLS_DIR/d8")
ZIPALIGN=$(command -v zipalign 2>/dev/null || echo "$BUILD_TOOLS_DIR/zipalign")
APKSIGNER=$(command -v apksigner 2>/dev/null || echo "$BUILD_TOOLS_DIR/apksigner")

echo "=== Build Environment ==="
echo "APP_DIR: $APP_DIR"
echo "SDK_ROOT: $SDK_ROOT"
echo "PLATFORM: $PLATFORM"
echo "AAPT2: $AAPT2"
echo "D8: $D8"
echo "ZIPALIGN: $ZIPALIGN"

rm -rf "$APP_DIR/build"
mkdir -p "$APP_DIR/build/gen" "$APP_DIR/build/classes" "$APP_DIR/build/dex"

echo "=== Compiling Resources ==="
"$AAPT2" compile --dir "$APP_DIR/res" -o "$APP_DIR/build/res.zip"

echo "=== Linking APK & Generating R.java ==="
"$AAPT2" link \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  -I "$PLATFORM" \
  -o "$APP_DIR/build/app-unaligned.apk" \
  --java "$APP_DIR/build/gen" \
  "$APP_DIR/build/res.zip" \
  --auto-add-overlay

echo "=== Compiling Java ==="
javac -source 11 -target 11 \
  -cp "$PLATFORM" \
  -d "$APP_DIR/build/classes" \
  "$APP_DIR/build/gen/com/organizer/downloads/R.java" \
  "$APP_DIR"/src/com/organizer/downloads/*.java

echo "=== Dexing with d8 ==="
jar cf "$APP_DIR/build/classes.jar" -C "$APP_DIR/build/classes" .
"$D8" --lib "$PLATFORM" --output "$APP_DIR/build/dex" "$APP_DIR/build/classes.jar"

echo "=== Adding DEX to APK ==="
cd "$APP_DIR/build/dex"
zip -u ../app-unaligned.apk classes.dex
cd "$APP_DIR"

echo "=== Zipalign ==="
"$ZIPALIGN" -v -p 4 "$APP_DIR/build/app-unaligned.apk" "$APP_DIR/build/app-aligned.apk"

# F-Droid expects an unsigned/aligned APK at the output path so F-Droid server can sign it with its own release key.
cp "$APP_DIR/build/app-aligned.apk" "$APP_DIR/build/DownloadOrganizer.apk"

# If local apksigner and keytool are available, sign locally for standalone testing
if [ -z "$CI" ] && [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
    echo "=== Signing APK for Local Test ==="
    KEYSTORE="$HOME/.android/debug.keystore"
    mkdir -p "$HOME/.android"
    if [ ! -f "$KEYSTORE" ]; then
        keytool -genkey -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null || true
    fi
    if [ -f "$KEYSTORE" ]; then
        "$APKSIGNER" sign \
          --ks "$KEYSTORE" \
          --ks-pass pass:android \
          --key-pass pass:android \
          --out "$APP_DIR/build/DownloadOrganizer.apk" \
          "$APP_DIR/build/app-aligned.apk" || true
    fi
fi

ls -lh "$APP_DIR/build/DownloadOrganizer.apk"
echo "=== Build Complete ==="
