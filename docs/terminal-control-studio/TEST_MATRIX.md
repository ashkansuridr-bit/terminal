# Test matrix

Evidence captured for this worktree on 2026-10-13; latest local checks ran on commits `e2bd89c` / `02ef848`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Most recent full rerun: 1,022 tests across 118 XML suites, 0 failures, 0 errors, 0 skipped (`--rerun-tasks`). |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; APK produced and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | MarketPreview APK output from current app source passes `apksigner`; the Gradle assemble task was interrupted when disk space reached zero. GPlayPreview was not rebuilt on the latest source. Earlier variants assembled on prior source. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed after the current accessibility and test-harness fixes. |
| MarketDebug lint | PASS | Latest app-source validation: 0 errors, 106 warnings, 2 hints. |
| `git diff --check` | PASS | Clean after removing trailing blank line. |
| Instrumentation / Compose UI | FAIL; new harness follow-up pending | Run 38433137725 on `757ec58`: API 26/36 Debug+Preview ran 114 tests each with 7–8 failures per job (31 failed executions total); API 37 Debug+Preview timed out before boot. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | CI exercised API 26/36/37. API 26/36 ran and exposed UI assertions; API37 did not reach test execution because both emulator variants timed out at boot. API31/34 are not in this workflow. Local `test36` AVD is not runnable because the installed image lacks `kernel-ranchu`; no physical target is attached here. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
