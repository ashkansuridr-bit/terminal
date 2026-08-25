package app.terminalssh.secure.agents

/**
 * tmux commands and output parsing.
 *
 * tmux is what makes serious work possible from a phone: without it, a passing tunnel or
 * a locked screen kills whatever was running. With it, the session is on the server and
 * the phone is just a window onto it.
 *
 * Pure string handling so the parsing is testable against real tmux output shapes.
 */
object TmuxCommands {

    /** A tmux session as `list-sessions` reports it. */
    data class Session(
        val name: String,
        val windows: Int,
        val attached: Boolean,
    )

    /**
     * ASCII unit separator, written as an escape rather than a literal control
     * character so it survives every editor and encoding this file passes through.
     */
    private const val SEPARATOR = "\u001F"

    /**
     * Lists sessions in a parseable form.
     *
     * A custom format with a control-character separator rather than tmux's default
     * human-readable line: session names may contain colons and spaces, which makes the
     * default output ambiguous to split.
     */
    fun listSessionsCommand(): String =
        "tmux list-sessions -F " +
            AgentInstallScript.shellQuote(
                "#{session_name}${SEPARATOR}#{session_windows}${SEPARATOR}#{?session_attached,1,0}",
            ) + " 2>/dev/null || true"

    /** Parses the output of [listSessionsCommand]; malformed lines are skipped. */
    fun parseSessions(output: String): List<Session> =
        output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(SEPARATOR)
                if (parts.size != 3) return@mapNotNull null
                val name = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val windows = parts[1].toIntOrNull() ?: return@mapNotNull null
                Session(name = name, windows = windows, attached = parts[2] == "1")
            }
            .toList()

    /**
     * Attaches to [name], detaching whatever else is attached.
     *
     * `-d` matters on mobile: reconnecting after a dropped connection leaves the old
     * client still registered, and without it the new attach shares a window sized for a
     * device that is no longer there.
     */
    fun attachCommand(name: String): String =
        "tmux attach -d -t ${AgentInstallScript.shellQuote(name)}"

    fun newSessionCommand(name: String, workingDir: String? = null): String {
        val dir = workingDir?.takeIf { it.isNotBlank() }
            ?.let { " -c " + AgentInstallScript.shellQuote(it) }
            ?: ""
        return "tmux new -s ${AgentInstallScript.shellQuote(name)}$dir"
    }

    fun killSessionCommand(name: String): String =
        "tmux kill-session -t ${AgentInstallScript.shellQuote(name)}"

    /** Whether tmux is present, answered as a single word. */
    fun availabilityProbe(): String =
        "command -v tmux >/dev/null 2>&1 && printf 'yes\\n' || printf 'no\\n'"

    /**
     * A session name tmux will accept.
     *
     * tmux rejects names containing a period or colon because it uses them to address
     * windows and panes, so those are replaced rather than passed through and rejected.
     */
    fun sanitizeSessionName(raw: String): String {
        val cleaned = raw.trim()
            .map { char ->
                when {
                    char == '.' || char == ':' -> '-'
                    char.isISOControl() -> '-'
                    char.isWhitespace() -> '-'
                    else -> char
                }
            }
            .joinToString("")
            .trim('-')
        return cleaned.takeIf { it.isNotEmpty() }?.take(40) ?: "session"
    }
}
