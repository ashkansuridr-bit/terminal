# Terminal 50-Features — Engine Checkpoint

Machine-readable status: [`FEATURE_STATUS.json`](FEATURE_STATUS.json)
Full narrative: [`FEATURE_IMPLEMENTATION_REPORT.md`](FEATURE_IMPLEMENTATION_REPORT.md)

## Where the work lives

| Item | Value |
| --- | --- |
| Worktree | `/root/work/terminal-features` |
| Branch | `feature/terminal-50-tools` |
| Base | `origin/main@f675bb1` |
| Commits | `9fdb91e`, `5aa2621`, `882abc2`, `da80eb1`, `0629b0e`, `f6a31f0`, `6c13b50` |
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
| `IMPLEMENTED_NOT_TESTED` | 42 | Engine implemented here, JVM unit tests green, **UI wiring / device E2E not done** |
| `EXISTS_VERIFIED` | 5 | Already complete on `main`; audited, not rebuilt |
| `NEEDS_IMPROVEMENT` | 0 | All six former partial items now have a pure engine in this branch |
| `BLOCKED` | 2 | F031 split panes, F035 multi-hop ProxyJump |
| `DEFERRED_WITH_REASON` | 1 | F049 Mosh |
| `VERIFIED` (end-to-end) | 0 | No emulator/SSH server available on the host |

> `IMPLEMENTED_NOT_TESTED` is deliberate and honest. The parsers/policies are covered by green
> JVM tests, but nothing is marked `VERIFIED` because the Compose UI path and a real device
> have not been exercised.

## What is implemented (42 features)

- **Host utilities:** F001 QuickConnect, F003 HostFilter, F005 HostIcons + `iconKey`
  persistence, F007 ConnectionInfo, F010 KeyToolbarConfig, F017 SnippetTemplate,
  F018 SnippetLibrary, F019 TabLayout, F020 SessionNotification, F040 ConnectionOptions.
- **Terminal engines:** F011 TerminalSearch, F012 search navigator, F013 TerminalBookmarks,
  F014 OutputExport, F015 TerminalLinks, F016 CommandHistory, F034 BroadcastInput.
- **Server tools:** F041 ServerResources, F042 LogBuffer, F043 ProcessList, F044 SystemdUnits,
  F045 DockerContainers, F046 GitStatus, F047 NetworkPorts, F050 Runbook.
- **Security / SFTP / SSH:** F021 ConflictBatchPolicy, F022 TransferPlan, F023 TransferBatchControl,
  F024 FolderSizeBudget, F025 RemoteFinder, F026 HexPreview, F028 IntegrityStatus,
  F029 TransferQueuePolicy, F037 KnownHostsFormat, F039 ConnectionDiagnostics, F048 PortableBackup.
- **Former `NEEDS_IMPROVEMENT` items, now engines:** F006 `aggregate()` (one badge for a host
  with several sessions), F009 CursorAppearance (tolerant cursor-shape parse), F032
  WorkspaceState/WorkspaceCodec (save/restore open tabs, restore never auto-connects),
  F033 TmuxCommands windows (list/rename/kill-window), F036 PortForwardSpec (rule validation +
  bind-port conflicts), F038 `SshConfigImport.analyze()` (reports the Include/IdentityFile/
  ProxyJump directives `parse()` deliberately does not apply).

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
| F031 split panes | The emulator renders a single buffer, so panes cannot be shown. The pure geometry is done (`terminal/PaneLayout.kt`, tested); the second-buffer transport is the blocker. |
| F035 multi-hop ProxyJump | JSch has no native ProxyJump; it needs a local port-forward tunnel chain plus independent hop auth and host-key verification. High risk; not half-built. The pure planner is done (`ssh/JumpChain.kt`, tested) and opens no socket. |
| F049 Mosh | Requires a native UDP implementation and server-side install; out of scope for a JVM-only change. |

### Environment blockers

- **No local instrumentation/emulator.** The AVD system image was deleted and the emulator
  fails with `FATAL Broken AVD system path`; the host also OOMs. CI is the authoritative
  device channel, and CI cannot go green because of pre-existing device-only test failures
  (identical before and after earlier work).
- **No production signing keystore**, so no market-signed release artifact was produced.
- **Shared host memory** (~3.9 GB) with the Codex agent running Gradle concurrently; builds
  are kept memory-lean and run one task at a time.
- **A single all-tests `testMarketDebugUnitTest` run was OOM-killed on this host** (the kernel
  killed an unrelated ZCode editor process under global OOM). Verification was therefore done
  with package-filtered runs, which are green; re-run the full suite on CI or a roomier host.

