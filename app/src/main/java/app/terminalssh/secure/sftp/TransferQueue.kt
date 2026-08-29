package app.terminalssh.secure.sftp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ordering and lifecycle for file transfers, with no I/O of its own.
 *
 * Keeping the queue pure means the rules that actually bite on mobile — what resumes
 * after the connection drops, what a retry does to the byte counter, how many transfers
 * run at once — are unit-testable without a server. [SftpTransferWorker] does the I/O and
 * reports back through [update].
 */
class TransferQueue(maxConcurrent: Int = DEFAULT_CONCURRENCY) {

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    /** Completed/cancelled transfers archived by [clearFinished], newest first, capped
     *  at [MAX_HISTORY] — "did that file actually finish last night" without keeping
     *  every transfer ever made for the life of the process. */
    private val _history = MutableStateFlow<List<Transfer>>(emptyList())
    val history: StateFlow<List<Transfer>> = _history.asStateFlow()

    /**
     * Current concurrency limit. Adaptive mode adjusts this dynamically based on
     * pending transfer sizes — 1 for a single large file, up to [MAX_CONCURRENCY]
     * for a batch of small files where latency dominates.
     */
    var maxConcurrent: Int = maxConcurrent
        private set

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
        // Highest priority first, then insertion order — a user who queued ten files
        // still gets the first one first unless they explicitly promoted something.
        return _transfers.value
            .filter { it.state == TransferState.QUEUED }
            .minWithOrNull(compareByDescending<Transfer> { it.priority }.thenBy { it.enqueuedAt })
    }

    /**
     * Promotes [id] above everything currently queued. Does not touch a transfer that is
     * already running: interrupting live I/O to honour a reorder would cost more than the
     * reorder is worth.
     */
    fun promote(id: String) {
        val highest = _transfers.value.filter { it.state == TransferState.QUEUED }
            .maxOfOrNull { it.priority } ?: 0
        update(id) { if (it.state == TransferState.QUEUED) it.copy(priority = highest + 1) else it }
    }

    /** Sends [id] behind everything else queued. */
    fun demote(id: String) {
        val lowest = _transfers.value.filter { it.state == TransferState.QUEUED }
            .minOfOrNull { it.priority } ?: 0
        update(id) { if (it.state == TransferState.QUEUED) it.copy(priority = lowest - 1) else it }
    }

    fun markRunning(id: String) = update(id) {
        it.copy(
            state = TransferState.RUNNING,
            attempts = it.attempts + 1,
            lastProgressAt = 0L,
            bytesPerSecond = 0f,
            startedAt = if (it.startedAt == 0L) System.currentTimeMillis() else it.startedAt,
        )
    }

    /**
     * @param atMillis wall-clock time of this sample, threaded through as a parameter
     *   (rather than read internally) so the rate calculation stays deterministic and
     *   testable like the rest of this class.
     */
    fun markProgress(
        id: String,
        transferredBytes: Long,
        totalBytes: Long = Transfer.UNKNOWN_SIZE,
        atMillis: Long = System.currentTimeMillis(),
    ) = update(id) { current ->
        // Never let a restarted attempt walk the counter backwards on screen.
        val newTransferred = maxOf(current.transferredBytes, transferredBytes)
        val deltaBytes = newTransferred - current.transferredBytes
        val hasPriorSample = current.lastProgressAt > 0L
        val deltaMillis = (atMillis - current.lastProgressAt).coerceAtLeast(1L)
        val instantaneous = if (hasPriorSample && deltaBytes > 0) {
            deltaBytes * 1000f / deltaMillis
        } else {
            current.bytesPerSecond
        }
        // Exponentially weighted toward the latest sample so the ETA settles quickly
        // after a speed change but doesn't jitter between two callbacks a few ms apart.
        val smoothed = if (current.bytesPerSecond <= 0f) instantaneous else {
            current.bytesPerSecond * 0.7f + instantaneous * 0.3f
        }
        current.copy(
            transferredBytes = newTransferred,
            totalBytes = if (totalBytes > 0) totalBytes else current.totalBytes,
            lastProgressAt = atMillis,
            bytesPerSecond = smoothed,
        )
    }

    /**
     * Forcibly resets the byte counter to 0, unlike [markProgress] which only ever
     * increases it. Needed when a resume's pre-flight consistency check finds the local
     * staging file (download) or the remote file's size (upload) no longer matches what
     * was previously recorded, so the transfer must restart from scratch instead of
     * resuming from a byte offset that can no longer be trusted.
     */
    fun resetProgress(id: String) = update(id) {
        it.copy(transferredBytes = 0L, lastProgressAt = 0L, bytesPerSecond = 0f)
    }

    fun markCompleted(id: String, atMillis: Long = System.currentTimeMillis()) = update(id) {
        it.copy(
            state = TransferState.COMPLETED,
            errorKind = null,
            // A finished transfer shows a full bar even if the server never sent a size.
            transferredBytes = if (it.totalBytes > 0) it.totalBytes else it.transferredBytes,
            finishedAt = atMillis,
        )
    }

    fun pause(id: String) = update(id) {
        if (it.canPause) it.copy(state = TransferState.PAUSED) else it
    }

    fun resume(id: String) = update(id) {
        if (it.canResume) it.copy(state = TransferState.QUEUED, errorKind = null) else it
    }

    fun cancel(id: String, atMillis: Long = System.currentTimeMillis()) = update(id) {
        if (it.canCancel) it.copy(state = TransferState.CANCELLED, finishedAt = atMillis) else it
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

    /** Moves completed and cancelled entries out of the live list and into [history]. */
    fun clearFinished() {
        val (finished, remaining) = _transfers.value.partition { it.state.isTerminal }
        _transfers.value = remaining
        if (finished.isNotEmpty()) {
            _history.value = (finished.reversed() + _history.value).take(MAX_HISTORY)
        }
    }

    /** Persists history entries so they survive process death. */
    fun persistHistory(historyFile: File) {
        val arr = JSONArray()
        _history.value.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("direction", t.direction.name)
                put("remotePath", t.remotePath)
                put("displayName", t.displayName)
                put("totalBytes", t.totalBytes)
                put("transferredBytes", t.transferredBytes)
                put("priority", t.priority)
                put("state", t.state.name)
                put("finishedAt", t.finishedAt)
                put("enqueuedAt", t.enqueuedAt)
                put("startedAt", t.startedAt)
            })
        }
        runCatching { historyFile.writeText(arr.toString()) }
    }

    fun loadHistory(historyFile: File) {
        runCatching {
            if (!historyFile.exists()) return
            val arr = JSONArray(historyFile.readText())
            val entries = mutableListOf<Transfer>()
            for (i in 0 until arr.length()) {
                val obj = runCatching { arr.getJSONObject(i) }.getOrNull() ?: continue
                entries.add(Transfer(
                    id = obj.optString("id", ""),
                    direction = runCatching { TransferDirection.valueOf(obj.getString("direction")) }.getOrDefault(TransferDirection.DOWNLOAD),
                    remotePath = obj.optString("remotePath", ""),
                    localUri = "",
                    displayName = obj.optString("displayName", ""),
                    totalBytes = obj.optLong("totalBytes", Transfer.UNKNOWN_SIZE),
                    transferredBytes = obj.optLong("transferredBytes", 0L),
                    priority = obj.optInt("priority", 0),
                    state = runCatching { TransferState.valueOf(obj.getString("state")) }.getOrDefault(TransferState.COMPLETED),
                    finishedAt = obj.optLong("finishedAt", 0L),
                    enqueuedAt = obj.optLong("enqueuedAt", 0L),
                    startedAt = obj.optLong("startedAt", 0L),
                ))
            }
            _history.value = entries.filter { it.id.isNotBlank() }
        }
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

    /**
     * Adaptively adjusts [maxConcurrent] based on queued transfers.
     * - Single large file (>10MB): 1 stream (bandwidth is the bottleneck)
     * - Multiple small files (<1MB each): up to [MAX_CONCURRENCY] (latency dominates)
     * - Mixed: 2 streams as a compromise
     */
    fun adaptConcurrency() {
        val queued = _transfers.value.filter { it.state == TransferState.QUEUED }
        if (queued.isEmpty()) {
            maxConcurrent = DEFAULT_CONCURRENCY
            return
        }
        val allSmall = queued.all { it.totalBytes in 0 until SMALL_FILE_THRESHOLD }
        val allLarge = queued.all { it.totalBytes >= LARGE_FILE_THRESHOLD }
        maxConcurrent = when {
            queued.size == 1 -> DEFAULT_CONCURRENCY
            allSmall -> MAX_CONCURRENCY
            allLarge -> DEFAULT_CONCURRENCY
            else -> 2
        }
    }

    private inline fun update(id: String, crossinline change: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    companion object {
        /** Default: one transfer at a time. Parallel transfers share TCP window. */
        const val DEFAULT_CONCURRENCY = 1

        /** Maximum concurrent transfers for small-file batches. */
        const val MAX_CONCURRENCY = 3

        /** Files smaller than this (bytes) are considered "small" for concurrency decisions. */
        const val SMALL_FILE_THRESHOLD = 1_000_000L // 1MB

        /** Files larger than this (bytes) are considered "large" — single-stream. */
        const val LARGE_FILE_THRESHOLD = 10_000_000L // 10MB

        /** Oldest history entries fall off once this many are archived. */
        const val MAX_HISTORY = 50

        private const val PERSIST_FILE = "transfer_queue.json"

        /** Restores pending transfers from a previous session. RUNNING entries are
         *  re-queued so they auto-resume on the next pump cycle. */
        fun fromPersisted(persistFile: File): TransferQueue {
            val queue = TransferQueue()
            runCatching {
                if (!persistFile.exists()) return@runCatching
                val json = persistFile.readText()
                val arr = JSONArray(json)
                val transfers = mutableListOf<Transfer>()
                for (i in 0 until arr.length()) {
                    val obj = runCatching { arr.getJSONObject(i) }.getOrNull() ?: continue
                    transfers.add(Transfer(
                        id = obj.optString("id", ""),
                        direction = runCatching { TransferDirection.valueOf(obj.getString("direction")) }.getOrDefault(TransferDirection.DOWNLOAD),
                        remotePath = obj.optString("remotePath", ""),
                        localUri = obj.optString("localUri", ""),
                        displayName = obj.optString("displayName", ""),
                        totalBytes = obj.optLong("totalBytes", Transfer.UNKNOWN_SIZE),
                        transferredBytes = obj.optLong("transferredBytes", 0L),
                    priority = obj.optInt("priority", 0),
                        state = runCatching { TransferState.valueOf(obj.getString("state")) }.getOrDefault(TransferState.QUEUED),
                        attempts = obj.optInt("attempts", 0),
                    ))
                }
                queue._transfers.value = transfers
            }
            return queue
        }
    }

    /**
     * Persists pending transfers to internal storage so they survive process death.
     * RUNNING transfers are re-queued so they resume on next app launch.
     */
    fun persist(persistFile: File) {
        val arr = JSONArray()
        _transfers.value.filter { !it.state.isTerminal }.forEach { t ->
            val state = if (t.state == TransferState.RUNNING) TransferState.QUEUED else t.state
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("direction", t.direction.name)
                put("remotePath", t.remotePath)
                put("localUri", t.localUri)
                put("displayName", t.displayName)
                put("totalBytes", t.totalBytes)
                put("transferredBytes", t.transferredBytes)
                put("priority", t.priority)
                put("state", state.name)
                put("attempts", t.attempts)
            })
        }
        runCatching { persistFile.writeText(arr.toString()) }
    }

    /** Clears the persisted file. */
    fun clearPersisted(persistFile: File) {
        runCatching { persistFile.delete() }
    }
}
