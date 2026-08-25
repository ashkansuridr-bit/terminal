package app.terminalssh.secure.agents

/**
 * Where an agent's API key is stored, and which key applies to a given connection.
 *
 * Keys are scoped: a key can be global to an agent, or specific to one server. A
 * development server and a production one should not have to share a credential, and
 * revoking one should not break the other.
 */
object AgentKeyRef {

    /** The vault reference for a key that applies to every server. */
    fun global(agent: CodingAgent): String = "agent-key:${agent.id}:*"

    /** The vault reference for a key that applies only to [hostId]. */
    fun forHost(agent: CodingAgent, hostId: String): String = "agent-key:${agent.id}:$hostId"

    /**
     * References to try, most specific first.
     *
     * A per-host key wins over the global one, so overriding a single server never means
     * re-entering the key everywhere else.
     */
    fun resolutionOrder(agent: CodingAgent, hostId: String?): List<String> = buildList {
        if (!hostId.isNullOrBlank()) add(forHost(agent, hostId))
        add(global(agent))
    }

    /** True when [reference] belongs to [agent], used when clearing an agent's keys. */
    fun belongsTo(reference: String, agent: CodingAgent): Boolean =
        reference.startsWith("agent-key:${agent.id}:")

    /** True when [reference] is scoped to a specific host rather than global. */
    fun isHostScoped(reference: String): Boolean =
        reference.startsWith("agent-key:") && !reference.endsWith(":*")

    /** The host id a reference is scoped to, or null when it is global or malformed. */
    fun hostIdOf(reference: String): String? {
        if (!reference.startsWith("agent-key:")) return null
        val hostPart = reference.substringAfterLast(':')
        return hostPart.takeIf { it.isNotBlank() && it != "*" }
    }
}
