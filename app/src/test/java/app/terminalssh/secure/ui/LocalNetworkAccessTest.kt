package app.terminalssh.secure.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkAccessTest {
    @Test fun privateRangesAreLocal() {
        listOf("10.0.0.5", "172.16.0.1", "172.31.255.1", "192.168.1.10", "169.254.3.4", "fe80::1", "fd12::1", "nas.local", "router.lan", "pi", "[fe80::2]")
            .forEach { assertTrue(LocalNetworkAccess.isLocalHost(it), it) }
    }

    @Test fun publicAndLoopbackAreNotLocal() {
        listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "example.com", "127.0.0.1", "localhost", "::1", "", "300.1.1.1")
            .forEach { assertFalse(LocalNetworkAccess.isLocalHost(it), it) }
    }
}
