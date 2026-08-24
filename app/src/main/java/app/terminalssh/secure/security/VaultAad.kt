package app.terminalssh.secure.security

enum class VaultAad(val wireValue: String) {
    PASSWORD("terminalssh:v1:password"),
    PRIVATE_KEY("terminalssh:v1:private-key"),
    PASSPHRASE("terminalssh:v1:passphrase"),
    SNIPPET("terminalssh:v1:snippet");

    fun bytes(): ByteArray = wireValue.encodeToByteArray()
}
