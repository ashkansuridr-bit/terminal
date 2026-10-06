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

    @Test fun deletingPrivateKeyHostRemovesStoredPassphrase() {
        val viewModel = AppViewModel(app)
        val passphraseBytes = "passphrase-secret".encodeToByteArray()
        val passphraseRef = "passphrase-ref-test"
        app.vault.put(passphraseRef, passphraseBytes, VaultAad.PASSPHRASE)
        val profile = HostProfile(
            id = "key-host-test",
            host = "127.0.0.1",
            username = "tester",
            auth = AuthMethod.PrivateKey(keyVaultRef = "key-ref-test", passphraseVaultRef = passphraseRef),
        )
        app.hosts.upsert(profile)

        assertTrue(app.vault.get(passphraseRef, VaultAad.PASSPHRASE) != null)

        viewModel.deleteHost(profile)

        assertTrue(app.vault.get(passphraseRef, VaultAad.PASSPHRASE) == null)
        assertTrue(viewModel.hosts.value.none { it.id == profile.id })
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
    @Test fun passwordReplacementUsesFreshReferenceAndDeletesOldCiphertext() {
        val viewModel = AppViewModel(app)
        val profile = HostProfile("replace", host = "127.0.0.1", username = "tester", auth = AuthMethod.Password(""))
        assertTrue(viewModel.saveHost(profile, charArrayOf('o', 'l', 'd')))
        val saved = viewModel.hosts.value.single()
        val oldRef = (saved.auth as AuthMethod.Password).vaultRef
        assertTrue(viewModel.saveHost(saved, charArrayOf('n', 'e', 'w')))
        val newRef = (viewModel.hosts.value.single().auth as AuthMethod.Password).vaultRef
        assertTrue(oldRef != newRef)
        assertTrue(app.vault.get(oldRef, VaultAad.PASSWORD) == null)
        val stored = requireNotNull(app.vault.get(newRef, VaultAad.PASSWORD))
        try { assertArrayEquals(byteArrayOf(110, 101, 119), stored) } finally { stored.fill(0) }
        assertTrue(app.hosts.pendingCredentialCleanup().isEmpty())
    }

    @Test fun authenticationChangesRetireOnlyUnreferencedHostSecrets() {
        val viewModel = AppViewModel(app)
        val profile = HostProfile("switch", host = "127.0.0.1", username = "tester", auth = AuthMethod.Password(""))
        assertTrue(viewModel.saveHost(profile, charArrayOf('p')))
        val saved = viewModel.hosts.value.single()
        val oldRef = (saved.auth as AuthMethod.Password).vaultRef
        assertTrue(viewModel.saveHost(saved.copy(auth = AuthMethod.PrivateKey("key")), null, charArrayOf('a')))
        assertTrue(app.vault.get(oldRef, VaultAad.PASSWORD) == null)
        val withPhrase = viewModel.hosts.value.single()
        val oldPhrase = requireNotNull((withPhrase.auth as AuthMethod.PrivateKey).passphraseVaultRef)
        val chars = charArrayOf('b')
        assertTrue(viewModel.saveHost(withPhrase, null, chars))
        assertTrue(chars.all { it == '\u0000' })
        assertTrue(app.vault.get(oldPhrase, VaultAad.PASSPHRASE) == null)
        val newPhrase = requireNotNull((viewModel.hosts.value.single().auth as AuthMethod.PrivateKey).passphraseVaultRef)
        assertTrue(viewModel.saveHost(viewModel.hosts.value.single().copy(auth = AuthMethod.Password("")), charArrayOf('c')))
        assertTrue(app.vault.get(newPhrase, VaultAad.PASSPHRASE) == null)
    }

    @Test fun startupRetriesCleanupRecordedBeforeInterruptedFreshSecretWrite() {
        val ref = "interrupted-fresh-ref"
        app.hosts.scheduleCredentialCleanup(app.terminalssh.secure.security.CredentialReference(ref, VaultAad.PASSWORD))
        val bytes = byteArrayOf(97)
        try { app.vault.put(ref, bytes, VaultAad.PASSWORD) } finally { bytes.fill(0) }
        AppViewModel(app)
        assertTrue(app.vault.get(ref, VaultAad.PASSWORD) == null)
        assertTrue(app.hosts.pendingCredentialCleanup().isEmpty())
    }

}
