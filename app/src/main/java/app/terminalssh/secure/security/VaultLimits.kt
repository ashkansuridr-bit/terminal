package app.terminalssh.secure.security

object VaultLimits {
    const val MAX_PRIVATE_KEY_BYTES = 256 * 1024
    const val MAX_SNIPPET_BYTES = 64 * 1024

    fun requirePrivateKeySize(bytes: ByteArray) =
        require(bytes.size <= MAX_PRIVATE_KEY_BYTES) { "private key exceeds limit" }

    fun requireSnippetSize(bytes: ByteArray) =
        require(bytes.size <= MAX_SNIPPET_BYTES) { "snippet exceeds limit" }
}
