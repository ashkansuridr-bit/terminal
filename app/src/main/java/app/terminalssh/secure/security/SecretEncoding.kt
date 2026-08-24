package app.terminalssh.secure.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.ceil

/**
 * Converts mutable character secrets to UTF-8 without first creating an immutable String.
 *
 * The caller can choose whether [chars] is wiped. The temporary encoding buffer is always
 * overwritten before returning.
 */
object SecretEncoding {
    fun utf8(chars: CharArray, wipeInput: Boolean = true): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val capacity = ceil(chars.size * encoder.maxBytesPerChar()).toInt().coerceAtLeast(1)
        val encoded = ByteBuffer.allocate(capacity)
        return try {
            val input = CharBuffer.wrap(chars)
            val first = encoder.encode(input, encoded, true)
            if (first.isError) first.throwException()
            val second = encoder.flush(encoded)
            if (second.isError) second.throwException()

            encoded.flip()
            ByteArray(encoded.remaining()).also { encoded.get(it) }
        } finally {
            encoded.array().fill(0)
            if (wipeInput) chars.fill('\u0000')
        }
    }
}
