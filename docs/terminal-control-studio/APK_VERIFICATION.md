# APK verification

The MarketDebug artifact was built from source commit `cf0ac9d` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. This is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,512,168 bytes | `36a954e19c2a9ae5831e2dd22c1ea196a0d72483cff9e0e6fd447fae924e98ae` |

The build and signature checks passed. No local ADB/device is available. The project owner reports a successful manual test on a physical Android phone; latest completed run [38428298877](https://github.com/ashkansuridr-bit/terminal/actions/runs/38428298877) on parent `1ef74e3` had API26/36 UI assertion failures and API37 emulator boot failure. Fresh CI for `cf0ac9d` is required before runtime acceptance. Production signing is unverified; do not describe this APK as a production release.
