# Test matrix

Evidence captured for this worktree on 2026-10-12.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | `:app:testMarketDebugUnitTest`; 1,012 tests across 118 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; APK produced and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PASS | Both minified R8 variants assembled; both are signed with the Android Debug certificate. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin`; instrumentation sources compile, but were not run. |
| MarketDebug lint | PASS | 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PASS | Clean after removing trailing blank line. |
| Instrumentation / Compose UI | BLOCKED | `adb: command not found`; no emulator/device available to Gradle. |
| API 26 / 31 / 34 / 36 / 37 device matrix | BLOCKED | No Android device/emulator attached; these API levels were not exercised. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
