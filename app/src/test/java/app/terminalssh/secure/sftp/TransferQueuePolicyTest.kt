package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferQueuePolicyTest {

    private fun item(id: String, priority: TransferQueuePolicy.Priority = TransferQueuePolicy.Priority.NORMAL, at: Long = 0L, size: Long = 0L) =
        TransferQueuePolicy.Item(id, priority, at, sizeBytes = size)

    private val settings = TransferQueuePolicy.Settings(maxConcurrent = 2, maxAttempts = 3, baseBackoffMs = 1_000, maxBackoffMs = 30_000)

    @Test fun highPriorityRunsFirst() {
        val ordered = TransferQueuePolicy.order(
            listOf(item("a"), item("b", TransferQueuePolicy.Priority.HIGH), item("c", TransferQueuePolicy.Priority.LOW)),
        )
        assertEquals(listOf("b", "a", "c"), ordered.map { it.value })
    }

    @Test fun withinAPriorityOldestComesFirst() {
        val ordered = TransferQueuePolicy.order(
            listOf(item("new", at = 20), item("old", at = 10)),
        )
        assertEquals(listOf("old", "new"), ordered.map { it.value })
    }

    @Test fun smallerTransfersTieBreakSoCountsMove() {
        val ordered = TransferQueuePolicy.order(
            listOf(item("big", at = 10, size = 10_000), item("small", at = 10, size = 10)),
        )
        assertEquals(listOf("small", "big"), ordered.map { it.value })
    }

    @Test fun nextBatchRespectsTheConcurrencyCap() {
        val items = listOf(item("a"), item("b"), item("c"), item("d"))
        assertEquals(2, TransferQueuePolicy.nextBatch(items, activeCount = 0, settings = settings).size)
        assertEquals(1, TransferQueuePolicy.nextBatch(items, activeCount = 1, settings = settings).size)
        assertTrue(TransferQueuePolicy.nextBatch(items, activeCount = 2, settings = settings).isEmpty())
        assertTrue(TransferQueuePolicy.nextBatch(items, activeCount = 5, settings = settings).isEmpty())
    }

    @Test fun nextBatchPicksTheHighestPriorityWork() {
        val items = listOf(
            item("a", TransferQueuePolicy.Priority.LOW),
            item("b", TransferQueuePolicy.Priority.HIGH),
        )
        assertEquals(listOf("b"), TransferQueuePolicy.nextBatch(items, 1, settings).map { it.value })
    }

    @Test fun backoffGrowsExponentiallyAndCaps() {
        assertEquals(1_000L, TransferQueuePolicy.backoffMs(1, settings))
        assertEquals(2_000L, TransferQueuePolicy.backoffMs(2, settings))
        assertEquals(4_000L, TransferQueuePolicy.backoffMs(3, settings))
        assertEquals(30_000L, TransferQueuePolicy.backoffMs(20, settings))
    }

    @Test fun backoffDoesNotOverflowOnAHugeAttemptCount() {
        assertTrue(TransferQueuePolicy.backoffMs(1_000, settings) > 0)
    }

    @Test fun retriesStopAtMaxAttempts() {
        assertTrue(TransferQueuePolicy.shouldRetry(1, settings))
        assertTrue(TransferQueuePolicy.shouldRetry(2, settings))
        assertFalse(TransferQueuePolicy.shouldRetry(3, settings))
    }

    @Test fun permanentServerErrorsAreNotRetried() {
        assertTrue(TransferQueuePolicy.isPermanentFailure(2)) // no such file
        assertTrue(TransferQueuePolicy.isPermanentFailure(3)) // permission denied
        assertTrue(TransferQueuePolicy.isPermanentFailure(8)) // unsupported
        assertFalse(TransferQueuePolicy.isPermanentFailure(4)) // generic: worth a retry
        assertFalse(TransferQueuePolicy.isPermanentFailure(7)) // connection lost: worth a retry
    }
}
