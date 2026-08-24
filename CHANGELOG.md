# Changelog

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
