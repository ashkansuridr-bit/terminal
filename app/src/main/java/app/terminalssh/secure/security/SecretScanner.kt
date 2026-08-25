package app.terminalssh.secure.security

/**
 * Finds credentials that have leaked into terminal output.
 *
 * The realistic failure is mundane: a user runs `env`, or an agent echoes its config, and
 * an API key scrolls past into a buffer that may later be shared in a bug report. Catching
 * it at the moment it appears is the only cheap place to catch it.
 *
 * Deliberately conservative. A false positive hides output the user needed, which is a
 * real cost, so every pattern requires a recognisable prefix and a plausible length rather
 * than guessing at "long random-looking string".
 */
object SecretScanner {

    /** A credential found in text, with the span to hide. */
    data class Finding(val start: Int, val end: Int, val label: String)

    private data class Pattern(val label: String, val regex: Regex)

    private val PATTERNS = listOf(
        // Anthropic
        Pattern("Anthropic API key", Regex("""sk-ant-[A-Za-z0-9_\-]{20,}""")),
        // OpenAI, both the classic and project-scoped forms
        Pattern("OpenAI API key", Regex("""sk-(?:proj-)?[A-Za-z0-9_\-]{20,}""")),
        // GitHub personal access / app tokens
        Pattern("GitHub token", Regex("""gh[pousr]_[A-Za-z0-9]{36,}""")),
        // AWS access key id
        Pattern("AWS access key", Regex("""(?:AKIA|ASIA)[0-9A-Z]{16}""")),
        // Google API key
        Pattern("Google API key", Regex("""AIza[0-9A-Za-z_\-]{35}""")),
        // Slack tokens
        Pattern("Slack token", Regex("""xox[baprs]-[0-9A-Za-z\-]{10,}""")),
        // A private key block header is unambiguous and worth flagging on its own
        Pattern("private key", Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----""")),
    )

    /** Every credential-looking span in [text], in the order they appear. */
    fun scan(text: String): List<Finding> =
        PATTERNS
            .flatMap { pattern ->
                pattern.regex.findAll(text).map { Finding(it.range.first, it.range.last + 1, pattern.label) }
            }
            // Overlapping matches (sk-ant- also matches the looser sk- rule) collapse to
            // the longest, so a key is never partially revealed by the shorter match.
            .sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
            .fold(mutableListOf()) { kept, finding ->
                if (kept.isEmpty() || finding.start >= kept.last().end) kept.add(finding)
                kept
            }

    /**
     * [text] with every credential replaced by its label.
     *
     * The label rather than a fixed blob of asterisks, because "•••" leaves the user
     * wondering what was hidden and whether it mattered.
     */
    fun mask(text: String): String {
        val findings = scan(text)
        if (findings.isEmpty()) return text
        return buildString {
            var cursor = 0
            findings.forEach { finding ->
                append(text, cursor, finding.start)
                append("[").append(finding.label).append(" hidden]")
                cursor = finding.end
            }
            append(text, cursor, text.length)
        }
    }

    fun containsSecret(text: String): Boolean = scan(text).isNotEmpty()
}
