# releases/

## ⬇️ Latest build — install this one

| File | Install? | Signing |
| --- | --- | --- |
| [`TerminalSSH-0.6.1-checkpoint-1f01a76-preview-TEST-SIGNED.apk`](TerminalSSH-0.6.1-checkpoint-1f01a76-preview-TEST-SIGNED.apk) | **Yes** — works on any Android 8.0–17 phone (universal, 17.9 MB) | Android Debug *test* certificate (not a production signature) |
| [`TerminalSSH-0.6.1-checkpoint-1f01a76-market-UNSIGNED.apk`](TerminalSSH-0.6.1-checkpoint-1f01a76-market-UNSIGNED.apk) | **No** — unsigned, Android refuses to install it | None; needs the publisher's production key |

Built from commit `1f01a76` (Android 17 / API 37 target). This is a checkpoint, not a
release: unit tests and lint pass, but device/emulator tests and real SSH/SFTP runs have
not been verified. The preview package id is `app.terminalssh.secure.preview`, so it
installs next to (not over) any other Terminal SSH build.

To install: download the TEST-SIGNED file on the phone, open it, and allow
"install unknown apps" for your browser when asked.

---

## Historical artifacts

Historical artifacts kept for provenance. **None of these is a signed production
release** — Terminal SSH has never published one, because the original production
keystore is unavailable.

| File | Really is | Signing |
| --- | --- | --- |
| `TerminalSSH-0.5.1-preview-*.apk` | 0.5.1 preview, id `…secure.preview` | debug key |
| `TerminalSSH-0.6.0-preview-*.apk` | 0.6.0 preview, id `…secure.preview` | debug key |
| `terminal-ssh-v0.6.0-*-DEBUG-0.5.1-NOT-FOR-PRODUCTION.apk` | **debug build**, id `…secure.debug`, versionName `0.5.1-debug` | Android debug cert |

The last row was published under a `v0.6.0` release name while carrying debug
identity — the release-audit blocker. The files are renamed rather than deleted so
the record stays intact; do not distribute them as releases.

The latest build is also attached to the GitHub release `checkpoint-1f01a76`.
Verify with `sha256sum -c SHA256SUMS.txt`.
