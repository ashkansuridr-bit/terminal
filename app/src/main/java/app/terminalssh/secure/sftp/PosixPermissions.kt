package app.terminalssh.secure.sftp

/**
 * POSIX mode bits as a 9-bit int (owner/group/other × read/write/execute), the shape
 * `ChannelSftp.chmod(int, String)` takes. Deliberately ignores setuid/setgid/sticky —
 * the editor built on this only exposes the common rwx grid, not those rarer bits.
 */
object PosixPermissions {

    private const val RWX = "rwxrwxrwx"

    /**
     * Parses an `ls -l`-style string like `-rwxr-xr--` into its 9-bit mode. Lenient about
     * the special-bit characters (`s`/`S`/`t`/`T`) some servers use in the execute
     * position — they're read as "execute set" for lowercase, "not set" for uppercase,
     * since this editor only seeds its checkboxes from this value, never round-trips it.
     * Returns null when the string isn't a recognizable 10-character permission string.
     */
    fun parse(permissionsString: String): Int? {
        if (permissionsString.length != 10) return null
        val bits = permissionsString.substring(1)
        var mode = 0
        for (i in 0 until 9) {
            val c = bits[i]
            val set = when {
                c == RWX[i] -> true
                i % 3 == 2 && (c == 's' || c == 't') -> true
                else -> false
            }
            if (set) mode = mode or (1 shl (8 - i))
        }
        return mode
    }

    /** The reverse of [parse]'s common case — no special bits, just `rwxr-xr--`. */
    fun format(mode: Int): String {
        val sb = StringBuilder(9)
        for (i in 0 until 9) {
            val bit = 1 shl (8 - i)
            sb.append(if (mode and bit != 0) RWX[i] else '-')
        }
        return sb.toString()
    }

    fun toOctalString(mode: Int): String = Integer.toOctalString(mode).padStart(3, '0')

    /** [text] must be 1-3 octal digits representing a value that fits in 9 bits. */
    fun parseOctal(text: String): Int? {
        if (text.isEmpty() || text.length > 3 || text.any { it !in '0'..'7' }) return null
        return text.toIntOrNull(8)?.takeIf { it in 0..0b111_111_111 }
    }

    /** `user  group` parsed from `LsEntry.getLongname()`'s `ls -l`-style third/fourth
     *  whitespace-separated fields, or null when the line doesn't have that shape (some
     *  servers omit longname owner/group entirely). */
    fun ownerGroup(longname: String?): String? {
        val fields = longname?.trim()?.split(Regex("\\s+")) ?: return null
        if (fields.size < 4) return null
        return "${fields[2]} ${fields[3]}"
    }
}
