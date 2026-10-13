# APK verification

The root `MarketDebug` test APK was assembled with `:app:assembleMarketDebug` from application source committed as `081d053`.

| Variant | Artifact | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,555,788 bytes | `01e0409dae251ccfaf1656c956f86165dbef1040e14dd9261e0ad1d54e404aec` | Android Debug certificate; v2 verified |

`apkanalyzer` reports package `app.terminalssh.secure.debug` and version `0.7.2-debug`. Build Tools 37.0.0 `apksigner verify --verbose` passed. Production signing was not attempted; this APK is not a release artifact.

Latest runtime evidence is CI run [38452720047](https://github.com/ashkansuridr-bit/terminal/actions/runs/38452720047) for commit `ab331c7`. The Verify job passed. API26 Debug/Preview each had 3 failures of 114 tests (HostList, SettingsToggle, and the Tab keyboard action); API36 Debug/Preview each had 2 failures of 114 (HostList and SettingsToggle). API37 Debug/Preview did not run tests because the emulator was offline and boot timed out. Local follow-up changes now add explicit switch state semantics, fixture and viewport diagnostics, Activity-localized palette lookup, focused-editor/field alignment checks, and horizontal scrolling before the Tab accessibility assertion. These fixes compile locally but still need a fresh device run.

The project owner reports a successful test on a physical Android phone; no independent device log was captured in this workspace. Local API26 and API36 instrumentation replay is unavailable because no device is attached and the installed emulator images are incomplete. The APK's signature does not certify installation/runtime behavior on those API levels.
