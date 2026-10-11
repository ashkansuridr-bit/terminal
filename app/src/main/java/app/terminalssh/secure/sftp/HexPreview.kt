package app.terminalssh.secure.sftp

/**
 * Read-only preview of a remote file (F026).
 *
 * A text editor must never pull a 2 GB log into memory, and a binary file must never be
 * rendered as text and corrupted by a re-save. Both risks are handled here: [isProbablyBinary]
 * decides which view to offer, [decodeText] caps how many bytes are decoded, and [hexDump]
 * produces a classic offset/hex/ascii listing for the bytes actually fetched.
 *
 * Pure over a `ByteArray` the caller has already bounded, so it is unit-tested without a
 * server and cannot itself allocate unboundedly.
 */
object HexPreview {

    const val BYTES_PER_LINE = 16
    const val DEFAULT_SNIFF_BYTES = 4_096
    const val DEFAULT_MAX_BYTES = 64 * 1024

    data class TextPreview(
        val text: String,
        val bytesDecoded: Int,
        val truncated: Boolean,
    )

    /**
     * A NUL byte is the strongest binary signal; failing that, more than 30% of the sniffed
     * bytes being non-printable (tabs/newlines/CR excepted) is treated as binary. The sniff
     * is capped so a huge file is only sampled from its head.
     */
    fun isProbablyBinary(bytes: ByteArray, sniffBytes: Int = DEFAULT_SNIFF_BYTES): Boolean {
        val limit = minOf(bytes.size, sniffBytes.coerceAtLeast(1))
        if (limit == 0) return false
        var nonPrintable = 0
        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            val printable = b in 0x20..0x7E || b == 0x09 || b == 0x0A || b == 0x0D || b >= 0x80
            if (!printable) nonPrintable++
        }
        return nonPrintable * 100 > limit * 30
    }

    /** Decodes at most [maxBytes] bytes as UTF-8, replacing malformed sequences. */
    fun decodeText(bytes: ByteArray, maxBytes: Int = DEFAULT_MAX_BYTES): TextPreview {
        val take = minOf(bytes.size, maxBytes.coerceAtLeast(0))
        val slice = if (take == bytes.size) bytes else bytes.copyOfRange(0, take)
        return TextPreview(
            text = String(slice, Charsets.UTF_8),
            bytesDecoded = take,
            truncated = take < bytes.size,
        )
    }

    /** Classic hex dump, one line per [BYTES_PER_LINE] bytes, capped at [maxBytes]. */
    fun hexDump(bytes: ByteArray, maxBytes: Int = DEFAULT_MAX_BYTES): List<String> {
        val take = minOf(bytes.size, maxBytes.coerceAtLeast(0))
        val lines = ArrayList<String>(take / BYTES_PER_LINE + 1)
        var offset = 0
        while (offset < take) {
            val end = minOf(offset + BYTES_PER_LINE, take)
            val slice = bytes.copyOfRange(offset, end)
            val hex = buildString {
                slice.forEachIndexed { index, byte ->
                    if (index > 0) append(' ')
                    append("%02x".format(byte.toInt() and 0xFF))
                }
                // Pad short final lines so the ascii column lines up.
                repeat(BYTES_PER_LINE - slice.size) { append("   ") }
            }
            val ascii = buildString {
                slice.forEach { byte ->
                    val c = byte.toInt() and 0xFF
                    append(if (c in 0x20..0x7E) c.toChar() else '.')
                }
            }
            lines += "%08x  %s  |%s|".format(offset, hex, ascii)
            offset = end
        }
        return lines
    }
}
