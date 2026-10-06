# Sync and transfer content identity

Source changes are not Android-verified. `ContentIdentityTest` and
`UploadCheckpointTest` are authored regression tests, not executed results.

Sync plans record local and remote size, mtime and SHA-256 snapshots. Execution rejects
unverified plans, checks the whole plan before its first action, and checks each source
and destination again before acting. Uploads snapshot local content and use verified
atomic remote staging. Permission/stat/mkdir/delete errors propagate. SFTP offers no
compare-and-swap; the gap after a final check is not a transaction guarantee.

Download resume compares the existing local prefix against the remote prefix. Final
delivery checks complete local and remote content identity. An interrupted or changed
remote file cannot be treated as verified merely because its length matches.

Upload authorization is persisted before sending file content: original destination
snapshot, source SHA-256 and a random remote staging name. Resume checks the complete
local source and staged prefix. Prefix verification uses a separate stream: JSch RESUME
performs its own skip, so the upload stream stays at offset zero. The staged file is
private before source content is sent, and the complete staged hash is checked before
replacement. No direct overwrite fallback is provided.

Requirements still pending real-server verification:

1. Interrupt upload/download at several offsets and resume; compare full SHA-256.
2. Change source or destination to different content of the same size; refuse stale work.
3. Mutate a sync source/destination after planning and during staging; show failure.
4. Deny stat, mkdir, hash read and rename separately; preserve existing destination.
5. Restore persisted transfers after process death and explicitly resume paused items.
6. Run the 30-file/3-worker queue scenario through actual SFTP channels.

Limitations: server ACLs and concurrent privileged/ancestor directory mutation cannot be
proven safe from POSIX directory mode bits. Atomic replacement does not preserve ACLs,
xattrs or hard-link identity. Cancelled uploads retain their checkpoint and remote stage;
explicit safe orphan cleanup remains pending. Neither Kotlin tests nor integration,
instrumentation, flavor builds or minified runtime have run in this environment.
