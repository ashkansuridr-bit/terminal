package app.terminalssh.secure.sftp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ssh.SshSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Android JSON and durable files; no server is required to import paused work. */
@RunWith(AndroidJUnit4::class)
class TransferRecoveryInstrumentedTest {
    @Test fun previousSessionIsImportedPausedAndRetiredOnlyAfterDurableCommit() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TerminalApp
        val root = File(app.cacheDir, "recovery-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val profile = HostProfile("recovery-host", host = "example.test", username = "alice", auth = AuthMethod.Password(""))
        val session = SshSession("new-session", profile, app.client, false, onClipboardCopy = {}, onPasteRequest = {})
        val controller = SftpController(session, app.contentResolver, root, scope,
            SftpWorkspaceStore(app.getSharedPreferences("recovery-test", 0), profile.id), persistentDir = root)
        try {
            val file = TransferStatePaths.queueFile(root, profile.id, "old-session")
            TransferStatePaths.registerSession(root, profile.id, "old-session", profile)
            val old = TransferQueue()
            old.enqueue(Transfer("unfinished", TransferDirection.DOWNLOAD, "/data/file", "content://local/file", "file", totalBytes = 20))
            old.markRunning("unfinished")
            old.markProgress("unfinished", 10)
            old.persist(file)
            assertEquals(1, controller.importRecoveredQueue(file))
            assertFalse(file.exists())
            val recovered = controller.queue.transfers.value.single()
            assertEquals(TransferState.PAUSED, recovered.state)
            assertEquals(10L, recovered.transferredBytes)
            val committed = TransferQueue.fromPersisted(TransferStatePaths.queueFile(root, profile.id, session.id))
            assertEquals(TransferState.PAUSED, committed.transfers.value.single().state)
            assertTrue(controller.queue.active.isEmpty())
        } finally {
            controller.closeAndJoin()
            session.destroy()
            scope.cancel()
            root.deleteRecursively()
        }
    }
}
