package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferBatchControlTest {

    private fun transfer(
        id: String,
        state: TransferState,
        errorKind: TransferErrorKind? = null,
        attempts: Int = 0,
    ) = Transfer(
        id = id,
        direction = TransferDirection.UPLOAD,
        remotePath = "/srv/$id",
        localUri = "content://local/$id",
        displayName = id,
        state = state,
        errorKind = errorKind,
        attempts = attempts,
    )

    @Test fun pauseOnlyTouchesRunningTransfers() {
        val items = listOf(
            transfer("a", TransferState.RUNNING),
            transfer("b", TransferState.QUEUED),
            transfer("c", TransferState.PAUSED),
        )
        assertEquals(listOf("a"), TransferBatchControl.idsFor(items, TransferBatchControl.Action.PAUSE))
    }

    @Test fun resumeTouchesPausedAndFailedTransfers() {
        val items = listOf(
            transfer("a", TransferState.PAUSED),
            transfer("b", TransferState.FAILED),
            transfer("c", TransferState.RUNNING),
            transfer("d", TransferState.COMPLETED),
        )
        assertEquals(
            listOf("a", "b"),
            TransferBatchControl.idsFor(items, TransferBatchControl.Action.RESUME),
        )
    }

    @Test fun retrySkipsIntegrityMismatchAndExhaustedAttempts() {
        val items = listOf(
            transfer("a", TransferState.FAILED, TransferErrorKind.CONNECTION_LOST, attempts = 1),
            transfer("b", TransferState.FAILED, TransferErrorKind.INTEGRITY_MISMATCH, attempts = 1),
            transfer("c", TransferState.FAILED, TransferErrorKind.CONNECTION_LOST, attempts = Transfer.MAX_ATTEMPTS),
            transfer("d", TransferState.COMPLETED),
        )
        assertEquals(listOf("a"), TransferBatchControl.idsFor(items, TransferBatchControl.Action.RETRY))
    }

    @Test fun retryAllowsAPermissionFailureWhenTheUserAsks() {
        assertTrue(
            TransferBatchControl.canRetry(
                transfer("a", TransferState.FAILED, TransferErrorKind.PERMISSION_DENIED, attempts = 1),
            ),
        )
    }

    @Test fun cancelTouchesEverythingNotTerminal() {
        val items = listOf(
            transfer("a", TransferState.QUEUED),
            transfer("b", TransferState.RUNNING),
            transfer("c", TransferState.FAILED),
            transfer("d", TransferState.COMPLETED),
            transfer("e", TransferState.CANCELLED),
        )
        assertEquals(
            listOf("a", "b", "c"),
            TransferBatchControl.idsFor(items, TransferBatchControl.Action.CANCEL),
        )
    }

    @Test fun countByStateOmitsEmptyStates() {
        val items = listOf(
            transfer("a", TransferState.RUNNING),
            transfer("b", TransferState.RUNNING),
            transfer("c", TransferState.FAILED),
        )
        val counts = TransferBatchControl.countByState(items)
        assertEquals(2, counts[TransferState.RUNNING])
        assertEquals(1, counts[TransferState.FAILED])
        assertFalse(counts.containsKey(TransferState.COMPLETED))
    }

    @Test fun settledRequiresANonEmptyAllTerminalBatch() {
        assertFalse(TransferBatchControl.isSettled(emptyList()))
        assertFalse(TransferBatchControl.isSettled(listOf(transfer("a", TransferState.RUNNING))))
        assertTrue(
            TransferBatchControl.isSettled(
                listOf(transfer("a", TransferState.COMPLETED), transfer("b", TransferState.CANCELLED)),
            ),
        )
        // FAILED is not terminal, so the batch is not settled.
        assertFalse(
            TransferBatchControl.isSettled(
                listOf(transfer("a", TransferState.COMPLETED), transfer("b", TransferState.FAILED)),
            ),
        )
    }

    @Test fun hasFailuresDetectsAnyFailedItem() {
        assertFalse(TransferBatchControl.hasFailures(listOf(transfer("a", TransferState.COMPLETED))))
        assertTrue(TransferBatchControl.hasFailures(listOf(transfer("a", TransferState.FAILED))))
    }

    @Test fun availableActionsOnlyListsUsefulButtons() {
        assertEquals(emptyList(), TransferBatchControl.availableActions(emptyList()))

        val items = listOf(
            transfer("a", TransferState.RUNNING),
            transfer("b", TransferState.FAILED, TransferErrorKind.CONNECTION_LOST, attempts = 1),
            transfer("c", TransferState.COMPLETED),
        )
        // RUNNING offers pause + cancel; FAILED offers resume + retry + cancel; COMPLETED offers
        // nothing; so every action has at least one target here.
        assertEquals(
            listOf(
                TransferBatchControl.Action.PAUSE,
                TransferBatchControl.Action.RESUME,
                TransferBatchControl.Action.RETRY,
                TransferBatchControl.Action.CANCEL,
            ),
            TransferBatchControl.availableActions(items),
        )
    }

    @Test fun aFinishedBatchOffersNoBatchActions() {
        val items = listOf(transfer("a", TransferState.COMPLETED), transfer("b", TransferState.CANCELLED))
        assertEquals(emptyList(), TransferBatchControl.availableActions(items))
    }

    @Test fun anIntegrityMismatchLeavesRetryUnavailable() {
        val items = listOf(transfer("a", TransferState.FAILED, TransferErrorKind.INTEGRITY_MISMATCH, attempts = 1))
        assertFalse(TransferBatchControl.availableActions(items).contains(TransferBatchControl.Action.RETRY))
    }
}
