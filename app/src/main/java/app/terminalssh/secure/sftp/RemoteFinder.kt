package app.terminalssh.secure.sftp

/**
 * Remote file search (F025).
 *
 * "Where is that jar on the deploy box" is a common task and `find` is the tool, but running
 * an unbounded `find /` over SFTP is how a phone sits for ten minutes. Every query here is
 * capped by depth and result count, the root and pattern reach the shell only quoted, and a
 * NUL or newline in either is rejected outright.
 *
 * Files and directories are emitted in two labelled sections so the type is known without
 * relying on GNU-only `find -printf` (busybox lacks it).
 */
object RemoteFinder {

    const val DIR_MARKER = "===TSSH_DIRS==="
    const val MAX_DEPTH = 20
    const val MAX_RESULTS = 500

    enum class Kind { ANY, FILE, DIRECTORY }

    data class Query(
        val root: String,
        val namePattern: String = "*",
        val kind: Kind = Kind.ANY,
        val maxDepth: Int = 6,
        val maxResults: Int = 200,
    )

    data class Result(val path: String, val isDirectory: Boolean)

    fun isValidRoot(root: String): Boolean =
        root.isNotBlank() && root.length <= 4096 && !root.contains('\u0000') && !root.contains('\n') && !root.contains('\r')

    fun isValidPattern(pattern: String): Boolean =
        pattern.isNotBlank() && pattern.length <= 512 && !pattern.contains('\u0000') && !pattern.contains('\n') && !pattern.contains('\r')

    /** Null when the query is invalid, so a bad value never reaches the shell. */
    fun command(query: Query): String? {
        if (!isValidRoot(query.root) || !isValidPattern(query.namePattern)) return null
        val depth = query.maxDepth.coerceIn(1, MAX_DEPTH)
        val limit = query.maxResults.coerceIn(1, MAX_RESULTS)
        val root = shellQuote(query.root)
        val pattern = shellQuote(query.namePattern)
        val files = if (query.kind == Kind.DIRECTORY) "" else
            "find $root -maxdepth $depth -type f -name $pattern -print 2>/dev/null | head -n $limit; "
        val dirs = if (query.kind == Kind.FILE) "" else
            "echo $DIR_MARKER; find $root -maxdepth $depth -type d -name $pattern -print 2>/dev/null | head -n $limit"
        return "LC_ALL=C; $files$dirs"
    }

    fun parse(output: String): List<Result> {
        val dirIndex = output.indexOf(DIR_MARKER)
        val fileSection = if (dirIndex >= 0) output.substring(0, dirIndex) else output
        val dirSection = if (dirIndex >= 0) output.substring(dirIndex + DIR_MARKER.length) else ""
        val files = fileSection.lineSequence().mapNotNull { clean(it)?.let { p -> Result(p, false) } }
        val dirs = dirSection.lineSequence().mapNotNull { clean(it)?.let { p -> Result(p, true) } }
        return (files + dirs).distinctBy { it.path }.sortedBy { it.path }.toList()
    }

    fun toQueryText(result: Result): String = if (result.isDirectory) "${result.path}/" else result.path

    private fun clean(line: String): String? =
        line.trimEnd('\r').takeIf { it.isNotBlank() }?.takeIf { !it.startsWith("find:") }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
