# Terminal SSH — 50-Feature Implementation Report

- **Branch:** `feature/terminal-50-tools` (base `origin/main@f675bb1`)
- **Worktree:** `/root/work/terminal-features`
- **Scope of this run:** Phases A–E of the 50-feature request (`F001`–`F050`)
- **Commits:** `9fdb91e`, `5aa2621`, `882abc2`, `da80eb1`, `0629b0e`, `f6a31f0`, `6c13b50`
- **Machine status:** [`FEATURE_STATUS.json`](FEATURE_STATUS.json) · **Resume notes:** [`FEATURE_ENGINE_CHECKPOINT.md`](FEATURE_ENGINE_CHECKPOINT.md)

---

## 1. Executive summary

Of the 50 requested features:

| Outcome | Count |
| --- | --- |
| Engine implemented here + JVM unit-tested (`IMPLEMENTED_NOT_TESTED`) | **42** |
| Already complete on `main`, audited not rebuilt (`EXISTS_VERIFIED`) | 5 |
| Pre-existing partial support, recorded as follow-up (`NEEDS_IMPROVEMENT`) | 0 |
| Blocked with a technical reason (`BLOCKED`) | 2 |
| Deferred with a reason (`DEFERRED_WITH_REASON`) | 1 |
| Verified end-to-end on a device (`VERIFIED`) | **0** |

Nothing is claimed as end-to-end verified: no emulator and no reachable SSH server exist on
this host, so the honest ceiling is "engine implemented, unit tests green, UI wiring pending".

The work was done in an isolated git worktree so it cannot disturb the separate Codex agent
fixing SFTP bugs in the shared checkout. Nothing was pushed, merged, or released.

## 2. Method and constraints honoured

1. **Audit before build.** Each feature was classified as already-existing, partial, or
   missing. Existing features (F002, F004, F008, F027, F030) were verified in place rather
   than rebuilt.
2. **Isolation.** All changes live in `/root/work/terminal-features` on
   `feature/terminal-50-tools`. The shared tree `/root/work/terminal` was left untouched.
3. **No force-push, no merge to `main`, no release.** Nothing was published.
4. **Memory-lean builds.** The host has ~3.9 GB RAM shared with the Codex agent, so Gradle
   ran with `--no-daemon`, `-Xmx1400m`, `-XX:MaxMetaspaceSize=448m`, `workers.max=2`, and
   in-process Kotlin compilation, one task per invocation.
5. **Honesty rule.** No test that was not run was marked PASS; device-dependent verification
   is recorded as unavailable rather than faked.
6. **Stack preserved.** Kotlin/Compose, API 26+, Market flavor without Google Play Services,
   Persian/RTL and other locales untouched, host-key verification untouched.

## 3. What was implemented, batch by batch

### Batch A — Host utilities and smart snippets (`9fdb91e`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F001 Quick Connect | `model/QuickConnect.kt` | Parses `[ssh://][user@]host[:port]`, including bracketed IPv6. |
| F003 Tag/environment filter | `model/HostFilter.kt` | Pure filter over tag / environment / favourite / text with counts. |
| F005 Offline host icons | `model/HostIcons.kt`, `HostProfile.iconKey`, `storage/HostStore.kt` | Built-in icon catalog + avatar luminance + tolerant decode (unknown key falls back, never crashes). |
| F007 Connection info | `model/ConnectionInfo.kt` | Copyable connection strings/links; deliberately never reads the credential reference. |
| F017 Snippet placeholders | `model/SnippetTemplate.kt` | POSIX-quoted placeholder substitution with warnings for unknown placeholders. |
| F018 Snippet library | `model/SnippetLibrary.kt`, `HostProfile.SnippetEntry`, `storage/HostStore.kt` | Category / tags / favourite metadata + filtered search, tolerated on decode. |

Tests: 6 classes, green.

