# APK verification

The root `MarketDebug` test APK was assembled with `:app:assembleMarketDebug` from the current local source, including accessible Settings switch semantics, profile chips that wrap for narrow screens, and the Shell preset Tab key priority.

| Variant | Artifact | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,681,527 bytes | `bca914ad9d1cd4550b251b08670edb07c02f81d8c9ffc3c378acfb314e346f10` | Android Debug certificate; v2 verified |

`apkanalyzer` reports package `app.terminalssh.secure.debug` and version `0.7.2-debug`. Build Tools 37.0.0 `apksigner verify --verbose` passed. Production signing was not attempted; this APK is not a release artifact.

Latest completed runtime evidence is CI run [38498546350](https://github.com/ashkansuridr-bit/terminal/actions/runs/38498546350) for commit `f7e8ab9`. Verify passed. API26 Debug/Preview each failed 1/114 on Snippets toolbar-key reachability; API36 Debug/Preview each failed 2/114 on Snippets reachability and Settings security-toggle scrolling. Aggregate: 450/456 passed, 6 failed, 0 skipped across four completed variants. Tab, HostEdit and HostList regressions passed. API37 Debug/Preview remained offline before tests (`adb: device offline`, emulator boot timeout); no reports were generated. Release candidates were skipped. Current local follow-up removes the page-scroll/fast-fling pair from the Snippets helper and increases Settings scroll reach while keeping each step under half a viewport; AndroidTest compilation passed and runtime evidence is pending.
The project owner reports a successful test on a physical Android phone; no independent device log was captured in this workspace. Local API26 and API36 instrumentation replay is unavailable because no device is attached and the installed emulator images are incomplete. The APK's signature does not certify installation/runtime behavior on those API levels.
