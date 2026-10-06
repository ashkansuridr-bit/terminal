# Terminal SSH source checkpoint — not a release

This snapshot contains actual implementation work and authored regression tests. It is NOT the requested fully completed modernized application and contains no new APK/AAB. Read docs/verification/0.7.0-progress.md and checkpoint-tests.txt before using it.

## Source identity

Base repository: https://github.com/ashkansuridr-bit/terminal
Base commit: 9e5ad4fbb1446ef54012dafa8775d321eada04f7
Working branch: release/0.7.0
Existing version unchanged: 0.6.1 / 9 until validated release work is complete.

## Apply safely

Preferred: checkout the base commit on a new branch, then apply source-update.patch from the ZIP with git apply --check first. It contains all source changes relative to the base, including new files. Do not apply it to a modified checkout without resolving differences.

Alternatively copy terminal-source/ over a clean checkout after inspecting the diff. The ZIP excludes Git internals, credentials/local configuration, caches and historical APK binaries. Excluded old binaries are not deletion instructions. The root .github folder is part of the source snapshot and must also be copied.

Review git diff, commit, and push the branch. The release branch workflow should run the Android gates on GitHub. Inspect failed checks and logs; do not tag/publish to skip them. No write to GitHub succeeded from this environment.

## Local validation

./gradlew clean testMarketDebugUnitTest testGplayDebugUnitTest lintMarketDebug lintGplayDebug assembleMarketDebug assembleGplayDebug
./gradlew connectedMarketDebugAndroidTest
./gradlew assembleMarketPreview assembleMarketRelease bundleMarketRelease

Run instrumentation on API26/36 minimum, then 30/34. Test keyboard/IME/RTL, real SSH/SFTP interrupted writes, 30-file queue, recovery, vault, credential changes and provider concurrent opens. Real test/build output is necessary; authored tests are not proof.

Candidate preparation: scripts/release_local.sh (requires installed Android SDK/Gradle dependencies and bundletool). Preview is test-signed under .preview; unsigned market package is production id. Artifact validator checks actual package/signature/version/hash. A test signature is not a production signature. Production signing must use the publisher's existing key; never commit the key or credentials.

## Evidence and unresolved risks

9 Python tests and 6 local shell protocol cases pass. Existing three static gates and XML syntax pass. Android compilation, Kotlin JVM tests, lint, device/emulator checks and generated artifacts remain unavailable: Gradle distribution download fails Network is unreachable. Several substantive safety/features are still pending as listed in the ledger.
