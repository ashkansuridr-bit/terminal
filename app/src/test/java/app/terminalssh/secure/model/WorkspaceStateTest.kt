package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceStateTest {

    private val tabs = listOf(
        WorkspaceTab("h1", "web", pinned = true),
        WorkspaceTab("h2", "db", pinned = false),
    )

    @Test fun encodeDecodeRoundTrips() {
        val state = WorkspaceState(tabs, selectedIndex = 1)
        assertEquals(state, WorkspaceCodec.decodeOrNull(WorkspaceCodec.encode(state)))
    }

    @Test fun absentDataDecodesToNull() {
        assertNull(WorkspaceCodec.decodeOrNull(null))
        assertNull(WorkspaceCodec.decodeOrNull(""))
        assertNull(WorkspaceCodec.decodeOrNull("   "))
    }

    @Test fun garbageDecodesToNullInsteadOfThrowing() {
        assertNull(WorkspaceCodec.decodeOrNull("not base64 !!"))
    }

    @Test fun selectedIndexIsClampedOnDecode() {
        val state = WorkspaceState(tabs, selectedIndex = 99)
        assertEquals(1, WorkspaceCodec.decodeOrNull(WorkspaceCodec.encode(state))!!.selectedIndex)
    }

    @Test fun tooManyTabsAreRejectedOnEncode() {
        val many = WorkspaceState(List(65) { WorkspaceTab("h$it", "t$it") })
        assertFails { WorkspaceCodec.encode(many) }
    }

    @Test fun planRestoreDropsTabsForMissingHosts() {
        val plan = WorkspaceCodec.planRestore(WorkspaceState(tabs, 1), knownHostIds = setOf("h1"))
        assertEquals(listOf("h1"), plan.tabs.map { it.hostId })
        assertEquals(listOf("h2"), plan.missingHostIds)
        assertTrue(plan.isPartial)
        assertEquals(0, plan.selectedIndex)
    }

    @Test fun planRestoreKeepsAllWhenEveryHostExists() {
        val plan = WorkspaceCodec.planRestore(WorkspaceState(tabs, 1), setOf("h1", "h2"))
        assertFalse(plan.isPartial)
        assertEquals(1, plan.selectedIndex)
        assertEquals(2, plan.tabs.size)
    }

    @Test fun anEmptyWorkspaceIsUsable() {
        val empty = WorkspaceState.empty
        assertEquals(empty, WorkspaceCodec.decodeOrNull(WorkspaceCodec.encode(empty)))
    }
}