## Known pre-existing CI failures (not caused by this branch)

Instrumentation failures observed on CI were byte-identical before and after the earlier
termlib 0.2.1 work: `SettingsToggleAccessibilityTest`, `SettingsAccessibilityTest`,
`HostEditValidationTest.kt:54`, `SnippetAccessibilityTest.kt:63`,
`HostListPresentationTest.kt:125`, and `TerminalKeyboardTest` (IME, infra-class).

## Next safe steps (resume order)

1. Wire UI for the highest-value engines, verified with `assembleMarketDebug` + `lintMarketDebug`
   and, where possible, new instrumentation tests: F001/F003/F005/F007/F017/F018 on the host
   list, F011–F016 on the terminal, F041–F050 as a server-tools dashboard, F006 badge via
   `aggregate()`, F032 restore-after-unlock, F033 tmux window list, F036 dashboard validation,
   F038 import notes.
2. Add string resources for any new user-visible text in `values/`, `values-en/`, `values-ar/`
   (`forward.error.*` keys from F036 are the first to land).
3. Feed `TransferQueuePolicy`/`TransferPlan` into the real transfer scheduler and surface
   F028's honest `UNVERIFIED` state.
4. Run `lintMarketDebug` then `assembleMarketDebug` (one task per invocation) once the UI lands.
5. Do **not** merge to `main` or publish without explicit permission.

## Batch H (commit `1c14668`) — the six former partial items, closed as engines

The six former `NEEDS_IMPROVEMENT` features were closed as pure engines so no host is left
without a testable core:

- F006 `aggregate()` — one badge for a host with several sessions; a healthy shell keeps a
  dropped SFTP tab from painting the row red.
- F009 `CursorAppearance` / `CursorShape.fromStored()` — a hand-edited or restored cursor
  setting falls back to the default instead of leaving the terminal with no cursor.
- F032 `WorkspaceState` / `WorkspaceCodec` — save/restore open tabs; `planRestore()` drops
  tabs whose host is gone and **nothing here connects on its own**.
- F033 `TmuxCommands` windows — `list-windows` parsing plus rename/kill-window commands, all
  quoted through the existing helper; window indexes are validated ints, never free text.
- F036 `PortForwardSpec` — validate a `-L`/`-R` rule before it is added, detect a bind port
  already taken by the same kind, flag privileged ports. The SSH layer still owns the binding.
- F038 `SshConfigImport.analyze()` — report the `Include` / `IdentityFile` / `ProxyJump`
  directives `parse()` deliberately does not apply, so an import can say what it skipped.

Verification for this batch: the six new test classes are green under a package-filtered
`testMarketDebugUnitTest`; `lintMarketDebug` and `assembleMarketDebug` both `BUILD SUCCESSFUL`.

## Batch I (commit `8e5088e`) — pure cores for the two blocked transport features

Both features remain genuinely blocked on a transport that does not exist, so the risky half
is still refused rather than faked: `JschSshClient` keeps throwing `JumpHostUnavailable` for a
jump host, and the emulator still renders one buffer. What lands here is the planning/geometry
half, tested and ready for the transport to consume.

- F035 `ssh/JumpChain.kt` — parses an OpenSSH `ProxyJump` value (comma-separated
  `[user@]host[:port]`, bracketed IPv6, the literal `none`) into ordered hops, caps the depth at
  8, and plans one local forward per hop for the eventual tunnel chain. The hop grammar is
  reused from `QuickConnect` rather than copied, and **no socket is opened**.
- F031 `terminal/PaneLayout.kt` — a binary split tree over pane ids. `split`/`close` keep the
  tree valid (close collapses the sibling, the last pane cannot be closed, cap 6), `resize`
  clamps the divider so neither side can vanish, and next/prev focus wraps and always lands on
  a real pane.

Their `FEATURE_STATUS.json` entries stay `BLOCKED`; the artifacts/evidence fields record the
engine as groundwork, not as a finished feature. New tests: `JumpChainTest`, `PaneLayoutTest`.

## Batch J (commit `4484e52`) — read-only completions for the server tools

Auditing the five `EXISTS_VERIFIED` features (F002/F004/F008/F027/F030) and the tool engines
found them already complete on `main`/earlier batches, with two exceptions where the tool
dashboard promised a read it could not yet perform:

- F044 `SystemdUnits.logsCommand(unit, tailLines)` — a read-only `journalctl -u <unit>
  --no-pager -n <n>` tail. The unit name is validated as a plain identifier (same gate as the
  actions), and `-n` is clamped to `MAX_LOG_LINES = 5000` so a chatty service cannot flood the
  bounded terminal ring (F042). It never calls `systemctl`.
