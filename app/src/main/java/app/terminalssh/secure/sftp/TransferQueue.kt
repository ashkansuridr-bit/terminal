package app.terminalssh.secure.sftp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ordering and lifecycle for file transfers, with no I/O of its own.
 *
 * Keeping the queue pure means the rules that actually bite on mobile — what resumes
 * after the connection drops, what a retry does to the byte counter, how many transfers
 * run at once — are unit-testable without a server. [SftpTransferWorker] does the I/O and
 * reports back through [update].
 */
class TransferQueue(private val maxConcurrent: Int = DEFAULT_CONCURRENCY) {

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    val active: List<Transfer> get() = _transfers.value.filter { it.state == TransferState.RUNNING }

    /** Transfers still worth showing in a summary; completed ones fall out. */
    val pending: List<Transfer>
        get() = _transfers.value.filterNot { it.state.isTerminal }

    fun enqueue(transfer: Transfer) {
        _transfers.value += transfer.copy(state = TransferState.QUEUED)
    }

    /**
     * The next transfer that should start, or null when the queue is saturated or empty.
     * Runs in insertion order: a user who queued ten files expects the first one first.
     */
    fun nextToStart(): Transfer? {
        if (active.size >= maxConcurrent) return null
        return _transfers.value.firstOrNull { it.state == TransferState.QUEUED }
    }

    fun markRunning(id: String) = update(id) { it.copy(state = TransferState.RUNNING, attempts = it.attempts + 1) }

    fun markProgress(id: String, transferredBytes: Long, totalBytes: Long = Transfer.UNKNOWN_SIZE) =
        update(id) {
            it.copy(
                // Never let a restarted attempt walk the counter backwards on screen.
                transferredBytes = maxOf(it.transferredBytes, transferredBytes),
                totalBytes = if (totalBytes > 0) totalBytes else it.totalBytes,
            )
        }

    /**
     * Forcibly resets the byte counter to 0, unlike [markProgress] which only ever
     * increases it. Needed when a resume's pre-flight consistency check finds the local
     * staging file (download) or the remote file's size (upload) no longer matches what
     * was previously recorded, so the transfer must restart from scratch instead of
     * resuming from a byte offset that can no longer be trusted.
     */
    fun resetProgress(id: String) = update(id) { it.copy(transferredBytes = 0L) }

    fun markCompleted(id: String) = update(id) {
        it.copy(
            state = TransferState.COMPLETED,
            errorKind = null,
            // A finished transfer shows a full bar even if the server never sent a size.
            transferredBytes = if (it.totalBytes > 0) it.totalBytes else it.transferredBytes,
        )
    }

    fun pause(id: String) = update(id) {
        if (it.canPause) it.copy(state = TransferState.PAUSED) else it
    }

    fun resume(id: String) = update(id) {
        if (it.canResume) it.copy(state = TransferState.QUEUED, errorKind = null) else it
    }

    fun cancel(id: String) = update(id) {
        if (it.canCancel) it.copy(state = TransferState.CANCELLED) else it
    }

    /**
     * A transient failure re-queues itself until [Transfer.MAX_ATTEMPTS]; anything else
     * stops immediately, because retrying a permission error just fails again more slowly.
     * The byte counter is kept so the retry resumes rather than restarting.
     */
    fun fail(id: String, kind: TransferErrorKind) = update(id) { transfer ->
        val retriable = kind.isRetriable && transfer.attempts < Transfer.MAX_ATTEMPTS
        transfer.copy(
            state = if (retriable) TransferState.QUEUED else TransferState.FAILED,
            errorKind = kind,
        )
    }

    /** Drops finished and cancelled entries from the list. */
    fun clearFinished() {
        _transfers.value = _transfers.value.filterNot { it.state.isTerminal }
    }

    /**
     * Called when the SSH session drops: every in-flight transfer becomes retriable
     * rather than being silently abandoned.
     */
    fun onConnectionLost() {
        _transfers.value = _transfers.value.map { transfer ->
            if (transfer.state == TransferState.RUNNING) {
                val retriable = transfer.attempts < Transfer.MAX_ATTEMPTS
                transfer.copy(
                    state = if (retriable) TransferState.QUEUED else TransferState.FAILED,
                    errorKind = TransferErrorKind.CONNECTION_LOST,
                )
            } else {
                transfer
            }
        }
    }

    private inline fun update(id: String, change: (Transfer) -> Transfer) {
        _transfers.value = _transfers.value.map { if (it.id == id) change(it) else it }
    }

    companion object {
        /**
         * One at a time. Parallel transfers over a single SSH connection share the same
         * TCP window, so they finish no sooner in aggregate and make each individual
         * progress bar useless.
         */
        const val DEFAULT_CONCURRENCY = 1
    }
}
