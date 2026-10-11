package app.terminalssh.secure.sftp

import java.io.ByteArrayOutputStream
import java.io.InputStream

const val MAX_EDIT_BYTES = 512_000L

data class TextLoadResult(val content: String, val truncated: Boolean, val totalSize: Long?) {
    fun requireEditable() {
        if (truncated) throw TextEditTooLargeException(this)
    }
}

/** Carries only a bounded read-only preview, never permission to save it. */
class TextEditTooLargeException(val preview: TextLoadResult) :
    java.io.IOException("Remote text exceeds the editor limit")

/** A bounded read-only byte sample: it can be shown, but never written back. */
class BytePreview(val bytes: ByteArray, val truncated: Boolean)

object BoundedText {

    /**
     * Reads at most [maxBytes] + 1 so truncation can be *proven* rather than inferred from
     * server metadata, then reports whether the source had more.
     */
    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        require(maxBytes in 1..MAX_EDIT_BYTES) { "Invalid preview limit" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = maxBytes.toInt() + 1
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) break
                output.write(byte)
                remaining--
            } else {
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        return output.toByteArray()
    }

    fun read(input: InputStream, maxBytes: Long = MAX_EDIT_BYTES): TextLoadResult {
        val bytes = readBounded(input, maxBytes)
        val truncated = bytes.size > maxBytes
        val length = minOf(bytes.size, maxBytes.toInt())
        // Complete editable files must round-trip without replacement characters.
        // Partial previews may end in the middle of a UTF-8 character and are read-only.
        val text = if (truncated) String(bytes, 0, length, Charsets.UTF_8)
        else Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        return TextLoadResult(text, truncated, if (truncated) null else bytes.size.toLong())
    }

    /**
     * Reads a bounded byte sample for the read-only preview (F026). Unlike [read] the raw
     * bytes are kept, because deciding text-vs-hex requires looking at the bytes themselves —
     * a decoded string has already lost the NUL that gives a binary file away.
     */
    fun readBytes(input: InputStream, maxBytes: Long = HexPreview.DEFAULT_MAX_BYTES.toLong()): BytePreview {
        val bytes = readBounded(input, maxBytes)
        val truncated = bytes.size > maxBytes
        val length = minOf(bytes.size, maxBytes.toInt())
        return BytePreview(if (truncated) bytes.copyOf(length) else bytes, truncated)
    }
}
