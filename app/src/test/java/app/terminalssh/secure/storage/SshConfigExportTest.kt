package app.terminalssh.secure.storage

import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SshConfigExportTest {

    private fun profile(
        label: String = "prod",
        host: String = "db.example.com",
        port: Int = 22,
        username: String = "deploy",
        notes: String = "",
        auth: AuthMethod = AuthMethod.Password("vault-ref"),
    ) = HostProfile(
        id = "id-$label", label = label, host = host, port = port,
        username = username, auth = auth, notes = notes,
    )

    @Test fun exportedConfigParsesBackToTheSameHosts() {
        val original = listOf(
            profile(label = "prod", host = "db.example.com", port = 2222),
            profile(label = "web", host = "web.example.com", username = "root"),
        )

        val reparsed = SshConfigImport.parse(SshConfigExport.render(original))

        assertEquals(original.size, reparsed.size)
        original.zip(reparsed).forEach { (before, after) ->
            assertEquals(before.host, after.host)
            assertEquals(before.port, after.port)
            assertEquals(before.username, after.username)
        }
    }

    @Test fun neverWritesSecretsOrVaultReferences() {
        val rendered = SshConfigExport.render(
            listOf(
                profile(auth = AuthMethod.Password("super-secret-vault-ref")),
                profile(label = "keyed", auth = AuthMethod.PrivateKey("key-ref", "passphrase-ref")),
            ),
        )
        assertFalse("vault-ref" in rendered)
        assertFalse("super-secret-vault-ref" in rendered)
        assertFalse("key-ref" in rendered)
        assertFalse("passphrase-ref" in rendered)
        assertFalse("IdentityFile" in rendered)
        assertFalse("Password" in rendered)
    }

    @Test fun labelWithWhitespaceFallsBackToTheAddress() {
        // "Host Prod DB" would parse as two patterns, so the address is used instead.
        val rendered = SshConfigExport.render(listOf(profile(label = "Prod DB")))
        assertTrue("Host db.example.com" in rendered, rendered)
        assertEquals("db.example.com", SshConfigImport.parse(rendered).single().host)
    }

    @Test fun labelWithGlobCharactersFallsBackToTheAddress() {
        // A "*" alias would become a defaults block, which the importer skips entirely.
        val rendered = SshConfigExport.render(listOf(profile(label = "prod-*")))
        assertTrue("Host db.example.com" in rendered, rendered)
        assertEquals(1, SshConfigImport.parse(rendered).size)
    }

    @Test fun defaultPortIsOmittedButCustomPortIsWritten() {
        assertFalse("Port 22" in SshConfigExport.render(listOf(profile(port = 22))))
        assertTrue("Port 2222" in SshConfigExport.render(listOf(profile(port = 2222))))
    }

    @Test fun multiLineNotesStayCommentedOnEveryLine() {
        val rendered = SshConfigExport.render(
            listOf(profile(notes = "reboot needs ops\ndisk is flaky")),
        )
        assertTrue("# reboot needs ops" in rendered, rendered)
        assertTrue("# disk is flaky" in rendered, rendered)
        // The notes must not leak into the parsed result as directives.
        assertEquals(1, SshConfigImport.parse(rendered).size)
    }

    @Test fun emptyListStillProducesAValidEmptyConfig() {
        val rendered = SshConfigExport.render(emptyList())
        assertTrue(SshConfigImport.parse(rendered).isEmpty())
    }
}
