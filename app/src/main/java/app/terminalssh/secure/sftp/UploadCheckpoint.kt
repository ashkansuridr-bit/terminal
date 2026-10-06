package app.terminalssh.secure.sftp

import java.io.File
import java.io.IOException
import java.util.Properties

/** Durable write authorization for one transfer, not refreshed on retries. */
internal data class UploadCheckpoint(
    val sourceSha256: String,
    val target: SyncFileSnapshot?,
    val stagingName: String = ".terminalssh-transfer-${java.util.UUID.randomUUID()}.part",
) {
    fun save(file: File) {
        val properties = Properties().apply {
            setProperty("sourceSha256", sourceSha256)
            setProperty("stagingName", stagingName)
            setProperty("targetExists", (target != null).toString())
            target?.let {
                setProperty("targetBytes", it.bytes.toString())
                setProperty("targetMtime", it.mtimeSeconds.toString())
                setProperty("targetSha256", it.sha256)
            }
        }
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            temporary.outputStream().use { properties.store(it, null); it.fd.sync() }
            if (!temporary.renameTo(file)) throw IOException("Could not persist upload authorization")
        } finally {
            temporary.delete()
        }
    }

    companion object {
        fun load(file: File): UploadCheckpoint {
            val p = Properties().apply { file.inputStream().use { load(it) } }
            val hash = p.getProperty("sourceSha256") ?: throw IOException("Missing upload source identity")
            require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid upload identity" }
            val target = when (p.getProperty("targetExists")) {
                "false" -> null
                "true" -> SyncFileSnapshot(
                    p.getProperty("targetBytes").toLong(), p.getProperty("targetMtime").toLong(),
                    p.getProperty("targetSha256").also { require(it.matches(Regex("[0-9a-f]{64}"))) },
                )
                else -> throw IOException("Missing upload destination authorization")
            }
            val stagingName = p.getProperty("stagingName") ?: throw IOException("Missing upload staging identity")
            require(stagingName.matches(Regex("[.]terminalssh-transfer-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}[.]part"))) {
                "Invalid upload staging identity"
            }
            return UploadCheckpoint(hash, target, stagingName)
        }
    }
}
