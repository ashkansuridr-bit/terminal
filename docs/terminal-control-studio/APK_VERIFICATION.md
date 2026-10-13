# APK verification

The MarketDebug artifact was built from source commit `c90198718dfe27bc1a412293f5536dd480db4fa0` on `main`. Android Build Tools 37.0.0 `apksigner verify --verbose` passed; the artifact carries the Android Debug certificate. This is an evaluation build and is not production-signed.

| Variant | Artifact | Size | SHA-256 |
|---|---|---:|---|
| MarketDebug | [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](../../TerminalSSH-0.7.2-MarketDebug-TEST.apk) | 43,636,502 bytes | `5ea6f3d21ab6012d34731fd3abb5e7b833bf3b072eed589f4a22c3229cebeb2f` |

The build and signature checks passed. No local ADB/device is available. The project owner reports a successful manual test on a physical Android phone; automated UI instrumentation for source commit `c901987` is pending in [GitHub run 38428061570](https://github.com/ashkansuridr-bit/terminal/actions/runs/38428061570). Production signing is unverified; do not describe this APK as a production release.
