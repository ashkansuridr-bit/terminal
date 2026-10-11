package app.terminalssh.secure.sftp

/**
 * Transfer queue scheduling policy (F029).
 *
 * The transfer manager already moves bytes; this is the policy that decides *which* bytes
 * next, how many at once, and when to give up. Kept pure so the ordering and backoff are
 * unit-tested instead of being buried in coroutine timing.
 *
 * Ordering is priority first, then oldest-first, so a low-priority item cannot starve behind
 * a stream of new high-priority ones forever (FIFO within a priority class). Retries use
 * exponential backoff capped at [Settings.maxBackoffMs]; a permanent server error is never
 * retried, because hammering a "permission denied" just wastes the user's battery.
 */
object TransferQueuePolicy {

    enum class Priority(val weight: Int) { LOW(0), NORMAL(1), HIGH(2) }

    data class Item<T>(
        val value: T,
        val priority: Priority = Priority.NORMAL,
        val enqueuedAt: Long = 0L,
        val attempts: Int = 0,
        val sizeBytes: Long = 0L,
    )

    data class Settings(
        val maxConcurrent: Int = 2,
        val maxAttempts: Int = 3,
        val baseBackoffMs: Long = 1_000,
        val maxBackoffMs: Long = 60_000,
    )

    /** Priority first (higher wins), then FIFO by enqueue time, then by size (small first). */
    fun <T> order(items: List<Item<T>>): List<Item<T>> = items.sortedWith(
        compareByDescending<Item<T>> { it.priority.weight }
            .thenBy { it.enqueuedAt }
            .thenBy { it.sizeBytes },
    )

    /**
     * The next items to start given [activeCount] transfers already running.
     * Respects [Settings.maxConcurrent] and never returns more than the free slots.
     */
    fun <T> nextBatch(
        items: List<Item<T>>,
        activeCount: Int,
        settings: Settings,
    ): List<Item<T>> {
        val free = (settings.maxConcurrent - activeCount).coerceAtLeast(0)
        if (free == 0) return emptyList()
        return order(items).take(free)
    }

    fun shouldRetry(attempt: Int, settings: Settings): Boolean = attempt < settings.maxAttempts

    /** 1-based [attempt]: attempt 1 schedules the first retry, and so on. Capped. */
    fun backoffMs(attempt: Int, settings: Settings): Long {
        if (attempt <= 0) return 0
        val base = settings.baseBackoffMs.coerceAtLeast(1)
        // Shift carefully so a large attempt count cannot overflow into a negative delay.
        val shifted = if (attempt - 1 >= 31) Long.MAX_VALUE else base shl (attempt - 1)
        return minOf(shifted, settings.maxBackoffMs)
    }

    /**
     * Errors that retrying cannot fix. SFTP status codes follow the SSH_FX_* values:
     * 2 = no such file, 3 = permission denied, 4 = generic failure, 8 = unsupported.
     */
    fun isPermanentFailure(statusCode: Int): Boolean = statusCode in PERMANENT_CODES

    private val PERMANENT_CODES = setOf(2, 3, 8)
}
