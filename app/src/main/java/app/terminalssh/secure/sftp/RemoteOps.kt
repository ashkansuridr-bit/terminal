package app.terminalssh.secure.sftp

/**
 * Shell commands the file browser runs on the server, and the parsing of what comes back.
 *
 * Everything here is a pure function so the fiddly half — quoting a filename a user
 * chose, reading output whose format varies per server — is unit-testable without a
 * server. Nothing in this file opens a channel; [SftpClient.exec] does that.
 *
 * Quoting is a security boundary, not a formatting detail. Every path that reaches a
 * command goes through [RemoteCommands.shellQuote], because a filename is attacker-
 * influenced input: a server can list a file called `x'; rm -rf ~; '` and the browser
 * will happily show it and let the user act on it.
 */
object RemoteOps {

    private fun q(path: String) = RemoteCommands.shellQuote(path)

    // ---- 14: follow a log ----

    /** Last [lines] lines of a file, for the initial fill of a follow view. */
    fun tailCommand(path: String, lines: Int = 200): String =
        "tail -n ${lines.coerceIn(1, MAX_TAIL_LINES)} ${q(path)} 2>/dev/null"

    /**
     * Lines added after byte [fromOffset]. Polling with an offset rather than holding a
     * `tail -f` channel open keeps the follow view resumable and costs nothing while the
     * user is not looking at it — a long-lived exec channel would survive backgrounding
     * and quietly hold a server process open.
     */
    fun tailSinceCommand(path: String, fromOffset: Long): String =
        "tail -c +${fromOffset + 1} ${q(path)} 2>/dev/null"

    // ---- 16: search inside files ----

    /**
     * Recursive fixed-string search. `-F` matters: without it a user searching for
     * `a.b[c]` gets regex behaviour they did not ask for and confusing results.
     */
    fun grepCommand(dir: String, needle: String, ignoreCase: Boolean, maxResults: Int = MAX_GREP_RESULTS): String {
        val flags = buildString {
            append("-rnIF")
            if (ignoreCase) append("i")
        }
        return "grep $flags -m $maxResults -- ${q(needle)} ${q(dir)} 2>/dev/null | head -n $maxResults"
    }

    /** One `path:line:text` hit from grep. */
    data class SearchHit(val path: String, val line: Int, val text: String)

    /**
     * Parses `path:line:text` from grep.
     *
     * Both ends are ambiguous: a path may contain `:` (`/tmp/a:b/file.txt`) and so may
     * the matched text (`127.0.0.1 localhost:8080`). Neither splitting from the left nor
     * from the right survives both. What is unambiguous is the separator itself — the
     * first `:<digits>:` in the line is the line number, because a path component cannot
     * be bounded by colons on both sides and still be part of the filename grep printed.
     */
    fun parseGrepOutput(output: String): List<SearchHit> = output.lineSequence()
        .mapNotNull { line -> parseGrepLine(line) }
        .toList()

    private fun parseGrepLine(line: String): SearchHit? {
        if (line.isBlank()) return null
        var from = line.indexOf(':')
        while (from >= 0) {
            var end = from + 1
            while (end < line.length && line[end].isDigit()) end++
            if (end > from + 1 && end < line.length && line[end] == ':') {
                val lineNo = line.substring(from + 1, end).toIntOrNull() ?: return null
                return SearchHit(line.substring(0, from), lineNo, line.substring(end + 1))
            }
            from = line.indexOf(':', from + 1)
        }
        return null
    }

    // ---- 17: run a command against a selection ----

    /**
     * Substitutes each selected path into [template] at `{}`, the way `find -exec` does,
     * and joins with `&&` so the run stops at the first failure instead of ploughing on.
     * A template without `{}` gets the paths appended, which is what `chmod +x` wants.
     */
    fun buildSelectionCommand(template: String, paths: List<String>): String? {
        val trimmed = template.trim()
        if (trimmed.isEmpty() || paths.isEmpty()) return null
        return if (trimmed.contains(PLACEHOLDER)) {
            paths.joinToString(" && ") { trimmed.replace(PLACEHOLDER, q(it)) }
        } else {
            trimmed + " " + paths.joinToString(" ") { q(it) }
        }
    }

    // ---- 18: extract an archive on the server ----

    /** True when [name] looks like something [extractCommand] knows how to open. */
    fun isExtractable(name: String): Boolean = archiveKind(name) != null

    private fun archiveKind(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> "tar.gz"
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> "tar.bz2"
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> "tar.xz"
            lower.endsWith(".tar") -> "tar"
            lower.endsWith(".zip") -> "zip"
            else -> null
        }
    }

    /**
     * Unpacks [archivePath] into [destDir], or null when the name is not a known archive.
     *
     * `mkdir -p` first, and every tar form gets `-C` rather than a `cd`, so a failure to
     * change directory cannot leave the archive unpacking over the user's current path.
     */
    fun extractCommand(archivePath: String, destDir: String): String? {
        val kind = archiveKind(archivePath) ?: return null
        val a = q(archivePath)
        val d = q(destDir)
        val unpack = when (kind) {
            "tar.gz" -> "tar -xzf $a -C $d"
            "tar.bz2" -> "tar -xjf $a -C $d"
            "tar.xz" -> "tar -xJf $a -C $d"
            "tar" -> "tar -xf $a -C $d"
            "zip" -> "unzip -o -q $a -d $d"
            else -> return null
        }
        return "mkdir -p $d && $unpack"
    }

    // ---- 19: ownership ----

    fun chownCommand(path: String, owner: String, group: String?, recursive: Boolean): String? {
        if (!isValidPrincipal(owner)) return null
        if (group != null && !isValidPrincipal(group)) return null
        val spec = if (group.isNullOrEmpty()) owner else "$owner:$group"
        val flag = if (recursive) "-R " else ""
        return "chown $flag${q(spec)} ${q(path)}"
    }

    /**
     * A user or group name the server will accept. Rejecting early keeps a name that
     * cannot be valid from reaching the shell at all, on top of the quoting.
     */
    fun isValidPrincipal(name: String): Boolean =
        name.isNotEmpty() && name.length <= 32 &&
            name.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }

    // ---- 21: links and empty files ----

    fun symlinkCommand(target: String, linkPath: String): String = "ln -s ${q(target)} ${q(linkPath)}"

    fun hardLinkCommand(target: String, linkPath: String): String = "ln ${q(target)} ${q(linkPath)}"

    fun touchCommand(path: String): String = "touch ${q(path)}"

    fun duplicateCommand(source: String, destination: String): String = "cp -a ${q(source)} ${q(destination)}"

    const val PLACEHOLDER = "{}"
    const val MAX_TAIL_LINES = 5_000
    const val MAX_GREP_RESULTS = 200
}
