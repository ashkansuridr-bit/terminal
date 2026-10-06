package app.terminalssh.secure.sftp

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.Properties
import java.io.StringWriter

/** No hostname/path enters a filename; every session has an independent durable ledger. */
internal object TransferStatePaths {
    private fun directory(root: File, hostId: String, sessionId: String): File {
        val identity = MessageDigest.getInstance("SHA-256").digest(
            "$hostId\u0000$sessionId".toByteArray(Charsets.UTF_8),
        ).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(File(root, "transfer-state"), identity)
    }

    fun queueFile(root: File, hostId: String, sessionId: String): File =
        File(directory(root, hostId, sessionId), "queue.json")

    fun historyFile(root: File, hostId: String, sessionId: String): File =
        File(directory(root, hostId, sessionId), "history.json")

    /** Retains server identity for a future recovery center without storing credentials. */
    fun registerSession(root: File, hostId: String, sessionId: String, profile: app.terminalssh.secure.model.HostProfile? = null) {
        val metadata = File(directory(root, hostId, sessionId), "identity.properties")
        if (metadata.isFile) return
        val properties = Properties().apply {
            setProperty("hostId", hostId)
            setProperty("sessionId", sessionId)
            setProperty("createdAt", System.currentTimeMillis().toString())
            profile?.let {
                setProperty("host", it.host)
                setProperty("port", it.port.toString())
                setProperty("username", it.username)
                setProperty("jumpHostId", it.jumpHostId)
            }
        }
        val writer = StringWriter()
        properties.store(writer, "Transfer recovery identity; no credentials")
        atomicWrite(metadata, writer.toString())
    }

    /** Same-directory rename after fsync: a failed write preserves the previous ledger. */
    fun atomicWrite(file: File, content: String) {
        val directory = file.parentFile ?: throw IOException("Missing transfer state directory")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create transfer state directory")
        }
        val temporary = File(directory, ".state-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!temporary.renameTo(file)) throw IOException("Cannot commit transfer state")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
