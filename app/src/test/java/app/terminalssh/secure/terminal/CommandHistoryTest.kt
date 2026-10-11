package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandHistoryTest {

    @Test fun captureIsOffUnlessEnabled() {
        val result = CommandHistory.record(emptyList(), "ls -la", 1L, enabled = false)
        assertTrue(result.isEmpty())
    }

    @Test fun recordsNewestFirst() {
        var h = CommandHistory.record(emptyList(), "ls", 1L, enabled = true)
        h = CommandHistory.record(h, "pwd", 2L, enabled = true)
        assertEquals(listOf("pwd", "ls"), h.map { it.command })
    }

    @Test fun blankCommandsAreIgnored() {
        assertTrue(CommandHistory.record(emptyList(), "   ", 1L, enabled = true).isEmpty())
    }

    @Test fun repeatedPreviousCommandIsNotDuplicated() {
        var h = CommandHistory.record(emptyList(), "ls", 1L, enabled = true)
        h = CommandHistory.record(h, "ls", 2L, enabled = true)
        assertEquals(1, h.size)
    }

    @Test fun secretsAreNeverStored() {
        assertTrue(CommandHistory.looksLikeSecret("export API_KEY=abcdef123456"))
        assertTrue(CommandHistory.looksLikeSecret("mysql -phunter2"))
        assertTrue(CommandHistory.looksLikeSecret("curl -H 'Authorization: Bearer x'"))
        assertTrue(CommandHistory.record(emptyList(), "export TOKEN=abc", 1L, enabled = true).isEmpty())
    }

    @Test fun anOrdinaryCommandIsStored() {
        assertFalse(CommandHistory.looksLikeSecret("systemctl status nginx"))
        assertEquals(1, CommandHistory.record(emptyList(), "systemctl status nginx", 1L, enabled = true).size)
    }

    @Test fun dangerousCommandsAreFlaggedSensitive() {
        assertTrue(CommandHistory.isSensitive("rm -rf /"))
        assertFalse(CommandHistory.isSensitive("ls -la"))
    }

    @Test fun historyIsBounded() {
        var h = emptyList<CommandHistory.Entry>()
        repeat(10) { h = CommandHistory.record(h, "cmd$it", it.toLong(), enabled = true, limit = 5) }
        assertEquals(5, h.size)
        assertEquals("cmd9", h.first().command)
    }

    @Test fun searchFindsSubstringAndFuzzy() {
        var h = CommandHistory.record(emptyList(), "systemctl status nginx", 1L, enabled = true)
        h = CommandHistory.record(h, "df -h", 2L, enabled = true)
        assertEquals(listOf("df -h"), CommandHistory.search(h, "df").map { it.command })
        assertEquals(listOf("systemctl status nginx"), CommandHistory.search(h, "sysngx").map { it.command })
    }

    @Test fun clearEmptiesEverything() {
        assertTrue(CommandHistory.clear().isEmpty())
    }
}
