package app.terminalssh.secure.terminal

/**
 * Search over terminal scrollback (F011/F012).
 *
 * The emulator hands the UI a list of rendered lines; search is a pure scan over that
 * snapshot so a long scrollback never blocks the frame — the caller can run [findAll] on
 * a background dispatcher and post the result. Offsets are UTF-16 indices into the line
 * string, which is what `Spannable`/`AnnotatedString` highlighting expects, so Persian and
 * emoji (surrogate pairs) are sliced correctly because we only ever cut on whole matches.
 */
object TerminalSearch {

    const val MAX_MATCHES = 5_000

    data class Match(val lineIndex: Int, val start: Int, val end: Int)

    /** A run of a line with a flag saying whether it is part of a hit. */
    data class Highlight(val text: String, val match: Boolean)

    fun findAll(
        lines: List<String>,
        query: String,
        caseSensitive: Boolean = false,
        maxMatches: Int = MAX_MATCHES,
    ): List<Match> {
        if (query.isEmpty() || maxMatches <= 0) return emptyList()
        val needle = if (caseSensitive) query else query.lowercase()
        val out = ArrayList<Match>()
        for ((index, line) in lines.withIndex()) {
            val haystack = if (caseSensitive) line else line.lowercase()
            var from = 0
            while (from <= haystack.length - needle.length) {
                val at = haystack.indexOf(needle, from)
                if (at < 0) break
                out += Match(index, at, at + needle.length)
                if (out.size >= maxMatches) return out
                from = at + needle.length
            }
        }
        return out
    }

    /** Splits [line] into alternating unmatched/matched runs for rendering. */
    fun highlight(line: String, query: String, caseSensitive: Boolean = false): List<Highlight> {
        val matches = findAll(listOf(line), query, caseSensitive)
        if (matches.isEmpty()) return listOf(Highlight(line, false))
        val out = ArrayList<Highlight>()
        var cursor = 0
        for (m in matches) {
            if (m.start > cursor) out += Highlight(line.substring(cursor, m.start), false)
            out += Highlight(line.substring(m.start, m.end), true)
            cursor = m.end
        }
        if (cursor < line.length) out += Highlight(line.substring(cursor), false)
        return out
    }

    /** The ordered, de-duplicated line numbers that contain a hit. */
    fun lineIndices(matches: List<Match>): List<Int> = matches.map { it.lineIndex }.distinct()
}

/**
 * Cursor over a match list with next/previous and wrap-around, so the search bar can show
 * "3 / 41" and jump between hits without re-scanning.
 */
class TerminalSearchNavigator(private val matches: List<TerminalSearch.Match>) {
    private var index = -1

    val count: Int get() = matches.size
    val isEmpty: Boolean get() = matches.isEmpty()

    /** 1-based position for the "3 / 41" label, or 0 when nothing is selected. */
    val position: Int get() = if (index < 0) 0 else index + 1

    fun current(): TerminalSearch.Match? = matches.getOrNull(index)

    fun next(): TerminalSearch.Match? {
        if (matches.isEmpty()) return null
        index = if (index + 1 >= matches.size) 0 else index + 1
        return matches[index]
    }

    fun previous(): TerminalSearch.Match? {
        if (matches.isEmpty()) return null
        index = if (index - 1 < 0) matches.size - 1 else index - 1
        return matches[index]
    }

    fun reset() {
        index = -1
    }
}
