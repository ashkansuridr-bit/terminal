package app.terminalssh.secure.terminal

/**
 * Link detection in terminal output (F015).
 *
 * A URL printed by a build tool or a log line should be tappable, but the terminal is
 * showing text the *server* chose. Following an arbitrary scheme from server output is how
 * a hostile MOTD opens an intent on the device, so only http/https/ftp/ssh are followed
 * directly; every other scheme is surfaced for an explicit decision or refused.
 *
 * This is deliberately conservative: it finds well-formed URLs, not "anything with a dot".
 */
object TerminalLinks {

    enum class Policy { OPEN, CONFIRM, BLOCK }

    data class DetectedLink(
        val text: String,
        val start: Int,
        val end: Int,
        val policy: Policy,
    ) {
        val isDirectlyOpenable: Boolean get() = policy == Policy.OPEN
    }

    /** Schemes that are safe to hand to the system browser without a prompt. */
    private val SAFE_SCHEMES = setOf("http", "https", "ftp", "ftps", "ssh", "sftp", "git")

    /**
     * Schemes that can reach other apps or the local filesystem. Opening them may be
     * legitimate, but the user is asked first; `javascript:`/`data:` are refused outright.
     */
    private val CONFIRM_SCHEMES = setOf("tel", "mailto", "sms", "market", "intent", "content", "file")
    private val BLOCK_SCHEMES = setOf("javascript", "data", "vbscript")

    // A scheme followed by :// and no whitespace/angle bracket. Brackets and quotes are
    // excluded so a URL inside prose or markup does not swallow the punctuation.
    private val SCHEME_URL = Regex("""(?i)\b([a-z][a-z0-9+.-]{1,31})://[^\s<>"')\]]+""")

    /** `www.` hosts with no scheme, which people still type and expect to work. */
    private val WWW_URL = Regex("""(?i)\bwww\.[a-z0-9-]+(\.[a-z0-9-]+)+[^\s<>"')\]]*""")

    fun policyFor(scheme: String): Policy {
        val s = scheme.lowercase()
        return when {
            s in BLOCK_SCHEMES -> Policy.BLOCK
            s in SAFE_SCHEMES -> Policy.OPEN
            s in CONFIRM_SCHEMES -> Policy.CONFIRM
            else -> Policy.CONFIRM
        }
    }

    fun detect(line: String): List<DetectedLink> {
        val out = ArrayList<DetectedLink>()
        for (m in SCHEME_URL.findAll(line)) {
            val scheme = m.groupValues[1]
            out += DetectedLink(
                text = m.value,
                start = m.range.first,
                end = m.range.last + 1,
                policy = policyFor(scheme),
            )
        }
        for (m in WWW_URL.findAll(line)) {
            // Skip a `www.` that is actually the host part of a scheme URL already matched.
            if (out.any { m.range.first in it.start until it.end }) continue
            out += DetectedLink(
                text = m.value,
                start = m.range.first,
                end = m.range.last + 1,
                policy = Policy.OPEN,
            )
        }
        return out.sortedBy { it.start }
    }

    /** Normalises a matched link into something `Intent.ACTION_VIEW` can consume. */
    fun toUri(text: String): String =
        if (Regex("""(?i)^[a-z][a-z0-9+.-]*://""").containsMatchIn(text)) text else "http://$text"

    /** Strips trailing punctuation that prose attaches to a URL. */
    fun trimTrailing(text: String): String = text.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '"', '\'')
}
