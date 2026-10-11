package app.terminalssh.secure.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SshConfigNotesTest {

    private val config = """
        Include ~/.ssh/config.d/*
        Host *
          ServerAliveInterval 30
          IdentityFile ~/.ssh/id_ed25519
        Host web
          HostName web.example.com
          User deploy
          ProxyJump bastion
          IdentityFile ~/.ssh/web_key
        Host bastion
          HostName bastion.example.com
          User jump
    """.trimIndent()

    @Test fun countsNamedAndWildcardBlocks() {
        val notes = SshConfigImport.analyze(config)
        assertEquals(2, notes.namedHostBlocks)
        assertEquals(1, notes.skippedWildcardBlocks)
    }

    @Test fun reportsIncludeProxyJumpAndIdentityFiles() {
        val notes = SshConfigImport.analyze(config)
        assertEquals(listOf("~/.ssh/config.d/*"), notes.includePatterns)
        assertEquals(listOf("bastion"), notes.proxyJumpAliases)
        assertEquals(2, notes.identityFileCount)
        assertTrue(notes.hasUnappliedDirectives)
    }

    @Test fun aPlainConfigHasNothingUnapplied() {
        val notes = SshConfigImport.analyze("Host web\n  HostName web.example.com\n  User deploy")
        assertEquals(1, notes.namedHostBlocks)
        assertEquals(0, notes.identityFileCount)
        assertTrue(notes.includePatterns.isEmpty())
        assertTrue(notes.proxyJumpAliases.isEmpty())
        assertFalse(notes.hasUnappliedDirectives)
    }

    @Test fun commentsAndBlanksAreIgnored() {
        val notes = SshConfigImport.analyze("# a comment\n\n  \nHost x\n  User u")
        assertEquals(1, notes.namedHostBlocks)
    }

    @Test fun proxyJumpInAWildcardBlockIsNotReportedAsAHostReference() {
        val notes = SshConfigImport.analyze("Host *\n  ProxyJump bastion")
        assertTrue(notes.proxyJumpAliases.isEmpty())
    }
}
