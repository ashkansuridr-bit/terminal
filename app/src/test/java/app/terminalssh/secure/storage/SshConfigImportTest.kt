package app.terminalssh.secure.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SshConfigImportTest {

    @Test fun parsesATypicalConfig() {
        val hosts = SshConfigImport.parse(
            """
            Host prod
                HostName db.example.com
                User deploy
                Port 2222

            Host web
                HostName web.example.com
                User root
            """.trimIndent(),
        )

        assertEquals(2, hosts.size)
        assertEquals("db.example.com", hosts[0].host)
        assertEquals("deploy", hosts[0].username)
        assertEquals(2222, hosts[0].port)
        assertEquals("prod", hosts[0].label)
        assertEquals(22, hosts[1].port)
    }

    @Test fun acceptsEqualsSeparatedDirectives() {
        val hosts = SshConfigImport.parse("Host=alias\nHostName=x.example.com\nUser=deploy")
        assertEquals(1, hosts.size)
        assertEquals("x.example.com", hosts[0].host)
        assertEquals("deploy", hosts[0].username)
    }

    @Test fun ignoresCommentsAndBlankLines() {
        val hosts = SshConfigImport.parse(
            """
            # a comment
            Host prod   # trailing comment

                HostName db.example.com
                User deploy
            """.trimIndent(),
        )
        assertEquals(1, hosts.size)
        assertEquals("db.example.com", hosts[0].host)
    }

    @Test fun skipsWildcardDefaultsBlocks() {
        val hosts = SshConfigImport.parse(
            """
            Host *
                ServerAliveInterval 60

            Host prod
                HostName db.example.com
                User deploy
            """.trimIndent(),
        )
        assertEquals(1, hosts.size)
        assertEquals("db.example.com", hosts[0].host)
    }

    @Test fun directivesBeforeAnyHostBlockAreIgnored() {
        val hosts = SshConfigImport.parse("User nobody\nPort 99\n\nHost prod\nHostName a.example.com\nUser deploy")
        assertEquals(1, hosts.size)
        assertEquals(22, hosts[0].port)
        assertEquals("deploy", hosts[0].username)
    }

    @Test fun hostWithoutUserIsSkippedUnlessADefaultIsSupplied() {
        val config = "Host prod\nHostName db.example.com"
        assertTrue(SshConfigImport.parse(config).isEmpty())
        assertEquals("deploy", SshConfigImport.parse(config, defaultUsername = "deploy").single().username)
    }

    @Test fun aliasWithNoHostNameBecomesTheAddress() {
        val hosts = SshConfigImport.parse("Host db.example.com\nUser deploy")
        assertEquals("db.example.com", hosts.single().host)
        // No redundant label when the alias is already the address.
        assertEquals("", hosts.single().label)
    }

    @Test fun outOfRangePortFallsBackToTheDefault() {
        val hosts = SshConfigImport.parse("Host prod\nHostName a.example.com\nUser deploy\nPort 99999")
        assertEquals(22, hosts.single().port)
    }

    @Test fun keywordsAreCaseInsensitive() {
        val hosts = SshConfigImport.parse("HOST prod\nhostname a.example.com\nUsEr deploy")
        assertEquals("a.example.com", hosts.single().host)
        assertEquals("deploy", hosts.single().username)
    }

    @Test fun emptyInputProducesNoHosts() {
        assertTrue(SshConfigImport.parse("").isEmpty())
        assertTrue(SshConfigImport.parse("\n\n   \n").isEmpty())
    }
}
