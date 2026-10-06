#!/usr/bin/env bash
# Build local candidates only. This helper does not certify release readiness or publish.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
fail() { printf 'CANDIDATE_PREFLIGHT_FAILED: %s\n' "$*" >&2; exit 1; }

: "${BUNDLETOOL_JAR:?set BUNDLETOOL_JAR to the locally verified bundletool inspector}"
[ -f "$BUNDLETOOL_JAR" ] || fail 'bundletool inspector not found'
command -v java >/dev/null || fail 'JDK 17 required'
command -v keytool >/dev/null || fail 'keytool required'
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK_ROOT" ] || fail 'set ANDROID_SDK_ROOT or ANDROID_HOME'
[ -f "$SDK_ROOT/platforms/android-37.0/android.jar" ] || [ -f "$SDK_ROOT/platforms/android-37/android.jar" ] || fail "Android platform 37 required"
[ -x "$SDK_ROOT/build-tools/35.0.0/apksigner" ] || fail 'Build-tools 35.0.0 required'
[ -x ./gradlew ] || fail 'executable Gradle wrapper required'
[ -f gradle/wrapper/gradle-wrapper.jar ] || fail 'Gradle wrapper JAR required'
[ -z "$(git status --porcelain --untracked-files=all)" ] || fail 'commit source before building candidates'

# Production credentials are deliberately unavailable to the market unsigned build.
export TERMINAL_KEYSTORE_PATH='' TERMINAL_KEYSTORE_PASSWORD=''
export TERMINAL_KEY_ALIAS='' TERMINAL_KEY_PASSWORD=''
python3 scripts/source_audit.py
python3 scripts/market_release_gate.py
python3 scripts/loop2_gate.py
python3 -m unittest discover -s scripts -p 'test_release_artifacts.py' -v
./gradlew --no-daemon clean testMarketDebugUnitTest testGplayDebugUnitTest \
  lintMarketDebug lintGplayDebug assembleMarketDebug assembleGplayDebug \
  testMarketReleaseUnitTest lintMarketRelease assembleMarketPreview assembleMarketRelease bundleMarketRelease

CANDIDATE_TMP="$(mktemp -d)"
trap 'rm -rf "$CANDIDATE_TMP"' EXIT
keytool -exportcert -keystore "$HOME/.android/debug.keystore" -alias androiddebugkey \
  -storepass android -file "$CANDIDATE_TMP/preview-cert.der"
python3 scripts/package_release.py --bundletool "$BUNDLETOOL_JAR" \
  --preview-cert "$CANDIDATE_TMP/preview-cert.der" --output release-assets
(cd release-assets && sha256sum --check SHA256SUMS.txt)
printf '\nCandidates inspected and packaged in release-assets/.\n'
printf 'No publication performed. Exact-binary device tests and full critical release gates remain required.\n'
