# APK verification

All artifacts below were built from this worktree. APK signatures were checked using Android Build Tools 37.0.0 `apksigner`; each verified artifact carries the `C=US, O=Android, CN=Android Debug` certificate. They are evaluation builds, not production-signed releases.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | `app/build/outputs/apk/market/debug/app-market-debug.apk` | 43,571,814 bytes | `b19031080566d68ed25ffcc3ca14e5361b27d5b9497eaf68397691aa6bbe7f87` |
| GPlayDebug | `app/build/outputs/apk/gplay/debug/app-gplay-debug.apk` | 42,391,770 bytes | `52e0ad2a1786eb4f1092296942be3a1e74ac6f94ffa8f3fb567b95a00969448a` |
| MarketPreview | `app/build/outputs/apk/market/preview/app-market-preview.apk` | 18,171,747 bytes | `38ee2c9044451bb38dd61299303fa53b10ce066a66c2d742b2a519c2d96015fa` |
| GPlayPreview | `app/build/outputs/apk/gplay/preview/app-gplay-preview.apk` | 18,445,578 bytes | `5b089c4c0c3d4f02d0b6cfc0bd81a1df1fa1a692a45931b097b638f35280e813` |

The Preview variants ran through R8/minification and resource shrinking before the final snippet-toolbar change; the latest source was rebuilt as MarketDebug and passed lint, JVM tests, and instrumentation-source compilation. `adb` is installed but no emulator/device is attached. No artifact is claimed to be suitable for production release signing.
