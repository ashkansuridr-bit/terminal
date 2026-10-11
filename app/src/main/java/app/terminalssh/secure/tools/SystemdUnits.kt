package app.terminalssh.secure.tools

/**
 * systemd service listing and confirmed control (F044).
 *
 * Reading unit state is read-only. Start/stop/restart are mutating and are gated on the
 * server actually having systemd ([listCommand] returns nothing when `systemctl` is absent,
 * which the caller renders as "systemd not available"). Unit names are validated before
 * they reach the shell.
 */
object SystemdUnits {

    data class Unit(
        val name: String,
        val load: String,
        val active: String,
        val sub: String,
        val description: String,
    ) {
        val isRunning: Boolean get() = active.equals("active", ignoreCase = true)
        val isFailed: Boolean get() = active.equals("failed", ignoreCase = true)
    }

    fun listCommand(): String =
        "LC_ALL=C systemctl list-units --type=service --all --no-pager --no-legend 2>/dev/null"

    fun parse(output: String): List<Unit> = output.lineSequence()
        .mapNotNull { parseLine(it) }
        .toList()

    private fun parseLine(line: String): Unit? {
        // `systemctl` prefixes the failed/active marker with a bullet on some terminals.
        val trimmed = line.trim().removePrefix("\u25CF").trim().trimStart('*').trim()
        if (trimmed.isEmpty()) return null
        val cols = trimmed.split(Regex("\\s+"), limit = 5)
        if (cols.size < 4) return null
        val name = cols[0]
        if (!name.contains('.')) return null
        return Unit(
            name = name,
            load = cols[1],
            active = cols[2],
            sub = cols[3],
            description = cols.getOrElse(4) { "" },
        )
    }

    enum class Action(val verb: String, val mutating: Boolean, val reason: String) {
        STATUS("status", false, ""),
        START("start", true, "starts the service if it is not running"),
        STOP("stop", true, "stops the service and anything depending on it"),
        RESTART("restart", true, "briefly interrupts the service, dropping its connections"),
        RELOAD("reload", true, "asks the service to reload its configuration"),
    }

    data class Command(val text: String, val mutating: Boolean, val reason: String)

    /** The unit name must be a plain systemd identifier; anything else is rejected. */
    fun isValidUnit(name: String): Boolean =
        name.isNotBlank() && name.length <= 256 && name.all { it.isLetterOrDigit() || it in "@._:-+" }

    fun actionCommand(action: Action, unit: String): Command? {
        if (!isValidUnit(unit)) return null
        return Command("systemctl ${action.verb} ${shellQuote(unit)}", action.mutating, action.reason)
    }

    /** Cap on `-n` so a chatty service cannot flood the terminal view. */
    const val MAX_LOG_LINES = 5000

    /**
     * Read-only tail of a unit's journal. `-n` bounds the output; the caller streams it into a
     * bounded ring (F042). Returns null when the unit name is not a plain identifier.
     */
    fun logsCommand(unit: String, tailLines: Int = 200): String? {
        if (!isValidUnit(unit)) return null
        val lines = tailLines.coerceIn(1, MAX_LOG_LINES)
        return "LC_ALL=C journalctl -u ${shellQuote(unit)} --no-pager -n $lines 2>&1"
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
