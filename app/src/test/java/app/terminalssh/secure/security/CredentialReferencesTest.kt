package app.terminalssh.secure.security

import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialReferencesTest {
    private fun host(id: String, auth: AuthMethod) = HostProfile(id, host = "example.test", username = "user", auth = auth)

    @Test fun sharedPasswordMustNotBeRetired() {
        val auth = AuthMethod.Password("shared")
        assertTrue(CredentialReferences.unreferenced(auth, listOf(host("other", auth))).isEmpty())
    }

    @Test fun passwordAndPassphraseHaveDifferentVaultNamespaces() {
        val old = AuthMethod.Password("same")
        val next = listOf(host("h", AuthMethod.PrivateKey("key", "same")))
        assertEquals(setOf(CredentialReference("same", VaultAad.PASSWORD)), CredentialReferences.unreferenced(old, next))
    }

    @Test fun reusablePrivateKeyIsNeverRetiredByHostChange() {
        assertEquals(setOf(CredentialReference("phrase", VaultAad.PASSPHRASE)),
            CredentialReferences.unreferenced(AuthMethod.PrivateKey("key", "phrase"), emptyList()))
    }

    @Test fun dependencyListIncludesEveryUsingHostOnly() {
        val first = host("1", AuthMethod.PrivateKey("key"))
        val second = host("2", AuthMethod.PrivateKey("key", "phrase"))
        assertEquals(listOf(first, second), CredentialReferences.keyDependents("key", listOf(
            first, host("password", AuthMethod.Password("key")), second, host("other-key", AuthMethod.PrivateKey("other")))))
    }

    @Test fun blankPromptPasswordHasNoStoredSecret() {
        assertTrue(CredentialReferences.ownedBy(AuthMethod.Password("")).isEmpty())
    }
}
