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

Install only for testing. It is not signed with a publisher's production key and must not be represented as a production or store-ready build. This APK contains the app source from `cf0ac9d`; later commits only changed instrumentation tests. No local device installation or runtime smoke test was available when this artifact was documented. The latest completed CI run [38433137725](https://github.com/ashkansuridr-bit/terminal/actions/runs/38433137725) tested `757ec58`: Verify passed; the four API26/36 instrumentation jobs each ran 114 tests and failed 7–8 assertions (31 failed executions total); API37 Debug and Preview emulator boot timed out before tests. The current test-harness correction is uncommitted and must run in fresh CI. The owner reports a successful manual test on a physical Android phone. Local instrumentation is blocked: the SDK emulator image under `/root/android-sdk/system-images/android-36` has no kernel file, and no device is attached.

The application source is licensed under Apache-2.0. The APK includes third-party components subject to their own licenses; see [`NOTICE.md`](NOTICE.md).