- F045 `DockerContainers.statsCommand`/`inspectCommand` (+ `parseStats`/`Stats`) — one-shot
  reads (`docker stats --no-stream`, `docker inspect`) with the target validated and
  shell-quoted, so the dashboard can show usage without opening a live stream it must stop.

Both were already `IMPLEMENTED_NOT_TESTED`; their `FEATURE_STATUS.json` evidence is extended,
counts unchanged. New tests: `SystemdUnitsLogsTest` (3), `DockerContainerReadTest` (5).

## Batch K (commit `8b94781`) — a real bug in the F011/F012 search

Reviewing the engines for the RTL/non-ASCII requirement turned up a genuine correctness bug,
not a missing operation. `TerminalSearch.findAll` lower-cased the whole haystack before
`indexOf`, and `String.lowercase()` can **change a string's length** (e.g. `İ` → `i` + U+0307, a
two-code-point sequence). A match located on the lowered copy then produced an `end` index that
pointed past the original line, so `highlight()` sliced the wrong characters or threw
`StringIndexOutOfBoundsException` on an ordinary-looking query.

- Fixed by matching the **original** text with `Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE`
  (`Pattern.quote` keeps the query literal, exactly as before), so offsets stay in original
  coordinates and non-ASCII scripts keep matching case-insensitively — the `ru` locale matters
  here, and ASCII-only case folding would have silently broken Cyrillic/Greek search.
- `TerminalSearchTest` gained 4 cases (offset correctness on `İ`, no slice past the line,
  Cyrillic + Greek case folding, metacharacters are literal); the class is now 13 tests, green.

This is a bug fix inside an existing feature, so the status stays `IMPLEMENTED_NOT_TESTED`
(the search bar is still not wired on device); only the F011/F012 evidence now records the fix.

## Batch L (commit `c4003d8`) — first real UI wiring: F036 port-forward dialog

