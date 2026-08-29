package app.terminalssh.secure.sftp

import com.jcraft.jsch.ChannelExec
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

    fun list(path: String): List<RemoteEntry> {
        val normalized = RemotePath.normalize(path)
        val raw = channel().ls(normalized)
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

    /** Modification time in epoch seconds. A failed stat must remain distinguishable. */
    fun mtime(remotePath: String): Long =
        channel().stat(RemotePath.normalize(remotePath)).mTime.toLong()

    /**
     * Whether [remotePath] currently exists on the server. A stat failure — including a
     * genuine "not found" — reads as false; the caller only needs a yes/no for a
     * pre-upload conflict check, not the reason.
     */
    fun exists(remotePath: String): Boolean =
        runCatching { channel().stat(RemotePath.normalize(remotePath)); true }
            .getOrDefault(false)

    fun delete(remotePath: String, isDirectory: Boolean) {
        val normalized = RemotePath.normalize(remotePath)
        if (isDirectory) channel().rmdir(normalized) else channel().rm(normalized)
    }

    fun makeDirectory(remotePath: String) = channel().mkdir(RemotePath.normalize(remotePath))

    fun rename(from: String, to: String) =
        channel().rename(RemotePath.normalize(from), RemotePath.normalize(to))

    /** Changes POSIX mode bits (9-bit int, e.g. 0b110_100_100 = 0o644). */
    fun chmod(remotePath: String, mode: Int) {
        channel().chmod(mode, RemotePath.normalize(remotePath))
    }

    /**
     * Owner and group as reported by the server in the long listing, or null when
     * the server omits them or the longname line doesn't have that shape.
     */
    fun ownerGroup(remotePath: String): String? {
        val attrs = runCatching { channel().stat(RemotePath.normalize(remotePath)) }.getOrNull() ?: return null
        return PosixPermissions.ownerGroup(attrs.toString())
    }

    /**
     * Recursively lists all files under [remotePath], returning pairs of
     * (remotePath, relativePath) for each non-directory entry. Used by
     * recursive folder download (#26).
     */
    fun listRecursive(remotePath: String): List<Pair<String, String>> {
        val normalized = RemotePath.normalize(remotePath)
        val results = mutableListOf<Pair<String, String>>()
        val guard = TraversalGuard()
        guard.enter(normalized, 0)
        walkRecursive(normalized, "", results, guard, 1)
        return results
    }

    private fun walkRecursive(
        remotePath: String,
        relativePath: String,
        results: MutableList<Pair<String, String>>,
        guard: TraversalGuard,
        depth: Int,
    ) {
        val entries = list(remotePath)
        for (entry in entries) {
            val childRelative = if (relativePath.isEmpty()) entry.name else "$relativePath/${entry.name}"
            if (guard.canDescend(entry)) {
                // enter() is what breaks a cycle: a directory already walked in this
                // traversal, or one past the depth/entry budget, is not descended again.
                if (guard.enter(entry.path, depth)) {
                    walkRecursive(entry.path, childRelative, results, guard, depth + 1)
                }
            } else if (!entry.isDirectory) {
                if (!guard.countFile()) return
                results.add(entry.path to childRelative)
            }
        }
    }

    /**
     * Recursively lists all local files under [localPath], returning pairs of
     * (localPath, relativePath) for each file. Used by recursive folder upload (#27).
     */
    fun listLocalRecursive(localPath: java.io.File): List<Pair<java.io.File, String>> {
        val results = mutableListOf<Pair<java.io.File, String>>()
        localPath.walkTopDown().filter { it.isFile }.forEach { file ->
            val relativePath = file.relativeTo(localPath).path
            results.add(file to relativePath)
        }
        return results
    }

    /** Downloads a file's content as a UTF-8 string, capped at [maxBytes]. */
    fun downloadText(remotePath: String, maxBytes: Long = 512_000): String {
        val baos = java.io.ByteArrayOutputStream()
        channel().get(RemotePath.normalize(remotePath)).use { stream ->
            val buf = ByteArray(8192)
            var remaining = maxBytes.toInt()
            while (remaining > 0) {
                val read = stream.read(buf, 0, minOf(buf.size, remaining))
                if (read == -1) break
                baos.write(buf, 0, read)
                remaining -= read
            }
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    /** Uploads text content as UTF-8 to a remote path. */
    fun uploadText(remotePath: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val monitor = ProgressMonitor(0L) { }
        channel().put(bytes.inputStream(), RemotePath.normalize(remotePath), monitor, ChannelSftp.OVERWRITE)
    }

    /** The target of a symlink, or null when [remotePath] isn't one or it can't be read. */
    fun readlink(remotePath: String): String? =
        runCatching { channel().readlink(RemotePath.normalize(remotePath)) }.getOrNull()

    /**
     * Recursively calculates the total size in bytes of all files under [remotePath].
     * Directories themselves contribute 0; only files count. For non-directory entries
     * this returns the file's own size. (#45)
     */
    /**
     * Runs [command] over its own exec channel and returns stdout, or null if the server
     * does not allow exec, the channel fails, or it takes longer than [timeoutMs].
     *
     * Null is a first-class answer here. A server with exec disabled is a normal,
     * supported configuration — checksum verification and the free-space check both
     * degrade to "not verified" rather than blocking the transfer.
     */
    fun exec(command: String, timeoutMs: Int = EXEC_TIMEOUT_MS): String? {
        var channel: ChannelExec? = null
        return try {
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setErrStream(null)
            val stdout = channel.inputStream
            channel.connect(CONNECT_TIMEOUT_MS)

            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                while (stdout.available() > 0) {
                    val read = stdout.read(buf)
                    if (read < 0) break
                    if (out.size() + read > EXEC_MAX_OUTPUT) return out.toString(Charsets.UTF_8.name())
                    out.write(buf, 0, read)
                }
                if (channel.isClosed) break
                Thread.sleep(20)
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            null
        } finally {
            runCatching { channel?.disconnect() }
        }
    }

    /** SHA-256 of a remote file as the server computes it, or null if it cannot. */
    fun remoteSha256(remotePath: String): String? =
        exec(RemoteCommands.checksumCommand(RemotePath.normalize(remotePath)))
            ?.let { RemoteCommands.parseChecksum(it) }

    /** Bytes writable on the volume holding [remoteDir], or null if it cannot be read. */
    fun freeSpaceBytes(remoteDir: String): Long? =
        exec(RemoteCommands.freeSpaceCommand(RemotePath.normalize(remoteDir)))
            ?.let { RemoteCommands.parseAvailableBytes(it) }

    fun recursiveSize(remotePath: String): Long {
        val normalized = RemotePath.normalize(remotePath)
        val stat = runCatching { channel().stat(normalized) }.getOrNull()
        if (stat != null && !stat.isDir) return stat.size
        val guard = TraversalGuard()
        guard.enter(normalized, 0)
        return recursiveSize(normalized, guard, 1)
    }

    private fun recursiveSize(remotePath: String, guard: TraversalGuard, depth: Int): Long {
        var total = 0L
        val entries = list(remotePath)
        for (entry in entries) {
            total += if (guard.canDescend(entry)) {
                if (guard.enter(entry.path, depth)) recursiveSize(entry.path, guard, depth + 1) else 0L
            } else if (entry.isDirectory) {
                0L // a symlinked directory: counted as the link itself, never descended
            } else {
                if (!guard.countFile()) return total
                entry.sizeBytes
            }
        }
        return total
    }

    override fun close() {
        runCatching { channel?.disconnect() }
        channel = null
    }

    private fun ChannelSftp.LsEntry.toRemoteEntry(parent: String): RemoteEntry {
        val attributes: SftpATTRS = attrs
        val path = RemotePath.join(parent, filename)
        // ls() reports lstat-style attributes: a symlink pointing at a directory would
        // otherwise come back with isDir=false and be unopenable in the browser. A
        // follow-symlink stat resolves what it actually points to; isSymlink stays true
        // either way so the row still shows a link icon.
        val isDir = if (attributes.isLink) {
            runCatching { channel().stat(path).isDir }.getOrDefault(false)
        } else {
            attributes.isDir
        }
        return RemoteEntry(
            name = filename,
            path = path,
            isDirectory = isDir,
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
        private const val EXEC_TIMEOUT_MS = 15_000
        private const val EXEC_MAX_OUTPUT = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** Maps an SFTP failure onto something the transfer list can explain. */
        fun classify(t: Throwable): TransferErrorKind = when {
            // A revoked SAF grant surfaces as SecurityException from the ContentResolver.
            // It is a local problem, not a server one, and retrying cannot fix it.
            t is SecurityException -> TransferErrorKind.LOCAL_UNAVAILABLE
            t is LocalUriUnavailableException -> TransferErrorKind.LOCAL_UNAVAILABLE
            t is NotEnoughRemoteSpaceException -> TransferErrorKind.NOT_ENOUGH_REMOTE_SPACE
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
