# Android test build — 0.7.2 MarketDebug

This APK was copied to the repository root from the MarketDebug build output for convenient review. It is an evaluation artifact, not a store release.

- Source commit: `cf0ac9d`
- Variant: `MarketDebug`
- Version name/code: `0.7.2` / `12`
- Application ID: `app.terminalssh.secure.debug` (confirm from Gradle build configuration)
- File: [`TerminalSSH-0.7.2-MarketDebug-TEST.apk`](TerminalSSH-0.7.2-MarketDebug-TEST.apk)
- Size: 41,512,168 bytes
- SHA-256: `36a954e19c2a9ae5831e2dd22c1ea196a0d72483cff9e0e6fd447fae924e98ae`
- Signature: Android Debug certificate; `apksigner verify --verbose` passed.

Install only for testing. It is not signed with a publisher's production key and must not be represented as a production or store-ready build. This APK contains the app source from `cf0ac9d`; later commits changed tests/docs only through `c68b95f`. No local device installation or runtime smoke test was available when this artifact was documented. The latest completed CI run [38444931145](https://github.com/ashkansuridr-bit/terminal/actions/runs/38444931145) tested `c68b95f`: Verify passed; API26 Debug/Preview and API36 Debug/Preview each failed 4/114 UI tests (16 failed executions, the same four tests in each variant); API37 Debug/Preview timed out before tests. A further test-harness and TCS-05 implementation is locally compiled/unit-tested and awaits fresh runtime CI. The owner reports a successful manual test on a physical Android phone; this workspace has not captured an independent device run. Local instrumentation is blocked: the SDK emulator image under `/root/android-sdk/system-images/android-36` lacks `kernel-ranchu`, and no device is attached.

The application source is licensed under Apache-2.0. The APK includes third-party components subject to their own licenses; see [`NOTICE.md`](NOTICE.md).
