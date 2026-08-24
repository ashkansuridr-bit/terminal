package app.terminalssh.secure.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SshSessionSecretLifetimeTest {

    @Test fun terminalConnectionFailureClearsPendingPassword() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as TerminalApp
        val password = byteArrayOf(115, 51, 99, 114, 101, 116)
        val session = SshSession(
            id = "secret-lifetime-test",
            profile = HostProfile(
                id = "secret-lifetime-host",
                host = "127.0.0.1",
                port = 1,
                username = "tester",
                auth = AuthMethod.Password(""),
            ),
            client = app.client,
            keepAlive = false,
            onClipboardCopy = {},
            onPasteRequest = {},
        )

        try {
            session.connect(password)
            withTimeout(30_000) {
                session.state.first { it is SshSessionState.Failed }
            }
            assertTrue(password.all { it == 0.toByte() })
        } finally {
            session.destroy()
        }
    }
}
