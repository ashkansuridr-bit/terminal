package app.terminalssh.secure.model

/**
 * A saved server. Secrets are never stored here: [auth] only carries vault references,
 * and the vault itself keeps ciphertext under an AndroidKeyStore-wrapped AES-GCM key.
 */
data class HostProfile(
    val id: String,
    val label: String = "",
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: AuthMethod,
    val hostKeyPolicy: HostKeyPolicy = HostKeyPolicy.TRUST_ON_FIRST_USE,
    val group: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val lastConnectedAt: Long = 0L,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port out of range" }
        require(username.isNotBlank()) { "username must not be blank" }
    }

    val displayName: String get() = if (label.isNotBlank()) label else host
    val subtitle: String get() = "$username@$host" + if (port != 22) ":$port" else ""

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return label.lowercase().contains(q) ||
            host.lowercase().contains(q) ||
            username.lowercase().contains(q) ||
            group.lowercase().contains(q) ||
            tags.any { it.lowercase().contains(q) }
    }
}

sealed interface AuthMethod {
    /** [vaultRef] may be blank, meaning "ask for the password at connect time". */
    data class Password(val vaultRef: String) : AuthMethod
    data class PrivateKey(val keyVaultRef: String, val passphraseVaultRef: String? = null) : AuthMethod
}

enum class HostKeyPolicy { STRICT, TRUST_ON_FIRST_USE }

/** A private key entry shown in the Keys screen. The key bytes live in the vault. */
data class KeyEntry(
    val id: String,
    val name: String,
    val fingerprint: String,
    val algorithm: String,
    val createdAt: Long,
    val hasPassphrase: Boolean,
)


/**
 * Non-secret snippet metadata. The command bytes are stored only in the encrypted vault.
 */
data class SnippetEntry(
    val id: String,
    val name: String,
    val createdAt: Long,
)
