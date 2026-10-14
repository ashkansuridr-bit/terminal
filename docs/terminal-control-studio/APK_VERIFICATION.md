# APK verification

The root `MarketDebug` test APK was assembled with `:app:assembleMarketDebug` from the current local source, including the explicit accessible Settings switch semantics. The fresh AndroidTest helper edits do not change the app binary.

| Variant | Artifact | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,556,964 bytes | `26d66df744045e47537894bbee05507124803cb95ba99498007f806798fb03a0` | Android Debug certificate; v2 verified |

`apkanalyzer` reports package `app.terminalssh.secure.debug` and version `0.7.2-debug`. Build Tools 37.0.0 `apksigner verify --verbose` passed. Production signing was not attempted; this APK is not a release artifact.

Latest completed runtime evidence is CI run [38490916105](https://github.com/ashkansuridr-bit/terminal/actions/runs/38490916105) for commit `90467df`. Verify passed; API26 Debug/Preview each reported 3 failures of 114 and API36 Debug 3/114, Preview 2/114 (11 failures among 456 executions). SettingsToggle failed in all variants; HostEdit failed on API36 Debug; TerminalKeyboard profile reachability failed in all four and Tab reachability in API26 Debug/Preview. API37 Debug/Preview did not reach tests because `adb` reported device offline and boot timed out. Release candidates were skipped. The newer local follow-up passed 1,030 JVM tests, app/AndroidTest compilation, lint and APK assembly; device runtime remains pending CI.
The project owner reports a successful test on a physical Android phone; no independent device log was captured in this workspace. Local API26 and API36 instrumentation replay is unavailable because no device is attached and the installed emulator images are incomplete. The APK's signature does not certify installation/runtime behavior on those API levels.
