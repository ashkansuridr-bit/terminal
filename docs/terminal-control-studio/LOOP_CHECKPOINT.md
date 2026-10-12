# Loop checkpoint

- Captured: 2026-10-12 UTC
- Feature branch: `feature/terminal-control-studio-pro`
- Baseline HEAD: `67021c1` (v0.7.2; `origin/main` at inspection)
- Implementation commit: `f2e7524`
- Stability/CI follow-up: `ead9c4f` (default Shell exposes Snippets; CI gate matches the action wiring)
- Latest checkpoint commit: `b15b22e`
- Pull request: https://github.com/ashkansuridr-bit/terminal/pull/4 (OPEN, not merged)
- Branch is pushed and tracks `origin/feature/terminal-control-studio-pro`.

## Verified results

- Final MarketDebug Kotlin compile and APK assembly: PASS.
- MarketDebug JVM tests: 1,013 passed; 0 failed, 0 errors, 0 skipped across 118 XML suites.
- MarketDebug Android-test Kotlin compilation: PASS. Instrumentation execution is BLOCKED: `adb` exists under `/root/android-sdk/platform-tools`, but no emulator/device is available. The API 36 system image could not be installed because the workspace had under 1 GB free and the image download exceeded available disk; the interrupted download cleaned up and disk space was restored.
- MarketDebug lint: PASS, 0 errors, 108 warnings, 2 hints.
- GPlayDebug APK, MarketPreview, and GPlayPreview builds: PASS. Both Preview variants completed R8 minification/resource shrinking. They were built before the final lint-only cleanup (Modifier parameter naming and removal of unused strings); final source was rebuilt and verified as MarketDebug.
- `apksigner` verified the APKs with the Android Debug certificate. Production signing is NOT VERIFIED.
- `git diff --check`: PASS; `git status` is clean after pushing.
- `source_audit.py`, `market_release_gate.py`, `loop2_gate.py`, and the release artifact Python suite (9 tests): PASS after the CI fix.
- Android API device matrix and Compose/TalkBack UI runtime checks: BLOCKED (no emulator/device; disk capacity prevents image installation).

## Root causes and fixes in the last loop

- Power profile exposed `F…` as `TextAction("")`, a visible no-op. Replaced it with a real F1 terminal action, exposed F1–F12 in the palette, and added regression tests.
- Repeat-while-held keys could emit the normal release click after repeated Backspace/Delete events. Suppressed the trailing click after repeat begins.
- New strings initially lacked Arabic values and Lint rejected them. Added all Terminal Control Studio resources for Persian, Arabic, English, Spanish, French, and Russian.
- An intermediate test compilation exposed an invalid enum range in the F-key regression test. Changed the assertion to enumerate the F1–F12 entries and reran the full test suite successfully.
- PR CI's static gate had a stale text check for the old snippet button; the default Shell profile also lacked direct snippet access. Added a functional Snip action, a repository regression test, and updated the gate to verify preset → dispatcher → sheet wiring. Artifact upload now warns when earlier gates fail before reports exist, preserving the primary CI failure.

## Artifacts and next action

- Final MarketDebug APK: `app/build/outputs/apk/market/debug/app-market-debug.apk`
- SHA-256: `b19031080566d68ed25ffcc3ca14e5361b27d5b9497eaf68397691aa6bbe7f87` (rebuilt from `ead9c4f`; Android Debug certificate)
- Reproduction: `./gradlew :app:compileMarketDebugKotlin :app:testMarketDebugUnitTest :app:compileMarketDebugAndroidTestKotlin :app:assembleMarketDebug :app:lintMarketDebug --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m`
- Next exact step: verify the new CI run for `ead9c4f`; then continue with Compose/instrumentation/accessibility validation when a device/emulator and disk capacity are available. Do not merge PR #4 without explicit authorization.

## Instrumentation follow-up (2026-10-13)

- Last remotely tested commit: `6d5646d`; PR run `38310804825` completed with successful `verify` but failed instrumentation on API 26 and 36. API 37 devices failed to boot (device remained offline / emulator boot timeout), so those failures do not establish app behavior there.
- Confirmed API 26 test-harness root cause: `TerminalKeyboardTest.tearDown()` first initialized `ClipboardManager` on the instrumentation thread; Android 8 creates its Handler there and throws the Looper exception. Cleanup now resolves/clears clipboard on Main.
- API 36 accessibility fixes now retain Slider range semantics by removing clickable parent semantics from Int/Choice rows, explicitly expose Choice selection, and localize built-in Control/Interrupt/EOF descriptions so tests and TalkBack query the app locale.
- Preview instrumentation keep rules now retain nested `WindowInsetsCompat.Type` in the test APK, fixing the specific R8 class resolution gap reported by the previous Preview run.
- Host form test now selects the accessible primary action by description because the production control intentionally clears child text semantics. Host list scrolling now returns true only when the target node is fully visible; it no longer treats scroll completion or swallowed exceptions as success.
- Local verification on these source edits: MarketDebug Kotlin compile PASS; AndroidTest Kotlin compile PASS; JVM 1,013/1,013 PASS (118 suites); MarketPreview assemble with R8 PASS. `lintMarketDebug` is currently running. No connected emulator is attached, so device-side fixes remain unverified until the next CI run.
- Next exact step: finish lint, commit/push this follow-up on `feature/terminal-control-studio-pro`, then inspect the newly triggered API 26/36/37 instrumentation jobs. In particular, use the new marker failure diagnostics to determine why intermittent terminal-tab selection misses “Show keyboard”; do not mark device regressions fixed from compile evidence alone.
