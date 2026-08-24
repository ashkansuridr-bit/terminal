package app.terminalssh.secure.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the vault against the real AndroidKeyStore.
 *
 * The JVM unit tests cannot cover this: they never touch the Keystore provider, which is
 * where the vault's constraints actually live. A round trip that passed on the JVM still
 * threw "Caller-provided IV not permitted" on device for every single write.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeyStoreVaultTest {

    private lateinit var vault: AndroidKeyStoreVault

    @Before fun setUp() {
        vault = AndroidKeyStoreVault(InstrumentationRegistry.getInstrumentation().targetContext)
        vault.clearEncryptedRecords()
    }

    @After fun tearDown() {
        vault.clearEncryptedRecords()
    }

    @Test fun passwordSurvivesRoundTrip() {
        val secret = "correct horse battery staple".encodeToByteArray()
        vault.put("host-1", secret, VaultAad.PASSWORD)
        assertArrayEquals(secret, vault.get("host-1", VaultAad.PASSWORD))
    }

    @Test fun eachWriteUsesAFreshNonce() {
        val secret = "same plaintext".encodeToByteArray()
        vault.put("a", secret, VaultAad.PASSWORD)
        val first = requireNotNull(rawRecord("a"))
        vault.put("a", secret, VaultAad.PASSWORD)
        val second = requireNotNull(rawRecord("a"))
        // Identical plaintext under a randomized-encryption key must not produce
        // identical ciphertext, or the nonce is being reused.
        assertNotEquals(first, second)
    }

    @Test fun recordSealedUnderOneAadCannotOpenUnderAnother() {
        val secret = "private material".encodeToByteArray()
        vault.put("k", secret, VaultAad.PRIVATE_KEY)

        // Records are namespaced by AAD, so a plain cross-AAD read just misses the entry.
        // The property worth testing is what happens when the ciphertext itself is moved:
        // replay a private-key record into the password slot and the GCM tag must reject it
        // instead of handing back the bytes under the wrong classification.
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("vault_v1", android.content.Context.MODE_PRIVATE)
        val record = requireNotNull(
            prefs.getString("${VaultAad.PRIVATE_KEY.wireValue}:k", null)
        )
        prefs.edit().putString("${VaultAad.PASSWORD.wireValue}:k", record).commit()

        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            vault.get("k", VaultAad.PASSWORD)
        }
    }

    @Test fun deleteRemovesTheRecord() {
        vault.put("gone", "x".encodeToByteArray(), VaultAad.SNIPPET)
        vault.delete("gone", VaultAad.SNIPPET)
        assertNull(vault.get("gone", VaultAad.SNIPPET))
    }

    private fun rawRecord(ref: String): String? =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("vault_v1", android.content.Context.MODE_PRIVATE)
            .getString("${VaultAad.PASSWORD.wireValue}:$ref", null)
}
