package app.terminalssh.secure.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultDurabilityTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private var failures = 0

    @Before fun reset() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = context.getSharedPreferences("vault_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        failures = 0
    }

    private fun vault(): AndroidKeyStoreVault {
        val wrapper = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                object : SharedPreferences by prefs {
                    override fun edit(): SharedPreferences.Editor {
                        val delegate = prefs.edit()
                        return object : SharedPreferences.Editor by delegate {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                delegate.putString(key, value)
                                return this
                            }
                            override fun clear(): SharedPreferences.Editor {
                                delegate.clear()
                                return this
                            }
                            override fun remove(key: String?): SharedPreferences.Editor {
                                delegate.remove(key)
                                return this
                            }
                            override fun commit(): Boolean {
                                if (failures > 0) {
                                    failures--
                                    delegate.apply()
                                    return false
                                }
                                return delegate.commit()
                            }
                        }
                    }
                }
        }
        return AndroidKeyStoreVault(wrapper)
    }

    @Test fun failedClearRestoresCiphertextAndThrows() {
        val vault = vault()
        val secret = byteArrayOf(41, 42, 43)
        try {
            vault.put("durability", secret, VaultAad.PASSWORD)
            val previous = prefs.all.toMap()
            failures = 1
            assertThrows(IllegalStateException::class.java) { vault.clearEncryptedRecords() }
            assertEquals(previous, prefs.all)
            val reopened = requireNotNull(AndroidKeyStoreVault(context).get("durability", VaultAad.PASSWORD))
            try { assertArrayEquals(secret, reopened) } finally { reopened.fill(0) }
        } finally { secret.fill(0) }
    }

    @Test fun uncertainClearBlocksReadsWritesAndFurtherDeletion() {
        val vault = vault()
        val secret = byteArrayOf(41, 42, 43)
        try {
            vault.put("durability", secret, VaultAad.PASSWORD)
            failures = 2
            assertThrows(IllegalStateException::class.java) { vault.clearEncryptedRecords() }
            assertThrows(IllegalStateException::class.java) { vault.get("durability", VaultAad.PASSWORD) }
            assertThrows(IllegalStateException::class.java) { vault.put("new", secret, VaultAad.PASSWORD) }
            assertThrows(IllegalStateException::class.java) { vault.delete("durability", VaultAad.PASSWORD) }
            assertThrows(IllegalStateException::class.java) { vault.clearEncryptedRecords() }
        } finally { secret.fill(0) }
    }

    @Test fun successfulClearIsObservedByReopenedVault() {
        val vault = vault()
        val secret = byteArrayOf(41, 42, 43)
        try {
            vault.put("durability", secret, VaultAad.PASSWORD)
            vault.clearEncryptedRecords()
            assertNull(AndroidKeyStoreVault(context).get("durability", VaultAad.PASSWORD))
        } finally { secret.fill(0) }
    }
}
