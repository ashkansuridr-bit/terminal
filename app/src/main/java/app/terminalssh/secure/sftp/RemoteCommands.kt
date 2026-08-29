package app.terminalssh.secure.sftp

/**
 * Parsing for the two shell commands the transfer layer needs. Kept apart from the
 * channel so the fiddly part — output that differs per server — is unit-testable
 * without a server.
 *
 * Both commands are best-effort. Plenty of servers restrict `exec`, run a shell that
 * prints a banner first, or ship a `df` whose columns move. None of that may fail a
 * transfer, so every parser here returns null rather than throwing, and callers treat
 * null as "unverified", never as "bad".
 */
object RemoteCommands {

    /** `sha256sum` and friends print `<hex>  <path>`; BSD `sha256 -q` prints just the hex. */
    fun parseChecksum(output: String): String? {
        for (line in output.lineSequence()) {
            val token = line.trim().substringBefore(' ').trim()
            if (token.length == 64 && token.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                return token.lowercase()
            }
        }
        return null
    }

    /**
     * Bytes still writable on the volume holding the queried path, from `df -Pk`.
     *
     * POSIX `-P` is what makes this parseable: it guarantees one line per filesystem and
     * a fixed column order, where plain `df` wraps long device names onto a second line
     * and shifts every column. The available column is the fourth, in 1K blocks.
     */
    fun parseAvailableBytes(output: String): Long? {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        // Skip the header; take the last data row, which is the filesystem for the path
        // even when the shell printed a banner above it.
        for (line in lines.asReversed()) {
            val cols = line.split(Regex("\\s+"))
            if (cols.size < 4) continue
            val availableKb = cols[3].toLongOrNull() ?: continue
            if (availableKb < 0) continue
            return availableKb * 1024L
        }
        return null
    }

    /** Single-quotes a path for a POSIX shell, so a name with spaces or `$` is safe. */
    fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    fun checksumCommand(remotePath: String): String {
        val quoted = shellQuote(remotePath)
        // Try the GNU name, then the BSD one; whichever exists answers.
        return "sha256sum $quoted 2>/dev/null || shasum -a 256 $quoted 2>/dev/null || sha256 -q $quoted 2>/dev/null"
    }

    fun freeSpaceCommand(remoteDir: String): String = "df -Pk ${shellQuote(remoteDir)} 2>/dev/null"

    /**
     * Whether an upload of [uploadBytes] fits in [availableBytes], keeping a margin so a
     * transfer cannot be the thing that fills a server's root volume completely.
     */
    fun fitsInFreeSpace(uploadBytes: Long, availableBytes: Long, marginBytes: Long = SAFETY_MARGIN): Boolean =
        uploadBytes >= 0 && availableBytes - marginBytes >= uploadBytes

    /** Leave a server enough room to still write a log line and let someone log in. */
    const val SAFETY_MARGIN = 8L * 1024 * 1024
}

/**
 * The receiving side does not have room. Carries both numbers so the UI can say how
 * much was needed rather than only that something failed.
 */
class NotEnoughRemoteSpaceException(val neededBytes: Long, val availableBytes: Long) :
    java.io.IOException("needs $neededBytes bytes, $availableBytes available")
