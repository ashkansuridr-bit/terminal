package app.terminalssh.secure.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BroadcastInputTest {

    private val sessions = listOf(
        BroadcastInput.Target("s1", "web-1", "10.0.0.1", connected = true),
        BroadcastInput.Target("s2", "web-2", "10.0.0.2", connected = true),
        BroadcastInput.Target("s3", "db-1", "10.0.0.3", connected = false),
        BroadcastInput.Target("s4", "log-tail", "10.0.0.4", connected = true, readOnly = true),
    )

    @Test fun planKeepsOnlyConnectedWritableSessions() {
        val plan = BroadcastInput.plan(sessions, setOf("s1", "s2", "s3", "s4"))
        assertEquals(listOf("s1", "s2"), plan.targets.map { it.sessionId })
        assertEquals(setOf("s3", "s4"), plan.excluded.map { it.target.sessionId }.toSet())
        assertTrue(plan.isBroadcast)
    }

    @Test fun onlySelectedSessionsAreConsidered() {
        val plan = BroadcastInput.plan(sessions, setOf("s2"))
        assertEquals(listOf("s2"), plan.targets.map { it.sessionId })
        assertFalse(plan.isBroadcast)
    }

    @Test fun unselectedSessionsAreNotListedAsExcluded() {
        val plan = BroadcastInput.plan(sessions, setOf("s1"))
        assertTrue(plan.excluded.none { it.target.sessionId == "s2" })
    }

    @Test fun summaryNamesTheTargetsAndCarriesNoSecrets() {
        val plan = BroadcastInput.plan(sessions, setOf("s1", "s2"))
        assertTrue(plan.summary().contains("web-1"))
        assertTrue(plan.summary().contains("web-2"))
        assertFalse(plan.summary().contains("10.0.0.1"))
    }

    @Test fun multiLineInputNeedsConfirmationOnlyWhenBroadcasting() {
        val broadcast = BroadcastInput.plan(sessions, setOf("s1", "s2"))
        val single = BroadcastInput.plan(sessions, setOf("s1"))
        assertTrue(BroadcastInput.requiresConfirmation("ls\nrm -rf /", broadcast))
        assertFalse(BroadcastInput.requiresConfirmation("ls", broadcast))
        assertFalse(BroadcastInput.requiresConfirmation("ls\npwd", single))
    }

    @Test fun longInputNeedsConfirmation() {
        val broadcast = BroadcastInput.plan(sessions, setOf("s1", "s2"))
        assertTrue(BroadcastInput.requiresConfirmation("x".repeat(600), broadcast))
    }

    @Test fun encodeRejectsEmptyAndEncodesText() {
        assertNull(BroadcastInput.encode(""))
        assertEquals("ls", BroadcastInput.encode("ls")?.toString(Charsets.UTF_8))
    }
}
