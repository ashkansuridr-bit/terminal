package app.terminalssh.secure.sftp

/**
 * Bounded, cancellable folder-size accounting (F024).
 *
 * A recursive walk of a big tree over SFTP can run for minutes and eat the phone's battery for
 * a number the user only glances at. This tracker enforces an entry budget and a wall-clock
 * budget, reports *why* it stopped instead of pretending the partial total is complete, and
 * gives the caller a cancellation decision to check between entries.
 *
 * The `du` helpers are the cheap alternative: one command, one number, no per-file round trips.
 * Paths reach the shell only quoted, and a NUL/newline is rejected outright.
 */
object FolderSizeBudget {

    data class Limits(val maxEntries: Int = 20_000, val maxMillis: Long = 5_000)

    enum class Decision { CONTINUE, STOP_ENTRY_LIMIT, STOP_TIME_LIMIT, STOP_CANCELLED }

    data class Progress(
        val entries: Long,
        val bytes: Long,
        val elapsedMillis: Long,
        val truncated: Boolean,
        val stopReason: String,
    )

    /**
     * Accumulates entries and decides when to stop. Not thread-safe by design: one walk owns
     * one tracker, and sharing it across coroutines would make the counters meaningless.
     */
    class Tracker(
        private val limits: Limits = Limits(),
        private val clock: () -> Long = { System.currentTimeMillis() },
    ) {
        private var entries = 0L
        private var bytes = 0L
        private var truncated = false
        private var stopReason = ""
        private val startedAt = clock()

        fun recordEntry(sizeBytes: Long): Decision {
            entries++
            if (sizeBytes > 0) bytes += sizeBytes
            if (entries >= limits.maxEntries) {
                truncated = true
                stopReason = "entry limit"
                return Decision.STOP_ENTRY_LIMIT
            }
            if (clock() - startedAt >= limits.maxMillis) {
                truncated = true
                stopReason = "time limit"
                return Decision.STOP_TIME_LIMIT
            }
            return Decision.CONTINUE
        }

        fun cancel(): Decision {
            truncated = true
            stopReason = "cancelled"
            return Decision.STOP_CANCELLED
        }

        fun progress(): Progress =
            Progress(entries, bytes, clock() - startedAt, truncated, stopReason)

        val isStopped: Boolean get() = truncated
    }

    fun isValidPath(path: String): Boolean =
        path.isNotBlank() && path.length <= 4096 && !path.contains('\u0000') && !path.contains('\n') && !path.contains('\r')

    /**
     * `du -sk` prints one `kilobytes\tpath` line per directory, the last being the total. We
     * take the verbose output and let the parser pick the total, so a single command answers
     * the summary question. Null when the path is unsafe.
     */
    fun command(path: String): String? =
        if (!isValidPath(path)) null else "LC_ALL=C du -sk ${shellQuote(path)} 2>/dev/null"

    /** The total from a `du -sk` run: the last well-formed line, in bytes. */
    fun parseTotalBytes(output: String): Long? {
        val kb = output.lineSequence()
            .mapNotNull { parseLine(it) }
            .lastOrNull()
            ?.first
            ?: return null
        return kb * 1024
    }

    /** Per-immediate-child sizes, keyed by path, excluding the root total line. */
    fun parseChildSizes(output: String, root: String): Map<String, Long> {
        val sizes = LinkedHashMap<String, Long>()
        for (line in output.lineSequence()) {
            val (kb, path) = parseLine(line) ?: continue
            if (path == root) continue
            sizes[path] = kb * 1024
        }
        return sizes
    }

    private fun parseLine(line: String): Pair<Long, String>? {
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null
        val kb = parts[0].toLongOrNull() ?: return null
        return kb to parts[1]
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
