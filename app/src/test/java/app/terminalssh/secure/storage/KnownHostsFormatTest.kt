package app.terminalssh.secure.storage

import app.terminalssh.secure.ssh.KnownHostsVerifier
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnownHostsFormatTest {

    private fun key(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun entry(host: String, port: Int = 22, algorithm: String = "ssh-ed25519", bytes: ByteArray = key(1, 2, 3)) =
        KnownHostsVerifier.KnownHost(host, port, algorithm, bytes)

    @Test fun roundTripsThroughOpenSshFormat() {
        val entries = listOf(
            entry("a.example.com"),
            entry("b.example.com", port = 2222),
            entry("c.example.com", algorithm = "ssh-rsa"),
        )
        val parsed = KnownHostsFormat.parse(KnownHostsFormat.export(entries))
        assertEquals(3, parsed.entries.size)
        assertEquals(listOf("a.example.com", "b.example.com", "c.example.com"), parsed.entries.map { it.host })
        assertEquals(2222, parsed.entries.first { it.host == "b.example.com" }.port)
        assertContentEquals(key(1, 2, 3), parsed.entries.first().key)
    }

    @Test fun nonDefaultPortsUseTheBracketForm() {
        val text = KnownHostsFormat.export(listOf(entry("box.example.com", port = 2200)))
        assertTrue(text.contains("[box.example.com]:2200 ssh-ed25519 "))
    }

    @Test fun defaultPortEntriesHaveNoBrackets() {
        val text = KnownHostsFormat.export(listOf(entry("box.example.com")))
        assertTrue(text.contains("box.example.com ssh-ed25519 "))
        assertFalse(text.contains("["))
    }

    @Test fun parsesAHandWrittenFileWithSeveralHostnamesAndAComment() {
        val text = "# my keys\n" +
            "alpha,beta ssh-ed25519 ${b64(key(9, 9))}\n"
        val parsed = KnownHostsFormat.parse(text)
        assertEquals(2, parsed.entries.size)
        assertEquals(setOf("alpha", "beta"), parsed.entries.map { it.host }.toSet())
    }

    @Test fun hashedHostsAreCountedButNotImported() {
        val text = "|1|abc=|def= ssh-ed25519 ${b64(key(1))}\n"
        val parsed = KnownHostsFormat.parse(text)
        assertEquals(0, parsed.entries.size)
        assertEquals(1, parsed.hashedHosts)
    }

    @Test fun markersAreCountedButNotImported() {
        val text = "@cert-authority ca.example.com ssh-ed25519 ${b64(key(1))}\n" +
            "@revoked bad.example.com ssh-rsa ${b64(key(2))}\n"
        val parsed = KnownHostsFormat.parse(text)
        assertEquals(2, parsed.markers)
        assertEquals(0, parsed.entries.size)
    }

    @Test fun invalidBase64IsSkippedWithoutAbortingTheImport() {
        val text = "good.example.com ssh-ed25519 ${b64(key(1))}\n" +
            "bad.example.com ssh-ed25519 not!base64!\n"
        val parsed = KnownHostsFormat.parse(text)
        assertEquals(1, parsed.entries.size)
        assertEquals("good.example.com", parsed.entries.single().host)
        assertEquals(1, parsed.skippedLines)
    }

    @Test fun unknownKeyTypesAreSkipped() {
        val text = "weird.example.com super-cipher ${b64(key(1))}\n"
        assertEquals(1, KnownHostsFormat.parse(text).skippedLines)
        assertTrue(KnownHostsFormat.isValidKeyType("ssh-ed25519"))
        assertTrue(KnownHostsFormat.isValidKeyType("ecdsa-sha2-nistp256"))
        assertTrue(KnownHostsFormat.isValidKeyType("sk-ssh-ed25519@openssh.com"))
        assertFalse(KnownHostsFormat.isValidKeyType("super-cipher"))
    }

    @Test fun emptyInputProducesAnEmptyResult() {
        val parsed = KnownHostsFormat.parse("")
        assertTrue(parsed.entries.isEmpty())
        assertEquals(0, parsed.skippedLines)
    }

    @Test fun fingerprintIsTheStandardSha256Form() {
        val fingerprint = KnownHostsFormat.fingerprint(entry("a.example.com"))
        assertTrue(fingerprint.startsWith("SHA256:"))
        assertEquals(KnownHostsVerifier.sha256Fingerprint(key(1, 2, 3)), fingerprint)
    }

    private fun b64(value: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(value)
}