### Batch B — Terminal engines and optional history (`5aa2621`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F011 Search scrollback | `terminal/TerminalSearch.kt` | Literal/regex search with match spans. |
| F012 Next/prev + count | `terminal/TerminalSearch.kt` (`TerminalSearchNavigator`) | Wrap-around navigation, current index, match count. |
| F013 Bookmarks | `terminal/TerminalBookmarks.kt` | Line-keyed bookmarks that shift and prune on scrollback trim. |
| F015 Link detection | `terminal/TerminalLinks.kt` | URL detection with a safe/confirm/block scheme policy. |
| F016 Command history | `terminal/CommandHistory.kt` | Off by default, bounded, suppresses secret-looking lines, fuzzy search. |

Tests: 4 classes, green.

### Batch C — Read-only server diagnostics and safe runbooks (`882abc2`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F041 Resource dashboard | `tools/ServerResources.kt` | Portable `/proc` + `df` + `uname` snapshot; an unsupported platform says so instead of faking zeroes. |
| F042 Live log viewer | `tools/LogBuffer.kt` | Bounded, pausable ring with CRLF trimming and case-insensitive filter. |
| F043 Process manager | `tools/ProcessList.kt` | `ps` parser; signals restricted to an allow-listed enum and a positive numeric pid; marked mutating. |
| F044 systemd management | `tools/SystemdUnits.kt` | Unit parser, name validation (rejects metacharacters), quoted `systemctl` actions marked mutating. |
| F045 Docker dashboard | `tools/DockerContainers.kt` | `ps` parser, validated actions, bounded/quoted log command. |
| F046 Git tool | `tools/GitStatus.kt` | `porcelain=v2` parser (branch/ahead/behind/changes/rename/conflict) with a validated `-C` directory. |
| F047 Ports view | `tools/NetworkPorts.kt` | Handles both `ss` and `netstat` column layouts; read-only. |
| F050 Safe runbook | `tools/Runbook.kt` | Dry-run preview, confirmation gate for dangerous steps, stop-on-failure state machine. |

Tests: 8 classes, green.

### Batch D — Security, SFTP and SSH (`da80eb1`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F039 Diagnostics | `ssh/ConnectionDiagnostics.kt` | DNS/TCP/KEX/HOST_KEY/AUTH/SHELL phase model; a failure marks later phases skipped; every `ConnectionErrorKind` maps to a phase and a localizable key. |
| F048 Encrypted backup | `security/PortableBackup.kt` | PBKDF2-HMAC-SHA256 (210 000) + AES-256-GCM with the header bound as AAD, so the KDF cost cannot be stripped. Wrong passphrase and tampering are distinct outcomes. Pure `javax.crypto`. |
| F026 File preview | `sftp/HexPreview.kt` | Binary sniffing, bounded UTF-8 decode, offset/hex/ascii dump. |
| F025 Remote search | `sftp/RemoteFinder.kt` | Depth- and count-capped `find` with quoted root/pattern and a labelled directory section (no GNU-only `-printf`). |
| F037 known_hosts | `storage/KnownHostsFormat.kt` | OpenSSH codec: bracket `host:port`, multi-host fields, hashed-host/marker counting, base64 validation, SHA-256 fingerprint. |

Tests: 5 classes, green.

### Batch E — Model, terminal and SFTP policies (`0629b0e`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F029 Queue policy | `sftp/TransferQueuePolicy.kt` | Priority + FIFO + size ordering, concurrency cap, exponential backoff with an overflow guard, permanent-vs-retryable error split. |
| F022 Transfer preview | `sftp/TransferPlan.kt` | File/dir/byte counts, conflicts, unreadable, symlinks, "estimated" flag for capped listings, human-readable sizes. |
| F014 Output export | `terminal/OutputExport.kt` | Byte-capped inclusive slice/whole copy with a truncation flag and a secret-looking-output warning before sharing. |
| F034 Broadcast input | `terminal/BroadcastInput.kt` | Connected + non-read-only targeting, excluded-with-reason, confirmation gate for multi-line/long input, secret-free summary. |
| F010 Key toolbar | `model/KeyToolbarConfig.kt` | Ordered editable extra-keys row: tolerant token parse, toggle, move, reset. |
| F020 Notifications | `model/SessionNotification.kt` | Event policy + a payload that carries only the host label/address — never a username, vault ref, command, or output. |

Tests: 6 classes, green.

