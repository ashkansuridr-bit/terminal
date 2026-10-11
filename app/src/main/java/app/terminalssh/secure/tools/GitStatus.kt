package app.terminalssh.secure.tools

/**
 * Server-side Git awareness (F046).
 *
 * Reading status/branch/diff is safe; pull/fetch/checkout change the working tree and are
 * marked mutating so the UI confirms. The command always passes an explicit `-C <dir>`
 * through a validated, quoted path, so the tool cannot run in an unexpected repository.
 */
object GitStatus {

    data class Change(val x: Char, val y: Char, val path: String) {
        /** Index/worktree status letters, e.g. `M`, `??`, `A`. */
        val code: String get() = "$x$y"

        val isUntracked: Boolean get() = x == '?' && y == '?'
        val isConflict: Boolean get() = x == 'U' || y == 'U' || code == "AA" || code == "DD"
    }

    data class Status(
        val branch: String = "",
        val upstream: String? = null,
        val ahead: Int = 0,
        val behind: Int = 0,
        val changes: List<Change> = emptyList(),
    ) {
        val isClean: Boolean get() = changes.isEmpty()
        val hasDiverged: Boolean get() = ahead > 0 && behind > 0
    }

    fun statusCommand(dir: String): String? {
        if (!isValidDir(dir)) return null
        return "git -C ${shellQuote(dir)} status --porcelain=v2 --branch 2>&1"
    }

    fun diffCommand(dir: String): String? {
        if (!isValidDir(dir)) return null
        return "git -C ${shellQuote(dir)} --no-pager diff --stat 2>&1"
    }

    fun parse(output: String): Status {
        var branch = ""
        var upstream: String? = null
        var ahead = 0
        var behind = 0
        val changes = ArrayList<Change>()

        for (raw in output.lineSequence()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("# branch.head ") -> branch = line.removePrefix("# branch.head ").trim()
                line.startsWith("# branch.upstream ") -> upstream = line.removePrefix("# branch.upstream ").trim()
                line.startsWith("# branch.ab ") -> {
                    line.removePrefix("# branch.ab ").trim().split(' ').forEach { token ->
                        token.toIntOrNull()?.let { n ->
                            if (token.startsWith("+")) ahead = n else if (token.startsWith("-")) behind = -n
                        }
                    }
                }
                line.startsWith("1 ") -> parseOrdinary(line)?.let { changes += it }
                line.startsWith("2 ") -> parseRename(line)?.let { changes += it }
                line.startsWith("? ") -> changes += Change('?', '?', line.removePrefix("? "))
                line.startsWith("! ") -> Unit
            }
        }
        return Status(branch, upstream, ahead, behind, changes)
    }

    // `1 XY sub mH mI mW hH hI path`
    private fun parseOrdinary(line: String): Change? {
        val cols = line.split(' ', limit = 9)
        if (cols.size < 9) return null
        val xy = cols[1]
        if (xy.length < 2) return null
        return Change(xy[0], xy[1], cols[8])
    }

    // `2 XY sub mH mI mW hH hI Xscore path\torigPath`
    private fun parseRename(line: String): Change? {
        val cols = line.split(' ', limit = 10)
        if (cols.size < 10) return null
        val xy = cols[1]
        if (xy.length < 2) return null
        val path = cols[9].substringBefore('\t')
        return Change(xy[0], xy[1], path)
    }

    enum class Action(val verb: String, val args: List<String>, val mutating: Boolean, val reason: String) {
        FETCH("fetch", emptyList(), true, "contacts the remote to update refs"),
        PULL("pull", listOf("--ff-only"), true, "updates the working tree from the remote"),
    }

    data class Command(val text: String, val mutating: Boolean, val reason: String)

    fun actionCommand(dir: String, action: Action): Command? {
        if (!isValidDir(dir)) return null
        val args = (listOf(action.verb) + action.args).joinToString(" ")
        return Command("git -C ${shellQuote(dir)} $args 2>&1", action.mutating, action.reason)
    }

    fun isValidDir(dir: String): Boolean =
        dir.isNotBlank() && !dir.contains('\n') && !dir.contains('\r') && dir.length <= 4096

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
