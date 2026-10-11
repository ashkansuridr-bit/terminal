package app.terminalssh.secure.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickConnectTest {

    private fun ok(input: String, defaultUser: String = ""): QuickConnect.Target {
        val result = QuickConnect.parse(input, defaultUser)
        assertTrue(result is QuickConnect.ParseResult.Ok, "expected Ok for '$input', got $result")
        return (result as QuickConnect.ParseResult.Ok).target
    }

    private fun reason(input: String): QuickConnect.Reason {
        val result = QuickConnect.parse(input)
        assertTrue(result is QuickConnect.ParseResult.Invalid, "expected Invalid for '$input'")
        return (result as QuickConnect.ParseResult.Invalid).reason
    }

    @Test fun hostOnlyUsesDefaultUsernameAndPort() {
        val t = ok("box.example.com", "root")
        assertEquals("root", t.username)
        assertEquals("box.example.com", t.host)
        assertEquals(22, t.port)
    }

    @Test fun userAndPortAreParsed() {
        val t = ok("deploy@10.0.0.4:2222")
        assertEquals("deploy", t.username)
        assertEquals("10.0.0.4", t.host)
        assertEquals(2222, t.port)
    }

    @Test fun sshSchemeIsAccepted() {
        val t = ok("ssh://deploy@10.0.0.4:2222")
        assertEquals("deploy", t.username)
        assertEquals(2222, t.port)
    }

    @Test fun bracketedIpv6KeepsItsColons() {
        val t = ok("[2001:db8::1]:22", "root")
        assertEquals("2001:db8::1", t.host)
        assertEquals("root", t.username)
        assertEquals(22, t.port)
    }

    @Test fun bareIpv6WithoutBracketsIsAHost() {
        val t = ok("2001:db8::1", "root")
        assertEquals("2001:db8::1", t.host)
        assertEquals(22, t.port)
    }

    @Test fun nonNumericPortIsRejected() {
        assertEquals(QuickConnect.Reason.INVALID_PORT, reason("host:ssh"))
    }

    @Test fun outOfRangePortIsRejected() {
        assertEquals(QuickConnect.Reason.INVALID_PORT, reason("host:70000"))
        assertEquals(QuickConnect.Reason.INVALID_PORT, reason("host:0"))
    }

    @Test fun emptyAndBlankInputAreRejected() {
        assertEquals(QuickConnect.Reason.EMPTY, reason(""))
        assertEquals(QuickConnect.Reason.EMPTY, reason("   "))
    }

    @Test fun hostWithSpacesIsRejected() {
        assertEquals(QuickConnect.Reason.INVALID_HOST, reason("not a host"))
    }

    @Test fun missingUserDefaultsToBlankForTheUiToPrompt() {
        val t = ok("box.example.com")
        assertEquals("", t.username)
    }

    @Test fun trailingAtWithoutHostIsRejected() {
        assertEquals(QuickConnect.Reason.MISSING_HOST, reason("user@"))
    }
}
