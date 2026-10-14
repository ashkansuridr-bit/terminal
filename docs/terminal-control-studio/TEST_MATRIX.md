# Test matrix

Evidence captured on 2026-10-14; local checks cover the current uncommitted follow-up, while the latest completed CI run tests `5bfbed7`.

| Gate | Result | Evidence / limitation |
|---|---|---|
| MarketDebug Kotlin compile | PASS | `:app:compileMarketDebugKotlin` completed in successful Gradle invocation. One deprecation warning for `SHOW_IMPLICIT`; no compile errors. |
| MarketDebug JVM unit tests | PASS | Latest local invocation: 1,030 tests across 122 XML suites, 0 failures, 0 errors, 0 skipped. |
| MarketDebug APK assembly | PASS | `:app:assembleMarketDebug`; 41,556,964-byte APK produced, copied to the root test artifact, SHA-256 `056f67ef0d33c82307c857287768418c03a42c174ab1cb0899e6660a2d2c4680`, and v2 signature verified with the Android Debug certificate. |
| GPlayDebug APK assembly | PASS | `:app:assembleGplayDebug`; APK produced and debug certificate verified. |
| MarketPreview / GPlayPreview | PARTIAL | Earlier MarketPreview/GPlayPreview outputs were built and signature-checked on prior source; these variants were not rebuilt for the current follow-up. Current CI Verify builds Debug variants; Release candidates were skipped because instrumentation failed. |
| Android test Kotlin compile | PASS | `:app:compileMarketDebugAndroidTestKotlin` passed with the current progress-checked scrolling and editor/profile tests. |
| MarketDebug lint | PASS | `:app:lintMarketDebug`: 0 errors, 108 warnings, 2 hints. |
| `git diff --check` | PENDING | Run before commit. |
| Instrumentation / Compose UI | FAIL; corrections pending fresh CI | Run [38492997313](https://github.com/ashkansuridr-bit/terminal/actions/runs/38492997313) on `5bfbed7`: Verify PASS. API26 Debug/Preview each failed 3/114; API36 Debug failed 2/114 and Preview 1/114; aggregate 447/456 passed, 9 failed, 0 skipped across 4 completed variants. Settings accessibility switch behavior passed on API36; API26 failed in the helper before state/action assertions. Remaining tests: profile editor/visibility on API26 and API36, Tab reachability on API26, and HostList vertical reachability on API36 Debug. API37 Debug/Preview remained offline and generated no reports. Current follow-up verifies actual tree movement before fallback gestures, fixes IME/back ordering, isolates the profile preset, and wraps all profile chips into accessible rows. Runtime status remains unverified until the new run passes. |
| API 26 / 31 / 34 / 36 / 37 device matrix | PARTIAL | Run 38492997313 exercised API26/36 and attempted API37. API37 `adb` repeatedly reported the emulator offline and there are no test reports. API31/34 are not in this workflow. Local AVD cannot boot because its API36 system image lacks `kernel-ranchu`; no physical target is attached here. |
| SSH/SFTP integration | NOT RUN | No isolated SSH server was provisioned for this toolbar-only feature. |
| Production signing | NOT VERIFIED | MarketDebug is debug-signed; production signing was not attempted. |
