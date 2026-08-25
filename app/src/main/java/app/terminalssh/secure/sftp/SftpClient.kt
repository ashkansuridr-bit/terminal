package app.terminalssh.secure.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.SftpProgressMonitor
import java.io.InputStream
import java.io.OutputStream

/**
 * SFTP over an SSH [Session] that is already connected and host-key verified.
 *
 * Reusing the terminal's session is the point: opening a second connection would mean a
 * second authentication, a second host-key check, and a second password prompt for the
 * same server the user is already sitting in.
 *
 * Every method here performs blocking network I/O and must never run on the main thread.
 */
class SftpClient(private val session: Session) : AutoCloseable {

    private var channel: ChannelSftp? = null

    private fun channel(): ChannelSftp {
        channel?.takeIf { it.isConnected }?.let { return it }
        val opened = session.openChannel("sftp") as ChannelSftp
        opened.connect(CONNECT_TIMEOUT_MS)
        channel = opened
        return opened
    }

    /** The server's idea of where the user starts, usually their home directory. */
    fun home(): String = runCatching { channel().home }.getOrDefault(RemotePath.ROOT)

    @Suppress("UNCHECKED_CAST")
    fun list(path: String): List<RemoteEntry> {
        val normalized = RemotePath.normalize(path)
        val raw = channel().ls(normalized) as? java.util.Vector<ChannelSftp.LsEntry> ?: return emptyList()
        return raw.asSequence()
            .map { entry -> entry.toRemoteEntry(normalized) }
            .filterNot { it.isNavigational }
            // Directories first, then case-insensitive by name: the ordering people
            // expect from every other file browser they have used.
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    /**
     * @param resumeFrom byte offset to continue from; 0 starts fresh. This is what makes
     *   a download survive a dropped connection on mobile data.
     */
    fun download(remotePath: String, sink: OutputStream, resumeFrom: Long = 0L, onProgress: (Long) -> Unit) {
        val monitor = ProgressMonitor(resumeFrom, onProgress)
        channel().get(RemotePath.normalize(remotePath), sink, monitor, ChannelSftp.RESUME, resumeFrom)
    }

    fun upload(source: InputStream, remotePath: String, resumeFrom: Long = 0L, onProgress: (Long) -> Unit) {
        val monitor = ProgressMonitor(resumeFrom, onProgress)
        val mode = if (resumeFrom > 0L) ChannelSftp.RESUME else ChannelSftp.OVERWRITE
        channel().put(source, RemotePath.normalize(remotePath), monitor, mode)
    }

    /** Size in bytes, or [Transfer.UNKNOWN_SIZE] when the server will not say. */
    fun size(remotePath: String): Long =
        runCatching { channel().stat(RemotePath.normalize(remotePath)).size }
            .getOrDefault(Transfer.UNKNOWN_SIZE)

    fun delete(remotePath: String, isDirectory: Boolean) {
        val normalized = RemotePath.normalize(remotePath)
        if (isDirectory) channel().rmdir(normalized) else channel().rm(normalized)
    }

    fun makeDirectory(remotePath: String) = channel().mkdir(RemotePath.normalize(remotePath))

    fun rename(from: String, to: String) =
        channel().rename(RemotePath.normalize(from), RemotePath.normalize(to))

    override fun close() {
        runCatching { channel?.disconnect() }
        channel = null
    }

    private fun ChannelSftp.LsEntry.toRemoteEntry(parent: String): RemoteEntry {
        val attributes: SftpATTRS = attrs
        return RemoteEntry(
            name = filename,
            path = RemotePath.join(parent, filename),
            isDirectory = attributes.isDir,
            isSymlink = attributes.isLink,
            sizeBytes = attributes.size,
            modifiedEpochSeconds = attributes.mTime.toLong(),
            permissions = attributes.permissionsString ?: "",
        )
    }

    /**
     * JSch reports each chunk's size; the UI wants a running total, and a resumed
     * transfer has to start counting from where it left off rather than zero.
     */
    private class ProgressMonitor(
        startedAt: Long,
        private val onProgress: (Long) -> Unit,
    ) : SftpProgressMonitor {
        private var total = startedAt

        override fun init(op: Int, src: String?, dest: String?, max: Long) = Unit

        override fun count(count: Long): Boolean {
            total += count
            onProgress(total)
            return true
        }

        override fun end() = Unit
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** Maps an SFTP failure onto something the transfer list can explain. */
        fun classify(t: Throwable): TransferErrorKind = when {
            t is SftpException -> when (t.id) {
                ChannelSftp.SSH_FX_NO_SUCH_FILE -> TransferErrorKind.NOT_FOUND
                ChannelSftp.SSH_FX_PERMISSION_DENIED -> TransferErrorKind.PERMISSION_DENIED
                else -> classifyMessage(t.message)
            }
            else -> classifyMessage(t.message)
        }

        private fun classifyMessage(message: String?): TransferErrorKind {
            val text = (message ?: "").lowercase()
            return when {
                "no space" in text || "quota" in text || "disk full" in text -> TransferErrorKind.OUT_OF_SPACE
                "permission" in text || "denied" in text -> TransferErrorKind.PERMISSION_DENIED
                "no such file" in text || "not found" in text -> TransferErrorKind.NOT_FOUND
                "connection" in text || "broken pipe" in text || "session is down" in text ->
                    TransferErrorKind.CONNECTION_LOST
                else -> TransferErrorKind.UNKNOWN
            }
        }
    }
}
