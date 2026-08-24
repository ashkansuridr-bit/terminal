# OpenCode Task — Build, Fix, Push, and Produce APK/AAB

Goal: take this Android project to a reproducible, installable release build and push it to GitHub using credentials already configured on this server. Do not print, echo, or commit any GitHub token, keystore password, private key, or other secret.

## Required outcome
1. Inspect the repository and run all existing local audits/smoke tests first.
2. Ensure a valid Gradle Wrapper exists and is pinned to Gradle 8.13. If wrapper files are missing, generate/add them using a trusted Gradle installation or official distribution.
3. Ensure Android SDK platform 36 and required Build Tools are installed and ANDROID_HOME/ANDROID_SDK_ROOT are configured.
4. Run, in this order where applicable:
   - source/security audit scripts already in the project
   - ./gradlew testReleaseUnitTest
   - ./gradlew lintRelease
   - ./gradlew assembleDebug
   - ./gradlew assembleRelease (only if signing config is available)
   - ./gradlew bundleRelease (only if signing config is available)
5. Fix every compile, dependency, lint, manifest, resource, Compose, termlib, JSch, or packaging error you encounter. Do not disable lint checks just to make the build green unless the finding is proven false-positive and documented.
6. For host key verification, never introduce StrictHostKeyChecking=no or silent host-key acceptance.
7. Do not convert passwords/private keys/passphrases to long-lived immutable Strings unless unavoidable by a library API; prefer byte arrays and wipe temporary buffers.
8. Do not commit keystores, signing passwords, GitHub tokens, local.properties, or secret files.
9. Produce at minimum a debug APK that installs. If production signing credentials are available on the server, also produce signed release APK and AAB and verify their signatures.
10. If adb/emulator or a physical device is available, install the debug/release APK and smoke-test app launch. If an SSH test host is configured on the server, test a real SSH session, TOFU approval, reconnect, and host-key mismatch rejection.
11. Initialize git if needed and push the final code to a GitHub repository. Prefer repository name `terminal-ssh-android` if no existing repository is configured. Use the server's existing authenticated git/gh setup; never place credentials in remote URLs or logs.
12. Add/repair GitHub Actions so pushes/PRs run unit tests + lint + debug APK build and upload the debug APK as an artifact. Release/tag jobs should build signed APK/AAB only when signing secrets are configured.
13. After push, confirm the workflow run result. If CI fails, inspect logs, fix, push again, and repeat until green or until blocked by a missing external credential/service.

## Final report to user
Return only concrete results:
- GitHub repository URL
- branch/commit SHA
- CI status
- exact APK path/artifact name and whether debug or signed release
- exact AAB path/artifact name if produced
- APK/AAB SHA-256
- signing certificate SHA-256 fingerprint for signed release artifacts
- Android min/target SDK and applicationId
- any remaining blocker, if one truly cannot be resolved on this server

## Security constraints
- Never reveal credentials in chat/output/logs.
- Never commit `.jks`, `.keystore`, `.p12`, `.pem`, tokens, passwords, `local.properties`, or decoded secret material.
- Keep `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` unless there is a documented product requirement to change them.
- Preserve explicit host-key verification and TOFU confirmation.
