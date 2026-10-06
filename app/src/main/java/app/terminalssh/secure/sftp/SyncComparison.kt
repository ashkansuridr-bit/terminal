package app.terminalssh.secure.sftp

import java.io.InputStream
import java.security.MessageDigest

/** SAFE verifies equal-sized candidates, including timestamp-preserving edits. */
enum class SyncMode { FAST, SAFE, VERIFY_ALL }

internal object SyncComparison {
    fun metadataMatches(localSize: Long, localMtimeSeconds: Long, remoteSize: Long, remoteMtimeSeconds: Long): Boolean {
        require(localSize >= 0 && remoteSize >= 0) { "Unknown file size cannot authorize skipping a file" }
        return localSize == remoteSize && localMtimeSeconds == remoteMtimeSeconds
    }

    fun identical(
        mode: SyncMode,
        localSize: Long,
        localMtimeSeconds: Long,
        remoteSize: Long,
        remoteMtimeSeconds: Long,
        localHash: () -> ByteArray,
        remoteHash: () -> ByteArray,
    ): Boolean {
        val metadataMatches = metadataMatches(localSize, localMtimeSeconds, remoteSize, remoteMtimeSeconds)
        if (localSize != remoteSize) return false
        if (mode == SyncMode.FAST) return metadataMatches
        // Hashing the candidate is bounded-memory and necessary even with equal timestamps:
        // an editor may preserve mtime, so equal metadata is not proof of identical content.
        return MessageDigest.isEqual(localHash(), remoteHash())
    }

    fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            return digest.digest()
        } finally {
            buffer.fill(0)
        }
    }
}
