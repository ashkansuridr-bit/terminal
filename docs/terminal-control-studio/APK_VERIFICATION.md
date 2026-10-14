# APK verification

The root `MarketDebug` test APK was assembled with `:app:assembleMarketDebug` from the current local source, including the explicit accessible Settings switch semantics and profile chips that wrap for narrow screens.

| Variant | Artifact | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,556,964 bytes | `056f67ef0d33c82307c857287768418c03a42c174ab1cb0899e6660a2d2c4680` | Android Debug certificate; v2 verified |

`apkanalyzer` reports package `app.terminalssh.secure.debug` and version `0.7.2-debug`. Build Tools 37.0.0 `apksigner verify --verbose` passed. Production signing was not attempted; this APK is not a release artifact.

Latest completed runtime evidence is CI run [38492997313](https://github.com/ashkansuridr-bit/terminal/actions/runs/38492997313) for commit `5bfbed7`. Verify passed. API26 Debug/Preview each reported 3 failures of 114; API36 Debug reported 2/114 and Preview 1/114 (9 failures among 456 executions). The Settings accessibility contract passed on API36 Debug/Preview; the API26 failure happened in test scrolling before control assertions. Remaining failures were toolbar Tab/profile reachability on API26, toolbar profile editor/visibility on API26/36, and host-list scrolling on API36 Debug. API37 Debug/Preview stayed `adb: device offline` and produced no test reports. Release candidates were skipped. The latest local follow-up passed 1,030 JVM tests, app/AndroidTest compilation, lint and APK assembly; new runtime corrections await CI.
The project owner reports a successful test on a physical Android phone; no independent device log was captured in this workspace. Local API26 and API36 instrumentation replay is unavailable because no device is attached and the installed emulator images are incomplete. The APK's signature does not certify installation/runtime behavior on those API levels.
