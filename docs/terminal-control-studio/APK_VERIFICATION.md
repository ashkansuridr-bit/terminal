# APK verification

The root `MarketDebug` test APK was assembled with `:app:assembleMarketDebug` from the current source, including the macro/import safety and Settings accessibility follow-up (pre-commit).

| Variant | Artifact | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 43,749,858 bytes | `bdcb2d74138d09a503d65428bfed1e7a91e18d472cf843de6b3ff844a2ac8aed` | Android Debug certificate; v2 verified |

`apkanalyzer` reports package `app.terminalssh.secure.debug` and version `0.7.2-debug`. Build Tools 37.0.0 `apksigner verify --verbose` passed. Production signing was not attempted; this APK is not a release artifact.

Latest completed runtime evidence is CI run [38457441726](https://github.com/ashkansuridr-bit/terminal/actions/runs/38457441726) for commit `cb40830`. Verify passed; API26 Debug/Preview each reported 4 failures of 114, API36 Debug 4/114 and Preview 2/114 (14 failures among 456 executions). HostList and SettingsToggle failed in all variants; HostEdit failed on API26 Debug/Preview and API36 Debug; TerminalKeyboard Tab failed on API26 Debug and Vim profile chip on API36 Debug. API37 Debug/Preview did not reach tests because the emulators remained offline and boot timed out. Release candidates were skipped. Current local follow-up passed 1,030 JVM tests, app/AndroidTest compilation, lint and APK assembly; runtime fixes await a fresh CI run.
The project owner reports a successful test on a physical Android phone; no independent device log was captured in this workspace. Local API26 and API36 instrumentation replay is unavailable because no device is attached and the installed emulator images are incomplete. The APK's signature does not certify installation/runtime behavior on those API levels.
