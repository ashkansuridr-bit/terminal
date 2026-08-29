package app.terminalssh.secure.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpProgressMonitor
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for the remote text editor's check-before-write contract. */
class SftpTextEditConflictTest {

    private val opened = "server {\n  listen 80;\n}\n"

    @Test
    fun changedRemoteFileIsNotUploadedBeforeConflictConfirmation() = runBlocking {
        val remote = FakeChannel(mtime = 200, content = opened)
        val controller = controller(remote, saved(mtime = 100, text = opened))

        val result = controller.checkFileTextConflict(PATH)

        assertTrue(result.getOrThrow(), "a changed remote mtime must report a conflict")
        assertEquals(0, remote.uploadCount, "conflict detection must not overwrite remote data")
        assertTrue(
            controller.checkFileTextConflict(PATH).getOrThrow(),
            "cancelling the warning must not bypass the next check",
        )
    }

    @Test
    fun unchangedRemoteFileIsCheckedWithoutPerformingAHiddenUpload() = runBlocking {
        val remote = FakeChannel(mtime = 100, content = opened)
        val controller = controller(remote, saved(mtime = 100, text = opened))

        val result = controller.checkFileTextConflict(PATH)

        assertFalse(result.getOrThrow(), "an unchanged remote must allow the UI to save")
        assertEquals(0, remote.uploadCount, "the checked callback is a guard; the UI performs the one upload")
    }

    @Test
    fun failedStatDoesNotFailOpenIntoAnUpload() = runBlocking {
        val remote = FakeChannel(mtime = null, content = opened)
        val controller = controller(remote, saved(mtime = 100, text = opened))

        val result = controller.checkFileTextConflict(PATH)

        assertTrue(result.isFailure, "an unavailable mtime must keep the editor open")
        assertEquals(0, remote.uploadCount)
    }

    // ---- the case mtime-only detection let through (silent-overwrite regression) ----

    @Test
    fun editThatPreservesSizeAndMtimeIsStillDetected() {
        // A one-character config change with the timestamp restored afterwards — what
        // `touch -r`, rsync and several editors do. Same mtime, same length, different
        // bytes. The old mtime-only check reported "no conflict" and the user's save
        // silently destroyed the other edit.
        val edited = opened.replace("80", "81")
        assertEquals(opened.length, edited.length, "the scenario requires an identical length")

        val remote = FakeChannel(mtime = 100, content = edited)
        val controller = controller(remote, saved(mtime = 100, text = opened))

        val result = runBlocking { controller.checkFileTextConflict(PATH) }

        assertTrue(result.getOrThrow(), "identical mtime and size must not be treated as unchanged")
        assertEquals(0, remote.uploadCount)
    }

    @Test
    fun sameLengthButDifferentContentHashesDifferently() {
        assertFalse(EditConflict.sha256("listen 80;") == EditConflict.sha256("listen 81;"))
        assertEquals(EditConflict.sha256("listen 80;"), EditConflict.sha256("listen 80;"))
    }

    @Test
    fun verdictFailsClosedWhenTheRemoteCannotBeRead() {
        val saved = EditFingerprint(100L, 10L, EditConflict.sha256("x"))
        assertEquals(ConflictVerdict.UNKNOWN, EditConflict.verdict(saved, null, 10L, "h"))
        assertEquals(ConflictVerdict.UNKNOWN, EditConflict.verdict(saved, 100L, null, "h"))
        assertEquals(ConflictVerdict.UNKNOWN, EditConflict.verdict(saved, 100L, 10L, null))
        assertEquals(ConflictVerdict.UNKNOWN, EditConflict.verdict(null, 100L, 10L, "h"))
    }

    @Test
    fun sizeChangeAloneIsEnoughToReportAConflict() {
        val saved = EditFingerprint(100L, 10L, EditConflict.sha256("x"))
        assertTrue(EditConflict.statProvesChange(saved, currentMtime = 100L, currentSize = 11L))
        assertFalse(EditConflict.statProvesChange(saved, currentMtime = 100L, currentSize = 10L))
    }

    private fun saved(mtime: Long, text: String) = EditFingerprint(
        mtimeEpochSeconds = mtime,
        sizeBytes = text.toByteArray().size.toLong(),
        sha256 = EditConflict.sha256(text),
    )

    private fun controller(remote: FakeChannel, fingerprint: EditFingerprint): SftpController {
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
        SftpController::class.java.getDeclaredField("editFingerprints").apply {
            isAccessible = true
            set(controller, ConcurrentHashMap(mapOf(PATH to fingerprint)))
        }
        return controller
    }

    private class FakeChannel(
        private val mtime: Int?,
        private val content: String,
    ) : ChannelSftp() {
        var uploadCount = 0
            private set

        override fun isConnected(): Boolean = true

        override fun stat(path: String): SftpATTRS = SftpATTRS().apply {
            val availableMtime = requireNotNull(mtime) { "stat unavailable" }
            setACMODTIME(availableMtime, availableMtime)
            setSIZE(content.toByteArray().size.toLong())
        }

        override fun get(source: String): InputStream =
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))

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