### Batch F — Tab layout and folder-size budgeting (`f6a31f0`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F019 Tab layout | `model/TabLayout.kt` | Rename (trim/cap/reject blank), pin/unpin with a stable pinned-first invariant, reorder, close, selection-after-close (next then previous). |
| F024 Folder size budget | `sftp/FolderSizeBudget.kt` | Entry- and wall-clock-bounded, cancellable walk tracker that reports why it stopped; quoted/validated `du -sk` total + per-child parsers. |

Tests: 2 classes, green.

### Batch G — Integrity honesty, conflict/batch policy, per-host options (`6c13b50`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F028 Integrity status | `sftp/IntegrityStatus.kt` | Optional-hash comparison where VERIFIED requires a real match; a missing hash is UNVERIFIED, never a silent success. |
| F021 Conflict apply-to-all | `sftp/ConflictBatchPolicy.kt` | Remembers an apply-to-all conflict decision so a folder transfer stops re-prompting; `ASK` is never remembered. |
| F023 Batch transfer control | `sftp/TransferBatchControl.kt` | Which queued items Pause all / Resume all / Retry failed / Cancel all may touch; a corrupt transfer is never retried. |
| F040 Per-host options | `model/ConnectionOptions.kt` | Timeout/keep-alive/compression/reconnect with clamping and a tolerant string-map codec. |

Tests: 4 classes, green.

### Batch H — The six former partial items, closed as engines (`1c14668`)

| Feature | File(s) | What it does |
| --- | --- | --- |
| F006 Connection status | `ui/ConnectionStatus.kt` | `aggregate()` picks one badge for a host holding several sessions, ordered needs-approval > connecting > connected > failed; a healthy shell keeps a dropped SFTP tab from painting the row red. |
| F009 Cursor appearance | `model/CursorAppearance.kt` | One tolerant parse of the stored shape (case/space-insensitive, unknown falls back to block) paired with the blink flag. |
| F032 Workspace save/restore | `model/WorkspaceState.kt` | Versioned save/restore of open tabs (host id/title/pin, no secrets); `planRestore()` drops tabs whose host is gone and **never auto-connects**. |
| F033 tmux windows | `agents/TmuxCommands.kt` | `list-windows` parsing plus rename-session / rename-window / kill-window, all quoted through the existing helper; window indexes are validated ints. |
| F036 Port-forwarding spec | `ssh/PortForwardSpec.kt` | Validates a `-L`/`-R` rule before it is added, detects a bind port already taken by the same kind, flags privileged ports. The SSH layer still owns the binding. |
| F038 ssh/config notes | `storage/SshConfigImport.kt` | `analyze()` reports the `Include` / `IdentityFile` / `ProxyJump` directives `parse()` deliberately does not apply, so an import can state what it skipped instead of dropping it silently. |

Tests: 6 classes, green (incl. the new `agents/TmuxWindowsTest`).

### Batch I — Pure cores for the two blocked transport features (`8e5088e`)

Both features stay blocked on a transport that does not exist, so the risky half is refused
rather than faked: `JschSshClient` still throws `JumpHostUnavailable` for a jump host, and the
emulator still renders one buffer. Only the planning/geometry half is added, tested.

| Feature | File(s) | What it does |
| --- | --- | --- |
| F035 Jump chain | `ssh/JumpChain.kt` | Parses an OpenSSH `ProxyJump` value (comma-separated `[user@]host[:port]`, bracketed IPv6, the literal `none`) into ordered hops, caps the depth at 8, and plans one local forward per hop. Hop grammar reused from `QuickConnect`; opens no socket. |
| F031 Pane layout | `terminal/PaneLayout.kt` | Binary split tree over pane ids: split/close keep the tree valid (close collapses the sibling, last pane protected, cap 6), resize clamps the divider, next/prev focus wraps. |

Tests: 2 classes, green (`JumpChainTest`, `PaneLayoutTest`). Status stays `BLOCKED` for both.

### Batch J — Read-only completions for the server tools (`4484e52`)

An audit of the five `EXISTS_VERIFIED` features (F002/F004/F008/F027/F030) and the tool
engines found them complete on `main`/earlier batches, except that two dashboards promised a
read they could not yet perform. Both additions are read-only and reuse the existing validation
and quoting helpers.

