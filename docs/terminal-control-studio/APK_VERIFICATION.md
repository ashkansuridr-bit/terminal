# APK verification

The MarketDebug artifact was built from source commit `cf0ac9d` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. This is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,512,168 bytes | `36a954e19c2a9ae5831e2dd22c1ea196a0d72483cff9e0e6fd447fae924e98ae` |

The build and signature checks passed. No local ADB/device is available. The project owner reports a successful manual test on a physical Android phone; the latest completed CI run [38442115157](https://github.com/ashkansuridr-bit/terminal/actions/runs/38442115157) for test commit `ac623d9` had 24 failed API26/36 instrumentation executions (the same six UI tests failed in each variant), while API37 Debug and Preview timed out waiting for emulator boot. These tests do not certify the APK's runtime behavior. The APK itself was built from app source `cf0ac9d`; production signing is unverified, so this is not a production release.
