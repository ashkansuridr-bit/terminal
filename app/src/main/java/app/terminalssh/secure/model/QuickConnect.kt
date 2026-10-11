package app.terminalssh.secure.model

/**
 * Parses the "just connect to this box once" input (F001).
 *
 * The common case for a terminal client is one server you were told about in a chat
 * message, used once. Making the user fill in a save-a-profile form first, name it, pick
 * an auth method and then forget to clean it up is friction that loses the connection.
 * This parses what people actually paste — `deploy@10.0.0.4`, `box.example.com:2222`,
 * `[2001:db8::1]:22` — into a transient target that never touches [HostStore].
 *
 * Kept pure so the awkward parts (IPv6 brackets, a port that is a word, a missing user)
 * are unit-tested without a device.
 */
object QuickConnect {

    const val DEFAULT_PORT = 22

    data class Target(
        val username: String,
        val host: String,
        val port: Int = DEFAULT_PORT,
    ) {
        init {
            require(host.isNotBlank()) { "host must not be blank" }
            require(port in 1..65535) { "port out of range" }
        }
    }

    sealed interface ParseResult {
        data class Ok(val target: Target) : ParseResult
        data class Invalid(val reason: Reason) : ParseResult
    }

    enum class Reason { EMPTY, MISSING_HOST, INVALID_PORT, INVALID_HOST }

    /**
     * Parses `[ssh://][user@]host[:port]`. A missing user defaults to [defaultUsername];
     * when that is blank too the caller must prompt for one (SSH needs a username).
     */
    fun parse(input: String, defaultUsername: String = ""): ParseResult {
        val raw = input.trim().removePrefix("ssh://").trim()
        if (raw.isEmpty()) return ParseResult.Invalid(Reason.EMPTY)

        val withoutUser = splitUser(raw, defaultUsername) ?: return ParseResult.Invalid(Reason.MISSING_HOST)
        val (username, rest) = withoutUser
        if (rest.isBlank()) return ParseResult.Invalid(Reason.MISSING_HOST)

        val (host, portText) = splitHostPort(rest)
        if (host.isBlank()) return ParseResult.Invalid(Reason.MISSING_HOST)
        if (!isValidHost(host)) return ParseResult.Invalid(Reason.INVALID_HOST)

        val port = if (portText == null) {
            DEFAULT_PORT
        } else {
            portText.toIntOrNull() ?: return ParseResult.Invalid(Reason.INVALID_PORT)
        }
        if (port !in 1..65535) return ParseResult.Invalid(Reason.INVALID_PORT)

        // An empty default user is allowed at parse time; the UI keeps the field editable.
        return ParseResult.Ok(Target(username = username, host = host, port = port))
    }

    private fun splitUser(value: String, defaultUsername: String): Pair<String, String>? {
        // A user part cannot contain '/' or ':' or '@'; the last '@' wins so an email-like
        // host is still handled. IPv6 literals contain ':' but never '@'.
        val at = value.lastIndexOf('@')
        return if (at < 0) {
            defaultUsername to value
        } else {
            val user = value.substring(0, at)
            val rest = value.substring(at + 1)
            if (rest.isBlank()) null else user to rest
        }
    }

    /** Splits `host:port` while treating `[::1]` brackets as part of an IPv6 host. */
    private fun splitHostPort(value: String): Pair<String, String?> {
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close < 0) return value to null
            val host = value.substring(1, close)
            val tail = value.substring(close + 1)
            val port = tail.removePrefix(":").takeIf { tail.startsWith(":") && it.isNotEmpty() }
            return host to port
        }
        // Only a single ':' can be a port separator; more than one means a bare IPv6 literal.
        val firstColon = value.indexOf(':')
        val lastColon = value.lastIndexOf(':')
        if (firstColon >= 0 && firstColon == lastColon) {
            return value.substring(0, firstColon) to value.substring(firstColon + 1)
        }
        return value to null
    }

    private fun isValidHost(host: String): Boolean =
        host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '@' }
}
