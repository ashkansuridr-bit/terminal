package app.terminalssh.secure.sftp

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide view of every live [SftpController], so something outside the UI can act
 * on transfers.
 *
 * The foreground-service notification is the reason this exists. Its Cancel button runs
 * in the service, which has no route to the ViewModel that owns the controllers — so
 * without a shared registry the button could only ever be decorative. Transfers also
 * outlive the Files tab's composition, so the registry is keyed by session and cleaned up
 * in `AppViewModel.closeSession`.
 */
object TransferCoordinator {

    private val controllers = ConcurrentHashMap<String, SftpController>()

    fun register(sessionId: String, controller: SftpController) {
        controllers[sessionId] = controller
    }

    fun unregister(sessionId: String) {
        controllers.remove(sessionId)
    }

    /** Transfers currently moving bytes, across every session. */
    fun activeCount(): Int = controllers.values.sumOf { it.queue.active.size }

    /** Progress across every running transfer, for a single notification bar. */
    fun aggregateProgress(): Pair<Long, Long> {
        var done = 0L
        var total = 0L
        for (controller in controllers.values) {
            for (t in controller.queue.active) {
                done += t.transferredBytes
                if (t.totalBytes > 0) total += t.totalBytes
            }
        }
        return done to total
    }

    /**
     * Stops everything in flight. Used by the notification's Cancel action, which is the
     * only way to stop a transfer without bringing the app to the foreground.
     */
    fun cancelAll() {
        for (controller in controllers.values) {
            for (t in controller.queue.pending) {
                runCatching { controller.cancel(t.id) }
            }
        }
    }
}
