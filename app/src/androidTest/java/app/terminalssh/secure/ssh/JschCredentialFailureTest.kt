package app.terminalssh.secure.ssh

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.security.AndroidKeyStoreVault
import app.terminalssh.secure.storage.HostStore
import app.terminalssh.secure.storage.KnownHostsStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual adapter's pre-network failure paths; it does not fake SSH success. */
@RunWith(AndroidJUnit4::class)
class JschCredentialFailureTest {
    private lateinit var client: JschSshClient
    private lateinit var hosts: HostStore

    @Before fun setup() {
        val delegate = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "ssh-failure-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(delegate) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                delegate.getSharedPreferences(prefix + name, mode)
        }
        hosts = HostStore(context)
        client = JschSshClient(AndroidKeyStoreVault(context), KnownHostsStore(context), hosts)
    }

    private fun host(id: String = "target", jump: String = "", auth: AuthMethod = AuthMethod.Password("")) =
        HostProfile(id, host = "unresolved.invalid", username = "tester", auth = auth, jumpHostId = jump)

    private fun assertBlocked(profile: HostProfile) {
        val override = byteArrayOf(71, 72, 73)
        try {
            client.connect(profile, 80, 24, passwordOverride = override)
            fail("Configured hop must not connect directly")
        } catch (failure: JumpHostUnavailable) {
            assertEquals(ConnectionErrorKind.JUMP_HOST_UNAVAILABLE, ConnectionError.classify(failure))
        } finally {
            assertTrue("Early failure left caller credential in memory", override.all { it == 0.toByte() })
        }
    }

    @Test fun missingJumpProfileNeverFallsBackToDirectConnection() = assertBlocked(host(jump = "missing"))

    @Test fun passwordJumpNeverFallsBackToDirectConnection() {
        hosts.upsert(host("hop"))
        assertBlocked(host(jump = "hop"))
    }

    @Test fun privateKeyJumpRequiresVerifiedHopTransport() {
        hosts.upsert(host("hop", auth = AuthMethod.PrivateKey("key-not-present")))
        assertBlocked(host(jump = "hop"))
    }

    @Test fun selfReferenceNeverFallsBackToDirectConnection() = assertBlocked(host(jump = "target"))

    @Test fun nestedJumpNeverFallsBackToDirectConnection() {
        hosts.upsert(host("hop", jump = "another-hop", auth = AuthMethod.PrivateKey("key-not-present")))
        assertBlocked(host(jump = "hop"))
    }

    @Test fun missingPrivateKeyStillWipesPasswordOverride() {
        val override = byteArrayOf(71, 72, 73)
        try {
            client.connect(host(auth = AuthMethod.PrivateKey("missing")), 80, 24, passwordOverride = override)
            fail("Missing key should fail before socket creation")
        } catch (failure: IllegalArgumentException) {
            assertEquals("missing private key", failure.message)
        } finally {
            assertTrue(override.all { it == 0.toByte() })
        }
    }
}
