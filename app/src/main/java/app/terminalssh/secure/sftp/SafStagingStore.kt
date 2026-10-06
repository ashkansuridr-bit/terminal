package app.terminalssh.secure.sftp

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.UUID

/** Durable app-private edits. Active descriptors are never retry candidates in this process. */
class SafStagingStore(private val directory: File, private val processOwner: String = PROCESS_OWNER) {
    data class Edit(val file: File, val hostId: String, val server: String, val path: String, val createdAt: Long)

    fun create(sessionId: String, hostId: String, server: String, path: String, writable: Boolean = true): Edit = synchronized(MUTEX) {
        check(directory.exists() || directory.mkdirs())
        val safeSession = sessionId.filter { it.isLetterOrDigit() || it == '-' }.take(40)
        val name = RemotePath.sanitizeDownloadName(path).take(80)
        val file = File(directory, "saf-$safeSession-${UUID.randomUUID()}-$name")
        check(file.createNewFile())
        val edit = Edit(file, hostId, server, path, System.currentTimeMillis())
        val props = Properties().apply {
            setProperty("hostId", hostId); setProperty("server", server)
            setProperty("path", path); setProperty("createdAt", edit.createdAt.toString())
            setProperty("writable", writable.toString()); setProperty("owner", processOwner)
            setProperty("state", "preparing")
        }
        try { writeMetadata(file, props) }
        catch (failure: Exception) {
            file.delete(); metadata(file).delete(); throw failure
        }
        edit
    }

    /** Fail closed on corrupt metadata: keep bytes, do not guess a destination or delete evidence. */
    fun pending(): List<Edit> = synchronized(MUTEX) {
        directory.listFiles().orEmpty().filter { it.name.endsWith(".meta") }.mapNotNull { meta ->
            val file = File(directory, meta.name.removeSuffix(".meta"))
            if (!file.exists()) return@mapNotNull null
            val props = readMetadata(file)
            val edit = parseEdit(file, props)
            val writable = props.getProperty("writable") ?: throw IOException("Missing staging write permission")
            val state = props.getProperty("state") ?: throw IOException("Missing staging state")
            if (writable !in listOf("true", "false") || state !in listOf("preparing", "active", "failed", "retrying")) {
                throw IOException("Invalid staging metadata")
            }
            if (writable != "true" || state == "preparing") return@mapNotNull null
            val owner = props.getProperty("owner")?.takeIf { it.isNotBlank() }
                ?: throw IOException("Missing staging owner")
            if (state == "failed" || owner != processOwner) edit else null
        }.sortedByDescending { it.createdAt }
    }

    /** Durable original bytes identity for ordinary close/save conflict detection. */
    fun recordFingerprint(edit: Edit, fingerprint: EditFingerprint) = synchronized(MUTEX) {
        val props = readMetadata(edit.file)
        props.setProperty("originalMtime", fingerprint.mtimeEpochSeconds.toString())
        props.setProperty("originalSize", fingerprint.sizeBytes.toString())
        props.setProperty("originalSha256", fingerprint.sha256)
        writeMetadata(edit.file, props)
    }

    fun fingerprint(edit: Edit): EditFingerprint = synchronized(MUTEX) {
        val props = readMetadata(edit.file)
        val mtime = props.getProperty("originalMtime")?.toLongOrNull()
            ?: throw IOException("Missing original edit timestamp")
        val size = props.getProperty("originalSize")?.toLongOrNull()
            ?: throw IOException("Missing original edit size")
        val hash = props.getProperty("originalSha256")
            ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            ?: throw IOException("Missing original edit digest")
        EditFingerprint(mtime, size, hash)
    }

    /** Only fully downloaded/truncated files handed to an editor may become recoverable. */
    fun activate(edit: Edit) = synchronized(MUTEX) {
        val props = readMetadata(edit.file)
        check(props.getProperty("state") == "preparing")
        props.setProperty("state", "active")
        writeMetadata(edit.file, props)
    }

    fun failed(edit: Edit) = synchronized(MUTEX) {
        val props = readMetadata(edit.file)
        props.setProperty("state", "failed")
        props.setProperty("owner", processOwner)
        writeMetadata(edit.file, props)
    }

    /** Atomic claim prevents repeated taps/activities from uploading the same stage concurrently. */
    fun claimRetry(edit: Edit): Boolean = synchronized(MUTEX) {
        val candidate = pending().any { it.file == edit.file }
        if (!candidate) return@synchronized false
        val props = readMetadata(edit.file)
        props.setProperty("state", "retrying")
        props.setProperty("owner", processOwner)
        writeMetadata(edit.file, props)
        true
    }

    fun complete(edit: Edit) = synchronized(MUTEX) {
        check(!edit.file.exists() || edit.file.delete()) { "Could not remove staging file" }
        check(!metadata(edit.file).exists() || metadata(edit.file).delete()) { "Could not remove staging metadata" }
    }

    private fun parseEdit(file: File, props: Properties): Edit {
        fun field(name: String) = props.getProperty(name)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Missing staging metadata field")
        val timestamp = field("createdAt").toLongOrNull() ?: throw IOException("Invalid staging timestamp")
        val path = field("path")
        if (!path.startsWith('/')) throw IOException("Invalid staging destination")
        return Edit(file, field("hostId"), field("server"), path, timestamp)
    }

    private fun readMetadata(file: File): Properties = try {
        Properties().apply { metadata(file).inputStream().use { load(it) } }
    } catch (failure: Exception) { throw IOException("Could not read staging metadata", failure) }

    private fun writeMetadata(file: File, props: Properties) {
        val target = metadata(file)
        val temporary = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { props.store(it, null); it.fd.sync() }
            if (!temporary.renameTo(target)) throw IOException("Could not commit staging metadata")
        } finally { temporary.delete() }
    }

    private fun metadata(file: File) = File(directory, file.name + ".meta")

    companion object {
        private val PROCESS_OWNER = UUID.randomUUID().toString()
        private val MUTEX = Any()
        fun isChild(parent: String, child: String): Boolean {
            val p = RemotePath.normalize(parent)
            val c = RemotePath.normalize(child)
            return c != p && c.startsWith(if (p == "/") "/" else "$p/")
        }
    }
}
