package app.terminalssh.secure.terminal

/**
 * Broadcasting one input to several terminals (F034).
 *
 * Typing the same command into six production boxes at once is powerful and exactly the kind
 * of thing that should be hard to do by accident. So broadcasting is only offered for
 * sessions that are actually connected and not marked read-only, the plan lists what was
 * excluded and why, and anything multi-line requires an explicit confirmation because a pasted
 * `\n` turns one command into many on every target.
 *
 * Pure planning only: the caller writes the bytes to the chosen channels.
 */
object BroadcastInput {

    data class Target(
        val sessionId: String,
        val label: String,
        val hostAddress: String,
        val connected: Boolean,
        val readOnly: Boolean = false,
    )

    data class Excluded(val target: Target, val reason: String)

    data class Plan(val targets: List<Target>, val excluded: List<Excluded>) {
        val canSend: Boolean get() = targets.isNotEmpty()
        val isBroadcast: Boolean get() = targets.size > 1

        /** One-line, secret-free summary for the confirmation dialog. */
        fun summary(): String =
            "sending to ${targets.size} terminal(s): " + targets.joinToString(", ") { it.label }
    }

    /**
     * Keeps only selected sessions that can receive input. Disconnected sessions are excluded
     * (writing to them would silently buffer or throw) and read-only sessions are excluded
     * because broadcasting to a log tail is never intended.
     */
    fun plan(all: List<Target>, selectedIds: Set<String>): Plan {
        val selected = all.filter { it.sessionId in selectedIds }
        val targets = ArrayList<Target>()
        val excluded = ArrayList<Excluded>()
        for (target in selected) {
            when {
                !target.connected -> excluded += Excluded(target, "not connected")
                target.readOnly -> excluded += Excluded(target, "read-only session")
                else -> targets += target
            }
        }
        return Plan(targets, excluded)
    }

    /** Any newline matters: a pasted multi-line block runs as separate commands per target. */
    fun requiresConfirmation(input: String, plan: Plan): Boolean =
        plan.isBroadcast && (input.contains('\n') || input.contains('\r') || input.length > LONG_INPUT)

    fun encode(input: String): ByteArray? = if (input.isEmpty()) null else input.toByteArray(Charsets.UTF_8)

    private const val LONG_INPUT = 512
}
