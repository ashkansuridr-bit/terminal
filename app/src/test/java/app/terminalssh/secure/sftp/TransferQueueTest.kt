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

    @Test fun rateIsZeroUntilASecondSample() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.markProgress("a", 100L, atMillis = 1_000L)
        assertEquals(0f, queue.byId("a").bytesPerSecond)
    }

    @Test fun rateReflectsBytesOverTimeBetweenTwoSamples() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.markProgress("a", 0L, atMillis = 1_000L)
        queue.markProgress("a", 500L, atMillis = 1_500L)
        // 500 bytes over 500ms = 1000 bytes/sec.
        assertEquals(1000f, queue.byId("a").bytesPerSecond)
    }

    @Test fun retryingResetsTheRateEstimate() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.markProgress("a", 0L, atMillis = 1_000L)
        queue.markProgress("a", 500L, atMillis = 1_500L)
        queue.fail("a", TransferErrorKind.CONNECTION_LOST)
        queue.markRunning("a")
        assertEquals(0f, queue.byId("a").bytesPerSecond)
    }

    @Test fun etaIsNullWithoutASpeedEstimate() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        assertNull(queue.byId("a").etaSeconds)
    }

    @Test fun etaIsNullWhenNotRunning() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        assertNull(queue.byId("a").etaSeconds)
    }

    @Test fun clearFinishedArchivesCompletedAndCancelledIntoHistory() {
        val queue = TransferQueue()
        queue.enqueue(transfer("done"))
        queue.enqueue(transfer("cancelled"))
        queue.enqueue(transfer("failed"))
        queue.markCompleted("done")
        queue.cancel("cancelled")
        queue.markRunning("failed")
        queue.fail("failed", TransferErrorKind.PERMISSION_DENIED)

        queue.clearFinished()

        val archived = queue.history.value.map { it.id }.toSet()
        assertEquals(setOf("done", "cancelled"), archived)
    }

    @Test fun clearFinishedRecordsWhenEachEntryFinished() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markCompleted("a", atMillis = 12_345L)
        queue.clearFinished()
        assertEquals(12_345L, queue.history.value.first { it.id == "a" }.finishedAt)
    }

    @Test fun historyStaysBoundedAndNewestFirst() {
        val queue = TransferQueue()
        repeat(TransferQueue.MAX_HISTORY + 5) { i ->
            queue.enqueue(transfer("t$i"))
            queue.markCompleted("t$i")
            queue.clearFinished()
        }
        assertEquals(TransferQueue.MAX_HISTORY, queue.history.value.size)
        assertEquals("t${TransferQueue.MAX_HISTORY + 4}", queue.history.value.first().id)
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

    // ---- why SftpController must serialize its pump loop (regression pin) ----

    @Test fun nextToStartDoesNotReserveSoTwoPumpLoopsWouldTakeTheSameTransfer() {
        // nextToStart() is a pure read: it reports the first QUEUED transfer but does not
        // claim it. Only markRunning() closes the window. Two pump loops racing between
        // those two calls would both start the same transfer — for an upload that means
        // two writers on one remote path. SftpController therefore creates its pump job
        // under a lock; this test pins the queue-side invariant that makes that necessary.
        val queue = TransferQueue(maxConcurrent = 2)
        queue.enqueue(transfer("a"))

        val firstCaller = queue.nextToStart()
        val secondCaller = queue.nextToStart()
        assertEquals("a", firstCaller?.id)
        assertEquals(firstCaller?.id, secondCaller?.id, "unreserved read must be treated as unsafe by callers")

        queue.markRunning("a")
        assertNull(queue.nextToStart(), "marking RUNNING is what actually claims the transfer")
    }

    @Test fun aTransferBecomingEligibleLaterIsStillOfferedByTheQueue() {
        // The pump loop used to stop after one drain and recurse into pump(), which could
        // never restart it. This is the queue-side half of that fix: work that becomes
        // eligible after the first drain must still be reported by nextToStart().
        val queue = TransferQueue(maxConcurrent = 1)
        queue.enqueue(transfer("a"))
        queue.enqueue(transfer("b"))
        queue.markRunning("a")
        assertNull(queue.nextToStart(), "b is blocked only by the concurrency limit")

        queue.markCompleted("a")
        assertEquals("b", queue.nextToStart()?.id, "b must become eligible once a finishes")
    }
}
