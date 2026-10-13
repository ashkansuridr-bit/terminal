# Test matrix

Evidence captured for this worktree on 2026-10-13; latest app-source checks are from `cf0ac9d`, instrumentation compile from local follow-up after `ac623d9`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,025 tests across 119 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; APK produced and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | MarketPreview APK output from current app source passes `apksigner`; the Gradle assemble task was interrupted when disk space reached zero. GPlayPreview was not rebuilt on the latest source. Earlier variants assembled on prior source. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed after the current accessibility and test-harness fixes. |
| MarketDebug lint | PASS | Latest app-source validation: 0 errors, 106 warnings, 2 hints. |
| `git diff --check` | PASS | Clean after removing trailing blank line. |
| Instrumentation / Compose UI | FAIL; new harness follow-up pending | Run 38448985782 on `7727c4f`: API26 Debug/Preview each failed 4/114, API36 Debug/Preview each failed 3/114. Failures: HostEdit, HostList, SettingsToggle; API26 also had TerminalKeyboard modifier/Tab failure. Snippet passed in all four variants. Local follow-up compiles and JVM tests pass, but has no runtime evidence yet. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | Run 38448985782 exercised API26/36 and attempted API37. API26/36 had respectively four/three failures per variant; API37 did not reach test execution (`adb: device offline`, emulator boot timeout). API31/34 are not in this workflow. Local `test36` AVD is not runnable because the installed image lacks `kernel-ranchu`; no physical target is attached here. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
