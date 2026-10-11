package app.terminalssh.secure.tools

/**
 * Listening ports / network services (F047).
 *
 * Purely read-only. Prefers `ss`, falls back to `netstat`; when neither exists the command
 * still exits cleanly and [parse] returns an empty list, which the caller shows as "no
 * tooling available" rather than "nothing is listening".
 *
 * `ss` and `netstat` lay their columns out differently (ss puts a `State` word in column 2,
 * netstat puts the numeric `Recv-Q` there), so the parser keys off that rather than assuming
 * one tool's positions.
 */
object NetworkPorts {

    data class Port(
        val protocol: String,
        val address: String,
        val port: Int,
        val process: String,
    ) {
        val isLoopback: Boolean get() = address.startsWith("127.") || address == "::1"
        val isAllInterfaces: Boolean get() = address == "0.0.0.0" || address == "::" || address == "*"
    }

    fun command(): String =
        "LC_ALL=C (ss -H -tulpn 2>/dev/null || netstat -tulpn 2>/dev/null)"

    fun parse(output: String): List<Port> = output.lineSequence()
        .mapNotNull { parseLine(it) }
        .distinctBy { Triple(it.protocol, it.address, it.port) }
        .toList()

    private fun parseLine(line: String): Port? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val proto = trimmed.substringBefore(' ').lowercase()
        if (proto !in KNOWN_PROTOCOLS) return null
        val cols = trimmed.split(Regex("\\s+"))
        if (cols.size < 4) return null

        // netstat: [proto, recv-q, send-q, local, peer, state, pid/prog]
        // ss:      [proto, state, recv-q, send-q, local, peer, process]
        val netstatLayout = cols.getOrNull(1)?.toIntOrNull() != null
        val localIndex = if (netstatLayout) 3 else 4
        val local = cols.getOrNull(localIndex) ?: return null
        val (addr, port) = splitAddress(local) ?: return null
        return Port(protocol = proto, address = addr, port = port, process = extractProcess(trimmed))
    }

    /** Handles `0.0.0.0:22`, `[::]:22`, `*:22` and `:::22`. */
    private fun splitAddress(local: String): Pair<String, Int>? {
        val colon = local.lastIndexOf(':')
        if (colon <= 0) return null
        val port = local.substring(colon + 1).toIntOrNull() ?: return null
        var addr = local.substring(0, colon).trim('[', ']')
        if (addr.isEmpty()) addr = "*"
        return addr to port
    }

    private fun extractProcess(line: String): String {
        // ss: users:(("sshd",pid=1,fd=3))
        val ss = Regex("users:\\(\\(\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        if (ss != null) return ss
        // netstat: ... LISTEN 1234/sshd
        val netstat = Regex("\\s\\d+/(\\S+)\\s*$").find(line)?.groupValues?.get(1)
        return netstat ?: ""
    }

    private val KNOWN_PROTOCOLS = setOf("tcp", "udp", "tcp6", "udp6")
}
