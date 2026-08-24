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
    /** Free-form operational note, e.g. "reboot needs the ops on-call". Never a secret. */
    val notes: String = "",
    /** Which environment this server belongs to, shown as a colour in the list. */
    val environment: Environment = Environment.NONE,
    /** Reconnect attempts before giving up; per-host because a flaky VPS is not a LAN box. */
    val maxReconnectAttempts: Int = DEFAULT_RECONNECT_ATTEMPTS,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port out of range" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(maxReconnectAttempts in 0..MAX_RECONNECT_ATTEMPTS) { "reconnect attempts out of range" }
    }

    val displayName: String get() = if (label.isNotBlank()) label else host
    val subtitle: String get() = "$username@$host" + if (port != 22) ":$port" else ""

    fun matches(query: String): Boolean = searchScore(query) > FuzzyMatch.NO_MATCH

    /**
     * Best fuzzy score across every searchable field, so the host list can be ordered by
     * relevance. Notes are searched too: "the box with the flaky disk" is often how a
     * server is actually remembered.
     */
    fun searchScore(query: String): Int {
        if (query.isBlank()) return 1
        return maxOf(
            FuzzyMatch.score(query, label),
            FuzzyMatch.score(query, host),
            FuzzyMatch.score(query, username),
            FuzzyMatch.score(query, group),
            FuzzyMatch.score(query, notes),
            tags.maxOfOrNull { FuzzyMatch.score(query, it) } ?: FuzzyMatch.NO_MATCH,
        )
    }

    companion object {
        const val DEFAULT_RECONNECT_ATTEMPTS = 3
        const val MAX_RECONNECT_ATTEMPTS = 10
    }
}

/** Environment banding. Colour lives in the theme; this stays a plain data enum. */
enum class Environment { NONE, DEVELOPMENT, STAGING, PRODUCTION }

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
