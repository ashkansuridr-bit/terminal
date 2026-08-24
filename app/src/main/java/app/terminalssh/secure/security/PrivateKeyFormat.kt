package app.terminalssh.secure.security

/**
 * Minimal container-format detection without decoding private-key bytes into an immutable String.
 * This is intentionally only a format gate; cryptographic parsing still belongs to JSch.
 */
object PrivateKeyFormat {
    fun detect(bytes: ByteArray): String {
        return when {
            containsAscii(bytes, "-----BEGIN OPENSSH PRIVATE KEY-----") -> "openssh"
            containsAscii(bytes, "-----BEGIN RSA PRIVATE KEY-----") -> "rsa"
            containsAscii(bytes, "-----BEGIN EC PRIVATE KEY-----") -> "ecdsa"
            PEM_HEADERS.any { containsAscii(bytes, it) } -> "pem"
            else -> throw IllegalArgumentException("not an OpenSSH/PEM private key")
        }
    }

    private val PEM_HEADERS = arrayOf(
        "-----BEGIN PRIVATE KEY-----",
        "-----BEGIN ENCRYPTED PRIVATE KEY-----",
        "-----BEGIN DSA PRIVATE KEY-----",
    )

    internal fun containsAscii(bytes: ByteArray, needle: String): Boolean {
        if (needle.isEmpty()) return true
        if (bytes.size < needle.length) return false
        outer@ for (start in 0..bytes.size - needle.length) {
            for (i in needle.indices) {
                if (bytes[start + i] != needle[i].code.toByte()) continue@outer
            }
            return true
        }
        return false
    }
}
