package app.terminalssh.secure.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.model.KeyEntry
import app.terminalssh.secure.security.CredentialReference
import app.terminalssh.secure.security.VaultAad
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostStoreCredentialTest {
    private lateinit var context: Context
    private fun host(auth: AuthMethod) = HostProfile("credential-test", host = "example.test", username = "tester", auth = auth)
    private val key = KeyEntry("key", "test", "hash", "RSA", 0L, false)

    @Before fun reset() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("hosts_v1", 0).edit().clear().commit()
    }

    @Test fun referencedKeyCannotBeDeletedAndUnlinkIsDurable() {
        val store = HostStore(context)
        store.upsertKey(key)
        store.upsert(host(AuthMethod.PrivateKey(key.id, "passphrase")))
        try { store.deleteKey(key.id); fail("referenced key was deleted") } catch (_: IllegalStateException) { }
        assertEquals(key, store.keys().single())
        store.replaceKeyForHosts(key.id, AuthMethod.Password(""))
        assertEquals(AuthMethod.Password(""), HostStore(context).hosts().single().auth)
        assertTrue(store.pendingCredentialCleanup().contains(CredentialReference("passphrase", VaultAad.PASSPHRASE)))
        store.deleteKey(key.id)
        assertTrue(HostStore(context).keys().isEmpty())
        assertTrue(store.pendingCredentialCleanup().contains(CredentialReference(key.id, VaultAad.PRIVATE_KEY)))
    }

    @Test fun failedMetadataCommitRestoresOldCredentialAndCleanupJournal() {
        val delegate = context.getSharedPreferences("hosts_v1", 0)
        var failures = 0
        val preferences = object : SharedPreferences by delegate {
            override fun edit(): SharedPreferences.Editor {
                val editor = delegate.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        if (failures > 0) {
                            failures--
                            editor.apply() // reproduces SharedPreferences memory mutation on a failed disk commit
                            return false
                        }
                        return editor.commit()
                    }
                }
            }
        }
        val wrapper = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
        }
        val store = HostStore(wrapper)
        store.upsert(host(AuthMethod.Password("old")))
        failures = 1
        try { store.saveCredentials(host(AuthMethod.Password("fresh"))); fail("commit failure hidden") }
        catch (_: IllegalStateException) { }
        assertEquals(AuthMethod.Password("old"), HostStore(context).hosts().single().auth)
        assertTrue(store.pendingCredentialCleanup().isEmpty())

        // Failure of both commit and rollback makes disk ownership unknowable. Even
        // an explicit retry must preserve ciphertext until durable metadata is reloaded.
        failures = 2
        try { store.saveCredentials(host(AuthMethod.Password("fresh"))); fail("double failure hidden") }
        catch (failure: MetadataCommitException) { assertFalse(failure.rollbackSucceeded) }
        try {
            store.credentialIsReferenced(CredentialReference("fresh", VaultAad.PASSWORD))
            fail("ambiguous ownership allowed cleanup")
        } catch (_: IllegalStateException) { }
        try { store.upsert(host(AuthMethod.Password("third"))); fail("ambiguous metadata allowed mutation") }
        catch (_: IllegalStateException) { }
    }
}
