#!/usr/bin/env bash
set -euo pipefail

echo "== Java =="
java -version 2>&1 | head -3 || true

echo "== Git =="
git --version || true

echo "== GitHub CLI =="
if command -v gh >/dev/null 2>&1; then
  gh --version | head -1
  gh auth status || true
else
  echo "gh not installed"
fi

echo "== Android SDK =="
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -n "$SDK" ]]; then
  echo "SDK=$SDK"
  test -d "$SDK/platforms/android-36" && echo "android-36: OK" || echo "android-36: MISSING"
  find "$SDK/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | tail -1 || true
else
  echo "ANDROID_SDK_ROOT/ANDROID_HOME not set"
fi

echo "== Gradle =="
if [[ -x ./gradlew ]]; then
  ./gradlew --version | sed -n '1,12p'
elif command -v gradle >/dev/null 2>&1; then
  gradle --version | sed -n '1,12p'
else
  echo "Gradle/wrapper not found"
fi

echo "== Project identity =="
grep -R "applicationId\|namespace\|versionCode\|versionName\|compileSdk\|targetSdk\|minSdk" -n app/build.gradle.kts 2>/dev/null || true
