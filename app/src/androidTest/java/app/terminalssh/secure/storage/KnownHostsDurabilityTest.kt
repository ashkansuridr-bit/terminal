package app.terminalssh.secure.storage

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real preference storage; only failed disk commits are injected at its boundary. */
@RunWith(AndroidJUnit4::class)
class KnownHostsDurabilityTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private var failures = 0
    private val original = byteArrayOf(1, 2, 3)
    private val changed = byteArrayOf(4, 5, 6)

    @Before fun reset() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = context.getSharedPreferences("known_hosts_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        failures = 0
    }

    private fun failingContext(): Context = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val delegate = prefs.edit()
                    return object : SharedPreferences.Editor by delegate {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                            delegate.putString(key, value)
                            return this
                        }
                        override fun commit(): Boolean {
                            if (failures > 0) {
                                failures--
                                // Android mutates the in-memory map even if commit fails on disk.
                                delegate.apply()
                                return false
                            }
                            return delegate.commit()
                        }
                    }
                }
            }
    }

    @Test fun approvedTrustIsDurableAndRemovalIsDurable() {
        val store = KnownHostsStore(context)
        store.put("example.test", 22, "ssh-ed25519", original)
        assertArrayEquals(original, KnownHostsStore(context).get("example.test", 22)?.key)
        store.remove("example.test", 22)
        assertNull(KnownHostsStore(context).get("example.test", 22))
    }

    @Test fun failedApprovalRestoresPreviousTrust() {
        val store = KnownHostsStore(failingContext())
        store.put("example.test", 22, "ssh-ed25519", original)
        failures = 1
        val failure = assertThrows(KnownHostCommitException::class.java) {
            store.put("example.test", 22, "ssh-ed25519", changed)
        }
        assertTrue(failure.rollbackSucceeded)
        assertArrayEquals(original, KnownHostsStore(context).get("example.test", 22)?.key)
    }

    @Test fun failedRemovalRetainsPreviousTrust() {
        val store = KnownHostsStore(failingContext())
        store.put("example.test", 22, "ssh-ed25519", original)
        failures = 1
        assertThrows(KnownHostCommitException::class.java) { store.remove("example.test", 22) }
        assertArrayEquals(original, KnownHostsStore(context).get("example.test", 22)?.key)
    }

    @Test fun failedFirstTrustDoesNotPublishNewApproval() {
        val store = KnownHostsStore(failingContext())
        failures = 1
        assertThrows(KnownHostCommitException::class.java) {
            store.put("example.test", 22, "ssh-ed25519", original)
        }
        assertNull(store.get("example.test", 22))
    }

    @Test fun failedRollbackBlocksSubsequentVerificationAndWrites() {
        val store = KnownHostsStore(failingContext())
        store.put("example.test", 22, "ssh-ed25519", original)
        failures = 2
        val failure = assertThrows(KnownHostCommitException::class.java) {
            store.put("example.test", 22, "ssh-ed25519", changed)
        }
        assertFalse(failure.rollbackSucceeded)
        assertThrows(IllegalStateException::class.java) { store.get("example.test", 22) }
        assertThrows(IllegalStateException::class.java) { store.all() }
        assertThrows(IllegalStateException::class.java) { store.remove("example.test", 22) }
        assertThrows(IllegalStateException::class.java) {
            store.put("example.test", 22, "ssh-ed25519", original)
        }
    }

    @Test fun orphanAlgorithmAndOrphanKeyAreNeverFirstUse() {
        val prefix = "host.example.test:22"
        assertTrue(prefs.edit().putString("$prefix.algorithm", "ssh-ed25519").commit())
        assertThrows(IllegalStateException::class.java) { KnownHostsStore(context).get("example.test", 22) }
        assertTrue(prefs.edit().clear().putString("$prefix.key", "AQID").commit())
        assertThrows(IllegalStateException::class.java) { KnownHostsStore(context).get("example.test", 22) }
        assertThrows(IllegalStateException::class.java) { KnownHostsStore(context).all() }
    }

    @Test fun emptyAndCorruptRecordsAreNeverFirstUse() {
        val prefix = "host.example.test:22"
        for (encoded in listOf("", "%%%", "AQID\n")) {
            assertTrue(prefs.edit().clear().putString("$prefix.algorithm", "ssh-ed25519")
                .putString("$prefix.key", encoded).commit())
            assertThrows(RuntimeException::class.java) { KnownHostsStore(context).get("example.test", 22) }
        }
    }
}
