package app.terminalssh.secure.tools

/**
 * A bounded, pausable view of a live remote log (F042).
 *
 * `tail -f` can emit thousands of lines a second on a busy server. Storing them in an
 * unbounded list is how a phone gets OOM-killed, so the buffer is a fixed-capacity ring:
 * old lines are dropped, never the whole session. Pausing freezes what the user is reading
 * while [append] keeps draining the channel in the background, and the remote process is
 * stopped by the caller closing the channel — this class only manages text.
 *
 * Filtering is a pure function over the current snapshot so the UI can re-filter while
 * paused without touching the network.
 */
class LogBuffer(val capacity: Int = DEFAULT_CAPACITY) {

    private val ring = ArrayDeque<String>()

    /** When true, [append] is ignored so the visible snapshot stops moving. */
    @Volatile
    var paused: Boolean = false
        private set

    @Synchronized
    fun pause() {
        paused = true
    }

    @Synchronized
    fun resume() {
        paused = false
    }

    @Synchronized
    fun append(chunk: String) {
        if (paused || chunk.isEmpty()) return
        // Split on newlines so one read of many lines fills many rows. An empty segment
        // (from "\r\n" or a trailing newline) is skipped so the ring is not padded with
        // blank rows.
        chunk.split('\n').forEach { line ->
            val trimmed = line.trimEnd('\r')
            if (trimmed.isNotEmpty()) push(trimmed)
        }
    }

    @Synchronized
    fun appendAll(newLines: List<String>) {
        if (paused) return
        newLines.forEach { if (it.isNotEmpty()) push(it) }
    }

    private fun push(line: String) {
        ring.addLast(line)
        while (ring.size > capacity) ring.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = ring.toList()

    @Synchronized
    fun clear() = ring.clear()

    @Synchronized
    fun size(): Int = ring.size

    /** Lines matching [query]; blank returns everything. Case-insensitive, literal. */
    fun filter(query: String): List<String> {
        val snapshot = snapshot()
        if (query.isBlank()) return snapshot
        return snapshot.filter { it.contains(query, ignoreCase = true) }
    }

    companion object {
        const val DEFAULT_CAPACITY = 2_000
    }
}
