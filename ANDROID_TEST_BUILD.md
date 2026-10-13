# Android test build — 0.7.2 MarketDebug

This APK was copied to the repository root from the MarketDebug build output for convenient review. It is an evaluation artifact, not a store release.

- Source commit: `baa4596823325728f0f4762a126f2d852a29e3fc`
- Variant: `MarketDebug`
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug` (confirm from Gradle build configuration)
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 43,636,502 bytes
- SHA-256: `841b97d655f303d6f8319f3b3f2795966a57e99967c70e87f871808540500a5c`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed.

Install only for testing. It is not signed with a publisher's production key and must not be represented as a production or store-ready build. No local device installation or runtime smoke test was available when this artifact was documented. CI run [38403567011](https://github.com/ashkansuridr-bit/terminal/actions/runs/38403567011) passed its verify job, while its six API 26/36/37 instrumentation jobs failed; inspect those reports before treating UI behavior as verified.

The application source is licensed under Apache-2.0. The APK includes third-party components subject to their own licenses; see [`NOTICE.md`](NOTICE.md).
