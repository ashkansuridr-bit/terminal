# Release gate changes — validation status

The workflow requires source audit, both flavor JVM/lint/debug builds, API 26 and
API 36 instrumentation for debug and minified preview (API 37 / Android 17 runs as an
advisory, non-blocking matrix entry), then release candidate builds.
There is no publication job. GitHub permissions are read-only and candidates are only
uploaded as private workflow artifacts, including on tag builds. Missing required market
outputs fail packaging; a debug APK cannot substitute for a market output.

All three candidates are built from one checkout. Production signing variables are
explicitly empty in the candidate job. The preview keeps its separate `.preview`
application ID and test certificate. Artifact inspection verifies actual package,
version, debuggable flag, preview certificate fingerprint, unsigned market ZIP/signing
state, bundle validity, names, sizes and SHA-256. The generated `ARTIFACTS.md` and JSON
record the full source commit. `SHA256SUMS.txt` is checked before artifact upload.
Unsigned market files require production signing before installation or distribution.

Locally executed: nine Python artifact-validator regression tests; Python compilation;
workflow YAML parse. No actual Android binary was produced or inspected in this environment.
The workflow has not been executed on GitHub. Emulator image availability, integration
coverage, Android build/tests, R8/runtime behavior and the full pipeline remain unverified.

Important existing limitation: preview instrumentation adds
`proguard-rules-androidtest.pro`, whereas the packaged preview uses canonical R8 rules.
The instrumented variant therefore does not prove the exact packaged binary's behavior.
Before distribution, install the packaged preview on API 26 and 36 and exercise launch,
FA/AR keyboard + IME, vault/key generation, provider concurrent save/recovery and SFTP
transfer/network/process-death scenarios. Keep this limitation open until exact-binary
runtime verification exists. CI instrumentation failures must block publication.

API 30/34 expansion remains pending. CI runs all available instrumentation suites, but
running a suite does not establish coverage for scenarios absent from its source.

Preview instrumentation also references application classes beyond the narrow key-related
test keep rules. Their final name resolution and Android test dependencies have not been
verified against R8 output. A green debug suite cannot establish minified suite viability.

See `build-environment-audit.md` for the current build blocker and coverage gaps. This
document describes required gates, not successful execution of those gates.
