# Changelog

## Unreleased — code review fixes and follow-up features

### Added

- **SFTP file browser with a resumable transfer queue.** Rides the terminal
  session's existing connection, so browsing files never means authenticating a
  second time. Downloads and uploads go through the system file picker, so the
  app needs no storage permission. A dropped connection re-queues transfers
  instead of abandoning them; a permission error stops immediately rather than
  retrying pointlessly.
- **Generate SSH keys inside the app** (Ed25519 on Android 13+, ECDSA P-256, or
  RSA-3072). The private half goes straight to the vault and is wiped from memory;
  the public half is shown once to copy into `authorized_keys`, and is never
  written to the vault or shown again.
- **Import servers from an OpenSSH `~/.ssh/config`**, and **export the list back
  out** as one. The export contains no passwords, passphrases, private keys, or
  vault references, so it is safe to keep as a backup.
- **Biometric / device-credential app lock**, re-armed whenever the app leaves the
  foreground. Offered only when the device has an enrolled credential, so enabling
  it can never make the app unopenable.
- **Automatic clipboard clearing** for terminal copies, on a configurable delay,
  and only when the clipboard still holds what the terminal put there.
- **Launcher shortcuts** for the four most recently used servers. They carry only
  a host id; the app lock still applies.
- **Fuzzy host search** with relevance ranking — "pdb" finds "prod-db-01" — now
  also searching the new per-host notes field.
- **Per-host notes and an environment band** (dev / staging / production), shown
  on the leading edge of the host row so "production" registers before the tap.
- **Per-host reconnect budget**, because a flaky VPS and a LAN box should not
  share one retry policy.

### Changed

- **The key toolbar gives haptic and visual feedback on every press**, and lays
  out in two rows instead of one scrolling row on windows 600dp and wider — large
  phone landscape, tablets, unfolded foldables. Keys are ordered by how often they
  are actually reached for.
- **Layouts adapt to the window, not the device.** Page margins grow with width
  and text columns get a reading-width cap, so a host row is not stretched across
  a tablet with its name and actions at opposite ends of the screen.
- **An animated splash mark**, and a status dot that pulses only while a
  connection is being established.
- Reconnect delay is now exponential with full jitter instead of linear, so
  several tabs recovering from one Wi-Fi drop stop retrying in lockstep.
- Connection failures are classified and shown as a sentence explaining what to
  change, in Persian or English, instead of raw JSch text.

### Fixed

- Fixed: a clean remote shell exit (typing `exit`) no longer triggers an automatic
  reconnect loop; only a channel close without an exit-status (an actual dropped
  connection) does. Detected via the SSH exit-status the remote sends when the shell
  process itself terminates.
- Fixed: automatic-reconnect eligibility (`SshSession.isTransient`) now checks the
  exception type (network I/O failures vs. `JSchException`), extracted into a pure,
  unit-tested `ReconnectPolicy`, instead of a substring match against the exception
  message. An exception with no message (e.g. a bug) is no longer silently retried
  three times under a "Reconnecting…" label.
- Fixed: a quick-connect password is now wiped from memory as soon as the connection
  succeeds for any host with a stored vault credential, instead of staying decrypted
  in memory for the life of the session.
- Fixed: deleting a private-key host now also deletes its stored passphrase from the
  vault. Previously only password-auth hosts were cleaned up, leaving passphrase
  ciphertext orphaned in `SharedPreferences` with no UI path to remove it.
- Fixed: the foreground service's type is now `specialUse` instead of `dataSync`.
  Starting with Android 15, `dataSync` foreground services are capped at ~6 hours of
  execution per rolling 24-hour window, which could cut off a long-lived interactive
  SSH session left backgrounded overnight.
- Fixed: the RTL bidi isolate (`ltr()`) is now applied to `HostProfile.subtitle`
  (`username@host:port`) in the host list, host edit sheet, and terminal header —
  the same string shape the isolate helper's own doc comment cites as its motivating
  bug, previously left unwrapped in these three call sites.

## 0.4.1 — AndroidKeyStore crash fix

- Fixed the add-server crash caused by supplying a caller-generated GCM IV to an
  AndroidKeyStore key that requires randomized encryption.
- Added real-device/emulator instrumentation coverage for vault round trips, nonce
  freshness, AAD isolation, deletion, and the complete ViewModel host-save path.
- Host-save failures are now contained and surfaced to the user instead of terminating
  the application.

## 0.4.0 — Loop 2: secure snippets + secret lifecycle

- Added encrypted command snippets stored only in AndroidKeyStore-backed Vault (`VaultAad.SNIPPET`).
- Snippets are inserted without an automatic Enter to avoid one-tap command execution.
- Removed extra immutable-String password conversions from ViewModel secret paths.
- Added bounded/wiping private-key input reader and orphan-secret cleanup on metadata failure.
- Clarified private-key hash labeling so it is not confused with an SSH public-key fingerprint.
- About screen now reads the real `BuildConfig.VERSION_NAME`.
- Added deterministic Remotion/Instavar launch video and carousel specs.
- Added pure JVM tests for secret encoding and bounded secret I/O plus `scripts/loop2_gate.py`.


## 0.2.0 — unreleased

### Fixed — crashes
- `setPtySize` ran on the main thread from the terminal's resize callback, throwing
  `NetworkOnMainThreadException` as soon as the terminal re-laid out after connecting.
  This was the crash-on-connect reported against 0.1.0.
- `Shell.close()` ran on the main thread from `connect()` and `onDestroy()` — same fault
  class, triggered by Reconnect and by leaving the app.
- Terminal output was written to the emulator from the reader thread; it is now posted
  to the main thread.

### Fixed — connection lifetime
- `Session.timeout` was left at 15 s after connecting, which is `SO_TIMEOUT`, so any
  15-second idle period killed the session. Cleared after connect; keepalive
  (30 s interval, 3 retries) used instead.

### Fixed — false host-key alarm
- `HostKeyRepository.getHostKey()` returned an empty array, so JSch could not pin the
  previously trusted algorithm. A server offering a different algorithm on a later
  connection was reported as a changed host key. Now returns the stored key.

### Added
- Multi-session terminal tabs
- Foreground service so sessions survive backgrounding
- Automatic reconnect (3 attempts) on transient network loss
- Special-keys toolbar with latching Ctrl/Alt
- Saved hosts with labels, groups, tags, favourites and search
- Private key import into the vault
- Multi-line paste confirmation
- Six terminal palettes and adjustable font size
- Persian-first UI with full RTL, English as secondary locale
- Per-ABI APK splits (~9 MB instead of a 28 MB universal APK)

### Changed
- Architecture: single Activity → Application container + ViewModel + service
- UI: programmatic Android Views → Jetpack Compose with Material 3

## 0.1.0
- Initial release: password SSH, VT100/ANSI terminal, TOFU host-key verification.

## 0.3.1 — Loop 1 UI/UX benchmark
- Calmer premium graphite palette with restrained turquoise accent.
- Floating rounded navigation dock with reduced visual noise.
- Increased title/body legibility and spacing hierarchy.
- Added explicit 2026 Termius benchmark and ship criteria.
- Added Persian-first minimal-mobile design principles.
