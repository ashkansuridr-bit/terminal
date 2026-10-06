package app.terminalssh.secure.sftp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferSchedulerTest {
    private fun transfer(id: Int) = Transfer(
        id = "$id", direction = TransferDirection.DOWNLOAD,
        remotePath = "/$id", localUri = "local:$id", displayName = "$id", totalBytes = 100,
    )

    @Test fun thirtyFilesDrainWithThreeSlotsWithoutExternalWake() = runBlocking {
        val queue = TransferQueue(3)
        var peak = 0
        var workers = 0
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false) {
            workers++
            peak = maxOf(peak, workers)
            // Longer than the old pump's 50 ms recheck: reproduces the saturated exit.
            delay(75)
            workers--
            queue.markCompleted(it.id)
        }
        try {
            repeat(30) { queue.enqueue(transfer(it)) }
            withTimeout(5_000) { queue.transfers.first { it.size == 30 && it.all { t -> t.state == TransferState.COMPLETED } } }
            assertEquals(3, peak)
            assertEquals(30, queue.transfers.value.size)
        } finally { scheduler.close() }
    }

    @Test fun retryAndFailureFreeSlots() = runBlocking {
        val queue = TransferQueue(1)
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false, retryDelayMillis = { 20 }) {
            when {
                it.id == "0" && it.attempts == 1 -> queue.fail(it.id, TransferErrorKind.CONNECTION_LOST)
                it.id == "1" -> throw IllegalStateException("worker failure")
                else -> queue.markCompleted(it.id)
            }
        }
        try {
            repeat(3) { queue.enqueue(transfer(it)) }
            withTimeout(2_000) { queue.transfers.first { it.size == 3 && it.none { t -> t.state == TransferState.RUNNING || t.state == TransferState.QUEUED } } }
            assertEquals(2, queue.transfers.value[0].attempts)
            assertEquals(TransferState.FAILED, queue.transfers.value[1].state)
            assertEquals(TransferState.COMPLETED, queue.transfers.value[2].state)
        } finally { scheduler.close() }
    }

    @Test fun networkWakeStartsHeldQueue() = runBlocking {
        val queue = TransferQueue()
        var allowed = false
        val scheduler = TransferScheduler(this, queue, { allowed }) { queue.markCompleted(it.id) }
        try {
            queue.enqueue(transfer(1))
            delay(25)
            assertEquals(TransferState.QUEUED, queue.transfers.value.single().state)
            allowed = true
            scheduler.wake()
            withTimeout(2_000) { queue.transfers.first { it.single().state == TransferState.COMPLETED } }
        } finally { scheduler.close() }
    }

    @Test fun pauseResumeWaitsForPreviousWorkerToExit() = runBlocking {
        val queue = TransferQueue(3)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var workers = 0
        var peak = 0
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false) {
            workers++
            peak = maxOf(peak, workers)
            if (it.attempts == 1) { started.complete(Unit); release.await() }
            else queue.markCompleted(it.id)
            workers--
        }
        try {
            queue.enqueue(transfer(1))
            withTimeout(2_000) { started.await() }
            queue.pause("1")
            queue.resume("1")
            delay(25)
            assertEquals(1, queue.transfers.value.single().attempts)
            release.complete(Unit)
            withTimeout(2_000) { queue.transfers.first { it.single().state == TransferState.COMPLETED } }
            assertEquals(1, peak)
            assertTrue(queue.transfers.value.single().attempts == 2)
        } finally { scheduler.close() }
    }
    @Test fun retryBackoffWakesWithoutExternalInteraction() = runBlocking {
        val queue = TransferQueue(1)
        val firstFinished = CompletableDeferred<Unit>()
        var secondStartedAt = 0L
        var failedAt = 0L
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false, retryDelayMillis = { 100 }) {
            if (it.attempts == 1) {
                failedAt = System.nanoTime()
                queue.fail(it.id, TransferErrorKind.CONNECTION_LOST)
                firstFinished.complete(Unit)
            } else {
                secondStartedAt = System.nanoTime()
                queue.markCompleted(it.id)
            }
        }
        try {
            queue.enqueue(transfer(1))
            withTimeout(2_000) { firstFinished.await() }
            delay(30)
            assertEquals(1, queue.transfers.value.single().attempts)
            withTimeout(2_000) { queue.transfers.first { it.single().state == TransferState.COMPLETED } }
            assertTrue((secondStartedAt - failedAt) / 1_000_000 >= 100)
        } finally { scheduler.close() }
    }

    @Test fun concurrencyIncreaseStartsQueuedWorkWithoutRowChange() = runBlocking {
        val queue = TransferQueue(1)
        val release = CompletableDeferred<Unit>()
        val twoStarted = CompletableDeferred<Unit>()
        var workers = 0
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false) {
            workers++
            if (workers == 2) twoStarted.complete(Unit)
            release.await()
            queue.markCompleted(it.id)
        }
        try {
            repeat(2) { queue.enqueue(transfer(it)) }
            withTimeout(2_000) { queue.transfers.first { it.count { t -> t.state == TransferState.RUNNING } == 1 } }
            queue.setConcurrency(2)
            withTimeout(2_000) { twoStarted.await() }
            release.complete(Unit)
            withTimeout(2_000) { queue.transfers.first { it.all { t -> t.state == TransferState.COMPLETED } } }
        } finally { scheduler.close() }
    }

    @Test fun pausedWorkerStillOccupiesConcurrencySlot() = runBlocking {
        val queue = TransferQueue(1)
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var peak = 0
        var workers = 0
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false) {
            workers++
            peak = maxOf(peak, workers)
            if (it.id == "0") {
                firstStarted.complete(Unit)
                release.await()
            } else queue.markCompleted(it.id)
            workers--
        }
        try {
            repeat(2) { queue.enqueue(transfer(it)) }
            withTimeout(2_000) { firstStarted.await() }
            queue.pause("0")
            delay(25)
            assertEquals(TransferState.QUEUED, queue.transfers.value[1].state)
            release.complete(Unit)
            withTimeout(2_000) { queue.transfers.first { it[1].state == TransferState.COMPLETED } }
            assertEquals(TransferState.PAUSED, queue.transfers.value[0].state)
            assertEquals(1, peak)
        } finally { scheduler.close() }
    }

    @Test fun closeAndJoinWaitsForWorkerFinally() = runBlocking {
        val queue = TransferQueue(1)
        val started = CompletableDeferred<Unit>()
        var stopped = false
        val scheduler = TransferScheduler(this, queue, adaptConcurrency = false) {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally { stopped = true }
        }
        queue.enqueue(transfer(1))
        withTimeout(2_000) { started.await() }
        withTimeout(2_000) { scheduler.closeAndJoin() }
        assertTrue(stopped)
    }

}
