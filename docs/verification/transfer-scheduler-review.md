# Transfer dispatch and teardown review

This change is implementation plus static review, **not runtime-verified**.

## Changed behavior

- A persistent actor tracks actual in-flight workers, including paused workers still exiting.
- Enqueue/state changes, worker completion/failure, concurrency changes, retry expiry, network capabilities, settings revisions and Connected transitions wake dispatch.
- Automatic transient retries use monotonic bounded exponential deadlines and an actual timer.
- Repeated progress notifications coalesce pending Wake events without losing Finished events.
- Resuming a paused transfer does not clear its cancellation flag until the prior worker exits.
- Controller teardown closes SFTP channels, joins its own coroutine job, then persists the final snapshot.
- Queues/history use per-host/per-session hashed directories in filesDir, not one shared cache filename.
- Atomic writes fsync a temporary file before same-directory rename; persistence failure is reported and final teardown persistence throws.
- identity.properties retains host/session identifiers without credentials for recovery discovery.

## Tests authored (not executed)

TransferSchedulerTest covers 30 files with 3 slots, retry/failure freeing slots, held-network wake, pause/resume overlap, timed retry wake, concurrency increase, and paused-worker slot ownership.

TransferStatePathsTest covers host/session namespace isolation, recovery identity and failure preserving the prior ledger.

## Executed checks

- `git diff --check`: no whitespace errors at review time.
- Focused Gradle attempt: unable to run. Java required explicit JAVA_HOME and LD_LIBRARY_PATH; after resolving that, Gradle 8.13 wrapper download failed with Network is unreachable. `--offline` does not avoid an absent wrapper distribution.

## Remaining verification and limitations

- Run focused tests, both flavor suites, lint, debug/minified builds and the real 30-file SFTP integration test in a provisioned Android/Gradle environment.
- Execute channel teardown under active upload/download; blocking I/O must actually exit on channel close before final persistence.
- Old cache transfer_queue.json/history files remain untouched and are not automatically loaded onto an unverified server.
- A new session UUID does not automatically load an old session ledger; recovery center selection and target validation are required. Process-death recovery is not claimed complete.
- persistenceFailed is exposed for UI consumption; an actionable persistent error/recovery UI must be wired before claiming completion.
- Existing upload resume consistency checks still only compare sizes; same-size remote mutation requires a separate safe-resume fix.
