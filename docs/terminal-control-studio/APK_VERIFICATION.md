# APK verification

The current-source artifact listed below was built from the local worktree based on commit `8008eba`; Android source was unchanged after assembly using Android Build Tools 37.0.0 `apksigner`; it carries the `C=US, O=Android, CN=Android Debug` certificate. It is an evaluation build, not a production-signed release. Older variant artifacts from previous checkpoints are not present in this worktree and are not claimed as current-source builds.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | `app/build/outputs/apk/market/debug/app-market-debug.apk` | 43,631,054 bytes | `b828532f4974f40bed461905578621f54fb69f3298c5c1cd50754ecdb7944631` |

Current-source MarketDebug assemble and signature verification passed. `adb` is installed but no emulator/device is attached. Production signing is unverified, and no artifact is claimed to be suitable for production release.
