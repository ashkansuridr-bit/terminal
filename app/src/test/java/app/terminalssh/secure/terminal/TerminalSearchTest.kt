package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalSearchTest {

    private val lines = listOf(
        "root@box:~# ls",
        "file-one  file-two",
        "root@box:~# grep error app.log",
        "ERROR: disk full",
        "error: again",
    )

    @Test fun findsEveryOccurrenceCaseInsensitivelyByDefault() {
        val matches = TerminalSearch.findAll(lines, "error")
        assertEquals(3, matches.size)
        assertEquals(listOf(2, 3, 4), matches.map { it.lineIndex })
    }

    @Test fun caseSensitiveOptionNarrows() {
        val matches = TerminalSearch.findAll(lines, "ERROR", caseSensitive = true)
        assertEquals(listOf(3), matches.map { it.lineIndex })
    }

    @Test fun emptyQueryFindsNothing() {
        assertTrue(TerminalSearch.findAll(lines, "").isEmpty())
    }

    @Test fun respectsMaxMatches() {
        assertEquals(2, TerminalSearch.findAll(lines, "e", maxMatches = 2).size)
    }

    @Test fun highlightSplitsIntoMatchedAndUnmatchedRuns() {
        val runs = TerminalSearch.highlight("xxERRORxx", "error")
        assertEquals(listOf(false, true, false), runs.map { it.match })
        assertEquals("ERROR", runs[1].text)
    }

    @Test fun highlightOfNoMatchIsOneRun() {
        assertEquals(listOf(false), TerminalSearch.highlight("nothing here", "zzz").map { it.match })
    }

    @Test fun lineIndicesAreDeduped() {
        val lines = listOf("error error", "error")
        assertEquals(listOf(0, 1), TerminalSearch.lineIndices(TerminalSearch.findAll(lines, "error")))
    }

    @Test fun navigatorWrapsBothWays() {
        val nav = TerminalSearchNavigator(TerminalSearch.findAll(lines, "error"))
        assertEquals(3, nav.count)
        assertEquals(0, nav.position)
        assertEquals(2, nav.next()?.lineIndex)
        assertEquals(1, nav.position)
        assertEquals(3, nav.next()?.lineIndex)
        assertEquals(4, nav.next()?.lineIndex)
        // past the end wraps to the first
        assertEquals(2, nav.next()?.lineIndex)
        // before the start wraps to the last
        assertEquals(4, nav.previous()?.lineIndex)
    }

    @Test fun navigatorOnNoMatchesIsSafe() {
        val nav = TerminalSearchNavigator(emptyList())
        assertTrue(nav.isEmpty)
        assertEquals(null, nav.next())
        assertEquals(null, nav.previous())
        assertEquals(0, nav.position)
    }
}