| Feature | File(s) | What it does |
| --- | --- | --- |
| F044 systemd | `tools/SystemdUnits.kt` | `logsCommand(unit, tailLines)` builds a read-only `journalctl -u <unit> --no-pager -n <n>` tail; unit validated as a plain identifier, `-n` clamped to `MAX_LOG_LINES = 5000`. Never calls `systemctl`. |
| F045 Docker | `tools/DockerContainers.kt` | `statsCommand` / `inspectCommand` (one-shot reads, `--no-stream`) plus `parseStats`/`Stats`; target validated and shell-quoted. |

Tests: 2 classes, green (`SystemdUnitsLogsTest` 3, `DockerContainerReadTest` 5). Both features
stay `IMPLEMENTED_NOT_TESTED` (UI still unverified); only their evidence fields are extended.

### Batch K — a real bug in the F011/F012 terminal search (`8b94781`)

Auditing for the RTL/non-ASCII requirement found a correctness bug rather than a missing
operation. `TerminalSearch.findAll` lower-cased the haystack and ran `indexOf` on the lowered
copy; because `String.lowercase()` can change length (`İ` → `i` + U+0307), the resulting `end`
offset could exceed the original line, making `highlight()` slice the wrong text or throw
`StringIndexOutOfBoundsException`.

The matcher now runs on the original text (`Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE`,
with `Pattern.quote` keeping the query literal), so offsets are original-coordinate and
non-ASCII scripts keep matching case-insensitively. `TerminalSearchTest` is now 13 tests
(+4: offset correctness, no slice past the line, Cyrillic/Greek folding, literal metacharacters).
Status stays `IMPLEMENTED_NOT_TESTED`; the F011/F012 evidence records the fix.

### Batch L — first real UI wiring: the F036 port-forward dialog (`c4003d8`)

The port-forward dialog is the first screen actually connected to an engine. It was chosen
because it is reachable from the terminal menu and has no instrumentation test, so the change
cannot silently break the on-device suite. It used to parse its fields inline and do nothing when
a value was bad, and never used `PortForwardSpec`.

| Piece | File(s) | What it does |
| --- | --- | --- |
| Form decision | `ui/PortForwardForm.kt` | `evaluate(...)` returns `(spec \| problemKey \| warningKey, canAdd)`: range/host validation, same-kind bind-port conflict (identical rule allowed), privileged-port warning; `reasonRes()` maps keys to strings. |
| Dialog | `ui/PortForwardDialog.kt` | Shows the localized reason/warning inline and disables Add unless usable; passes the trimmed, validated host to the SSH layer. |
| Strings | `values*` (7 locales) | `forward_error_bind_port/target_port/host/conflict`, `forward_warning_privileged`. |

Tests: `PortForwardFormTest` (9), green. This batch touches Compose, so it is
compile/lint/assemble-verified but **not device-rendered**; F036 stays `IMPLEMENTED_NOT_TESTED`.

### Batch M — F021 apply-to-all reaches the dialog (`bf7f7a8`)

`SftpController.resolveConflict(resolution, applyToAll)` existed, but nothing passed
`applyToAll = true`, so a folder upload re-prompted for every colliding file.

| Piece | File(s) | What it does |
| --- | --- | --- |
| Conflict dialog | `ui/UploadConflictDialog.kt` | Carries `(ConflictResolution, applyToAll)`; adds an "apply to all remaining files" checkbox and threads the flag into Overwrite/Rename/Skip. Cancel is intentionally never applied to the batch. |
| Call site | `ui/FilesScreen.kt` | Forwards `applyToAll` to `sftp.resolveConflict`. |
| String | `values*` (6 locales) | `sftp_conflict_apply_all`. |

No new JVM test: the remembered-decision rule is already covered by `ConflictBatchPolicyTest`,
and the rest is Compose. Verified by test compile + lint + assemble only; **not device-rendered**,
so F021 stays `IMPLEMENTED_NOT_TESTED`.

### Batch N — F026 binary/hex file preview, end to end (`cd7cbc5`)

The preview always decoded to text, so a binary was a wall of replacement characters and
`HexPreview` never received bytes. This batch supplies bounded bytes and renders hex when the
sample is not text.

