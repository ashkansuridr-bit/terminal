package app.terminalssh.secure.tools

/**
 * Process list + explicit signal sending (F043).
 *
 * Listing is read-only. Sending a signal is a mutating action on someone's server, so the
 * command builder only accepts a numeric PID and a small allow-list of signals, and callers
 * are expected to confirm with the user first — [Command.isMutating] marks that for the UI.
 */
object ProcessList {

    enum class Signal(val flag: String) {
        TERM("-TERM"), KILL("-KILL"), HUP("-HUP"), INT("-INT"), STOP("-STOP"), CONT("-CONT")
    }

    data class Process(
        val pid: Int,
        val user: String,
        val cpuPercent: Double,
        val memPercent: Double,
        val rssKb: Long,
        val state: String,
        val command: String,
    )

    const val MAX_ROWS = 500

    /** Compatible with procps; unsupported servers return an error the caller surfaces. */
    fun listCommand(limit: Int = MAX_ROWS): String =
        "LC_ALL=C ps -eo pid,user,pcpu,pmem,rss,stat,comm --sort=-pcpu 2>/dev/null | head -n ${limit.coerceIn(1, MAX_ROWS)}"

    fun parse(output: String): List<Process> = output.lineSequence()
        .mapNotNull { parseLine(it) }
        .toList()

    private fun parseLine(line: String): Process? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("PID", ignoreCase = true)) return null
        // pid user cpu mem rss stat comm-and-arguments
        val cols = trimmed.split(Regex("\\s+"), limit = 7)
        if (cols.size < 7) return null
        val pid = cols[0].toIntOrNull() ?: return null
        return Process(
            pid = pid,
            user = cols[1],
            cpuPercent = cols[2].toDoubleOrNull() ?: 0.0,
            memPercent = cols[3].toDoubleOrNull() ?: 0.0,
            rssKb = cols[4].toLongOrNull() ?: 0L,
            state = cols[5],
            command = cols[6],
        )
    }

    /** A mutating command plus the reason the UI must confirm it. */
    data class Command(val text: String, val reason: String) {
        val isMutating: Boolean get() = true
    }

    /** Null when [pid] is not a positive integer, so a stray value cannot reach the shell. */
    fun signalCommand(pid: Int, signal: Signal = Signal.TERM): Command? {
        if (pid <= 0) return null
        val reason = when (signal) {
            Signal.KILL -> "SIGKILL cannot be caught; the process loses its chance to clean up"
            Signal.TERM -> "asks the process to stop (it may ignore the signal)"
            Signal.HUP -> "often restarts daemons, which can drop connected users"
            Signal.INT -> "sends an interrupt as if the user pressed Ctrl-C"
            Signal.STOP, Signal.CONT -> "freezes/resumes the process without ending it"
        }
        return Command("kill ${signal.flag} $pid", reason)
    }

    fun quoteForDisplay(command: String): String = command
}
