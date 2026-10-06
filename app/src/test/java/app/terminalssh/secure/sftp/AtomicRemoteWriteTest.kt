package app.terminalssh.secure.sftp

import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Protocol model tests only: these do not replace real SFTP/OpenSSH integration coverage. */
class AtomicRemoteWriteTest {
    private val target = "/etc/config"
    private val original = "original configuration".toByteArray()
    private val edited = "new configuration".toByteArray()

    @Test fun successfulWriteVerifiesThenValidatesThenRenames() {
        val remote = ProtocolModel()
        AtomicRemoteWrite.replace(remote, edited.inputStream(), target) { remote.events += "guard" }
        assertContentEquals(edited, remote.files[target])
        assertEquals(listOf("write", "size", "digest", "guard", "rename"), remote.events)
        assertEquals(setOf(target), remote.files.keys)
    }

    @Test fun disconnectMidUploadLeavesOriginalIntact() {
        val remote = ProtocolModel(failWrite = true)
        assertFailsWith<IOException> { AtomicRemoteWrite.replace(remote, edited.inputStream(), target) }
        assertContentEquals(original, remote.files[target])
        assertFalse(remote.events.contains("rename"))
        assertEquals(setOf(target), remote.files.keys)
    }

    @Test fun wrongRemoteHashCannotCommitEvenIfSizeMatches() {
        val remote = ProtocolModel(corruptBytes = true)
        assertFailsWith<IOException> { AtomicRemoteWrite.replace(remote, edited.inputStream(), target) }
        assertContentEquals(original, remote.files[target])
        assertFalse(remote.events.contains("rename"))
    }

    @Test fun concurrentModificationDuringUploadCannotBeReplaced() {
        val remote = ProtocolModel()
        assertFailsWith<IOException> {
            AtomicRemoteWrite.replace(remote, edited.inputStream(), target) {
                remote.files[target] = "changed by another editor".toByteArray()
                throw IOException("Conflict")
            }
        }
        assertContentEquals("changed by another editor".toByteArray(), remote.files[target])
        assertFalse(remote.events.contains("rename"))
    }

    @Test fun unsupportedAtomicRenameRefusesBeforeUploading() {
        val remote = ProtocolModel(supported = false)
        assertFailsWith<IOException> { AtomicRemoteWrite.replace(remote, edited.inputStream(), target) }
        assertTrue(remote.events.isEmpty())
        assertContentEquals(original, remote.files[target])
    }

    @Test fun failedRenameNeverDeletesDestinationOrRetriesDestructively() {
        val remote = ProtocolModel(failRename = true)
        assertFailsWith<IOException> { AtomicRemoteWrite.replace(remote, edited.inputStream(), target) }
        assertContentEquals(original, remote.files[target])
        assertEquals(1, remote.events.count { it == "rename" })
        assertEquals(setOf(target), remote.files.keys)
    }

    @Test fun cleanupErrorIsPreservedAlongsideOriginalFailure() {
        val remote = ProtocolModel(failWrite = true, failCleanup = true)
        val failure = assertFailsWith<IOException> {
            AtomicRemoteWrite.replace(remote, edited.inputStream(), target)
        }
        assertEquals("disconnected", failure.message)
        assertEquals("cleanup denied", failure.suppressed.single().message)
        assertContentEquals(original, remote.files[target])
    }

    @Test fun incompleteTransportReadNeverCommits() {
        val remote = ProtocolModel(shortRead = true)
        assertFailsWith<IOException> { AtomicRemoteWrite.replace(remote, edited.inputStream(), target) }
        assertContentEquals(original, remote.files[target])
        assertFalse(remote.events.contains("rename"))
    }

    private inner class ProtocolModel(
        val supported: Boolean = true,
        val failWrite: Boolean = false,
        val corruptBytes: Boolean = false,
        val failRename: Boolean = false,
        val failCleanup: Boolean = false,
        val shortRead: Boolean = false,
    ) : AtomicRemoteWrite.Protocol {
        val files = mutableMapOf(target to original.copyOf())
        val events = mutableListOf<String>()
        override fun supportsAtomicReplace() = supported
        override fun write(path: String, input: InputStream) {
            assertTrue(path.startsWith("/etc/.terminalssh-save-"))
            events += "write"
            if (failWrite) { files[path] = byteArrayOf(1); throw IOException("disconnected") }
            files[path] = if (shortRead) byteArrayOf(input.read().toByte()) else input.readBytes()
            if (corruptBytes) files[path]!![0] = (files[path]!![0].toInt() xor 1).toByte()
        }
        override fun size(path: String): Long { events += "size"; return files.getValue(path).size.toLong() }
        override fun digest(path: String): ByteArray {
            events += "digest"
            return MessageDigest.getInstance("SHA-256").digest(files.getValue(path))
        }
        override fun rename(from: String, to: String) {
            events += "rename"
            if (failRename) throw IOException("rename denied")
            files[to] = files.remove(from)!!
        }
        override fun delete(path: String) {
            assertTrue(path != target)
            if (failCleanup) throw IOException("cleanup denied")
            files.remove(path)
        }
    }
}
