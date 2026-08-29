package app.terminalssh.secure.sftp

import java.nio.charset.Charset

/**
 * Decoding remote text, and putting line endings back the way they were (#29).
 *
 * Two things used to corrupt a file silently. A non-UTF-8 file — a Persian config saved
 * as windows-1256, which is still common — was decoded as UTF-8, shown as mojibake, and
 * written back as mojibake. And a file with CRLF endings, edited on a phone that inserts
 * LF, came back with mixed endings that break shell scripts in ways that are miserable
 * to debug.
 */
object TextEncoding {

    enum class LineEnding { LF, CRLF, MIXED, NONE }

    val SUPPORTED: List<String> = listOf("UTF-8", "windows-1256", "ISO-8859-1", "windows-1252", "UTF-16")

    /** Bytes that cannot be valid UTF-8, checked before falling back to a guess. */
    fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val extra = when {
                b <= 0x7F -> 0
                b in 0xC2..0xDF -> 1
                b in 0xE0..0xEF -> 2
                b in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + extra >= bytes.size) return false
            for (j in 1..extra) {
                if ((bytes[i + j].toInt() and 0xC0) != 0x80) return false
            }
            i += extra + 1
        }
        return true
    }

    /**
     * Best guess at the charset. UTF-8 wins whenever the bytes are valid UTF-8, because a
     * false positive there is impossible for anything but pure ASCII, where it does not
     * matter. Everything else falls back to [fallback] rather than guessing between
     * single-byte encodings that are indistinguishable by inspection.
     */
    fun detect(bytes: ByteArray, fallback: String = "UTF-8"): String = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> "UTF-8"
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> "UTF-16"
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> "UTF-16"
        isValidUtf8(bytes) -> "UTF-8"
        else -> fallback
    }

    /** Decodes with [charsetName], stripping a BOM so it does not appear as a character. */
    fun decode(bytes: ByteArray, charsetName: String): String {
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        val text = String(bytes, charset)
        return text.removePrefix("\uFEFF")
    }

    fun encode(text: String, charsetName: String): ByteArray {
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        return text.toByteArray(charset)
    }

    fun detectLineEnding(text: String): LineEnding {
        val crlf = Regex("\r\n").findAll(text).count()
        val totalLf = text.count { it == '\n' }
        val loneLf = totalLf - crlf
        return when {
            crlf == 0 && loneLf == 0 -> LineEnding.NONE
            crlf > 0 && loneLf > 0 -> LineEnding.MIXED
            crlf > 0 -> LineEnding.CRLF
            else -> LineEnding.LF
        }
    }

    /** Normalises every ending to [target], via LF so mixed input converges. */
    fun convertLineEndings(text: String, target: LineEnding): String {
        val lf = text.replace("\r\n", "\n").replace("\r", "\n")
        return when (target) {
            LineEnding.CRLF -> lf.replace("\n", "\r\n")
            else -> lf
        }
    }

    /**
     * Writes [text] back in the ending style [original] used, so editing one line of a
     * CRLF file does not rewrite every line of it.
     */
    fun preserveOriginalEndings(text: String, original: LineEnding): String =
        if (original == LineEnding.CRLF) convertLineEndings(text, LineEnding.CRLF)
        else convertLineEndings(text, LineEnding.LF)
}
