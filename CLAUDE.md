# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Terminal SSH — a native Android SSH/SFTP client (Kotlin + Jetpack Compose), built Persian-first
(RTL, `values/strings.xml` is the Persian default, `values-en/` is English). Ships without Google
Play Services for Iranian markets (Cafe Bazaar, Myket, F-Droid); an optional `gplay` flavor adds
Google sign-in. `applicationId` is `app.terminalssh.secure`.

## Commands

```bash
# Fast JVM unit tests (no device needed)
./gradlew testMarketDebugUnitTest
./gradlew testGplayDebugUnitTest

# Single test class / method
./gradlew testMarketDebugUnitTest --tests "app.terminalssh.secure.ssh.KnownHostsVerifierTest"
./gradlew testMarketDebugUnitTest --tests "app.terminalssh.secure.ssh.KnownHostsVerifierTest.methodName"

# Lint (must be error-free)
./gradlew lintMarketDebug
./gradlew lintGplayDebug

# Installable debug APK
./gradlew assembleMarketDebug

# Minified, shareable build (debug-signed, separate applicationId .preview)
./gradlew assembleMarketPreview

# Instrumentation tests — need a device/emulator; cover AndroidKeyStore behavior
# a JVM test cannot reach
./gradlew connectedMarketDebugAndroidTest

# Release static gates (run in CI before any build)
python3 scripts/source_audit.py
python3 scripts/market_release_gate.py
python3 scripts/loop2_gate.py
```

Both the `market` and `gplay` flavors must build; anything in the core SSH workflow belongs in
`main` source, never in a flavor. CI (`.github/workflows/android-release.yml`) runs the three
gate scripts, then unit tests + lint + `assembleMarketDebug`/`assembleGplayDebug` on every push/PR;
a `v*` tag additionally builds and signs a release APK/AAB if the signing secrets
(`TERMINAL_KEYSTORE_*`) are present.

## Architecture

```
TerminalApp (Application)            ← lives as long as the process
├── AndroidKeyStoreVault             ← secrets/keys, ciphertext only (AES-256-GCM)
├── KnownHostsStore                  ← trusted server public keys (TOFU)
├── HostStore                        ← non-secret metadata (JSON in SharedPreferences)
├── Settings
├── JschSshClient                    ← JSch adapter; background thread only
└── SessionRegistry                  ← all open sessions (tabs)
    └── SshSession × n
        ├── io: ExecutorService      ← single thread; all socket ops
        ├── reader: Thread           ← reads from the SSH shell
        ├── main: Handler            ← posts writes to the terminal emulator
        └── emulator: TerminalEmulator (termlib/libvterm, ConnectBot)

MainActivity → AppViewModel → RootScreen → {Hosts, Terminal, Keys, Settings}
SshForegroundService                 ← runs while any session is alive
```

`SessionRegistry` lives in `Application`, not `Activity` — rotation/backgrounding never kills a
session. `SshForegroundService` starts when at least one session is alive and stops when the
last one closes. `MainActivity.onDestroy` only closes sessions when `isFinishing`.

There's no Room/DI: the object graph is six things, and Room's KSP/codegen is an extra build
failure point for the market build. If host count ever needs it, migration is contained because
everything routes through `HostStore`.

### Thread contract (the thing v0.1.0 crashed on)

| Operation | Allowed thread |
|---|---|
| connect / write / setPtySize / close | `SshSession.io` only |
| `emulator.writeInput` / `clearScreen` | main thread only (`main.post`) |
| reading from `shell.input` | `reader` thread only |
| mutating `_state` | any thread (it's a `StateFlow`) |

Any new SSH-touching code must follow this table. A `NetworkOnMainThreadException` means it's
been violated.

### Package layout (`app/src/main/java/app/terminalssh/secure/`)

- `ssh/` — `JschSshClient`, `SshSession`, `SessionRegistry`, `KnownHostsVerifier`, `ReconnectPolicy`
- `security/` — `AndroidKeyStoreVault`, `AesGcmVaultCodec`, `KeyGeneration`, `SecretScanner`
- `storage/` — `HostStore`, `KnownHostsStore`, `Settings`, `SshConfigExport`/`Import`
- `sftp/` — `SftpClient`, `SftpController`, `TransferQueue` (resumable transfer queue)
- `agents/` — installs Claude Code / OpenCode / Aider onto the remote server (`AgentInstallScript`,
  `DangerousCommand` detection, `TmuxCommands`)
- `vm/AppViewModel.kt` — the single ViewModel backing `RootScreen`
- `ui/` — Compose screens; `Bidi.kt` has `ltr()` for wrapping LTR technical values in RTL text
- `settings/` — declarative `SettingsCatalog` schema (drives fuzzy search in the Settings screen)
- Flavor source sets `market/` and `gplay/` hold only account/auth code specific to each
  distribution (`account/`, `auth/`); everything else is flavor-agnostic in `main/`

## Rules for secret-handling code (enforced by `scripts/source_audit.py` and tests, not style)

- Secrets are `ByteArray`/`CharArray`, never `String` — a `String` is immutable and can't be
  wiped. The audit script greps for `.concatToString()`, `passChars.toString()`, and
  `String(password|passChars|chars|secret...)` across every flavor's sources and fails the build
  if found.
- Zero the buffer in a `finally` block as soon as the secret has been used.
- Every write of secret material to the vault pairs with cleanup on the failure path — orphaned
  ciphertext with no metadata can't be deleted through the UI later.
- Nothing secret reaches a log, crash report, exception message, or filename.

## Rules for connection code

- Every socket operation runs off the main thread (see thread contract above).
- Automatic reconnect is only for connections that dropped — never for a session the user or
  server ended deliberately, and never after an authentication failure.
- User-facing failures go through `ConnectionError`, not raw JSch exception text.
- Host-key verification (`KnownHostsVerifier`) and TOFU confirmation must stay explicit; never
  introduce `StrictHostKeyChecking=no` or silent host-key acceptance.

## Other constraints

- Every user-visible string must exist in both `values/strings.xml` (Persian) and
  `values-en/strings.xml` — one-locale-only strings break the build for half the users.
- Wrap LTR technical values (hostnames, ports, versions, fingerprints) in `ltr()`
  (`ui/Bidi.kt`) or the bidi algorithm reorders them inside Persian text.
- No new required network calls — the core workflow must keep working with no account, no
  cloud, and no connection to anything but the user's own server.
- Keep `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` unless there's a
  documented product reason to change them.
- Don't commit `.jks`/`.keystore`/`.p12`/`.pem`, tokens, passwords, or `local.properties`.
  Signing is entirely env-var driven (`TERMINAL_KEYSTORE_PATH`, `TERMINAL_KEYSTORE_PASSWORD`,
  `TERMINAL_KEY_ALIAS`, `TERMINAL_KEY_PASSWORD`) — `signingReady` in `app/build.gradle.kts` no-ops
  release signing when they're absent.
- Commit messages should read like a note for someone reading `git log` in a year: what changed
  and why it was wrong before. Conventional prefixes (`fix:`, `feat:`, `docs:`) are used
  throughout. Security issues go through `SECURITY.md`, never a public issue/PR.
