package app.terminalssh.secure.security

import java.io.InputStream

/** Memory-bounded reader for secret files such as private keys. */
object SecretIo {
    fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val scratch = ByteArray(maxBytes + 1)
        var total = 0
        return try {
            while (total < scratch.size) {
                val read = input.read(scratch, total, scratch.size - total)
                if (read < 0) break
                if (read == 0) {
                    val next = input.read()
                    if (next < 0) break
                    scratch[total++] = next.toByte()
                } else {
                    total += read
                }
            }
            require(total <= maxBytes) { "secret exceeds size limit" }
            scratch.copyOf(total)
        } finally {
            scratch.fill(0)
        }
    }
}
