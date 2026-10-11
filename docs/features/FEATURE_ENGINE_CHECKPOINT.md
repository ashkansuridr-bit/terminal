# Terminal 50-Features — Engine Checkpoint

Machine-readable status: [`FEATURE_STATUS.json`](FEATURE_STATUS.json)
Full narrative: [`FEATURE_IMPLEMENTATION_REPORT.md`](FEATURE_IMPLEMENTATION_REPORT.md)

## Where the work lives

| Item | Value |
| --- | --- |
| Worktree | `/root/work/terminal-features` |
| Branch | `feature/terminal-50-tools` |
| Base | `origin/main@f675bb1` |
| Commits | `9fdb91e`, `5aa2621`, `882abc2`, `da80eb1`, `0629b0e` |
| Pushed | No (nothing pushed; no PR opened) |
| Shared tree | `/root/work/terminal` is a **different** worktree owned by the Codex agent — do not touch it |

This worktree was created specifically to avoid clobbering the Codex agent that is fixing
SFTP stability bugs in the shared checkout. No merge to `main`, no force-push, and no release
was performed.

## How to resume

```bash
# from a fresh shell
cd /root/work/terminal-features
git --no-pager log --oneline -5

# run only the engine unit tests the host can afford (memory-lean, one task per invocation)
ANDROID_HOME=/root/android-sdk ./gradlew --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx1400m -XX:MaxMetaspaceSize=448m" \
  -Dorg.gradle.workers.max=2 \
  -Pkotlin.compiler.execution.strategy=in-process \
  testMarketDebugUnitTest \
  --tests "app.terminalssh.secure.model.*" \
  --tests "app.terminalssh.secure.sftp.*" \
  --tests "app.terminalssh.secure.terminal.*" \
  --tests "app.terminalssh.secure.tools.*" \
  --tests "app.terminalssh.secure.ssh.*" \
  --tests "app.terminalssh.secure.security.*" \
  --tests "app.terminalssh.secure.storage.*"

# full gates, ONE task per invocation (the host has ~3.9 GB RAM and Codex shares it)
ANDROID_HOME=/root/android-sdk ./gradlew --no-daemon ... lintMarketDebug
ANDROID_HOME=/root/android-sdk ./gradlew --no-daemon ... assembleMarketDebug
```

Kill stale Gradle daemons before a run if the host is tight on memory
(`./gradlew --stop` / `pkill -f GradleDaemon`).

## Status at this checkpoint

| Status | Count | Meaning |
| --- | --- | --- |
| `IMPLEMENTED_NOT_TESTED` | 30 | Engine implemented here, JVM unit tests green, **UI wiring / device E2E not done** |
| `EXISTS_VERIFIED` | 5 | Already complete on `main`; audited, not rebuilt |
| `NEEDS_IMPROVEMENT` | 12 | Partial on `main`; untouched here (recorded as follow-up) |
| `BLOCKED` | 2 | F031 split panes, F035 multi-hop ProxyJump |
| `DEFERRED_WITH_REASON` | 1 | F049 Mosh |
| `VERIFIED` (end-to-end) | 0 | No emulator/SSH server available on the host |

> `IMPLEMENTED_NOT_TESTED` is deliberate and honest. The parsers/policies are covered by green
> JVM tests, but nothing is marked `VERIFIED` because the Compose UI path and a real device
> have not been exercised.

## What is implemented (30 features)

- **Host utilities:** F001 QuickConnect, F003 HostFilter, F005 HostIcons + `iconKey`
  persistence, F007 ConnectionInfo, F017 SnippetTemplate, F018 SnippetLibrary.
- **Terminal engines:** F011 TerminalSearch, F012 search navigator, F013 TerminalBookmarks,
  F014 OutputExport, F015 TerminalLinks, F016 CommandHistory.
- **Server tools:** F041 ServerResources, F042 LogBuffer, F043 ProcessList, F044 SystemdUnits,
  F045 DockerContainers, F046 GitStatus, F047 NetworkPorts, F050 Runbook.
- **Security / SFTP / SSH:** F025 RemoteFinder, F026 HexPreview, F029 TransferQueuePolicy,
  F037 KnownHostsFormat, F039 ConnectionDiagnostics, F048 PortableBackup, F022 TransferPlan,
  F010 KeyToolbarConfig, F020 SessionNotification, F034 BroadcastInput.

## Design rules followed

- **No untrusted string concatenation into a shell.** Roots, patterns, unit names, container
  targets and directories are validated, then single-quoted (`'` → `'\''`).
- **Read-only vs mutating is explicit.** Mutating server commands are marked (`isMutating` /
  `mutating`) so the UI can require confirmation before Restart / Stop / Kill / Delete.
- **No credentials ever in commands, history, logs or notifications.** F016 suppresses
  secret-looking lines; F020's payload has no field for a username/vault ref; F007 reads a
  profile but never its credential reference.
- **Bounded everything.** Log ring, hex dump, text decode, find depth/results, copy byte cap,
  transfer queue — all capped so a phone cannot be OOM-killed by a busy server.
- **Dangerous automation is gated.** F050 previews every step, requires confirmation for
  detector-flagged steps, and stops on the first non-zero exit.
- **Free resources on drop / cancellable long ops** are the caller's contract for the tools
  (they hold no channels themselves); no network or file I/O happens on the main thread here.

## Blockers and why

| Feature | Blocker |
| --- | --- |
| F031 split panes | Needs terminal-emulator pane infrastructure; not available in the current engine wiring. |
| F035 multi-hop ProxyJump | JSch has no native ProxyJump; needs a local port-forward tunnel chain plus independent hop auth and host-key verification. High risk; deferred rather than half-built. |
| F049 Mosh | Requires a native UDP implementation and server-side install; out of scope for a JVM-only change. |

### Environment blockers

- **No local instrumentation/emulator.** The AVD system image was deleted and the emulator
  fails with `FATAL Broken AVD system path`; the host also OOMs. CI is the authoritative
  device channel, and CI cannot go green because of pre-existing device-only test failures
  (identical before and after earlier work).
- **No production signing keystore**, so no market-signed release artifact was produced.
- **Shared host memory** (~3.9 GB) with the Codex agent running Gradle concurrently; builds
  are kept memory-lean and run one task at a time.

## Known pre-existing CI failures (not caused by this branch)

Instrumentation failures observed on CI were byte-identical before and after the earlier
termlib 0.2.1 work: `SettingsToggleAccessibilityTest`, `SettingsAccessibilityTest`,
`HostEditValidationTest.kt:54`, `SnippetAccessibilityTest.kt:63`,
`HostListPresentationTest.kt:125`, and `TerminalKeyboardTest` (IME, infra-class).

## Next safe steps (resume order)

1. Wire UI for the highest-value engines, verified with `assembleMarketDebug` + `lintMarketDebug`
   and, where possible, new instrumentation tests: F001/F003/F005/F007/F017/F018 on the host
   list, F011–F016 on the terminal, and F041–F050 as a server-tools dashboard.
2. Add string resources for any new user-visible text in `values/`, `values-en/`, `values-ar/`.
3. Feed `TransferQueuePolicy`/`TransferPlan` into the real transfer scheduler and surface
   F028's honest `UNVERIFIED` state.
4. Run `lintMarketDebug` then `assembleMarketDebug` (one task per invocation) once the UI lands.
5. Do **not** merge to `main` or publish without explicit permission.
