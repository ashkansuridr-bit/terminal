package app.terminalssh.secure.agents

/**
 * Recognises commands worth stopping to confirm.
 *
 * This is not a security boundary — anyone with a shell can do anything, and a determined
 * user will. It is a guard against the specific mobile failure: a mis-tap or a
 * half-considered paste on a phone, on a server the user forgot was production.
 *
 * Conservative on purpose. Prompting for ordinary work trains people to dismiss the
 * prompt, which is worse than never having shown one.
 */
object DangerousCommand {

    enum class Severity {
        /** Irreversible and wide-reaching: data loss beyond one file. */
        DESTRUCTIVE,

        /** Reversible, but disruptive to anyone relying on the machine. */
        DISRUPTIVE,
    }

    data class Finding(val severity: Severity, val reason: String)

    private data class Rule(val severity: Severity, val reason: String, val regex: Regex)

    private val RULES = listOf(
        Rule(
            Severity.DESTRUCTIVE, "recursive delete of a root-level path",
            // rm -rf / or /etc or /var ... but not ./build or ~/project
            Regex("""\brm\s+(-[a-zA-Z]*\s+)*-?[a-zA-Z]*[rR][a-zA-Z]*[fF]?[a-zA-Z]*\s+/(\s|$|[a-z]+\s*$)"""),
        ),
        Rule(
            Severity.DESTRUCTIVE, "recursive delete of the home directory",
            // \$ in a raw string would interpolate, so the dollar is escaped by code point.
            Regex("\\brm\\s+.*-[a-zA-Z]*[rR].*\\s+(~|\\${'$'}HOME)(/\\s*)?(\\s|${'$'})"),
        ),
        Rule(
            Severity.DESTRUCTIVE, "writing directly to a block device",
            Regex("""\bdd\s+.*\bof=/dev/(sd|nvme|vd|hd)"""),
        ),
        Rule(
            Severity.DESTRUCTIVE, "formatting a filesystem",
            Regex("""\bmkfs(\.[a-z0-9]+)?\s"""),
        ),
        Rule(
            Severity.DESTRUCTIVE, "discarding uncommitted work",
            Regex("""\bgit\s+(reset\s+--hard|clean\s+-[a-zA-Z]*[fd])"""),
        ),
        Rule(
            Severity.DESTRUCTIVE, "force-pushing over remote history",
            Regex("""\bgit\s+push\b.*(--force(?!-with-lease)|\s-f(\s|$))"""),
        ),
        Rule(
            Severity.DESTRUCTIVE, "dropping a database",
            Regex("""\bDROP\s+(DATABASE|TABLE|SCHEMA)\b""", RegexOption.IGNORE_CASE),
        ),
        Rule(
            Severity.DISRUPTIVE, "rebooting or powering off the server",
            Regex("""\b(reboot|shutdown|poweroff|halt)\b"""),
        ),
        Rule(
            Severity.DISRUPTIVE, "stopping a system service",
            Regex("""\bsystemctl\s+(stop|disable|mask)\b"""),
        ),
        Rule(
            Severity.DISRUPTIVE, "making a path world-writable",
            Regex("""\bchmod\s+(-[a-zA-Z]+\s+)*777\b"""),
        ),
        Rule(
            Severity.DISRUPTIVE, "killing every process",
            Regex("""\b(killall5|kill\s+-9\s+-1)\b"""),
        ),
    )

    /** The most severe rule [command] matches, or null when nothing matches. */
    fun inspect(command: String): Finding? {
        val text = command.trim()
        if (text.isEmpty()) return null
        return RULES
            .filter { it.regex.containsMatchIn(text) }
            .minByOrNull { it.severity.ordinal }
            ?.let { Finding(it.severity, it.reason) }
    }

    fun isDangerous(command: String): Boolean = inspect(command) != null
}