| Piece | File(s) | What it does |
| --- | --- | --- |
| Bounded bytes | `sftp/BoundedText.kt` | `BytePreview` + `readBytes()`: bounded to `MAX_EDIT_BYTES`, reads one byte past the cap so truncation is proven, keeps the bytes. |
| Fetch | `sftp/SftpClient.kt`, `sftp/SftpController.kt` | `downloadPreview` / `downloadPreviewBytes`, default cap 64 KiB. |
| Decision | `sftp/PreviewContent.kt` | `of(bytes, maxBytes, sourceTruncated) -> View.Text \| View.Hex` via `HexPreview`; shared `truncated` flag. |
| Dialog | `ui/FilePreviewDialogs.kt`, `ui/SftpScreen.kt`, `ui/FilesScreen.kt` | `FilePreviewDialog` renders text or a monospace hex dump with a binary label and bounded-sample note; `fetchPreviewBytes` threaded through; falls back to the text fetch. |
| Strings | `values*` (6 locales) | `sftp_preview_binary`, `sftp_preview_truncated` (French apostrophe escaped for aapt2). |

Tests: `PreviewContentTest` (8), `BoundedTextReadBytesTest` (6), green. Rendering is still **not
device-verified**, so F026 stays `IMPLEMENTED_NOT_TESTED`.

## 4. Security posture of the new code

- **No untrusted data is concatenated into a shell.** Shell roots, search patterns, systemd
  unit names, Docker targets, process pids, and git directories are validated first (rejecting
  NUL/newline/metacharacters) and then single-quoted (`'` → `'\''`). Fixed tool names are the
  only literals.
- **Read-only and mutating operations are distinct types.** Mutating commands carry an
  explicit `isMutating` / `mutating` flag so the UI can gate them behind a confirmation,
  matching the requirement that Restart/Stop/Kill/Delete/Overwrite are never one-tap.
- **No secrets leak.** F016 suppresses secret-looking history lines; F020's payload has no
  field for a username or a vault reference; F007 builds connection strings without reading
  the credential reference; F048 never logs the passphrase or derived key (the key array is
  zeroed in a `finally`).
- **Bounded memory.** Log ring, hex dump, text decode, find depth/results, copy byte budget
  and transfer queue are all capped, so a busy remote host cannot OOM the phone.
- **Automation is gated.** F050 previews every step, forces confirmation for steps the danger
  detector flags, and halts on the first non-zero exit.
- **Host-key verification untouched.**

## 5. Test evidence

New JVM unit-test classes added on this branch (all green when run together):

```
model:      QuickConnectTest, HostFilterTest, HostIconsTest, ConnectionInfoTest,
            SnippetTemplateTest, SnippetLibraryTest, KeyToolbarConfigTest,
            SessionNotificationTest, TabLayoutTest, ConnectionOptionsTest,
            CursorAppearanceTest, WorkspaceStateTest
ui:         ConnectionStatusTest
terminal:   TerminalSearchTest, TerminalBookmarksTest, TerminalLinksTest,
            CommandHistoryTest, OutputExportTest, BroadcastInputTest, PaneLayoutTest
tools:      ServerResourcesTest, ProcessListTest, SystemdUnitsTest, DockerContainersTest,
            GitStatusTest, NetworkPortsTest, LogBufferTest, RunbookTest
sftp:       HexPreviewTest, RemoteFinderTest, TransferPlanTest, TransferQueuePolicyTest,
            FolderSizeBudgetTest, IntegrityStatusTest, ConflictBatchPolicyTest,
            TransferBatchControlTest
ssh:        ConnectionDiagnosticsTest, PortForwardSpecTest, JumpChainTest
security:   PortableBackupTest
storage:    KnownHostsFormatTest, SshConfigNotesTest
agents:     TmuxWindowsTest
```

Run command used (memory-lean, one task per invocation):

