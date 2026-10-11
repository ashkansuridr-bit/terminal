package app.terminalssh.secure.ssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortForwardSpecTest {

    private fun spec(
        kind: PortForwardSpec.Kind = PortForwardSpec.Kind.LOCAL,
        bind: Int = 8080,
        host: String = "10.0.0.5",
        port: Int = 80,
    ) = PortForwardSpec.Spec(kind, bind, host, port)

    @Test fun aWellFormedRuleHasNoProblem() {
        assertNull(PortForwardSpec.validate(spec()))
    }

    @Test fun outOfRangePortsAreRejected() {
        assertEquals("forward.error.bind_port", PortForwardSpec.validate(spec(bind = 0))!!.reasonKey)
        assertEquals("forward.error.target_port", PortForwardSpec.validate(spec(port = 70000))!!.reasonKey)
    }

    @Test fun aBadHostIsRejected() {
        assertEquals("forward.error.host", PortForwardSpec.validate(spec(host = "  "))!!.reasonKey)
        assertEquals("forward.error.host", PortForwardSpec.validate(spec(host = "a b"))!!.reasonKey)
        assertEquals("forward.error.host", PortForwardSpec.validate(spec(host = "a/b"))!!.reasonKey)
    }

    @Test fun hostValidationAllowsNormalNames() {
        assertTrue(PortForwardSpec.isValidHost("db.internal"))
        assertTrue(PortForwardSpec.isValidHost("2001:db8::1"))
        assertFalse(PortForwardSpec.isValidHost(""))
    }

    @Test fun theSameBindPortForTheSameKindConflicts() {
        val existing = listOf(spec(bind = 8080, host = "a", port = 80))
        val conflicting = spec(bind = 8080, host = "b", port = 90)
        assertEquals(existing[0], PortForwardSpec.conflicts(existing, conflicting))
    }

    @Test fun readdingTheIdenticalRuleIsNotAConflict() {
        val existing = listOf(spec())
        assertNull(PortForwardSpec.conflicts(existing, spec()))
    }

    @Test fun aDifferentKindOnTheSamePortIsNotAConflict() {
        val existing = listOf(spec(kind = PortForwardSpec.Kind.LOCAL, bind = 8080))
        assertNull(PortForwardSpec.conflicts(existing, spec(kind = PortForwardSpec.Kind.REMOTE, bind = 8080)))
    }

    @Test fun keysAndCountsUseTheKindLetter() {
        val specs = listOf(
            spec(kind = PortForwardSpec.Kind.LOCAL, bind = 8080),
            spec(kind = PortForwardSpec.Kind.LOCAL, bind = 9090),
            spec(kind = PortForwardSpec.Kind.REMOTE, bind = 3000),
        )
        assertTrue("L:8080" in PortForwardSpec.keySet(specs))
        assertEquals(2, PortForwardSpec.countByKind(specs)[PortForwardSpec.Kind.LOCAL])
        assertEquals(1, PortForwardSpec.countByKind(specs)[PortForwardSpec.Kind.REMOTE])
    }

    @Test fun privilegedPortsAreFlagged() {
        assertTrue(PortForwardSpec.isPrivileged(22))
        assertFalse(PortForwardSpec.isPrivileged(8080))
        assertFalse(PortForwardSpec.isPrivileged(0))
    }
}
