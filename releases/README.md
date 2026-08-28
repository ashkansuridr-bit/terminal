# releases/

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

Current artifacts are attached to the GitHub release, not committed here.
Verify with `sha256sum -c SHA256SUMS.txt`.
