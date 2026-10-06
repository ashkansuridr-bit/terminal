package app.terminalssh.secure.sftp

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import java.io.IOException

class TransferStatePathsTest {
    @Test fun sessionsAndHostsCannotOverwriteEachOther() {
        val root = Files.createTempDirectory("transfer-state-test").toFile()
        try {
            val first = TransferStatePaths.queueFile(root, "host-a", "session-one")
            val second = TransferStatePaths.queueFile(root, "host-b", "session-one")
            val third = TransferStatePaths.queueFile(root, "host-a", "session-two")
            assertNotEquals(first, second)
            assertNotEquals(first, third)
            TransferStatePaths.registerSession(root, "host-a", "session-one")
            val metadata = java.util.Properties().apply {
                java.io.File(first.parentFile, "identity.properties").inputStream().use { load(it) }
            }
            assertEquals("host-a", metadata.getProperty("hostId"))
            assertEquals("session-one", metadata.getProperty("sessionId"))
            TransferStatePaths.atomicWrite(first, "first")
            TransferStatePaths.atomicWrite(second, "second")
            TransferStatePaths.atomicWrite(third, "third")
            assertEquals("first", first.readText())
            assertEquals("second", second.readText())
            assertEquals("third", third.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun writeFailureDoesNotReplacePreviousLedger() {
        val root = Files.createTempDirectory("transfer-state-test").toFile()
        try {
            val state = TransferStatePaths.queueFile(root, "host", "session")
            TransferStatePaths.atomicWrite(state, "original")
            // A directory cannot be atomically replaced with a file.
            val blocked = java.io.File(state.parentFile, "blocked").apply { mkdirs() }
            java.io.File(blocked, "keep").writeText("existing")
            assertFailsWith<IOException> { TransferStatePaths.atomicWrite(blocked, "new") }
            assertEquals("original", state.readText())
            assertEquals("existing", java.io.File(blocked, "keep").readText())
            assertEquals(0, state.parentFile!!.listFiles()!!.count { it.name.endsWith(".tmp") })
        } finally { root.deleteRecursively() }
    }
}
