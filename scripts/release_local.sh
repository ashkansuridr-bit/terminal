#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() { printf 'RELEASE_PREFLIGHT_FAILED: %s\n' "$*" >&2; exit 1; }

: "${TERMINAL_KEYSTORE_PATH:?set TERMINAL_KEYSTORE_PATH}"
: "${TERMINAL_KEYSTORE_PASSWORD:?set TERMINAL_KEYSTORE_PASSWORD}"
: "${TERMINAL_KEY_ALIAS:?set TERMINAL_KEY_ALIAS}"
: "${TERMINAL_KEY_PASSWORD:?set TERMINAL_KEY_PASSWORD}"

[ -f "$TERMINAL_KEYSTORE_PATH" ] || fail "keystore not found: $TERMINAL_KEYSTORE_PATH"
command -v java >/dev/null || fail "java not found (JDK 17 required)"
command -v javac >/dev/null || fail "javac not found (full JDK required)"
command -v jarsigner >/dev/null || fail "jarsigner not found"

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[ -n "$JAVA_MAJOR" ] && [ "$JAVA_MAJOR" -ge 17 ] || fail "JDK 17+ required"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK_ROOT" ] || fail "set ANDROID_SDK_ROOT (or ANDROID_HOME)"
[ -d "$SDK_ROOT/platforms/android-36" ] || fail "Android SDK platform 36 is not installed"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -1)"
[ -n "$APKSIGNER" ] || fail "Android SDK Build-Tools/apksigner not found"

if [ -x ./gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  GRADLE=(./gradlew)
elif command -v gradle >/dev/null; then
  GRADLE=(gradle)
else
  fail "Gradle 8.13 is required (or add a Gradle wrapper)"
fi

GRADLE_VERSION="$(${GRADLE[@]} --version | sed -n 's/^Gradle //p' | head -1)"
[ "$GRADLE_VERSION" = "8.13" ] || fail "Gradle 8.13 required; found ${GRADLE_VERSION:-unknown}"

python3 scripts/source_audit.py
python3 scripts/market_release_gate.py
./scripts/verify_jvm.sh
"${GRADLE[@]}" --no-daemon clean testReleaseUnitTest lintRelease assembleRelease bundleRelease

shopt -s nullglob
APKS=(app/build/outputs/apk/release/*.apk)
AABS=(app/build/outputs/bundle/release/*.aab)
[ ${#APKS[@]} -gt 0 ] || fail "release APK not produced"
[ ${#AABS[@]} -gt 0 ] || fail "release AAB not produced"

for apk in "${APKS[@]}"; do
  "$APKSIGNER" verify --verbose --print-certs "$apk"
done
for aab in "${AABS[@]}"; do
  jarsigner -verify -strict "$aab"
done
sha256sum "${APKS[@]}" "${AABS[@]}" > SHA256SUMS.txt

printf '\nArtifacts:\n'
printf '  %s\n' "${APKS[@]}" "${AABS[@]}"
printf '\nChecksums:\n'
cat SHA256SUMS.txt
printf '\nMARKET_RELEASE_BINARY_GATE_OK\n'
