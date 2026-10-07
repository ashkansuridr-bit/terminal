# releases/

## ⬇️ Latest build — install this one

| File | Install? | Signing |
| --- | --- | --- |
| [`TerminalSSH-0.7.0-preview-TEST-SIGNED.apk`](TerminalSSH-0.7.0-preview-TEST-SIGNED.apk) | **Yes** — works on any Android 8.0+ phone (universal, 17.9 MB) | Android Debug *test* certificate (not a production signature) |
| [`TerminalSSH-0.7.0-market-UNSIGNED.apk`](TerminalSSH-0.7.0-market-UNSIGNED.apk) | **No** — unsigned, Android refuses to install it | None; needs the publisher's production key |
| [`TerminalSSH-0.7.0-market-UNSIGNED.aab`](TerminalSSH-0.7.0-market-UNSIGNED.aab) | No — unsigned app bundle for market submission | None; needs the publisher's production key |

Built from commit `dff8ac0` (version code 10, Android 17 / API 37 target, min API 26).
Identity was machine-verified before packaging: package IDs, versionCode/versionName,
non-debuggable, and signature/unsignedness were inspected from the actual binaries.

### Verification status (0.7.0)

This is a release-candidate build, not a hyped release. As of the round that produced it:

- Static gates (source audit, market release gate, loop2 gate) and unit tests pass.
- Lint passes for both flavors; debug APKs build for both flavors.
- CI instrumentation (emulator matrix) is the authoritative verification channel; the
  results of the current run are tracked in `docs/verification/0.7.0-progress.md`. The
  test-signed preview APK here byte-matches the artifact verified there.
- The preview package id is `app.terminalssh.secure.preview`, so it installs next to
  (not over) any other Terminal SSH build.
- Production signing is **not established**: the original production keystore is
  unavailable, so no artifact here is a signed production release.

To install: download the TEST-SIGNED file on the phone, open it, and allow
"install unknown apps" for your browser when asked.

---

## Historical artifacts

Historical artifacts kept for provenance. **None of these is a signed production
release** — Terminal SSH has never published one, because the original production
keystore is unavailable.

| File | Really is | Signing |
| --- | --- | --- |
| `TerminalSSH-0.6.1-checkpoint-1f01a76-preview-TEST-SIGNED.apk` | 0.6.1 checkpoint, id `…secure.preview` | debug key |
| `TerminalSSH-0.6.1-checkpoint-1f01a76-market-UNSIGNED.apk` | 0.6.1 checkpoint, unsigned, id `…secure` | none |
| `TerminalSSH-0.5.1-preview-*.apk` | 0.5.1 preview, id `…secure.preview` | debug key |
| `TerminalSSH-0.6.0-preview-*.apk` | 0.6.0 preview, id `…secure.preview` | debug key |
| `terminal-ssh-v0.6.0-*-DEBUG-0.5.1-NOT-FOR-PRODUCTION.apk` | **debug build**, id `…secure.debug`, versionName `0.5.1-debug` | Android debug cert |

The last row was published under a `v0.6.0` release name while carrying debug
identity — the release-audit blocker. The files are renamed rather than deleted so
the record stays intact; do not distribute them as releases.

Verify with `sha256sum -c SHA256SUMS.txt`.