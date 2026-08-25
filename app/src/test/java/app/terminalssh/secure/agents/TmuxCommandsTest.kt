package app.terminalssh.secure.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TmuxCommandsTest {

    private val sep = "\u001F"

    @Test fun parsesRealSessionOutput() {
        val output = """
            dev${sep}3${sep}1
            build${sep}1${sep}0
        """.trimIndent()
        val sessions = TmuxCommands.parseSessions(output)
        assertEquals(2, sessions.size)
        assertEquals(TmuxCommands.Session("dev", 3, attached = true), sessions[0])
        assertEquals(TmuxCommands.Session("build", 1, attached = false), sessions[1])
    }

    @Test fun sessionNamesContainingSpacesAndColonsSurviveParsing() {
        // The reason for a control-character separator: tmux's default output cannot be
        // split unambiguously when a name contains a colon or a space.
        val output = "my: session${sep}2${sep}0"
        val session = TmuxCommands.parseSessions(output).single()
        assertEquals("my: session", session.name)
        assertEquals(2, session.windows)
    }

    @Test fun malformedLinesAreSkippedRatherThanCrashing() {
        val output = """
            good${sep}1${sep}0
            garbage without separators
            ${sep}${sep}
            missing${sep}fields
            alsogood${sep}2${sep}1
        """.trimIndent()
        val sessions = TmuxCommands.parseSessions(output)
        assertEquals(listOf("good", "alsogood"), sessions.map { it.name })
    }

    @Test fun emptyOutputMeansNoSessionsNotAnError() {
        // A server with tmux installed but nothing running prints nothing at all.
        assertTrue(TmuxCommands.parseSessions("").isEmpty())
        assertTrue(TmuxCommands.parseSessions("\n\n").isEmpty())
    }

    @Test fun listCommandNeverFailsTheShell() {
        // A non-zero exit when no server is running would abort a chained command.
        assertTrue(TmuxCommands.listSessionsCommand().endsWith("|| true"))
    }

    @Test fun attachDetachesTheStaleClient() {
        // Reconnecting after a drop leaves the old client registered; without -d the new
        // attach shares a window sized for a device that is no longer there.
        assertTrue("-d" in TmuxCommands.attachCommand("dev"))
    }

    @Test fun everyCommandQuotesTheSessionName() {
        val hostile = "a; rm -rf /"
        listOf(
            TmuxCommands.attachCommand(hostile),
            TmuxCommands.newSessionCommand(hostile),
            TmuxCommands.killSessionCommand(hostile),
        ).forEach { command ->
            assertTrue("'a; rm -rf /'" in command, "unquoted session name in: $command")
        }
    }

    @Test fun newSessionQuotesTheWorkingDirectory() {
        val command = TmuxCommands.newSessionCommand("dev", "/srv/it's mine")
        assertTrue("""-c '/srv/it'\''s mine'""" in command, command)
    }

    @Test fun newSessionWithoutADirectoryOmitsTheFlag() {
        assertFalse(" -c " in TmuxCommands.newSessionCommand("dev"))
        assertFalse(" -c " in TmuxCommands.newSessionCommand("dev", "   "))
    }

    @Test fun sessionNamesAreSanitizedToWhatTmuxAccepts() {
        // tmux uses '.' and ':' to address windows and panes, so it rejects them in names.
        assertEquals("my-host-com", TmuxCommands.sanitizeSessionName("my.host.com"))
        assertEquals("a-b", TmuxCommands.sanitizeSessionName("a:b"))
        assertEquals("two-words", TmuxCommands.sanitizeSessionName("two words"))
    }

    @Test fun sanitizingNeverProducesAnEmptyName() {
        assertEquals("session", TmuxCommands.sanitizeSessionName(""))
        assertEquals("session", TmuxCommands.sanitizeSessionName("   "))
        assertEquals("session", TmuxCommands.sanitizeSessionName("..."))
        assertEquals("session", TmuxCommands.sanitizeSessionName(":::"))
    }

    @Test fun sanitizedNamesAreBounded() {
        val long = TmuxCommands.sanitizeSessionName("x".repeat(500))
        assertTrue(long.length <= 40, "session name was ${long.length} characters")
    }

    @Test fun availabilityProbeAnswersWithASingleWord() {
        val probe = TmuxCommands.availabilityProbe()
        assertTrue("yes" in probe && "no" in probe)
    }
}
