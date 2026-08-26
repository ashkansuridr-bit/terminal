package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferQueueTest {

    private fun transfer(id: String, remote: String = "/tmp/$id") = Transfer(
        id = id,
        direction = TransferDirection.DOWNLOAD,
        remotePath = remote,
        localUri = "content://downloads/$id",
        displayName = id,
        totalBytes = 1_000L,
    )

    private fun TransferQueue.byId(id: String) = transfers.value.first { it.id == id }

    @Test fun startsTransfersInInsertionOrder() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.enqueue(transfer("b"))
        assertEquals("a", queue.nextToStart()?.id)
    }

    @Test fun respectsTheConcurrencyLimit() {
        val queue = TransferQueue(maxConcurrent = 1)
        queue.enqueue(transfer("a"))
        queue.enqueue(transfer("b"))
        queue.markRunning("a")
        assertNull(queue.nextToStart(), "a second transfer started while one was running")

        queue.markCompleted("a")
        assertEquals("b", queue.nextToStart()?.id)
    }

    @Test fun progressNeverWalksBackwards() {
        // A retry restarts JSch's own counter; the bar must not jump backwards.
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markProgress("a", 800L)
        queue.markProgress("a", 200L)
        assertEquals(800L, queue.byId("a").transferredBytes)
    }

    @Test fun completionFillsTheBarEvenWithoutByteReports() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markCompleted("a")
        assertEquals(1_000L, queue.byId("a").transferredBytes)
        assertEquals(1f, queue.byId("a").progress)
    }

    @Test fun unknownSizeReportsNoProgressRatherThanAFakeOne() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a").copy(totalBytes = Transfer.UNKNOWN_SIZE))
        queue.markProgress("a", 500L)
        assertNull(queue.byId("a").progress)
    }

    @Test fun transientFailureRequeuesUntilTheAttemptLimit() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        repeat(Transfer.MAX_ATTEMPTS) {
            queue.markRunning("a")
            queue.fail("a", TransferErrorKind.CONNECTION_LOST)
        }
        // The final attempt exhausts the budget and stops.
        assertEquals(TransferState.FAILED, queue.byId("a").state)
        assertEquals(Transfer.MAX_ATTEMPTS, queue.byId("a").attempts)
    }

    @Test fun transientFailureBelowTheLimitIsRetried() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.fail("a", TransferErrorKind.CONNECTION_LOST)
        assertEquals(TransferState.QUEUED, queue.byId("a").state)
    }

    @Test fun permanentFailureIsNotRetried() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.fail("a", TransferErrorKind.PERMISSION_DENIED)
        assertEquals(TransferState.FAILED, queue.byId("a").state)
        assertEquals(1, queue.byId("a").attempts)
    }

    @Test fun retryResumesRatherThanRestarting() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.markProgress("a", 600L)
        queue.fail("a", TransferErrorKind.CONNECTION_LOST)
        assertEquals(600L, queue.byId("a").transferredBytes)
    }

    @Test fun droppedConnectionRequeuesEverythingInFlight() {
        val queue = TransferQueue(maxConcurrent = 2)
        queue.enqueue(transfer("a"))
        queue.enqueue(transfer("b"))
        queue.markRunning("a")
        queue.onConnectionLost()

        assertEquals(TransferState.QUEUED, queue.byId("a").state)
        assertEquals(TransferErrorKind.CONNECTION_LOST, queue.byId("a").errorKind)
        // An untouched queued transfer is left exactly as it was.
        assertEquals(TransferState.QUEUED, queue.byId("b").state)
        assertNull(queue.byId("b").errorKind)
    }

    @Test fun droppedConnectionFailsTransfersThatExhaustedTheirBudget() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        repeat(Transfer.MAX_ATTEMPTS) { queue.markRunning("a") }
        queue.onConnectionLost()
        assertEquals(TransferState.FAILED, queue.byId("a").state)
    }

    @Test fun pauseAndResumeMoveThroughTheRightStates() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.pause("a")
        assertEquals(TransferState.PAUSED, queue.byId("a").state)
        queue.resume("a")
        assertEquals(TransferState.QUEUED, queue.byId("a").state)
    }

    @Test fun pausingSomethingNotRunningDoesNothing() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.pause("a")
        assertEquals(TransferState.QUEUED, queue.byId("a").state)
    }

    @Test fun resumingClearsThePreviousError() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.fail("a", TransferErrorKind.PERMISSION_DENIED)
        queue.resume("a")
        assertNull(queue.byId("a").errorKind)
    }

    @Test fun completedTransfersCannotBeCancelledOrRestarted() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markCompleted("a")
        queue.cancel("a")
        assertEquals(TransferState.COMPLETED, queue.byId("a").state)
    }

    @Test fun cancelledTransfersAreNeverStarted() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.enqueue(transfer("b"))
        queue.cancel("a")
        assertEquals("b", queue.nextToStart()?.id)
    }

    @Test fun clearFinishedKeepsOnlyLiveWork() {
        val queue = TransferQueue()
        queue.enqueue(transfer("done"))
        queue.enqueue(transfer("cancelled"))
        queue.enqueue(transfer("failed"))
        queue.enqueue(transfer("queued"))
        queue.markCompleted("done")
        queue.cancel("cancelled")
        queue.markRunning("failed")
        queue.fail("failed", TransferErrorKind.PERMISSION_DENIED)

        queue.clearFinished()

        val remaining = queue.transfers.value.map { it.id }.toSet()
        // A failed transfer stays: the user may still want to retry it.
        assertEquals(setOf("failed", "queued"), remaining)
    }

    @Test fun pendingExcludesFinishedWork() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.enqueue(transfer("b"))
        queue.markCompleted("a")
        assertEquals(listOf("b"), queue.pending.map { it.id })
    }

    @Test fun resetProgressZeroesTheCounter() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markProgress("a", 500L)
        queue.resetProgress("a")
        assertEquals(0L, queue.byId("a").transferredBytes)
    }

    @Test fun resetProgressLeavesStateAndErrorAlone() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.fail("a", TransferErrorKind.CONNECTION_LOST)
        queue.resetProgress("a")
        assertEquals(TransferState.QUEUED, queue.byId("a").state)
        assertEquals(TransferErrorKind.CONNECTION_LOST, queue.byId("a").errorKind)
    }

    @Test fun retriableKindsAreExactlyTheTransientOnes() {
        assertTrue(TransferErrorKind.CONNECTION_LOST.isRetriable)
        listOf(
            TransferErrorKind.PERMISSION_DENIED,
            TransferErrorKind.NOT_FOUND,
            TransferErrorKind.OUT_OF_SPACE,
            TransferErrorKind.LOCAL_UNAVAILABLE,
            TransferErrorKind.UNKNOWN,
        ).forEach { assertTrue(!it.isRetriable, "$it must not auto-retry") }
    }
}
