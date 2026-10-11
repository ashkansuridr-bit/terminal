package app.terminalssh.secure.model

/**
 * Optional session status notifications (F020).
 *
 * A dropped production session is worth a notification; a reconnect that succeeded three
 * seconds later usually is not. This models *what* is notified and *when*, and — critically —
 * what is allowed into the payload. A notification is rendered by the system UI, may appear on
 * a lock screen, and is logged by the OS, so it carries only the host's display name and
 * address. Never a username, a command, output, or anything vault-backed.
 *
 * Title/body text is resolved by the UI from the returned keys, so nothing here is a
 * user-facing English literal.
 */
object SessionNotification {

    enum class Event { DISCONNECTED, RECONNECTING, RECONNECTED, RECONNECT_FAILED }

    data class Policy(
        val notifyOnDisconnect: Boolean = true,
        val notifyOnReconnectFailure: Boolean = true,
        val notifyOnReconnectSuccess: Boolean = false,
    )

    /** The only data allowed into a notification. Note the absence of username/credentials. */
    data class Content(
        val event: Event,
        val hostLabel: String,
        val hostAddress: String,
        val attempt: Int?,
        val tag: String,
    )

    fun build(event: Event, host: HostProfile, attempt: Int? = null): Content = Content(
        event = event,
        hostLabel = host.displayName,
        hostAddress = host.host,
        attempt = attempt,
        tag = tagFor(host.id),
    )

    /** A stable tag so a later update replaces the same notification instead of stacking. */
    fun tagFor(hostId: String): String = "session:$hostId"

    fun titleKey(event: Event): String = "notif.title.${event.name.lowercase()}"

    fun bodyKey(event: Event): String = "notif.body.${event.name.lowercase()}"

    /**
     * @param foreground true when the app is on screen; a reconnect that succeeds while the
     *   user is watching the terminal does not need a notification.
     */
    fun shouldNotify(event: Event, enabled: Boolean, foreground: Boolean, policy: Policy = Policy()): Boolean {
        if (!enabled) return false
        return when (event) {
            Event.DISCONNECTED -> policy.notifyOnDisconnect
            Event.RECONNECT_FAILED -> policy.notifyOnReconnectFailure
            Event.RECONNECTED -> policy.notifyOnReconnectSuccess && !foreground
            Event.RECONNECTING -> false
        }
    }
}
