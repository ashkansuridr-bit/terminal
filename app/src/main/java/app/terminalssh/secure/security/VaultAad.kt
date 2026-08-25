package app.terminalssh.secure.security

enum class VaultAad(val wireValue: String) {
    PASSWORD("terminalssh:v1:password"),
    PRIVATE_KEY("terminalssh:v1:private-key"),
    PASSPHRASE("terminalssh:v1:passphrase"),
    SNIPPET("terminalssh:v1:snippet"),

    /**
     * API credential for a coding agent. A distinct AAD from [PASSWORD] so a record
     * cannot be decrypted as the wrong kind even if the reference were confused.
     */
    AGENT_API_KEY("terminalssh:v1:agent-api-key");

    fun bytes(): ByteArray = wireValue.encodeToByteArray()
}
