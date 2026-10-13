# APK verification

The current-source artifact was built from the source committed as `93135b0` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose --print-certs` passed; the artifact carries the Android Debug certificate. It is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | `app/build/outputs/apk/market/debug/app-market-debug.apk` | 43,636,502 bytes | `feb9be8b5ec04bd9874c6215a4feba90670cb0071b1b6816bf262db53e2fe534` |

The MarketDebug assemble and signature checks passed. The current workspace has no `adb` executable or connected device, so installation and runtime checks were not performed. Production signing is unverified; this APK is not suitable to describe as a production release.
