package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class TransferRecoveryQueueTest {
    private fun transfer(id: String) = Transfer(id, TransferDirection.DOWNLOAD, "/file/$id", "content://local/$id", id)

    @Test fun recoveryNeverEmitsQueuedWorkAndKeepsOffsets() {
        val queue = TransferQueue()
        queue.importPaused((1..30).map { transfer("item-$it").copy(state = TransferState.RUNNING, transferredBytes = 12) })
        assertEquals(30, queue.transfers.value.size)
        assertEquals(setOf(TransferState.PAUSED), queue.transfers.value.map { it.state }.toSet())
        assertEquals(setOf(12L), queue.transfers.value.map { it.transferredBytes }.toSet())
        assertNull(queue.nextToStart())
        queue.importPaused(listOf(transfer("item-1")))
        assertEquals(30, queue.transfers.value.size)
    }

    @Test fun conflictingTransferIdentityFailsWithoutChangingQueue() {
        val queue = TransferQueue()
        queue.importPaused(listOf(transfer("item")))
        assertFailsWith<IllegalArgumentException> {
            queue.importPaused(listOf(transfer("item").copy(remotePath = "/other"), transfer("new")))
        }
        assertEquals(listOf("item"), queue.transfers.value.map { it.id })
    }
}
