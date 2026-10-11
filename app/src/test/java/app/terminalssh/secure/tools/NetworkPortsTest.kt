package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkPortsTest {

    private val ssSample =
        "tcp   LISTEN 0      128           0.0.0.0:22      0.0.0.0:*    users:((\"sshd\",pid=1,fd=3))\n" +
            "tcp   LISTEN 0      511                 *:80           *:*    users:((\"nginx\",pid=99,fd=6))\n" +
            "udp   UNCONN 0      0            127.0.0.1:53      0.0.0.0:*    users:((\"dnsmasq\",pid=5,fd=4))\n"

    private val netstatSample =
        "tcp        0      0 0.0.0.0:22              0.0.0.0:*               LISTEN      1234/sshd\n" +
            "tcp6       0      0 :::80                   :::*                    LISTEN      99/nginx\n"

    @Test fun parsesSsOutput() {
        val ports = NetworkPorts.parse(ssSample)
        assertEquals(3, ports.size)
        assertEquals(22, ports[0].port)
        assertEquals("sshd", ports[0].process)
        assertEquals("*", ports[1].address)
        assertEquals(80, ports[1].port)
        assertEquals(53, ports[2].port)
        assertTrue(ports[2].isLoopback)
    }

    @Test fun parsesNetstatOutputToo() {
        val ports = NetworkPorts.parse(netstatSample)
        assertEquals(2, ports.size)
        assertEquals("0.0.0.0", ports[0].address)
        assertEquals(22, ports[0].port)
        assertEquals("sshd", ports[0].process)
        assertEquals("::", ports[1].address)
        assertEquals(80, ports[1].port)
        assertTrue(ports[1].isAllInterfaces)
    }

    @Test fun dedupesRepeatedEntries() {
        val twice = ssSample + ssSample
        assertEquals(3, NetworkPorts.parse(twice).size)
    }

    @Test fun unknownToolingYieldsNothing() {
        assertTrue(NetworkPorts.parse("ss: command not found\n").isEmpty())
        assertTrue(NetworkPorts.parse("").isEmpty())
    }

    @Test fun commandIsReadOnlyAndPrefersSs() {
        val cmd = NetworkPorts.command()
        assertTrue(cmd.contains("ss -H -tulpn"))
        assertTrue(cmd.contains("netstat"))
    }
}
