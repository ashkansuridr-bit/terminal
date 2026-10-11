package app.terminalssh.secure.ssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class JumpChainTest {

    @Test fun blankAndNoneMeanNoJump() {
        assertTrue(JumpChain.parse("") is JumpChain.ParseResult.Disabled)
        assertTrue(JumpChain.parse("   ") is JumpChain.ParseResult.Disabled)
        assertTrue(JumpChain.parse(null) is JumpChain.ParseResult.Disabled)
        assertTrue(JumpChain.parse("none") is JumpChain.ParseResult.Disabled)
        assertTrue(JumpChain.parse("NONE") is JumpChain.ParseResult.Disabled)
    }

    @Test fun aSingleHopParsesWithDefaults() {
        val result = JumpChain.parse("bastion.example.com") as JumpChain.ParseResult.Parsed
        assertEquals(listOf(JumpChain.Hop(null, "bastion.example.com", 22)), result.hops)
    }

    @Test fun userAndPortAreKept() {
        val result = JumpChain.parse("deploy@bastion:2222") as JumpChain.ParseResult.Parsed
        assertEquals(listOf(JumpChain.Hop("deploy", "bastion", 2222)), result.hops)
    }

    @Test fun bracketedIpv6Parses() {
        val result = JumpChain.parse("[2001:db8::1]:2200") as JumpChain.ParseResult.Parsed
        assertEquals(listOf(JumpChain.Hop(null, "2001:db8::1", 2200)), result.hops)
    }

    @Test fun commaSeparatedHopsKeepOrder() {
        val result = JumpChain.parse("a.example, b.example, c.example") as JumpChain.ParseResult.Parsed
        assertEquals(listOf("a.example", "b.example", "c.example"), result.hops.map { it.host })
    }

    @Test fun anEmptyHopTokenIsRejected() {
        val result = JumpChain.parse("a.example,,b.example") as JumpChain.ParseResult.Invalid
        assertEquals("jump.error.empty_hop", result.reasonKey)
    }

    @Test fun aBadPortIsRejected() {
        assertEquals("jump.error.port", (JumpChain.parse("host:abc") as JumpChain.ParseResult.Invalid).reasonKey)
        assertEquals("jump.error.port", (JumpChain.parse("host:0") as JumpChain.ParseResult.Invalid).reasonKey)
    }

    @Test fun aBadHostIsRejected() {
        assertEquals("jump.error.host", (JumpChain.parse("bad host") as JumpChain.ParseResult.Invalid).reasonKey)
        assertEquals("jump.error.host", (JumpChain.parse("user@") as JumpChain.ParseResult.Invalid).reasonKey)
    }

    @Test fun anAbsurdlyLongChainIsRejected() {
        val spec = (1..(JumpChain.MAX_HOPS + 1)).joinToString(",") { "h$it" }
        val result = JumpChain.parse(spec) as JumpChain.ParseResult.Invalid
        assertEquals("jump.error.too_many_hops", result.reasonKey)
    }

    @Test fun planOpensOneTunnelPerHop() {
        val hops = listOf(JumpChain.Hop(null, "a", 22), JumpChain.Hop("j", "b", 2222))
        val tunnels = JumpChain.plan(hops, targetHost = "target", targetPort = 22, startPort = 3000)
        assertEquals(2, tunnels.size)
        assertEquals(JumpChain.Tunnel(3000, "a", 22, "b", 2222), tunnels[0])
        assertEquals(JumpChain.Tunnel(3001, "b", 2222, "target", 22), tunnels[1])
    }

    @Test fun planCannotOverflowThePortRange() {
        val hops = List(JumpChain.MAX_HOPS) { JumpChain.Hop(null, "h$it", 22) }
        val last = 65536 - JumpChain.MAX_HOPS
        assertEquals(JumpChain.MAX_HOPS, JumpChain.plan(hops, "t", 22, startPort = last).size)
        assertFails { JumpChain.plan(hops, "t", 22, startPort = 65535) }
    }

    @Test fun planRejectsAnEmptyChainAndABadTarget() {
        assertFails { JumpChain.plan(emptyList(), "t", 22) }
        assertFails { JumpChain.plan(listOf(JumpChain.Hop(null, "a", 22)), "", 22) }
        assertFails { JumpChain.plan(listOf(JumpChain.Hop(null, "a", 22)), "t", 0) }
    }

    @Test fun describeNamesTheWholeRoute() {
        val hops = listOf(JumpChain.Hop(null, "a", 22), JumpChain.Hop(null, "b", 22))
        assertEquals("a -> b -> target", JumpChain.describe(hops, "target"))
    }
}
