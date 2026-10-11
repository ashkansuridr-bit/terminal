package app.terminalssh.secure.terminal

/**
 * Bounded selection/copy/save of terminal output (F014).
 *
 * A user scrolling a build log wants a hundred lines, not the whole scrollback; and copying
 * a gigabyte of output because they dragged from the top is how the app runs out of memory.
 * Every export is capped by a byte budget and reports whether it was truncated rather than
 * silently dropping the tail.
 *
 * Also flags output that looks like it carries a secret, so the UI can warn before the text
 * is pasted into a chat or an issue — the same reasoning as [app.terminalssh.secure.terminal.CommandHistory].
 */
object OutputExport {

    const val DEFAULT_MAX_BYTES = 1_000_000

    data class Slice(
        val text: String,
        val lineCount: Int,
        val bytes: Int,
        val truncated: Boolean,
        val mayContainSecrets: Boolean,
    )

    /** Inclusive line range; out-of-range indices are clamped, and an empty range is empty. */
    fun slice(
        lines: List<String>,
        fromIndex: Int,
        toIndexInclusive: Int,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): Slice {
        if (lines.isEmpty()) return empty(maxBytes)
        val from = fromIndex.coerceIn(0, lines.size - 1)
        val to = toIndexInclusive.coerceIn(from, lines.size - 1)
        val chosen = lines.subList(from, to + 1)
        return pack(chosen, maxBytes)
    }

    /** The whole buffer, capped. */
    fun whole(lines: List<String>, maxBytes: Int = DEFAULT_MAX_BYTES): Slice = pack(lines, maxBytes)

    private fun pack(lines: List<String>, maxBytes: Int): Slice {
        val cap = maxBytes.coerceAtLeast(0)
        val builder = StringBuilder()
        var used = 0
        var kept = 0
        var truncated = false
        for (line in lines) {
            val cost = line.toByteArray(Charsets.UTF_8).size + 1
            if (used + cost > cap) {
                truncated = true
                break
            }
            if (kept > 0) builder.append('\n')
            builder.append(line)
            used += cost
            kept++
        }
        // A single line longer than the budget: keep a byte-bounded prefix of it.
        if (kept == 0 && lines.isNotEmpty() && cap > 0) {
            val first = lines.first()
            val cut = first.toByteArray(Charsets.UTF_8)
            val prefixBytes = cut.copyOfRange(0, minOf(cut.size, cap))
            builder.append(String(prefixBytes, Charsets.UTF_8))
            used = prefixBytes.size
            kept = 1
            truncated = true
        }
        val text = builder.toString()
        return Slice(
            text = text,
            lineCount = kept,
            bytes = text.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            mayContainSecrets = looksSensitive(text),
        )
    }

    private fun empty(maxBytes: Int) = Slice("", 0, 0, truncated = false, mayContainSecrets = false)

    private val SECRET_MARKERS = listOf(
        "-----begin", "authorization:", "bearer ", "password", "passwd",
        "api_key", "apikey", "secret", "token=",
    )

    /** Conservative: a match only means the UI should ask, not that the text is unsendable. */
    fun looksSensitive(text: String): Boolean {
        val lower = text.lowercase()
        return SECRET_MARKERS.any { it in lower }
    }
}
