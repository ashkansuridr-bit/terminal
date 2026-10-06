package app.terminalssh.secure.security

import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile

/** Reusable private keys are owned by the Keys screen; passwords/passphrases by hosts. */
data class CredentialReference(val ref: String, val aad: VaultAad)

object CredentialReferences {
    fun ownedBy(auth: AuthMethod): Set<CredentialReference> = when (auth) {
        is AuthMethod.Password -> auth.vaultRef.takeIf { it.isNotBlank() }
            ?.let { setOf(CredentialReference(it, VaultAad.PASSWORD)) } ?: emptySet()
        is AuthMethod.PrivateKey -> auth.passphraseVaultRef?.takeIf { it.isNotBlank() }
            ?.let { setOf(CredentialReference(it, VaultAad.PASSPHRASE)) } ?: emptySet()
    }

    fun unreferenced(previous: AuthMethod?, hosts: List<HostProfile>): Set<CredentialReference> {
        val used = hosts.flatMap { ownedBy(it.auth) }.toSet()
        return previous?.let(::ownedBy).orEmpty() - used
    }

    fun keyDependents(keyRef: String, hosts: List<HostProfile>): List<HostProfile> =
        hosts.filter { (it.auth as? AuthMethod.PrivateKey)?.keyVaultRef == keyRef }
}
