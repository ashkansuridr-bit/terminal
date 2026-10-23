# APK verification

The root `MarketDebug` test APK was assembled with `:app:assembleMarketDebug` from the current local source, including the accessible Settings switch and the Shell preset with Snippets prioritized in the compact toolbar.

| Variant | Artifact | Size | SHA-256 | Signature |
|---|---|---:|---|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,681,533 bytes | `09457c88f01e6ab5a667ffb64c7774f879ae74c4279aa4251a9d897ea9b91149` | Android Debug certificate; v2 verified |

`apkanalyzer` reports package `app.terminalssh.secure.debug` and version `0.7.2-debug`. Build Tools 37.0.0 `apksigner verify --verbose` passed. Production signing was not attempted; this APK is not a release artifact.

Latest completed runtime evidence is CI run [39059627211](https://github.com/ashkansuridr-bit/terminal/actions/runs/39059627211) for commit `377c644`. Verify passed. API26/36 Debug/Preview ran 456 tests: 448 passed, 8 failed, 0 skipped. The failures were Settings search (all four variants), a stale HostEdit field (API26 Preview and API36 Debug), and wide-layout test geometry (API36 Debug/Preview). Palette selection and compact Snippets passed in all four variants. API37 Debug/Preview did not boot (`adb: device offline`, timeout), so no tests ran there. Release candidates were skipped. Follow-up UI test corrections compile but still require runtime confirmation.
The project owner reports a successful test on a physical Android phone; no independent device log was captured in this workspace. Local API26 and API36 instrumentation replay is unavailable because no device is attached and the installed emulator images are incomplete. The APK's signature does not certify installation/runtime behavior on those API levels.
