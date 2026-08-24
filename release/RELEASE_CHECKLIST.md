# Terminal SSH 0.1.0 market release checklist

## Automated gates
- [x] Source security audit.
- [x] Market metadata/config audit.
- [x] Known-host policy JVM smoke tests.
- [x] AES-GCM vault JVM smoke tests.
- [x] Final version/package configuration.
- [x] Privacy policy and market listing source included.
- [x] CI configured for Android unit test, lint, APK and AAB builds.
- [x] Tag CI configured for release signing, signature verification and SHA-256 hashes.

## Publisher-owned gates
- [ ] Add a real Android release keystore to CI secrets (`TERMINAL_KEYSTORE_BASE64`, passwords and alias).
- [ ] Run the tag build and confirm `testReleaseUnitTest` and `lintRelease` pass.
- [ ] Install the signed release APK on at least API 26 and API 36 devices/emulators.
- [ ] Verify connection to a controlled SSH server, first-use fingerprint flow and changed-host-key rejection.
- [ ] Host the privacy policy at a public HTTPS URL if the selected market requires a URL.
- [ ] Capture real screenshots from the signed build and upload the signed artifact through the publisher account.

The unchecked items require publisher credentials, signing identity, Android runtime/emulator or market account access and cannot be truthfully completed by source-only static validation.
