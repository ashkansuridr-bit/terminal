# APK verification

The MarketDebug artifact was built from source commit `baa4596823325728f0f4762a126f2d852a29e3fc` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. This is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 43,636,502 bytes | `841b97d655f303d6f8319f3b3f2795966a57e99967c70e87f871808540500a5c` |

The build and signature checks passed. The workspace has no `adb` executable or connected device, so local installation/runtime checks were not performed. Production signing is unverified; do not describe this APK as a production release. The related GitHub run [38403567011](https://github.com/ashkansuridr-bit/terminal/actions/runs/38403567011) passed `verify` but its API 26/36/37 instrumentation matrix failed.
