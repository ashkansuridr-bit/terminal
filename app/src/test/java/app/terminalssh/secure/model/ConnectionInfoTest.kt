package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionInfoTest {

    private fun host(
        host: String = "box.example.com",
        port: Int = 22,
        username: String = "deploy",
    ) = HostProfile(
        id = "h1",
        label = "Prod",
        host = host,
        port = port,
        username = username,
        auth = AuthMethod.PrivateKey(keyVaultRef = "super-secret-vault-ref"),
    )

    @Test fun userAtHostOmitsDefaultPort() {
        assertEquals("deploy@box.example.com", ConnectionInfo.value(host(), ConnectionInfo.Field.USER_AT_HOST))
    }

    @Test fun userAtHostKeepsNonDefaultPort() {
        assertEquals(
            "deploy@box.example.com:2222",
            ConnectionInfo.value(host(port = 2222), ConnectionInfo.Field.USER_AT_HOST),
        )
    }

    @Test fun sshCommandCarriesThePortFlag() {
        assertEquals(
            "ssh -p 2222 deploy@box.example.com",
            ConnectionInfo.value(host(port = 2222), ConnectionInfo.Field.SSH_COMMAND),
        )
        assertEquals(
            "ssh deploy@box.example.com",
            ConnectionInfo.value(host(), ConnectionInfo.Field.SSH_COMMAND),
        )
    }

    @Test fun hostPortAndUsernameArePlain() {
        val h = host()
        assertEquals("box.example.com", ConnectionInfo.value(h, ConnectionInfo.Field.HOST))
        assertEquals("22", ConnectionInfo.value(h, ConnectionInfo.Field.PORT))
        assertEquals("deploy", ConnectionInfo.value(h, ConnectionInfo.Field.USERNAME))
    }

    @Test fun noCopyableFieldEverExposesACredentialReference() {
        val h = host()
        val rendered = ConnectionInfo.fields.joinToString("\n") { ConnectionInfo.value(h, it) }
        assertFalse(rendered.contains("super-secret-vault-ref"))
        assertFalse(rendered.contains("vault", ignoreCase = true))
    }

    @Test fun fieldsIncludeTheUsefulOnes() {
        assertTrue(ConnectionInfo.Field.USER_AT_HOST in ConnectionInfo.fields)
        assertTrue(ConnectionInfo.Field.SSH_COMMAND in ConnectionInfo.fields)
    }
}
