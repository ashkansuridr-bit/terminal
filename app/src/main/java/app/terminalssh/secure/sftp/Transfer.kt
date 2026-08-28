package app.terminalssh.secure.sftp

/** One entry in a remote directory listing. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    /** POSIX mode string as the server reports it, e.g. `-rw-r--r--`. */
    val permissions: String,
) {
    /** `..` and the current directory are navigation, not content. */
    val isNavigational: Boolean get() = name == "." || name == ".."
}

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferState {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    /** Whether the queue may still act on this item. */
    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
}

/**
 * A single queued file transfer.
 *
 * [transferredBytes] is what makes resume possible: SFTP can reopen a remote file at an
 * offset, so a transfer interrupted by a dropped connection continues rather than
 * restarting — which is the difference between usable and useless on mobile data.
 */
data class Transfer(
    val id: String,
    val direction: TransferDirection,
    val remotePath: String,
    /** A `content://` URI on the device side; SAF owns the actual location. */
    val localUri: String,
    val displayName: String,
    val totalBytes: Long = UNKNOWN_SIZE,
    val transferredBytes: Long = 0L,
    val state: TransferState = TransferState.QUEUED,
    /** Set only when [state] is [TransferState.FAILED]. */
    val errorKind: TransferErrorKind? = null,
    val attempts: Int = 0,
    /** Wall-clock time (epoch millis) of the last [TransferQueue.markProgress] call; 0
     *  means no progress has been reported yet for the current attempt. */
    val lastProgressAt: Long = 0L,
    /** Smoothed transfer rate in bytes/second; 0 until at least two progress samples
     *  have arrived for the current attempt. */
    val bytesPerSecond: Float = 0f,
    /** Wall-clock time this transfer reached a terminal state; 0 while still live. */
    val finishedAt: Long = 0L,
    /** Wall-clock time this transfer was first enqueued. */
    val enqueuedAt: Long = System.currentTimeMillis(),
    /** Wall-clock time this transfer first started running (0 if never started). */
    val startedAt: Long = 0L,
) {
    /** 0f..1f, or null when the server did not report a size. */
    val progress: Float?
        get() = when {
            totalBytes <= 0L -> null
            else -> (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        }

    /** Seconds remaining at the current rate, or null when there isn't enough information. */
    val etaSeconds: Long?
        get() = when {
            state != TransferState.RUNNING -> null
            totalBytes <= 0L || bytesPerSecond <= 0f -> null
            else -> ((totalBytes - transferredBytes) / bytesPerSecond).toLong().coerceAtLeast(0L)
        }

    val canPause: Boolean get() = state == TransferState.RUNNING
    val canResume: Boolean get() = state == TransferState.PAUSED || state == TransferState.FAILED
    val canCancel: Boolean get() = !state.isTerminal

    companion object {
        const val UNKNOWN_SIZE = -1L
        const val MAX_ATTEMPTS = 3
    }
}

/** Why a transfer failed, in terms a user can act on. */
enum class TransferErrorKind {
    /** The connection dropped; retrying is likely to work. */
    CONNECTION_LOST,

    /** The server refused to read or write the path. */
    PERMISSION_DENIED,

    /** The remote path disappeared between listing and transferring. */
    NOT_FOUND,

    /** No room left on the receiving side. */
    OUT_OF_SPACE,

    /** The device rejected the local file (SAF permission revoked, storage detached). */
    LOCAL_UNAVAILABLE,

    UNKNOWN,
    ;

    /** Only a transient failure is worth an automatic retry. */
    val isRetriable: Boolean get() = this == CONNECTION_LOST
}
