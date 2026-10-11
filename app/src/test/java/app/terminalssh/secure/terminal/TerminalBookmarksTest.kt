package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalBookmarksTest {

    private fun bm(id: String, line: Int, label: String = "x") =
        TerminalBookmarks.Bookmark(id, line, label, 0L)

    @Test fun addKeepsThemSortedByLine() {
        var list = TerminalBookmarks.add(emptyList(), 10, "ten", "a", 1L)
        list = TerminalBookmarks.add(list, 2, "two", "b", 2L)
        assertEquals(listOf(2, 10), list.map { it.lineIndex })
    }

    @Test fun addReplacesAMarkerOnTheSameLine() {
        var list = TerminalBookmarks.add(emptyList(), 5, "old", "a", 1L)
        list = TerminalBookmarks.add(list, 5, "new", "b", 2L)
        assertEquals(1, list.size)
        assertEquals("new", list.single().label)
        assertEquals("b", list.single().id)
    }

    @Test fun blankLabelGetsAReadableFallback() {
        val list = TerminalBookmarks.add(emptyList(), 3, "   ", "a", 1L)
        assertEquals("line 4", list.single().label)
    }

    @Test fun removeById() {
        val list = listOf(bm("a", 1), bm("b", 2))
        assertEquals(listOf("b"), TerminalBookmarks.remove(list, "a").map { it.id })
    }

    @Test fun rename() {
        val list = listOf(bm("a", 1, "old"))
        assertEquals("fresh", TerminalBookmarks.rename(list, "a", "fresh").single().label)
    }

    @Test fun pruneShiftsForTrimmedLines() {
        val list = listOf(bm("a", 5), bm("b", 20))
        // 3 lines trimmed off the top, buffer now holds 30 lines.
        val pruned = TerminalBookmarks.prune(list, totalLines = 30, trimmedFromTop = 3)
        assertEquals(listOf(2, 17), pruned.map { it.lineIndex })
    }

    @Test fun pruneDropsOutOfRangeMarkers() {
        val list = listOf(bm("a", 99), bm("b", 5))
        assertEquals(listOf("b"), TerminalBookmarks.prune(list, totalLines = 10).map { it.id })
    }

    @Test fun navigationHelpers() {
        val list = listOf(bm("a", 2), bm("b", 8), bm("c", 20))
        assertEquals("b", TerminalBookmarks.atOrBefore(list, 10)?.id)
        assertEquals("c", TerminalBookmarks.after(list, 10)?.id)
        assertNull(TerminalBookmarks.after(list, 20))
        assertNull(TerminalBookmarks.atOrBefore(list, 1))
    }
}
