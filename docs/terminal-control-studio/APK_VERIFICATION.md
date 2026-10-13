# APK verification

The current-source artifact was built from the source committed as `28b679d` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. It is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | `app/build/outputs/apk/market/debug/app-market-debug.apk` | 43,636,502 bytes | `a354001d71c3b2dc586e662347172ab4b4cced17b8ebf918b8a63fed27176d0c` |

The MarketDebug assemble and signature checks passed. The current workspace has no `adb` executable or connected device, so installation and runtime checks were not performed. Production signing is unverified; this APK is not suitable to describe as a production release.
