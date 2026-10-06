package app.terminalssh.secure.sftp

import app.terminalssh.secure.model.HostProfile
import java.io.File
import java.io.IOException
import java.util.Properties

/** Discovery only: a previous process never grants permission to write to another server. */
class TransferRecoveryStore(private val root: File) {
    data class Entry(
        val file: File,
        val hostId: String,
        val sessionId: String,
        val createdAt: Long,
        val server: String,
        val identityComplete: Boolean,
    )

    fun discover(activeSessionIds: Set<String>): List<Entry> {
        val directory = File(root, "transfer-state")
        if (!directory.exists()) return emptyList()
        val children = directory.listFiles() ?: throw IOException("Cannot list recovery state")
        return children.mapNotNull { child ->
            val queue = File(child, "queue.json")
            if (!child.isDirectory || !queue.isFile) return@mapNotNull null
            // The queue writer stores only nonterminal items. Ignore empty ledgers
            // left by normal teardown without parsing remote paths or credentials.
            if (queue.length() <= 8_388_608L && queue.readText().trim().matches(Regex("\\[\\s*\\]"))) return@mapNotNull null
            val metadata = readIdentity(queue) ?: return@mapNotNull Entry(queue, "", "", 0L, "", false)
            val sessionId = metadata.getProperty("sessionId", "")
            if (sessionId in activeSessionIds) return@mapNotNull null
            val hostId = metadata.getProperty("hostId", "")
            val host = metadata.getProperty("host", "")
            val user = metadata.getProperty("username", "")
            val port = metadata.getProperty("port", "").toIntOrNull()
            Entry(queue, hostId, sessionId, metadata.getProperty("createdAt", "0").toLongOrNull() ?: 0L,
                if (host.isNotBlank()) "$user@$host:${port ?: 22}" else "",
                hostId.isNotBlank() && sessionId.isNotBlank() && host.isNotBlank() && user.isNotBlank() && port != null && port in 1..65535)
        }.sortedByDescending { it.createdAt }
    }

    fun matches(file: File, profile: HostProfile): Boolean {
        val directory = File(root, "transfer-state").canonicalFile
        if (file.name != "queue.json" || file.parentFile?.parentFile?.canonicalFile != directory) return false
        val metadata = readIdentity(file) ?: return false
        return metadata.getProperty("hostId") == profile.id &&
            metadata.getProperty("host") == profile.host &&
            metadata.getProperty("port") == profile.port.toString() &&
            metadata.getProperty("username") == profile.username &&
            metadata.getProperty("jumpHostId", "") == profile.jumpHostId
    }

    private fun readIdentity(queue: File): Properties? {
        val file = File(queue.parentFile, "identity.properties")
        if (!file.isFile || file.length() > 16_384) return null
        return try { Properties().apply { file.inputStream().use(::load) } }
        catch (_: IOException) { null }
        catch (_: IllegalArgumentException) { null }
    }

    companion object { internal val importLock = Any() }
}
