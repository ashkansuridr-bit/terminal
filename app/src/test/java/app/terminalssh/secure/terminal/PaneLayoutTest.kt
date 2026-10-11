package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaneLayoutTest {

    private val leafA = PaneNode.Leaf("a")

    @Test fun splittingASinglePaneCreatesTwo() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        assertEquals(listOf("a", "b"), root.leaves)
        assertTrue(root is PaneNode.Split)
    }

    @Test fun splittingAnAbsentPaneDoesNothing() {
        assertNull(PaneLayout.split(leafA, "zzz", "b", PaneNode.Orientation.VERTICAL))
    }

    @Test fun splittingWithAnExistingIdDoesNothing() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        assertNull(PaneLayout.split(root, "a", "b", PaneNode.Orientation.VERTICAL))
        assertNull(PaneLayout.split(root, "a", "a", PaneNode.Orientation.VERTICAL))
    }

    @Test fun splittingStopsAtThePaneCap() {
        var root: PaneNode = PaneNode.Leaf("p0")
        for (i in 1 until PaneLayout.MAX_PANES) {
            root = PaneLayout.split(root, "p${i - 1}", "p$i", PaneNode.Orientation.HORIZONTAL)!!
        }
        assertEquals(PaneLayout.MAX_PANES, root.leaves.size)
        assertNull(PaneLayout.split(root, "p0", "overflow", PaneNode.Orientation.HORIZONTAL))
    }

    @Test fun leavesPreserveVisualOrder() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        val nested = PaneLayout.split(root, "a", "a2", PaneNode.Orientation.VERTICAL)!!
        assertEquals(listOf("a", "a2", "b"), nested.leaves)
    }

    @Test fun closingAPaneCollapsesItsSibling() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        val closed = PaneLayout.close(root, "b")!!
        assertEquals(listOf("a"), closed.leaves)
        assertTrue(closed is PaneNode.Leaf)
    }

    @Test fun closingTheLastPaneIsRefused() {
        assertNull(PaneLayout.close(leafA, "a"))
    }

    @Test fun closingAnAbsentPaneIsRefused() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        assertNull(PaneLayout.close(root, "zzz"))
    }

    @Test fun closingANestedPaneKeepsTheRest() {
        var root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        root = PaneLayout.split(root, "a", "a2", PaneNode.Orientation.VERTICAL)!!
        val closed = PaneLayout.close(root, "a2")!!
        assertEquals(listOf("a", "b"), closed.leaves)
    }

    @Test fun resizingClampsSoNoPaneVanishes() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        val wide = PaneLayout.resize(root, emptyList(), 0.99f) as PaneNode.Split
        assertEquals(1f - PaneLayout.MIN_RATIO, wide.ratio)
        val narrow = PaneLayout.resize(root, emptyList(), 0f) as PaneNode.Split
        assertEquals(PaneLayout.MIN_RATIO, narrow.ratio)
    }

    @Test fun resizeCanTargetANestedSplit() {
        var root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        root = PaneLayout.split(root, "a", "a2", PaneNode.Orientation.VERTICAL)!!
        val resized = PaneLayout.resize(root, listOf(true), 0.3f) as PaneNode.Split
        assertEquals(0.3f, (resized.first as PaneNode.Split).ratio)
    }

    @Test fun focusMovesForwardAndWraps() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        assertEquals("b", PaneLayout.focusNext(root, "a"))
        assertEquals("a", PaneLayout.focusNext(root, "b"))
    }

    @Test fun focusMovesBackwardAndWraps() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        assertEquals("a", PaneLayout.focusPrev(root, "b"))
        assertEquals("b", PaneLayout.focusPrev(root, "a"))
    }

    @Test fun focusFromAnUnknownPaneLandsSomewhereReal() {
        val root = PaneLayout.split(leafA, "a", "b", PaneNode.Orientation.HORIZONTAL)!!
        assertEquals("a", PaneLayout.focusNext(root, "ghost"))
        assertEquals("b", PaneLayout.focusPrev(root, "ghost"))
    }
}
