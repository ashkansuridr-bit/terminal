package app.terminalssh.secure.sftp

import java.io.EOFException
import java.io.InputStream
import java.security.MessageDigest

/** Bounded-memory hashing used before resuming or authorizing a stale sync plan. */
internal object ContentIdentity {
    fun prefix(input: InputStream, bytes: Long): ByteArray {
        require(bytes >= 0)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var remaining = bytes
        try {
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (count < 0) throw EOFException("Transfer prefix is shorter than recorded progress")
                if (count == 0) continue
                digest.update(buffer, 0, count)
                remaining -= count
            }
            return digest.digest()
        } finally {
            buffer.fill(0)
        }
    }

    fun matches(expected: ByteArray, actual: ByteArray): Boolean = MessageDigest.isEqual(expected, actual)
    fun hex(hash: ByteArray): String = hash.joinToString("") { "%02x".format(it) }
}

/** null snapshots represent explicit absence, never an unknown stat result. */
data class SyncFileSnapshot(val bytes: Long, val mtimeSeconds: Long, val sha256: String)

internal object SyncPlanGuard {
    fun requireUnchanged(expected: SyncFileSnapshot?, current: SyncFileSnapshot?) {
        if (expected != current) throw java.io.IOException("Sync source or destination changed; recompute the plan")
    }

    fun requireRelativePath(path: String) {
        require(path.isNotEmpty() && !path.startsWith('/') && '\\' !in path &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid sync relative path" }
    }
}
