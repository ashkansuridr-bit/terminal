package app.terminalssh.secure.terminal

/**
 * Named markers on terminal output (F013).
 *
 * Scrollback is finite and the server keeps printing, so a bookmark is stored against a
 * line index and must survive — or be pruned — when the buffer trims. Bookmarks are a
 * navigation aid, never persistent state the user expects forever, so the model is a plain
 * immutable list the caller can persist next to the session.
 */
object TerminalBookmarks {

    data class Bookmark(
        val id: String,
        val lineIndex: Int,
        val label: String,
        val createdAt: Long,
    )

    /** Adds a bookmark, replacing any existing marker on the same line. */
    fun add(
        bookmarks: List<Bookmark>,
        lineIndex: Int,
        label: String,
        id: String,
        now: Long,
    ): List<Bookmark> =
        (bookmarks.filterNot { it.lineIndex == lineIndex } +
            Bookmark(id = id, lineIndex = lineIndex, label = label.trim().ifBlank { "line ${lineIndex + 1}" }, createdAt = now))
            .sortedBy { it.lineIndex }

    fun remove(bookmarks: List<Bookmark>, id: String): List<Bookmark> = bookmarks.filterNot { it.id == id }

    fun rename(bookmarks: List<Bookmark>, id: String, label: String): List<Bookmark> = bookmarks.map {
        if (it.id == id) it.copy(label = label.trim().ifBlank { it.label }) else it
    }

    /**
     * Drops bookmarks whose line has scrolled out of the buffer.
     *
     * When the emulator trims [trimmed] lines off the top, every surviving line shifts down
     * by that amount. Callers that cannot supply an exact shift pass zero and only
     * out-of-range markers are removed.
     */
    fun prune(bookmarks: List<Bookmark>, totalLines: Int, trimmedFromTop: Int = 0): List<Bookmark> =
        bookmarks
            .map { it.copy(lineIndex = it.lineIndex - trimmedFromTop) }
            .filter { it.lineIndex in 0 until totalLines }
            .sortedBy { it.lineIndex }

    /** The bookmark at or before [lineIndex], for "jump to previous marker". */
    fun atOrBefore(bookmarks: List<Bookmark>, lineIndex: Int): Bookmark? =
        bookmarks.lastOrNull { it.lineIndex <= lineIndex }

    /** The bookmark strictly after [lineIndex], for "jump to next marker". */
    fun after(bookmarks: List<Bookmark>, lineIndex: Int): Bookmark? =
        bookmarks.firstOrNull { it.lineIndex > lineIndex }
}
