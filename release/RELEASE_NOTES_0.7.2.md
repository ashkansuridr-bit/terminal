# Terminal SSH 0.7.2

Integrated release candidate. No production signing; market artifacts stay unsigned.

This build merges two independent work streams forked from 0.7.1:

- `feature/terminal-50-tools` — a 50-item feature engine
  (42 implemented with green JVM tests, 5 already on main, 2 blocked, 1 deferred).
- `fix/0.7.2-instrumentation-reliability` — a 55-item bug/stability ledger.

## Highlights

- **Server tools:** resource dashboard, live log viewer, process manager, systemd,
  Docker dashboard, server-side Git, network ports, and a dry-run runbook.
- **SFTP:** transfer preview, conflict "apply to all", batch pause/resume/retry/cancel,
  bounded folder size, remote search, text/hex preview, and honest SHA-256 integrity
  (`verified` only on a real match, otherwise `unverified`).
- **Terminal:** scrollback search, bookmarks, link detection, bounded output export,
  optional local command history, broadcast input, configurable toolbar/cursor.
- **Connection:** quick connect, tags/filter, offline host icons, aggregate status,
  per-host options, connection diagnostics, `known_hosts` codec, `~/.ssh/config` analysis.
- **Security:** encrypted local backup (PBKDF2-HMAC-SHA256 210k + AES-256-GCM).
- **Stability:** real SSH exec deadline + non-zero exit rejection, fail-closed jump
  routes, no unused local history retention, fail-closed recursive SFTP, SAF null
  handling, larger accessibility targets.

## Honest status

- 47/50 features implemented; most are **pure engines with JVM tests**. Compose UI
  rendering and on-device E2E paths are **not device-verified** in this environment.
- F031 (split panes) and F035 (multi-hop ProxyJump) remain **BLOCKED** and ship
  planning cores only; F049 (Mosh) is deferred.
- No production keystore is available. The preview APK is test-signed and uses a
  separate package id; the market APK/AAB are unsigned and not installable.

See `docs/features/FEATURE_STATUS.json`, `docs/BUGFIX_STATUS.json` and
`releases/ARTIFACTS.md` for the exact, machine-checked state.
