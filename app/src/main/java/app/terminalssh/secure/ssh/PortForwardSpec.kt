package app.terminalssh.secure.ssh

/**
 * Validation and bookkeeping for the port-forwarding dashboard (F036).
 *
 * A forwarding rule is a small thing that fails in confusing ways: two rules on the same local
 * port silently fight, a privileged port fails on a phone with no explanation, and a host with a
 * stray space fails at connection time rather than when the user typed it. Checking here means
 * the dashboard can refuse a bad rule at the moment it is added, with a reason the UI can
 * localize, instead of surfacing an opaque failure later.
 *
 * Only structure is decided here; the SSH layer still owns the actual `-L`/`-R` binding.
 */
object PortForwardSpec {

    enum class Kind {
        LOCAL,
        REMOTE,
        ;

        val letter: String get() = if (this == LOCAL) "L" else "R"
    }

    data class Spec(
        val kind: Kind,
        val bindPort: Int,
        val host: String,
        val port: Int,
    ) {
        /** Stable identity: one binding per (kind, local port), the same key ssh uses for -L/-R. */
        val key: String get() = "${kind.letter}:$bindPort"
    }

    /** A reason the rule cannot be used, as a localization key. */
    data class Problem(val reasonKey: String)

    /** Null when the rule is usable; otherwise the first problem found. */
    fun validate(spec: Spec): Problem? = when {
        spec.bindPort !in 1..65535 -> Problem("forward.error.bind_port")
        spec.port !in 1..65535 -> Problem("forward.error.target_port")
        !isValidHost(spec.host) -> Problem("forward.error.host")
        else -> null
    }

    fun isValidHost(host: String): Boolean {
        val value = host.trim()
        return value.isNotEmpty() &&
            value.length <= 255 &&
            value.none { it.isWhitespace() || it.isISOControl() || it == '/' }
    }

    /**
     * A rule already using this bind port for the same kind. Re-adding the identical rule is not
     * a conflict; a different target on the same key is.
     */
    fun conflicts(existing: List<Spec>, candidate: Spec): Spec? =
        existing.firstOrNull { it.key == candidate.key && it != candidate }

    /** Local ports below 1024 need root on the server side of a remote forward, and are worth a hint. */
    fun isPrivileged(bindPort: Int): Boolean = bindPort in 1..1023

    fun countByKind(specs: List<Spec>): Map<Kind, Int> =
        Kind.entries.associateWith { kind -> specs.count { it.kind == kind } }.filterValues { it > 0 }

    fun keySet(specs: List<Spec>): Set<String> = specs.map { it.key }.toSet()
}
