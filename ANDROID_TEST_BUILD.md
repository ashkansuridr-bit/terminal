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

Install only for testing. It is not signed with a publisher's production key and must not be represented as a production or store-ready build. This APK was rebuilt from commit `108d7d23b7bc71b6eb6810369d510e1bfe2b02ea` with `:app:assembleMarketDebug`. It is signed with the Android Debug certificate; `/root/android-sdk/build-tools/37.0.0/apksigner verify --verbose` passed. Size: 43704018 bytes; SHA-256: `aff97cf78f6e31ab21406c5877ae666a2609f61af5eec212f93cce738c2b5a4b`. The latest completed CI run [38448985782](https://github.com/ashkansuridr-bit/terminal/actions/runs/38448985782) tested commit `7727c4f`: Verify passed; API26 Debug/Preview each had 4/114 UI failures and API36 Debug/Preview each had 3/114 (14 failed executions). API37 Debug/Preview timed out before tests. Local changes after that run pass 1,025 JVM tests, app/test compilation and lint, but still await device runtime CI. The owner reports a successful manual test on a physical Android phone; this workspace has not captured an independent device run. Local instrumentation is blocked: the SDK emulator image under `/root/android-sdk/system-images/android-36` lacks `kernel-ranchu`, and no device is attached.
