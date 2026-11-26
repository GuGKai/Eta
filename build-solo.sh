#!/bin/sh
# 出 Eta Solo（debug 变体，release 签名），包名 io.github.mangi.eta.plan
# 出包差异全部走 -P 参数，app/build.gradle.kts 与主 Eta 保持逐字一致。
set -e
SRC=/workspace/eta-plan
PIN=/workspace/pin-patch
VERSION_CODE=2026112402
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/build-tools/36.0.0:$PATH"
export ETA_RELEASE_STORE_FILE="$PIN/eta-fork.jks"
export ETA_RELEASE_STORE_PASSWORD="$(cat "$PIN/store-password.txt")"
export ETA_RELEASE_KEY_ALIAS="$(cat "$PIN/key-alias.txt")"
export ETA_RELEASE_KEY_PASSWORD="$(cat "$PIN/key-password.txt")"
cd "$SRC"
echo "== HEAD: $(git rev-parse --short HEAD)"
./gradlew --stop >/dev/null 2>&1 || true
./gradlew --no-daemon assembleDebug \
    -Peta.appIdSuffix=plan \
    -Peta.versionCode="$VERSION_CODE" \
    -Peta.debugSignWithRelease=true \
    -Pandroid.aapt2FromMavenOverride=$PIN/bin/aapt2 \
    -Pkotlin.compiler.execution.strategy=in-process \
    -Dorg.gradle.daemon.registry.base=/dev/shm/gradle-daemon-reg \
    --no-configuration-cache \
    "-Dorg.gradle.jvmargs=-Xmx1600m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" \
    --console=plain
APK=$(ls -t app/build/outputs/apk/debug/*.apk | head -1)
NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)
OUT="/workspace/eta-solo-${NAME}-${VERSION_CODE}-couifix.apk"
cp -f "$APK" "$OUT"
echo "== 产物：$OUT"
apksigner verify --print-certs "$OUT" | sed -n '1,4p'
"$PIN/bin/aapt2" dump badging "$OUT" 2>/dev/null | sed -n '1,2p'
