# Test matrix

Evidence captured for this worktree on 2026-10-13; latest local checks ran on commits `e2bd89c` / `02ef848`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | `:app:testMarketDebugUnitTest`; 1,013 tests across 118 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; APK produced and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | MarketPreview APK output from current app source passes `apksigner`; the Gradle assemble task was interrupted when disk space reached zero. GPlayPreview was not rebuilt on the latest source. Earlier variants assembled on prior source. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed after the current accessibility and test-harness fixes. |
| MarketDebug lint | PASS | 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Clean after removing trailing blank line. |
| Instrumentation / Compose UI | FAIL; follow-up queued after push | PR run 38320080882 completed: API 26/36 had UI failures; API 37 could not boot. Latest code changes have compile evidence only until fresh PR CI runs. No local emulator/device is attached. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | PR CI exercises API 26/36/37; API 31/34 are not included in this workflow. API 26/36 ran and exposed failures; API 37 was blocked by offline emulator boot. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
