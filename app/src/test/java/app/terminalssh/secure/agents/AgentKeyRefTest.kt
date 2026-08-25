package app.terminalssh.secure.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentKeyRefTest {

    @Test fun hostScopedKeyWinsOverGlobal() {
        val order = AgentKeyRef.resolutionOrder(CodingAgent.CLAUDE_CODE, "host-1")
        assertEquals(AgentKeyRef.forHost(CodingAgent.CLAUDE_CODE, "host-1"), order.first())
        assertEquals(AgentKeyRef.global(CodingAgent.CLAUDE_CODE), order.last())
    }

    @Test fun withoutAHostOnlyTheGlobalKeyIsTried() {
        assertEquals(
            listOf(AgentKeyRef.global(CodingAgent.CLAUDE_CODE)),
            AgentKeyRef.resolutionOrder(CodingAgent.CLAUDE_CODE, null),
        )
        assertEquals(
            listOf(AgentKeyRef.global(CodingAgent.CLAUDE_CODE)),
            AgentKeyRef.resolutionOrder(CodingAgent.CLAUDE_CODE, "  "),
        )
    }

    @Test fun referencesAreDistinctPerAgent() {
        val refs = CodingAgent.entries.map { AgentKeyRef.global(it) }
        assertEquals(refs.size, refs.toSet().size, "two agents share a key reference")
    }

    @Test fun referencesAreDistinctPerHost() {
        val a = AgentKeyRef.forHost(CodingAgent.AIDER, "host-a")
        val b = AgentKeyRef.forHost(CodingAgent.AIDER, "host-b")
        assertTrue(a != b)
    }

    @Test fun ownershipIsRecognisedForClearingAnAgentsKeys() {
        val claude = AgentKeyRef.forHost(CodingAgent.CLAUDE_CODE, "h")
        assertTrue(AgentKeyRef.belongsTo(claude, CodingAgent.CLAUDE_CODE))
        assertFalse(AgentKeyRef.belongsTo(claude, CodingAgent.AIDER))
        // A vault reference from some other feature must never be claimed.
        assertFalse(AgentKeyRef.belongsTo("some-uuid", CodingAgent.CLAUDE_CODE))
    }

    @Test fun hostScopeIsDistinguishableFromGlobal() {
        assertTrue(AgentKeyRef.isHostScoped(AgentKeyRef.forHost(CodingAgent.OPENCODE, "h")))
        assertFalse(AgentKeyRef.isHostScoped(AgentKeyRef.global(CodingAgent.OPENCODE)))
    }

    @Test fun hostIdIsRecoverableFromAReference() {
        assertEquals("host-42", AgentKeyRef.hostIdOf(AgentKeyRef.forHost(CodingAgent.AIDER, "host-42")))
        assertNull(AgentKeyRef.hostIdOf(AgentKeyRef.global(CodingAgent.AIDER)))
        assertNull(AgentKeyRef.hostIdOf("not-an-agent-key"))
    }
}
