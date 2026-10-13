# APK verification

The MarketDebug artifact was rebuilt from source commit `108d7d23b7bc71b6eb6810369d510e1bfe2b02ea` on `main` with `:app:assembleMarketDebug`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. This is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 43,704,018 bytes | `aff97cf78f6e31ab21406c5877ae666a2609f61af5eec212f93cce738c2b5a4b` |

The build and signature checks passed. No local ADB/device is available. The project owner reports a successful manual test on a physical Android phone; the latest completed CI run [38448985782](https://github.com/ashkansuridr-bit/terminal/actions/runs/38448985782) for commit `7727c4f` had 14 failed API26/36 instrumentation executions (four per API26 variant and three per API36 variant). API37 Debug and Preview both failed before tests because `adb` reported the emulator offline and boot timed out. A later local source/test follow-up compiles and passes JVM tests but has not run on an emulator. These tests do not certify the APK's runtime behavior. The APK was rebuilt after CI from app source commit `108d7d23b7bc71b6eb6810369d510e1bfe2b02ea`; production signing is unverified, so this is not a production release.
