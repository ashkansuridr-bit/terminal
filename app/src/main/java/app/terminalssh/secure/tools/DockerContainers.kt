package app.terminalssh.secure.tools

/**
 * Docker container dashboard (F045).
 *
 * Listing and log reading are read-only. Start/stop/restart/pause are mutating and are
 * marked so the UI confirms. Absence of `docker` yields an empty list (or a permission
 * error string) that the caller renders as "Docker not available here", not as "no
 * containers".
 */
object DockerContainers {

    data class Container(
        val id: String,
        val name: String,
        val image: String,
        val status: String,
        val state: String,
    ) {
        val isRunning: Boolean get() = state.equals("running", ignoreCase = true)
        val shortId: String get() = id.take(12)
    }

    fun listCommand(): String =
        "LC_ALL=C docker ps -a --format '{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null"

    fun parse(output: String): List<Container> = output.lineSequence()
        .mapNotNull { parseLine(it) }
        .toList()

    private fun parseLine(line: String): Container? {
        if (line.isBlank()) return null
        val cols = line.split('\t')
        if (cols.size < 5) return null
        val id = cols[0].trim()
        if (id.isEmpty()) return null
        return Container(
            id = id,
            name = cols[1].trim(),
            image = cols[2].trim(),
            status = cols[3].trim(),
            state = cols[4].trim(),
        )
    }

    enum class Action(val verb: String, val reason: String) {
        START("start", "starts a stopped container"),
        STOP("stop", "stops a running container, killing its processes"),
        RESTART("restart", "interrupts the container's service"),
        PAUSE("pause", "freezes the container's processes"),
        UNPAUSE("unpause", "resumes a paused container"),
    }

    data class Command(val text: String, val reason: String) {
        val isMutating: Boolean get() = true
    }

    fun isValidTarget(target: String): Boolean =
        target.isNotBlank() && target.length <= 128 &&
            target.all { it.isLetterOrDigit() || it in "_.-" }

    fun actionCommand(action: Action, target: String): Command? {
        if (!isValidTarget(target)) return null
        return Command("docker ${action.verb} ${shellQuote(target)}", action.reason)
    }

    fun logsCommand(target: String, tailLines: Int = 200): String? {
        if (!isValidTarget(target)) return null
        return "docker logs --tail ${tailLines.coerceIn(1, 5000)} ${shellQuote(target)} 2>&1"
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
