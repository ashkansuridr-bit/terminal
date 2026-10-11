package app.terminalssh.secure.sftp

/**
 * Batch-level actions over the transfer queue (F023).
 *
 * The per-item flags already exist on [Transfer] (`canPause`, `canResume`, `canCancel`). What
 * was missing is the batch question: given everything in the queue right now, which items may
 * "Pause all", "Resume all", "Retry failed" or "Cancel all" actually touch, and which must be
 * left alone. Answering that here keeps the buttons honest — a button that claims "Retry all"
 * but silently skips the integrity-mismatch item is fine as long as its label says what it did.
 *
 * Pure list arithmetic; the caller performs the I/O.
 */
object TransferBatchControl {

    enum class Action { PAUSE, RESUME, RETRY, CANCEL }

    fun applies(transfer: Transfer, action: Action): Boolean = when (action) {
        Action.PAUSE -> transfer.canPause
        Action.RESUME -> transfer.canResume
        Action.RETRY -> canRetry(transfer)
        Action.CANCEL -> transfer.canCancel
    }

    fun actionable(transfers: List<Transfer>, action: Action): List<Transfer> =
        transfers.filter { applies(it, action) }

    fun idsFor(transfers: List<Transfer>, action: Action): List<String> =
        actionable(transfers, action).map { it.id }

    /**
     * Which batch buttons are worth showing for [transfers], in a stable order. A button that
     * would touch nothing is not offered, so the strip never shows a control that does nothing.
     */
    fun availableActions(transfers: List<Transfer>): List<Action> =
        Action.entries.filter { actionable(transfers, it).isNotEmpty() }

    /**
     * Whether "Retry failed" should offer this item.
     *
     * A corrupt transfer is never retried: re-running it hides the corruption. Everything
     * else that failed is fair game for an explicit user retry, as long as the attempt budget
     * is not spent.
     */
    fun canRetry(transfer: Transfer): Boolean =
        transfer.state == TransferState.FAILED &&
            transfer.attempts < Transfer.MAX_ATTEMPTS &&
            transfer.errorKind != TransferErrorKind.INTEGRITY_MISMATCH

    /** Non-zero counts per state, handy for a "2 running, 3 failed" summary line. */
    fun countByState(transfers: List<Transfer>): Map<TransferState, Int> =
        TransferState.entries
            .associateWith { state -> transfers.count { it.state == state } }
            .filterValues { it > 0 }

    /** True when the batch has work in it and none of it is still moving. */
    fun isSettled(transfers: List<Transfer>): Boolean =
        transfers.isNotEmpty() && transfers.all { it.state.isTerminal }

    fun hasFailures(transfers: List<Transfer>): Boolean =
        transfers.any { it.state == TransferState.FAILED }
}
