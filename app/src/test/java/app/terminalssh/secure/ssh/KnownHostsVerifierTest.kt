package app.terminalssh.secure.ssh

import app.terminalssh.secure.model.HostKeyPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KnownHostsVerifierTest {
    private val verifier = KnownHostsVerifier()
    private val key = "server-key".encodeToByteArray()

    @Test fun strictRejectsUnknownHost() {
        val result = verifier.verify("example.com", 22, "ssh-ed25519", key, null, HostKeyPolicy.STRICT)
        assertIs<KnownHostsVerifier.Decision.Reject>(result)
        assertEquals("unknown_host", result.reason)
    }

    @Test fun tofuPromptsOnFirstUse() {
        val result = verifier.verify("example.com", 22, "ssh-ed25519", key, null, HostKeyPolicy.TRUST_ON_FIRST_USE)
        assertIs<KnownHostsVerifier.Decision.FirstUse>(result)
    }

    @Test fun changedKeyIsRejected() {
        val known = KnownHostsVerifier.KnownHost("example.com", 22, "ssh-ed25519", key)
        val result = verifier.verify("example.com", 22, "ssh-ed25519", "evil-key".encodeToByteArray(), known, HostKeyPolicy.STRICT)
        assertIs<KnownHostsVerifier.Decision.Reject>(result)
        assertEquals("host_key_changed", result.reason)
    }

    @Test fun changedAlgorithmIsRejectedEvenWhenKeyBytesMatch() {
        val known = KnownHostsVerifier.KnownHost("example.com", 22, "ecdsa-sha2-nistp256", key)
        val result = verifier.verify("example.com", 22, "ssh-ed25519", key, known, HostKeyPolicy.STRICT)
        assertIs<KnownHostsVerifier.Decision.Reject>(result)
        assertEquals("host_key_algorithm_changed", result.reason)
    }

    @Test fun sameKeyIsAccepted() {
        val known = KnownHostsVerifier.KnownHost("example.com", 22, "ssh-ed25519", key)
        assertEquals(KnownHostsVerifier.Decision.Accept, verifier.verify("example.com", 22, "ssh-ed25519", key, known, HostKeyPolicy.STRICT))
    }
}
