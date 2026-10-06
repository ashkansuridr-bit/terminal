package app.terminalssh.secure.sftp

import java.io.InputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

/** Atomic replacement protocol, independently testable without pretending to test a server. */
object AtomicRemoteWrite {
    interface Protocol {
        fun supportsAtomicReplace(): Boolean
        fun write(path: String, input: InputStream)
        fun size(path: String): Long
        fun digest(path: String): ByteArray
        fun rename(from: String, to: String)
        fun delete(path: String)
    }

    fun replace(protocol: Protocol, source: InputStream, target: String, beforeCommit: () -> Unit = {}) {
        if (!protocol.supportsAtomicReplace()) throw IOException("Server does not support atomic replacement")
        val temporary = RemotePath.join(RemotePath.parent(target), ".terminalssh-save-${UUID.randomUUID()}")
        var count = 0L
        val counter = object : java.io.FilterInputStream(source) {
            override fun read(): Int = `in`.read().also { if (it >= 0) count++ }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                `in`.read(bytes, offset, length).also { if (it > 0) count += it }
            // DigestInputStream skipping does not hash skipped bytes: do not allow a transport to skip.
            override fun skip(n: Long): Long = 0L
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            protocol.write(temporary, DigestInputStream(counter, digest))
            // Confirm transport consumed all input, and verify bytes from server before replacing.
            if (source.read() != -1) throw IOException("Upload did not consume complete input")
            if (protocol.size(temporary) != count ||
                !MessageDigest.isEqual(digest.digest(), protocol.digest(temporary))) {
                throw IOException("Remote staged upload verification failed")
            }
            beforeCommit()
            protocol.rename(temporary, target)
        } catch (failure: Throwable) {
            // Only remove our random temporary. Never delete/truncate the destination as fallback.
            try { protocol.delete(temporary) } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}
