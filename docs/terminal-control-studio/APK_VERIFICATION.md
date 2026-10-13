# APK verification

The current-source artifact listed below was built from the local worktree that became commit `895aea1`; Android source was unchanged after assembly. Android Build Tools 37.0.0 `apksigner`; it carries the `C=US, O=Android, CN=Android Debug` certificate. It is an evaluation build, not a production-signed release. Older variant artifacts from previous checkpoints are not present in this worktree and are not claimed as current-source builds.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | `app/build/outputs/apk/market/debug/app-market-debug.apk` | 43,631,054 bytes | `66b444cc092643aa295bc4bdf5594b6efa7addac06d15b96e0b42a8f47c8a831` |

Current-source MarketDebug assemble and signature verification passed. `adb` is installed but no emulator/device is attached. Production signing is unverified, and no artifact is claimed to be suitable for production release.
