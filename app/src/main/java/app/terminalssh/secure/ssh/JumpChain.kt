package app.terminalssh.secure.ssh

import app.terminalssh.secure.model.QuickConnect

/**
 * The pure half of multi-hop `ProxyJump` (F035).
 *
 * JSch has no native ProxyJump, so a routed connection can only be built as a chain of local
 * forwarding tunnels, one per hop. That transport does not exist yet and is deliberately not
 * faked: [JschSshClient] still refuses a profile with a jump host outright rather than silently
 * falling back to a direct connection. What *can* be built now, and tested, is the planning —
 * parsing a `ProxyJump` value into ordered hops, rejecting the malformed and the absurd, and
 * deciding which local ports the transport will need. When the tunnel chain lands it consumes
 * this plan instead of re-deriving the rules.
 *
 * The hop grammar is exactly [QuickConnect]'s (`[user@]host[:port]`, bracketed IPv6 literals), so
 * that parser is reused rather than copied. Only the extra OpenSSH semantics live here: a
 * comma-separated list, the literal `none`, and a depth cap.
 */
object JumpChain {

    const val DEFAULT_PORT = QuickConnect.DEFAULT_PORT

    /** Deep enough for real bastion chains, shallow enough to bound tunnel fan-out. */
    const val MAX_HOPS = 8

    data class Hop(val user: String?, val host: String, val port: Int)

    sealed interface ParseResult {
        /** A usable chain, nearest hop first. */
        data class Parsed(val hops: List<Hop>) : ParseResult

        /** Blank or the literal `none`: no jump host at all. */
        object Disabled : ParseResult

        data class Invalid(val reasonKey: String) : ParseResult
    }

    /** One local listener the transport must open to reach the next endpoint. */
    data class Tunnel(
        val bindPort: Int,
        val hopHost: String,
        val hopPort: Int,
        val toHost: String,
        val toPort: Int,
    )

    fun parse(spec: String?): ParseResult {
        val trimmed = spec?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("none", ignoreCase = true)) return ParseResult.Disabled

        val tokens = trimmed.split(',')
        if (tokens.any { it.isBlank() }) return ParseResult.Invalid("jump.error.empty_hop")
        if (tokens.size > MAX_HOPS) return ParseResult.Invalid("jump.error.too_many_hops")

        val hops = ArrayList<Hop>(tokens.size)
        for (token in tokens) {
            when (val result = QuickConnect.parse(token.trim(), defaultUsername = "")) {
                is QuickConnect.ParseResult.Invalid -> return ParseResult.Invalid(
                    when (result.reason) {
                        QuickConnect.Reason.EMPTY -> "jump.error.empty_hop"
                        QuickConnect.Reason.MISSING_HOST, QuickConnect.Reason.INVALID_HOST -> "jump.error.host"
                        QuickConnect.Reason.INVALID_PORT -> "jump.error.port"
                    },
                )

                is QuickConnect.ParseResult.Ok -> {
                    val target = result.target
                    hops += Hop(
                        user = target.username.ifBlank { null },
                        host = target.host,
                        port = target.port,
                    )
                }
            }
        }
        return ParseResult.Parsed(hops)
    }

    /**
     * Local bind ports the transport must open: one per hop, each tunnelling *through* that hop
     * to the next endpoint (the following hop, or the target for the last one). Purely arithmetic
     * — no socket is opened here.
     */
    fun plan(hops: List<Hop>, targetHost: String, targetPort: Int, startPort: Int = 2222): List<Tunnel> {
        require(hops.isNotEmpty()) { "a jump chain needs at least one hop" }
        require(hops.size <= MAX_HOPS) { "too many hops to plan" }
        require(targetHost.isNotBlank()) { "target host must not be blank" }
        require(targetPort in 1..65535) { "target port out of range" }
        require(startPort in 1..(65536 - hops.size)) { "start port leaves no room for the chain" }

        return hops.mapIndexed { index, hop ->
            val next = hops.getOrNull(index + 1)
            Tunnel(
                bindPort = startPort + index,
                hopHost = hop.host,
                hopPort = hop.port,
                toHost = next?.host ?: targetHost,
                toPort = next?.port ?: targetPort,
            )
        }
    }

    /** Human-readable chain for a confirmation dialog: `bastion1 -> bastion2 -> target`. */
    fun describe(hops: List<Hop>, targetHost: String): String =
        (hops.map { it.host } + targetHost).joinToString(" -> ")
}
