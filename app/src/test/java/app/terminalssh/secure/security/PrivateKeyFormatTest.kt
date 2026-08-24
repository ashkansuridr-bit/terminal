package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrivateKeyFormatTest {
    @Test fun detectsOpenSshWithoutStringDecode() {
        val bytes = "-----BEGIN OPENSSH PRIVATE KEY-----".encodeToByteArray()
        assertEquals("openssh", PrivateKeyFormat.detect(bytes))
        bytes.fill(0)
    }

    @Test fun rejectsNonPrivateKey() {
        assertFailsWith<IllegalArgumentException> {
            PrivateKeyFormat.detect("hello".encodeToByteArray())
        }
    }

    @Test fun detectsSupportedPemHeaders() {
        assertEquals("rsa", PrivateKeyFormat.detect("-----BEGIN RSA PRIVATE KEY-----".encodeToByteArray()))
        assertEquals("ecdsa", PrivateKeyFormat.detect("-----BEGIN EC PRIVATE KEY-----".encodeToByteArray()))
        assertEquals("pem", PrivateKeyFormat.detect("-----BEGIN PRIVATE KEY-----".encodeToByteArray()))
        assertEquals("pem", PrivateKeyFormat.detect("-----BEGIN ENCRYPTED PRIVATE KEY-----".encodeToByteArray()))
        assertEquals("pem", PrivateKeyFormat.detect("-----BEGIN DSA PRIVATE KEY-----".encodeToByteArray()))
    }

    @Test fun rejectsPrivateKeyWordsWithoutACompleteHeader() {
        listOf(
            "PRIVATE KEY",
            "metadata: OPENSSH PRIVATE KEY",
            "-----BEGIN RSA PRIVATE KEY",
            "BEGIN EC PRIVATE KEY-----",
        ).forEach { misleadingInput ->
            assertFailsWith<IllegalArgumentException>(misleadingInput) {
                PrivateKeyFormat.detect(misleadingInput.encodeToByteArray())
            }
        }
    }
}
