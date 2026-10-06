package app.terminalssh.secure.ui

/** Foreground authentication ownership; no persisted or restored unlocked state. */
internal class AppLockGate(required: Boolean) {
    var locked: Boolean = required
        private set
    private var required = required
    private var foreground = false
    private var generation = 0L
    private var promptTicket: Long? = null

    fun start(required: Boolean) {
        if (required && !this.required) locked = true
        this.required = required
        if (!required) locked = false
        foreground = true
    }

    fun stop(preserveSystemPrompt: Boolean = false) {
        foreground = false
        if (!preserveSystemPrompt) {
            generation++
            promptTicket = null
        }
        if (required) locked = true
    }

    fun beginPrompt(): Long? {
        if (!foreground || !required || !locked || promptTicket != null) return null
        return (++generation).also { promptTicket = it }
    }

    fun authenticationResult(ticket: Long, succeeded: Boolean) {
        if (ticket != promptTicket) return
        promptTicket = null
        if (succeeded && foreground) locked = false
    }
}