```bash
ANDROID_HOME=/root/android-sdk ./gradlew --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx1400m -XX:MaxMetaspaceSize=448m" \
  -Dorg.gradle.workers.max=2 -Pkotlin.compiler.execution.strategy=in-process \
  testMarketDebugUnitTest \
  --tests "app.terminalssh.secure.model.*" --tests "app.terminalssh.secure.sftp.*" \
  --tests "app.terminalssh.secure.terminal.*" --tests "app.terminalssh.secure.tools.*" \
  --tests "app.terminalssh.secure.ssh.*" --tests "app.terminalssh.secure.security.*" \
  --tests "app.terminalssh.secure.storage.*" --tests "app.terminalssh.secure.ui.*" \
  --tests "app.terminalssh.secure.agents.*"
```

Result: `BUILD SUCCESSFUL`.

### Verification actually performed on this host

| Check | Command (memory-lean) | Result |
| --- | --- | --- |
| Package-filtered unit tests | `testMarketDebugUnitTest` for `model/sftp/terminal/tools` | `BUILD SUCCESSFUL` (422 tests in `model`+`sftp`) |
| `ssh`/`security`/`storage` unit tests | same, filtered | `BUILD SUCCESSFUL` |
| Batch H unit tests | same, six filters (`ui`/`model`/`agents`/`ssh`/`storage`) | `BUILD SUCCESSFUL` |
| Batch J unit tests | same, filter `app.terminalssh.secure.tools.*` | `BUILD SUCCESSFUL` (`SystemdUnitsLogsTest` 3, `DockerContainerReadTest` 5) |
| Batch K unit tests | same, filter `app.terminalssh.secure.terminal.*` | `BUILD SUCCESSFUL` (`TerminalSearchTest` 13, incl. 4 Unicode cases) |
| Batch L unit tests | same, filter `app.terminalssh.secure.ui.PortForwardFormTest` | `BUILD SUCCESSFUL` (9 tests) |
| Batch M unit tests | same, filter `app.terminalssh.secure.ui.*` (compile check; no new test) | `BUILD SUCCESSFUL` |
| Batch N unit tests | same, filter `app.terminalssh.secure.sftp.PreviewContentTest` + `BoundedTextReadBytesTest` | `BUILD SUCCESSFUL` (8 + 6 tests) |
| Android lint (Market) | `lintMarketDebug` | `BUILD SUCCESSFUL` |
| Market debug APK | `assembleMarketDebug` | `BUILD SUCCESSFUL` |
| Gplay flavor compiles | `compileGplayDebugKotlin` | `BUILD SUCCESSFUL` |

A single all-tests `testMarketDebugUnitTest` run was **OOM-killed by the host** (global OOM under
~3.9 GB shared with the Codex agent), so the full suite was not observed in one shot; the
package-filtered runs above are the evidence. This is an infrastructure limit, not a test
failure.

## 6. What was **not** done (honest limitations)

- **No UI wiring.** The 42 implemented features are engines with green unit tests. The Compose
  screens/flows that call them are not built in this run, so a user cannot yet reach most of
  them. This is why none are marked `VERIFIED`.
- **No device/emulator verification.** The AVD image was deleted and the emulator fails with
  `FATAL Broken AVD system path`; the host also OOMs. CI is the device channel and CI cannot go
  green because of pre-existing device-only failures that are byte-identical before and after
  this work.
- **No signed release.** The production signing keystore is not available.
- **F031, F035, F049** remain blocked/deferred as features; see the checkpoint doc for reasons.
  For F031 and F035 the pure planning/geometry core is now implemented and tested
  (`terminal/PaneLayout.kt`, `ssh/JumpChain.kt`), so the remaining work is the transport half
  only — the second terminal buffer, and the jump-host tunnel chain.
- **No feature is left in a partial state.** The six former `NEEDS_IMPROVEMENT` items
  (F006, F009, F032, F033, F036, F038) now each have a pure engine; what remains for them is
  UI wiring, the same as every other engine on this branch.

## 7. How to verify

1. `git -C /root/work/terminal-features log --oneline` shows the feature commits on top of
   `origin/main@f675bb1` (the last one is `8e5088e`).
2. Run the unit-test command above — expect `BUILD SUCCESSFUL`.
3. Read `FEATURE_STATUS.json` for the per-feature status and artifacts.
4. Run `lintMarketDebug` and `assembleMarketDebug` (one task per invocation) to confirm the
   branch compiles and lints cleanly on the Market flavor.
5. Do **not** merge to `main` or publish without explicit permission.
