package app.terminalssh.secure.ssh

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.sftp.Transfer
import app.terminalssh.secure.sftp.TransferDirection
import app.terminalssh.secure.sftp.TransferState
import app.terminalssh.secure.sftp.TransferStatePaths
import app.terminalssh.secure.vm.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual application/controller ownership across ViewModel destruction; no mocked lifecycle. */
@RunWith(AndroidJUnit4::class)
class SessionApplicationOwnershipTest {
    @Test fun clearingActivityViewModelDoesNotDestroyApplicationController() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TerminalApp
        val identity = java.util.UUID.randomUUID().toString()
        val profile = HostProfile("ownership-$identity", host = "example.test", username = "tester", auth = AuthMethod.Password(""))
        val session = SshSession(identity, profile, app.client, false, onClipboardCopy = {}, onPasteRequest = {})
        val store = ViewModelStore()
        var controller: app.terminalssh.secure.sftp.SftpController? = null
        try {
            withContext(Dispatchers.Main) {
                app.sessions.add(session)
                val first = AppViewModel(app)
                store.put("activity", first)
                controller = first.sftpControllerFor(session)
                controller!!.queue.importPaused(listOf(Transfer("paused-$identity", TransferDirection.DOWNLOAD,
                    "/data/file", "content://local/file", "file", state = TransferState.PAUSED)))
                store.clear()
                val replacement = AppViewModel(app)
                store.put("replacement", replacement)
                assertSame(controller, replacement.sftpControllerFor(session))
                assertEquals(TransferState.PAUSED, controller!!.queue.transfers.value.single().state)
                assertTrue(app.sessions.sessions.value.any { it.id == identity })
                replacement.closeSession(identity)
            }
            withTimeout(10_000) { app.sessions.sessions.first { open -> open.none { it.id == identity } } }
            assertTrue(!app.lifecycle.controllers.containsKey(identity))
            val file = TransferStatePaths.queueFile(app.filesDir, profile.id, identity)
            assertTrue("close must preserve pending work", file.isFile)
        } finally {
            withContext(Dispatchers.Main) { store.clear(); app.lifecycle.closeSession(identity) }
            withTimeout(10_000) { app.sessions.sessions.first { open -> open.none { it.id == identity } } }
            TransferStatePaths.queueFile(app.filesDir, profile.id, identity).parentFile?.deleteRecursively()
        }
    }
}
