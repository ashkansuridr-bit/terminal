package app.terminalssh.secure.terminal

import app.terminalssh.secure.agents.DangerousCommand

/**
 * Optional local command history (F016).
 *
 * Off unless the user turns it on. A command line is exactly where a token gets pasted
 * (`export API_KEY=...`, `mysql -p...`), so capture is never the default and a line that
 * looks like it carries a secret is dropped rather than stored. History is a convenience;
 * a leaked `Authorization:` header in a prefs file is not recoverable.
 *
 * The dangerous-command detector is reused so the same lines that would prompt on paste are
 * the ones history refuses to keep verbatim.
 */
object CommandHistory {

    data class Entry(val command: String, val at: Long)

    const val MAX_ENTRIES = 200

    // Substrings that indicate a credential is present in the line itself.
    private val SECRET_MARKERS = listOf(
        "password", "passwd", "pass=", "pass ", "--password",
        "api_key", "apikey", "api-key", "secret", "token", "bearer ",
        "authorization:", "private_key", "privatekey", "-----begin",
    )

    private val EXPORT_SECRET = Regex("""(?i)\b(export|set)\s+\w*(key|token|secret|pass)\w*=""")

    /** True when the line itself looks like it carries a secret and must not be stored. */
    fun looksLikeSecret(command: String): Boolean {
        val lower = command.lowercase()
        if (SECRET_MARKERS.any { it in lower }) return true
        if (EXPORT_SECRET.containsMatchIn(command)) return true
        // A `-p<value>` (no space) is the classic mysql/ssh password form; a bare `-p` is not.
        return Regex("""(?i)(^|\s)-p\S{2,}""").containsMatchIn(command)
    }

    fun isSensitive(command: String): Boolean = looksLikeSecret(command) || DangerousCommand.isDangerous(command)

    /**
     * Returns the history with [command] appended, or unchanged when capture is off, the
     * line is blank, it looks sensitive, or it repeats the previous entry.
     */
    fun record(
        history: List<Entry>,
        command: String,
        at: Long,
        enabled: Boolean,
        limit: Int = MAX_ENTRIES,
    ): List<Entry> {
        if (!enabled) return history
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return history
        if (looksLikeSecret(trimmed)) return history
        if (history.firstOrNull()?.command == trimmed) return history
        // Newest first, bounded.
        return (listOf(Entry(trimmed, at)) + history).take(limit)
    }

    fun search(history: List<Entry>, query: String): List<Entry> {
        if (query.isBlank()) return history
        return history.filter {
            it.command.contains(query, ignoreCase = true) ||
                app.terminalssh.secure.model.FuzzyMatch.matches(query, it.command)
        }
    }

    fun clear(): List<Entry> = emptyList()
}
