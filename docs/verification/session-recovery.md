# Explicit session and transfer recovery

The home screen provides a session/recovery overview, recent hosts and saved transfer ledgers. Active session selection uses the existing application registry; it does not create duplicate SSH connections. Recent-server connect uses the normal credential prompt and host-key verification. Reconnecting cannot restore shell processes; tmux remains the remote process continuity mechanism.

Recovery scans app-private `transfer-state/*/queue.json` across prior session IDs. Each new ledger records host ID, address, port, user and jump-host ID only; it never copies auth metadata or vault references. A host whose address/user/port/route changed cannot adopt an old ledger. Missing/legacy identity remains visible but nonresumable. The original local file remains for support/recovery rather than being silently removed.

The user connects normally to the matching server, explicitly imports saved transfers and reviews PAUSED entries in Files. No writes start from merely opening the dashboard. The destination controller commits its new queue before deleting the old ledger. Repeated import after a persistence/delete failure is idempotent for matching transfer identities and rejects collisions. Imports serialize against teardown. Persistence reads the current queue under the same import lock so a stale empty emission cannot clear a just-imported durable queue.

Evidence authored: TransferRecoveryStoreTest covers process restart discovery, current-session exclusion, changed address/user/port/route refusal, absent identity and absence of vault reference persistence. TransferRecoveryInstrumentedTest exercises real Android JSON, queue migration, byte offset retention and PAUSED durable restore.

Executed here: source audit passes; FA/EN/AR XML parsed. Android build/instrumentation are not verified here because Android SDK/Gradle distribution access is unavailable in this environment. CI/device execution remains required; these are not claimed passing Android tests.

SessionApplicationOwnershipTest uses the actual TerminalApp composition root: a controller and PAUSED transfer survive clearing an Activity ViewModelStore, a replacement ViewModel sees the same controller, and the single lifecycle owner closes/removes the session while durably retaining pending work. It is authored but not executed locally.

Manual/device scenarios pending:
1. Interrupt a download, kill the process, reopen app: recovery entry survives; dashboard does not transfer bytes.
2. Connect to original endpoint through the normal host-key prompt, restore: rows are PAUSED; review and resume in Files.
3. Edit saved host address/user/port/jump route: old ledger cannot import to the changed endpoint.
4. Remove old identity file: row remains visible, action disabled and local ledger preserved.
5. Force disk write failure during import: old ledger retained; import retry must not create duplicate IDs.
6. Close session concurrently with restore: import serializes with teardown; no controller left registered.
7. FA/AR/EN on small phone and tablet: dialog scrolls, technical endpoints render LTR, actions retain Material touch targets.

Cleanup failures from SessionLifecycleManager are now visible in the dashboard and recovery dialog with FA/EN/AR guidance to inspect saved state and independently verify remote data. The UI does not imply cleanup retry is available after the controller was removed. Explicit PAUSED import uses TransferQueue.importPaused, which publishes one atomic batch; TransferRecoveryQueueTest checks 30 paused transfers cannot be scheduled, offsets survive, retry does not duplicate IDs and identity collisions leave the queue unchanged. These Kotlin tests remain unexecuted locally.
