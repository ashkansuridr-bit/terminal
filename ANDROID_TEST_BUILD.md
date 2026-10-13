# Android test build — 0.7.2 MarketDebug

This APK was copied to the repository root from the MarketDebug build output for convenient review. It is an evaluation artifact, not a store release.

- Source commit: `c90198718dfe27bc1a412293f5536dd480db4fa0`
- Variant: `MarketDebug`
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug` (confirm from Gradle build configuration)
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 43,636,502 bytes
- SHA-256: `5ea6f3d21ab6012d34731fd3abb5e7b833bf3b072eed589f4a22c3229cebeb2f`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed.

Install only for testing. It is not signed with a publisher's production key and must not be represented as a production or store-ready build. No local device installation or runtime smoke test was available when this artifact was documented. CI run [38428061570](https://github.com/ashkansuridr-bit/terminal/actions/runs/38428061570) is validating source commit `c901987`; UI instrumentation results are pending. The owner reports a successful manual test on a physical Android phone. Local ADB/device runtime verification is unavailable in this workspace.

The application source is licensed under Apache-2.0. The APK includes third-party components subject to their own licenses; see [`NOTICE.md`](NOTICE.md).
