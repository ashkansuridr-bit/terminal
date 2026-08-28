package app.terminalssh.secure.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpProgressMonitor
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for the remote text editor's check-before-write contract. */
class SftpTextEditConflictTest {

    @Test
    fun changedRemoteFileIsNotUploadedBeforeConflictConfirmation() = runBlocking {
        val remote = FakeChannel(mtime = 200)
        val controller = controller(remote, savedMtime = 100)

        val result = controller.checkFileTextConflict(PATH)

        assertTrue(result.getOrThrow(), "a changed remote mtime must report a conflict")
        assertEquals(0, remote.uploadCount, "conflict detection must not overwrite remote data")
        assertTrue(controller.checkFileTextConflict(PATH).getOrThrow(), "cancelling the warning must not bypass the next check")
    }

    @Test
    fun unchangedRemoteFileIsCheckedWithoutPerformingAHiddenUpload() = runBlocking {
        val remote = FakeChannel(mtime = 100)
        val controller = controller(remote, savedMtime = 100)

        val result = controller.checkFileTextConflict(PATH)

        assertFalse(result.getOrThrow(), "an unchanged remote mtime must allow the UI to save")
        assertEquals(0, remote.uploadCount, "the checked callback is a guard; the UI performs the one upload")
    }

    @Test
    fun failedStatDoesNotFailOpenIntoAnUpload() = runBlocking {
        val remote = FakeChannel(mtime = null)
        val controller = controller(remote, savedMtime = 100)

        val result = controller.checkFileTextConflict(PATH)

        assertTrue(result.isFailure, "an unavailable mtime must keep the editor open")
        assertEquals(0, remote.uploadCount)
    }

    private fun controller(remote: FakeChannel, savedMtime: Long): SftpController {
        val sftp = SftpClient(JSch().getSession("unused", "localhost"))
        SftpClient::class.java.getDeclaredField("channel").apply {
            isAccessible = true
            set(sftp, remote)
        }

        val controller = allocateWithoutConstructor(SftpController::class.java)
        SftpController::class.java.getDeclaredField("client").apply {
            isAccessible = true
            set(controller, sftp)
        }
        SftpController::class.java.getDeclaredField("editMtimes").apply {
            isAccessible = true
            set(controller, ConcurrentHashMap(mapOf(PATH to savedMtime)))
        }
        return controller
    }

    private class FakeChannel(private val mtime: Int?) : ChannelSftp() {
        var uploadCount = 0
            private set

        override fun isConnected(): Boolean = true

        override fun stat(path: String): SftpATTRS = SftpATTRS().apply {
            val availableMtime = requireNotNull(mtime) { "stat unavailable" }
            setACMODTIME(availableMtime, availableMtime)
        }

        override fun put(
            source: InputStream,
            destination: String,
            monitor: SftpProgressMonitor,
            mode: Int,
        ) {
            uploadCount++
        }
    }

    private companion object {
        const val PATH = "/home/user/notes.txt"

        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            it.get(null)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> allocateWithoutConstructor(type: Class<T>): T =
            unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
