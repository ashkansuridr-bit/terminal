# Contributing

This is an SSH client. A bug here can leak someone's production credentials, so the bar
for changes touching secrets, host-key verification, or the terminal is deliberately high.

## Building

```bash
./gradlew testMarketDebugUnitTest   # pure JVM tests, fast
./gradlew assembleMarketDebug       # installable debug APK
./gradlew lintMarketDebug           # lint must be error-free
```

The `market` flavor ships without Google Play Services (Cafe Bazaar, Myket, F-Droid);
`gplay` adds optional Google sign-in. Both must build. Anything in the core SSH workflow
belongs in `main`, never in a flavor.

Instrumentation tests (`connectedMarketDebugAndroidTest`) need a device or emulator with
hardware acceleration — the AndroidKeyStore behaviour they cover cannot be reached from a
JVM test.

## What a change needs

- **Tests.** Pure logic goes in `app/src/test` and must be covered there. Anything that
  depends on AndroidKeyStore, the real clipboard, or Compose goes in `app/src/androidTest`.
- **Both locales.** Every user-visible string exists in `values/strings.xml` (Persian,
  the default) and `values-en/strings.xml`. A string in only one is a broken build for
  half the users.
- **RTL correctness.** Wrap left-to-right technical values — host names, ports, versions,
  fingerprints — in `ltr()`. Without it the bidi algorithm reorders them inside Persian
  text and `192.168.1.10:22` renders wrong.
- **No new required network calls.** The core workflow must keep working with no account,
  no cloud, and no connection to anything except the user's own server.

## Rules for secret-handling code

These are not style preferences; PRs that break them will be asked to change.

- Secrets are `ByteArray` or `CharArray`, never `String`. A `String` is immutable and
  cannot be wiped; it stays in the heap until GC decides otherwise.
- Zero the buffer in a `finally` block as soon as the secret has been used.
- Every write of secret material to the vault must be paired with cleanup on the failure
  path. Orphaned ciphertext with no metadata cannot be deleted through the UI later.
- Nothing secret reaches a log, a crash report, an exception message, or a filename.

## Rules for connection code

- Every socket operation runs off the main thread. `JschSshClient` and the reader thread
  document this contract; `SshSession` enforces it.
- Automatic reconnect is for connections that dropped, never for a session the user or
  the server ended deliberately, and never for an authentication failure.
- Failures shown to a user go through `ConnectionError`, so they get a sentence about what
  to change rather than raw JSch text.

## Commits and pull requests

Write the commit message for someone reading `git log` in a year: what changed, and why it
was wrong before. Conventional prefixes (`fix:`, `feat:`, `docs:`) are used throughout.

Say in the PR what you actually verified. "Tests pass" is only useful if you say which
ones and on what — a device, an emulator, or JVM only.

## Security issues

Do not open a public issue or PR. Follow [SECURITY.md](SECURITY.md).
