package app.terminalssh.secure.vm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.security.VaultAad
import app.terminalssh.secure.ssh.SshSessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppViewModelHostTest {

    private lateinit var app: TerminalApp

    @Before fun clearStores() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TerminalApp
        app.getSharedPreferences("hosts_v1", 0).edit().clear().commit()
        app.vault.clearEncryptedRecords()
    }

    @Test fun addingPasswordHostPersistsProfileAndEncryptedSecret() {
        val viewModel = AppViewModel(app)
        val password = charArrayOf('s', '3', 'c', 'r', 'e', 't')
        val profile = HostProfile(
            id = "host-test",
            host = "127.0.0.1",
            username = "tester",
            auth = AuthMethod.Password(""),
        )

        assertTrue(viewModel.saveHost(profile, password))
        assertTrue(password.all { it == '\u0000' })

        val saved = viewModel.hosts.value.single()
        assertEquals("127.0.0.1", saved.host)
        val vaultRef = (saved.auth as AuthMethod.Password).vaultRef
        assertTrue(vaultRef.isNotBlank())

        val storedSecret = requireNotNull(app.vault.get(vaultRef, VaultAad.PASSWORD))
        try {
            assertArrayEquals(byteArrayOf(115, 51, 99, 114, 101, 116), storedSecret)
        } finally {
            storedSecret.fill(0)
        }
    }

    @Test fun savedHostCanEnterSessionFlowWithoutCrashingApplication() = runBlocking {
        val viewModel = AppViewModel(app)
        val profile = HostProfile(
            id = "session-test",
            host = "127.0.0.1",
            port = 1,
            username = "tester",
            auth = AuthMethod.Password(""),
        )
        assertTrue(viewModel.saveHost(profile, charArrayOf('s', '3', 'c', 'r', 'e', 't')))

        val saved = viewModel.hosts.value.single()
        val session = viewModel.openSession(saved)
        try {
            val failed = withTimeout(30_000) {
                session.state.first { it is SshSessionState.Failed }
            }
            assertTrue(failed is SshSessionState.Failed)
        } finally {
            viewModel.closeSession(session.id)
        }
    }

    @Test fun sessionPasswordIsClearedAfterOpening() {
        val viewModel = AppViewModel(app)
        val password = charArrayOf('s', '3', 'c', 'r', 'e', 't')
        val profile = HostProfile(
            id = "session-password-test",
            host = "127.0.0.1",
            port = 1,
            username = "tester",
            auth = AuthMethod.Password(""),
        )

        val session = viewModel.openSession(profile, password)
        try {
            assertTrue(password.all { it == '\u0000' })
        } finally {
            viewModel.closeSession(session.id)
        }
    }
}
