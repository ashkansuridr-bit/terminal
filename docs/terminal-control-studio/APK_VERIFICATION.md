# APK verification

The MarketDebug artifact was built from source commit `cf0ac9d` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. This is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 41,512,168 bytes | `36a954e19c2a9ae5831e2dd22c1ea196a0d72483cff9e0e6fd447fae924e98ae` |

The build and signature checks passed. No local ADB/device is available. The project owner reports a successful manual test on a physical Android phone; the latest completed CI run [38448985782](https://github.com/ashkansuridr-bit/terminal/actions/runs/38448985782) for commit `7727c4f` had 14 failed API26/36 instrumentation executions (four per API26 variant and three per API36 variant). API37 Debug and Preview both failed before tests because `adb` reported the emulator offline and boot timed out. A later local source/test follow-up compiles and passes JVM tests but has not run on an emulator. These tests do not certify the APK's runtime behavior. The APK itself was built from app source `cf0ac9d`; production signing is unverified, so this is not a production release.
