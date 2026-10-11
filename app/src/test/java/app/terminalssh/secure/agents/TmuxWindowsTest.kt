package app.terminalssh.secure.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class TmuxWindowsTest {

    private val sep = "\u001F"

    @Test fun parseWindowsReadsAllFields() {
        val output = "0${sep}editor${sep}1${sep}2\n1${sep}logs${sep}0${sep}1\n"
        val windows = TmuxCommands.parseWindows(output)
        assertEquals(2, windows.size)
        assertEquals(TmuxCommands.Window(0, "editor", active = true, panes = 2), windows[0])
        assertEquals(TmuxCommands.Window(1, "logs", active = false, panes = 1), windows[1])
    }

    @Test fun malformedWindowLinesAreSkipped() {
        val output = "bad line\n0${sep}ok${sep}0${sep}1\n"
        assertEquals(listOf(0), TmuxCommands.parseWindows(output).map { it.index })
    }

    @Test fun listWindowsCommandQuotesTheSession() {
        val cmd = TmuxCommands.listWindowsCommand("my session")
        assertTrue(cmd.contains("tmux list-windows -t 'my session'"), cmd)
        assertTrue(cmd.contains("#{window_index}"), cmd)
    }

    @Test fun renameSessionQuotesBothNames() {
        val cmd = TmuxCommands.renameSessionCommand("old", "new name")
        assertTrue(cmd.contains("rename-session -t 'old' 'new name'"), cmd)
    }

    @Test fun renameWindowTargetsByIndex() {
        val cmd = TmuxCommands.renameWindowCommand("sess", 2, "editor")
        assertTrue(cmd.contains("rename-window -t 'sess:2' 'editor'"), cmd)
    }

    @Test fun killWindowTargetsByIndex() {
        val cmd = TmuxCommands.killWindowCommand("sess", 3)
        assertEquals("tmux kill-window -t 'sess:3'", cmd)
    }

    @Test fun negativeWindowIndexIsRejected() {
        assertFails { TmuxCommands.renameWindowCommand("s", -1, "x") }
        assertFails { TmuxCommands.killWindowCommand("s", -1) }
    }
}