The port-forward dialog was the first place to connect an engine to a screen, chosen because it
is reachable (the terminal's overflow menu) and has **no** instrumentation test, so a UI change
here cannot silently break the on-device suite. It parsed its fields inline and did nothing when
a value was bad, so "Add" looked broken with no explanation, and `PortForwardSpec` was unused.

- New pure `ui/PortForwardForm.kt`: `evaluate(kind, bind, host, target, existing)` returns
  `(spec | problemKey | warningKey, canAdd)`. It validates ranges/host, detects a second rule on
  the same local port (re-adding the identical rule is allowed), and warns on privileged ports.
  `reasonRes()` maps engine keys to string resources.
- `PortForwardDialog` shows the reason/warning inline and disables Add unless the rule is usable;
  the host handed to the SSH layer is the trimmed, validated one.
- Five strings (`forward_error_*`, `forward_warning_privileged`) added to **all seven** locale
  files; `lintMarketDebug` fails with `MissingTranslation` otherwise, which is how the missing
  es/fr/ru keys surfaced.
- Tests: `PortForwardFormTest` (9), green.

Unlike earlier batches this touches Compose, so it is compile/lint/assemble-verified but **not
device-rendered**; F036 stays `IMPLEMENTED_NOT_TESTED`.

## Batch M (commit `bf7f7a8`) — F021 apply-to-all reaches the dialog

The same lesson as Batch L, one layer over: `SftpController` already implemented
`resolveConflict(resolution, applyToAll)` with a standing conflict policy, but no caller ever
passed `applyToAll = true`, so a folder upload re-asked the same question once per file.

- `UploadConflictDialog` now takes `(ConflictResolution, applyToAll)`, renders an "apply to all
  remaining files" checkbox, and threads the flag into Overwrite/Rename/Skip.
- **Cancel is never applied to the batch**, on purpose: it stops the current file rather than
  answering the question. That is the same rule `ConflictBatchPolicy` encodes ("ASK is never
  remembered"), so the UI and the pure engine now agree even though the controller still owns
  the runtime state.
- `FilesScreen` passes the flag straight to `sftp.resolveConflict`.
- New string `sftp_conflict_apply_all` in all six locale files.

No new JVM test is added: the remembered-decision rule is already covered by
`ConflictBatchPolicyTest`, and the remaining work is Compose. Verified by test compile, lint and
assemble only; **not device-rendered**, so F021 stays `IMPLEMENTED_NOT_TESTED`.

## Batch N (commit `cd7cbc5`) — F026 binary/hex preview, end to end

The read-only preview always decoded to text, so a binary file rendered as a wall of replacement
characters with no way to tell a genuine U+FFFD from a corrupted read. `HexPreview` was written
for exactly this but nothing ever handed it bytes — the text path had already destroyed them.

- `BoundedText.readBytes` returns a read-only `BytePreview`, bounded like `read` (one byte past
  the cap so truncation is *proven*) but keeping the bytes.
- `SftpClient.downloadPreview` / `SftpController.downloadPreviewBytes` expose a bounded sample,
  defaulting to `HexPreview.DEFAULT_MAX_BYTES` (64 KiB) rather than the 16 MiB image cap.
- New pure `sftp/PreviewContent.kt`: `of(bytes, maxBytes, sourceTruncated) -> View.Text |
  View.Hex`, using `HexPreview`'s detection and dump, with a shared `truncated` flag.
- `ui/FilePreviewDialogs.kt`: `FilePreviewDialog` replaces `TextPreviewDialog` and renders text
  or a monospace hex dump, plus a binary label and an honest "bounded sample" note.
- `SftpScreen.RemoteTextPreview` now fetches bytes (falling back to the text fetch for callers
  that still only provide that), threaded via a new `fetchPreviewBytes` parameter from
  `FilesScreen`, bound to the capped controller call.
- Strings `sftp_preview_binary` / `sftp_preview_truncated` in all six locales; the French
  apostrophe is escaped (`d\'un`) — aapt2 rejects it otherwise, which is how it surfaced.

Tests: `PreviewContentTest` (8) and `BoundedTextReadBytesTest` (6), green. UI is still **not
device-rendered**, so F026 stays `IMPLEMENTED_NOT_TESTED`.

## Batch O (commit `3248694`) — F023 batch transfer controls

The queue had per-row pause/resume/cancel only, so a forty-file folder transfer had to be
triaged one row at a time. `TransferBatchControl` already encoded which items each batch action
may touch, but nothing called it, and there were no batch buttons.

- `TransferBatchControl.availableActions(transfers)` returns the actions worth offering, in a
  stable order, so the strip never renders a button that would do nothing.
- `SftpController.pauseAll/resumeAll/retryFailed/cancelAll` resolve ids through that same engine
  over `queue.pending`. `retryFailed` maps onto `resume`, which already re-queues `FAILED` items;
  an integrity mismatch is excluded by the engine, not by the caller remembering to.
- `TransferStrip` shows a batch row (only when more than one item is live) whose buttons *are*
  `availableActions(...)`; Cancel is tinted as destructive.
- `FilesScreen` passes the four callbacks; strings `sftp_pause_all` / `sftp_resume_all` /
  `sftp_retry_failed` / `sftp_cancel_all` in all six locales.

Tests: `TransferBatchControlTest` 8 → 11 (a finished batch offers nothing; an integrity mismatch
never offers retry). UI is **not device-rendered**, so F023 stays `IMPLEMENTED_NOT_TESTED`.

## Batch P (commit `fbb7912`) — F028 integrity reaches the transfer row

`IntegrityStatus` already encoded the whole point — "finishing is not proving" — but nothing
fed it. `verifyIntegrity` returned a private `MATCH/MISMATCH/UNVERIFIED` enum, which was thrown
away the moment the transfer was marked complete, so a completed row read as done whether or
not its bytes had ever been compared.

- `verifyIntegrity` now returns `IntegrityStatus.State` and delegates the comparison to
  `IntegrityStatus.verify(remote, local)`; the private enum is deleted, so the rule "only two
  equal hashes mean VERIFIED" lives in exactly one place. The empty/absent/mismatch branches are
  behaviour-preserving.
- `Transfer` gains `integrity: IntegrityStatus.State`, defaulting to `UNVERIFIED`.
- `TransferQueue.markCompleted(id, atMillis, integrity)` records it; the controller passes the
  verified state on success, while a genuine `MISMATCH` still fails the transfer with
  `INTEGRITY_MISMATCH`.
- `TransferRow`'s status line appends `· verified` only for a proven match and `· unverified`
  otherwise, so a completed row never implies a check mark it did not earn.
- Strings `sftp_integrity_verified` / `sftp_integrity_unverified` in all six locales.

Tests: `TransferQueueTest` 31 → 32 (default stays `UNVERIFIED`; `VERIFIED` and `MISMATCH` survive
onto the completed item). Full `sftp` package: 369 tests, 0 failures. UI is **not device-rendered**,
so F028 stays `IMPLEMENTED_NOT_TESTED`.
