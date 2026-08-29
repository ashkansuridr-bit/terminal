package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransferPriorityTest {

    private var clock = 0L
    private fun transfer(id: String) = Transfer(
        id = id,
        direction = TransferDirection.DOWNLOAD,
        remotePath = "/tmp/$id",
        localUri = "content://downloads/$id",
        displayName = id,
        totalBytes = 1_000L,
        enqueuedAt = ++clock,
    )

    @Test fun withoutReorderingInsertionOrderIsUnchanged() {
        val q = TransferQueue()
        listOf("a", "b", "c").forEach { q.enqueue(transfer(it)) }
        assertEquals("a", q.nextToStart()?.id)
    }

    @Test fun aPromotedTransferJumpsTheQueue() {
        val q = TransferQueue()
        listOf("a", "b", "c").forEach { q.enqueue(transfer(it)) }

        q.promote("c")

        assertEquals("c", q.nextToStart()?.id, "the promoted file must run next")
    }

    @Test fun promotingTwiceKeepsTheMostRecentPromotionFirst() {
        val q = TransferQueue()
        listOf("a", "b", "c").forEach { q.enqueue(transfer(it)) }
        q.promote("c")
        q.promote("b")
        assertEquals("b", q.nextToStart()?.id)
    }

    @Test fun aDemotedTransferGoesLast() {
        val q = TransferQueue(maxConcurrent = 1)
        listOf("a", "b").forEach { q.enqueue(transfer(it)) }

        q.demote("a")

        assertEquals("b", q.nextToStart()?.id)
    }

    @Test fun reorderingDoesNotDisturbSomethingAlreadyRunning() {
        // Promotion must not interrupt live I/O; it only changes what starts next.
        val q = TransferQueue(maxConcurrent = 1)
        listOf("a", "b").forEach { q.enqueue(transfer(it)) }
        q.markRunning("a")

        q.promote("b")

        assertNull(q.nextToStart(), "the concurrency limit still holds")
        assertEquals(TransferState.RUNNING, q.transfers.value.first { it.id == "a" }.state)
        assertEquals(0, q.transfers.value.first { it.id == "a" }.priority, "a running transfer is untouched")
    }

    // The persistence round-trip lives in androidTest: org.json is a stub in JVM unit
    // tests, so a green result here would prove nothing. See TransferQueuePersistenceTest.
}
