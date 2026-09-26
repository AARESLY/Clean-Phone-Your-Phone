#!/bin/bash
set -e

BUILD_TOOLS=/usr/local/share/android-commandlinetools/build-tools/34.0.0
PLATFORM=/Users/aarian/.android-sdk/platforms/android-34/android.jar
APP_DIR=/Users/aarian/auto_organizer_app

rm -rf $APP_DIR/build
mkdir -p $APP_DIR/build/gen $APP_DIR/build/classes $APP_DIR/build/dex

echo "=== Compiling Resources ==="
$BUILD_TOOLS/aapt2 compile --dir $APP_DIR/res -o $APP_DIR/build/res.zip

echo "=== Linking APK & Generating R.java ==="
$BUILD_TOOLS/aapt2 link \
  --manifest $APP_DIR/AndroidManifest.xml \
  -I $PLATFORM \
  -o $APP_DIR/build/app-unaligned.apk \
  --java $APP_DIR/build/gen \
  $APP_DIR/build/res.zip \
  --auto-add-overlay

echo "=== Compiling Java ==="
javac -source 17 -target 17 \
  -cp $PLATFORM \
  -d $APP_DIR/build/classes \
  $APP_DIR/build/gen/com/organizer/downloads/R.java \
  $APP_DIR/src/com/organizer/downloads/*.java

echo "=== Dexing with d8 ==="
d8 --lib $PLATFORM \
  --output $APP_DIR/build/dex \
  $(find $APP_DIR/build/classes -name "*.class")

echo "=== Adding DEX to APK ==="
cd $APP_DIR/build/dex
zip -u ../app-unaligned.apk classes.dex
cd -

echo "=== Zipalign ==="
$BUILD_TOOLS/zipalign -v -p 4 $APP_DIR/build/app-unaligned.apk $APP_DIR/build/app-aligned.apk

echo "=== Generating Debug Keystore if missing ==="
KEYSTORE=$HOME/.android/debug.keystore
mkdir -p $HOME/.android
if [ ! -f $KEYSTORE ]; then
  keytool -genkey -v -keystore $KEYSTORE -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

echo "=== Signing APK ==="
$BUILD_TOOLS/apksigner sign \
  --ks $KEYSTORE \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out $APP_DIR/build/DownloadOrganizer.apk \
  $APP_DIR/build/app-aligned.apk

echo "=== Verification ==="
$BUILD_TOOLS/apksigner verify $APP_DIR/build/DownloadOrganizer.apk
ls -lh $APP_DIR/build/DownloadOrganizer.apk
