package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabLayoutTest {

    private fun tab(id: String, title: String = id, pinned: Boolean = false) =
        TabLayout.Tab(id, title, pinned)

    @Test fun normalizeAlwaysPutsPinnedFirstAndIsStable() {
        val tabs = listOf(tab("a"), tab("b", pinned = true), tab("c"), tab("d", pinned = true))
        assertEquals(listOf("b", "d", "a", "c"), TabLayout.normalize(tabs).map { it.id })
    }

    @Test fun renameTrimsAndCapsAndRejectsBlank() {
        val tabs = listOf(tab("a", "old"))
        assertEquals("fresh", TabLayout.rename(tabs, "a", "  fresh  ").single().title)
        assertEquals("old", TabLayout.rename(tabs, "a", "   ").single().title)
        assertEquals(TabLayout.MAX_TITLE, TabLayout.rename(tabs, "a", "x".repeat(100)).single().title.length)
    }

    @Test fun togglePinFloatsTheTabToTheFront() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        val pinned = TabLayout.togglePin(tabs, "c")
        assertEquals(listOf("c", "a", "b"), pinned.map { it.id })
        assertTrue(pinned.first().pinned)
        // Unpinning does not teleport the tab back: the sort is stable, so it stays in place.
        val unpinned = TabLayout.togglePin(pinned, "c")
        assertEquals(listOf("c", "a", "b"), unpinned.map { it.id })
        assertFalse(unpinned.first().pinned)
    }

    @Test fun moveReordersThenRestoresThePinnedInvariant() {
        val tabs = listOf(tab("a", pinned = true), tab("b"), tab("c"), tab("d"))
        // Move "d" to the front; normalize must still keep the pinned "a" first.
        val moved = TabLayout.move(tabs, 3, 0)
        assertEquals("a", moved.first().id)
        assertTrue(moved.first().pinned)
        assertEquals(listOf("a", "d", "b", "c"), moved.map { it.id })
    }

    @Test fun moveWithABadIndexIsANoOp() {
        val tabs = listOf(tab("a"), tab("b"))
        assertEquals(tabs, TabLayout.move(tabs, 9, 0))
    }

    @Test fun closeRemovesTheTab() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        assertEquals(listOf("a", "c"), TabLayout.close(tabs, "b").map { it.id })
    }

    @Test fun closingTheSelectedTabSelectsTheNextOne() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        assertEquals("c", TabLayout.selectionAfterClose(tabs, "b", currentlySelected = "b"))
        assertEquals("b", TabLayout.selectionAfterClose(tabs, "c", currentlySelected = "c"))
    }

    @Test fun closingTheLastTabFallsBackToThePreviousOne() {
        val tabs = listOf(tab("a"), tab("b"))
        // "b" was last: no tab slid into its slot, so pick the previous remaining tab.
        assertEquals("a", TabLayout.selectionAfterClose(tabs, "b", currentlySelected = "b"))
    }

    @Test fun closingAnUnselectedTabKeepsTheSelection() {
        val tabs = listOf(tab("a"), tab("b"))
        assertEquals("a", TabLayout.selectionAfterClose(tabs, "b", currentlySelected = "a"))
    }

    @Test fun closingTheOnlyTabClearsTheSelection() {
        assertNull(TabLayout.selectionAfterClose(listOf(tab("a")), "a", currentlySelected = "a"))
    }

    @Test fun movesAreOnlyAllowedWithinTheSamePinnedGroup() {
        val tabs = listOf(tab("pinned", pinned = true), tab("a"), tab("b"))
        assertTrue(TabLayout.canMoveWithinGroup(tabs, "a", 2))
        assertFalse(TabLayout.canMoveWithinGroup(tabs, "a", 0))
        assertFalse(TabLayout.canMoveWithinGroup(tabs, "missing", 0))
    }
}
