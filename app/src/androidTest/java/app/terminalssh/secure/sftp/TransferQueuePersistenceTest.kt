package app.terminalssh.secure.sftp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Queue persistence on a device, where `org.json` is the real implementation.
 *
 * The JVM unit suite cannot cover this: Android's stubbed `org.json` throws
 * "not mocked" on the first `put`, so a JVM test would either fail for the wrong reason
 * or pass without exercising anything.
 */
@RunWith(AndroidJUnit4::class)
class TransferQueuePersistenceTest {

    private val file: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "queue-test.json")
    }

    @After fun cleanUp() { file.delete() }

    private var clock = 0L
    private fun transfer(id: String, priority: Int = 0) = Transfer(
        id = id,
        direction = TransferDirection.DOWNLOAD,
        remotePath = "/tmp/$id",
        localUri = "content://downloads/$id",
        displayName = id,
        totalBytes = 1_000L,
        priority = priority,
        enqueuedAt = ++clock,
    )

    @Test fun promotionSurvivesProcessDeath() {
        val queue = TransferQueue()
        listOf("a", "b", "c").forEach { queue.enqueue(transfer(it)) }
        queue.promote("c")
        queue.persist(file)

        val restored = TransferQueue.fromPersisted(file)

        assertEquals("the promoted transfer must still run first after a restart",
            "c", restored.nextToStart()?.id)
    }

    @Test fun aRunningTransferComesBackQueuedSoItResumes() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.persist(file)

        val restored = TransferQueue.fromPersisted(file)

        val a = restored.transfers.value.firstOrNull { it.id == "a" }
        assertNotNull("the transfer must survive the restart", a)
        assertEquals(TransferState.QUEUED, a!!.state)
    }

    @Test fun byteProgressSurvivesSoAResumeDoesNotRestartFromZero() {
        val queue = TransferQueue()
        queue.enqueue(transfer("a"))
        queue.markRunning("a")
        queue.markProgress("a", 512L)
        queue.persist(file)

        val restored = TransferQueue.fromPersisted(file)

        assertEquals(512L, restored.transfers.value.first { it.id == "a" }.transferredBytes)
    }

    @Test fun aMissingQueueFileIsAnEmptyQueueNotACrash() {
        file.delete()
        assertEquals(0, TransferQueue.fromPersisted(file).transfers.value.size)
    }

    @Test fun aCorruptQueueFileIsAnEmptyQueueNotACrash() {
        file.writeText("{ this is not json")
        assertEquals(0, TransferQueue.fromPersisted(file).transfers.value.size)
    }
}
