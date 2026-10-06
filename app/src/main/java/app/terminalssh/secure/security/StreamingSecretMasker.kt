package app.terminalssh.secure.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** One instance per output stream. Never emits a possible credential prefix prematurely. */
class StreamingSecretMasker {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var carry = ByteArray(0)
    private val pending = StringBuilder()
    private var suppressToken = false
    private var suppressPrivateKey = false
    private val endMarker = StringBuilder()
    private var finished = false
    internal val bufferedCharacters: Int get() = pending.length + endMarker.length

    fun accept(bytes: ByteArray): ByteArray {
        check(!finished)
        return decode(bytes, false).toByteArray(Charsets.UTF_8)
    }

    fun finish(): ByteArray {
        check(!finished)
        finished = true
        val output = decode(ByteArray(0), true) + pending.toString()
        pending.setLength(0)
        endMarker.setLength(0)
        return output.toByteArray(Charsets.UTF_8)
    }

    private fun decode(bytes: ByteArray, end: Boolean): String {
        val joined = ByteArray(carry.size + bytes.size)
        carry.copyInto(joined)
        bytes.copyInto(joined, carry.size)
        carry.fill(0)
        val input = ByteBuffer.wrap(joined)
        val chars = CharBuffer.allocate(joined.size + 2)
        decoder.decode(input, chars, end).throwExceptionIfError()
        carry = ByteArray(input.remaining())
        input.get(carry)
        joined.fill(0)
        if (end) decoder.flush(chars).throwExceptionIfError()
        chars.flip()
        val result = buildString {
            while (chars.hasRemaining()) consume(chars.get(), this)
        }
        chars.array().fill('\u0000')
        return result
    }

    private fun java.nio.charset.CoderResult.throwExceptionIfError() {
        if (isError) throwException()
        check(!isOverflow) { "UTF-8 decoder output overflow" }
    }

    private fun consume(char: Char, output: StringBuilder) {
        if (suppressPrivateKey) {
            endMarker.append(char)
            if (endMarker.length > MAX_CANDIDATE) endMarker.deleteCharAt(0)
            if (PRIVATE_END.containsMatchIn(endMarker)) {
                suppressPrivateKey = false
                endMarker.setLength(0)
            }
            return
        }
        if (suppressToken) {
            if (tokenChar(char)) return
            suppressToken = false
        }
        pending.append(char)
        while (pending.isNotEmpty()) {
            val text = pending.toString()
            val finding = SecretScanner.scan(text).firstOrNull { it.start == 0 }
            if (finding != null) {
                output.append('[').append(finding.label).append(" hidden]")
                suppressPrivateKey = finding.label == "private key"
                suppressToken = !suppressPrivateKey
                pending.setLength(0)
                return
            }
            if (possible(text)) {
                // A malformed, unbounded PEM header must not grow the terminal buffer.
                if (pending.length >= MAX_CANDIDATE) {
                    output.append("[possible private key hidden]")
                    suppressPrivateKey = true
                    pending.setLength(0)
                }
                return
            }
            output.append(pending[0])
            pending.deleteCharAt(0)
        }
    }

    private fun possible(text: String): Boolean {
        if (PREFIXES.any { it.startsWith(text) }) return true
        if (text.startsWith("-----BEGIN ")) {
            val body = text.removePrefix("-----BEGIN ")
            return body.all { it in 'A'..'Z' || it == ' ' || it == '-' }
        }
        return PREFIXES.filter { it != "-----BEGIN " }.any { prefix ->
            text.startsWith(prefix) && text.substring(prefix.length).all { char ->
                when {
                    prefix == "AKIA" || prefix == "ASIA" -> char in 'A'..'Z' || char in '0'..'9'
                    prefix.startsWith("gh") -> char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'
                    else -> tokenChar(char)
                }
            }
        }
    }

    companion object {
        private const val MAX_CANDIDATE = 128
        private val PREFIXES = listOf("sk-", "ghp_", "gho_", "ghu_", "ghs_", "ghr_", "AKIA", "ASIA", "AIza", "xoxb-", "xoxa-", "xoxp-", "xoxr-", "xoxs-", "-----BEGIN ")
        private val PRIVATE_END = Regex("-----END [A-Z ]*PRIVATE KEY-----")
        private fun tokenChar(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-'
    }
}
