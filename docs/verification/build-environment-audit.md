# Build environment and release evidence audit

Audited 2026-10-06. This audit is not an Android build certificate or release report.

## Actual execution

JDK 17 can start with `LD_LIBRARY_PATH` set to its permitted local library directories.
The following requested baseline was attempted without escalation or proxy fallback:

```bash
env LD_LIBRARY_PATH=/usr/lib/jvm/java-17-openjdk-amd64/lib:/usr/lib/jvm/java-17-openjdk-amd64/lib/server \
  JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
  GRADLE_USER_HOME=/workspace/scratch/fe106073006a/gradle-cache \
  ./gradlew --no-daemon clean testMarketDebugUnitTest testGplayDebugUnitTest \
  lintMarketDebug lintGplayDebug assembleMarketDebug assembleGplayDebug
```

Exit status: **1**. The wrapper attempted to download Gradle 8.13 and immediately raised
`java.net.SocketException: Network is unreachable`. Failure occurred before project
configuration, so no requested Gradle task ran. Do not count even `clean` as executed.
The cached distribution has only incomplete `.part`/lock files, not an installed Gradle.

No usable Android SDK, emulator, `adb`, Gradle installation or Kotlin compiler was found
in the permitted inspected environment or PATH. Available tools do not provide an Android
compilation service. Repeated downloads, alternate proxies, escalations or runtime compiler
mocking are not valid substitutes for access and real build evidence.

Python artifact validation tests were run: **9 tests passed**. Workflow YAML was parsed;
read-only permissions, publication absence, candidate dependency on verify/instrumentation,
API 26/36 minimum, debug/preview matrix and empty candidate signing variables were asserted.
These are source/tooling checks; no real APK/AAB was supplied to the inspectors.

## Current source configuration

- Application ID: `app.terminalssh.secure`; preview adds `.preview`; debug adds `.debug`.
- Source version: `0.6.1`, code `9`; preview suffix `-preview`.
- Flavors: `market`, `gplay`; min SDK 26; compile/target SDK 36.
- Preview and release enable minification/resource shrinking.
- Preview uses the test certificate. Market release only receives a production signer
  when every external signing variable exists; candidate workflow clears them explicitly.
- No production keystore was inspected, generated or assumed available.
- No output package, installed behavior, certificate, version, size or binary SHA256 was
  verified because there is no newly built Android output.

## Critical source coverage limitations

Source includes a 30-item/3-slot scheduler test. It exercises an in-memory coroutine
scheduler, not 30 real SFTP transfers; both test execution and real-channel integration
remain required. Existence/remote-write failure tests require actual channel/network
verification in addition to policy tests.

Provider instrumentation currently proves registration, permission gating, root/document
rejection and path boundaries. It does not establish two simultaneous external editor
opens, real remote close-upload failure or process-death recovery. Staging/persistence
unit tests cannot replace these external-caller lifecycle scenarios.

API 26/36 debug and preview CI jobs are configured but have not run for this source. API
30/34 expansion remains pending. Preview instrumentation adds test-only R8 rules; the
canonical packaged preview has different rules and has not been installed or exercised.
Application-class references outside the key-generation test keep list are another
unverified minified test linkage risk. Real runner execution must diagnose this risk.

No instrumentation pass has been observed for biometric, terminal IME on a small phone,
FA/AR layout, host-key mutation, real vault, concurrent SAF editing, transfer interruption,
remote mutation during resume, or process death. Existing test files are test proposals
until they execute successfully against their actual required environment.

## Safe next verification environment

Use an Android development machine or authorized CI runner with JDK 17, Gradle 8.13,
Android platform 36/build tools and emulators. Commit the intended source snapshot before
candidate packaging. Run the baseline, negative/regression suites and device matrix;
diagnose every failure without skipping tests. Then build preview and unsigned market
APK/AAB from that snapshot, inspect them with the release scripts, and install/exercise
the exact canonical preview binaries on API 26/36. Production signing must use the real
project key outside source control. Publication stays blocked until all mandatory gates
have actual evidence.
