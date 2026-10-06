# Atomic editor and DocumentsProvider writes

Implementation scope: built-in text editor, DocumentsProvider close/save and failed-save retry.

The target is never opened in overwrite mode by these paths. The write uploads to a random
sibling created empty and restricted to 0600 before content is uploaded; preserves existing POSIX owner, group and permission bits, reads back the staged bytes and checks SHA-256
and size, revalidates the destination's original byte fingerprint, then invokes SFTP rename.
The adapter refuses saves unless the server advertises `posix-rename@openssh.com` version `1`.
JSch uses the negotiated extension in its rename implementation; the real-server integration
test below remains required to validate that behavior with the pinned dependency.
There is no delete-target or direct-upload fallback. Existing directories and symbolic links
are rejected. Another writer modifying a destination before the commit guard prevents save.

Ordinary saves compare the original loaded fingerprint. A force save must be a separate
explicit UI action. Force retry captures the *current* file fingerprint and still rejects a
change during staging. SAF stores the original hash/size/mtime in app-private metadata and
preserves the edited local bytes on upload, verification, conflict or rename failure.

Limitations: SFTP has no compare-and-swap operation, so another writer can change the file
between the final fingerprint check and atomic rename. Atomic replacement changes the inode:
ACLs, extended attributes and hard-link relationships are not preserved
by this implementation (POSIX owner, group and permission bits are checked; failures block save). Do not claim concurrent edit transactions or
ACL-preserving replacement. Read-back verification incurs one additional staged-file read.
Sibling staging refuses a symlink parent or a POSIX group/other-writable directory, including
/tmp. This is a restrictive baseline; ancestor symlinks, ACL permissions and hostile server
control cannot be proven safe from mode bits. JSch exposes no exclusive no-follow create, so
UUID names alone do not guarantee safety against an attacker who can observe/swap entries in
the same directory despite the permission checks. These are residual security limitations.
Unsupported servers fail closed and local edits remain available for download/recovery.

## Execution evidence

`python3 scripts/source_audit.py`: executed successfully, SOURCE_AUDIT_OK.
`python3 scripts/loop2_gate.py`: executed successfully, LOOP2_GATE_OK.
`git diff --check`: executed successfully at task review.

Focused Gradle tests attempted with the restored Java environment:

```
./gradlew testMarketDebugUnitTest \
  --tests 'app.terminalssh.secure.sftp.AtomicRemoteWriteTest' \
  --tests 'app.terminalssh.secure.sftp.SftpTextEditConflictTest'
```

The wrapper stopped downloading Gradle 8.13 with `java.net.SocketException: Network is
unreachable`. No Kotlin compilation or JVM test execution occurred. Newly added protocol
model unit tests are not an integration result. SafStagingStore tests add fingerprint
persistence and fail-closed missing fingerprint coverage; these are not yet executed.

## Required real-server verification

Use an actual OpenSSH SFTP server and a second SSH client; test both market and gplay:

1. Save a 0600 regular file: verify unchanged permission bits and complete expected SHA-256.
2. Drop the connection during staged upload: original hash remains unchanged; local editor
   or SAF retained edit remains recoverable. Reconnect and explicitly retry.
3. Edit the target through the second client during staging: ordinary save must fail and
   retain local data. Cancel warning, then repeat; cancellation cannot authorize force.
4. Explicitly confirm force and edit again during staging: final guard must reject save.
5. Deny chmod/stat/read/rename independently: error is shown, original remains intact,
   recovery stage stays present. Deny temporary cleanup too: original still intact.
6. Use an SFTP server without the posix-rename extension: no direct target write occurs.
7. Open the same document with two SAF editors. Close one successfully, then close the other:
   second close must become a failed-save recovery item rather than replace the newer file.
8. Kill the process before SAF close/retry completes; restart, unlock and confirm recovery
   metadata and local content are retained, without an automatic overwrite.
9. Edit a 2 MiB file through the built-in editor: load is read-only and save is unavailable.
10. Try a symlink and directory: neither may be replaced by the regular-file save path.

These scenarios and minified/device builds are required release gates, not verified here.

## Recovery app lock follow-up

Recovery honors the enabled lock even when no biometric/device credential is currently
available or enrolled. The recovery screen shows an enrollment/security-settings route and
never displays pending-edit metadata through an availability fallback. Backgrounding invalidates the save authorization generation and hides sensitive content,
but retains a pending system-authentication ticket to permit device-credential handoff on
API 26/30. Authentication success is accepted only while foreground. Background results are
consumed without unlocking. Activity destruction invalidates and cancels the prompt;
recreation starts locked. Queued writes from a previous save generation cannot authorize
later writes. The instrumentation test for lock-required recreation is
authored, not executed in this environment.

On a real device test biometric cancel/error, revoked enrollment, Activity recreation,
background/foreground during a prompt and during failed-save retry. Ensure no path/server
metadata appears before successful current-generation authentication, and that stale
callbacks cannot unlock the resumed screen. API 26/30 device-credential return flows require manual verification; they have not been
executed here and must not be marked verified.
