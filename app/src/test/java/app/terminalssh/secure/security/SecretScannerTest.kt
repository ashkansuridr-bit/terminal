package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretScannerTest {

    @Test fun findsAnthropicKeys() {
        val text = "ANTHROPIC_API_KEY=sk-ant-api03-abcdefghijklmnopqrstuvwxyz012345"
        assertTrue(SecretScanner.containsSecret(text))
        assertFalse("sk-ant-api03" in SecretScanner.mask(text))
    }

    @Test fun findsCommonProviderKeyShapes() {
        val cases = listOf(
            "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789",
            "ghp_abcdefghijklmnopqrstuvwxyz0123456789AB",
            "AKIAIOSFODNN7EXAMPLE",
            "AIzaSyA1234567890abcdefghijklmnopqrstuvw",
            "xoxb-1234567890-abcdefghij",
        )
        cases.forEach { secret ->
            assertTrue(SecretScanner.containsSecret("token: $secret"), "missed $secret")
            assertFalse(secret in SecretScanner.mask("token: $secret"), "did not mask $secret")
        }
    }

    @Test fun findsPrivateKeyHeaders() {
        val text = "-----BEGIN OPENSSH PRIVATE KEY-----\nbody\n"
        assertTrue(SecretScanner.containsSecret(text))
        assertTrue("private key hidden" in SecretScanner.mask(text))
    }

    @Test fun ordinaryOutputIsLeftAlone() {
        // False positives hide output the user needed, which is a real cost.
        val benign = listOf(
            "total 24\ndrwxr-xr-x 2 root root 4096 Jan  1 00:00 .",
            "Cloning into 'project'...",
            "sk-", // too short to be anything
            "AKIA", // prefix alone
            "commit 9f2c1ab3d4e5f60718293a4b5c6d7e8f90a1b2c3",
            "https://example.com/path?query=value",
        )
        benign.forEach { text ->
            assertFalse(SecretScanner.containsSecret(text), "false positive on: $text")
            assertEquals(text, SecretScanner.mask(text))
        }
    }

    @Test fun overlappingPatternsMaskTheLongestMatch() {
        // "sk-ant-..." also matches the looser "sk-" rule; the key must not be partially
        // revealed by the shorter match winning.
        val secret = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz012345"
        val masked = SecretScanner.mask("key=$secret end")
        assertFalse("ant-api03" in masked, masked)
        assertTrue(masked.endsWith(" end"), masked)
        assertEquals(1, SecretScanner.scan("key=$secret end").size)
    }

    @Test fun multipleSecretsInOneChunkAreAllMasked() {
        val text = "a=sk-ant-api03-abcdefghijklmnopqrstuvwxyz012345 b=ghp_abcdefghijklmnopqrstuvwxyz0123456789AB"
        val masked = SecretScanner.mask(text)
        assertFalse("sk-ant" in masked, masked)
        assertFalse("ghp_" in masked, masked)
        assertEquals(2, SecretScanner.scan(text).size)
    }

    @Test fun surroundingTextIsPreservedExactly() {
        val text = "before sk-ant-api03-abcdefghijklmnopqrstuvwxyz012345 after"
        val masked = SecretScanner.mask(text)
        assertTrue(masked.startsWith("before "), masked)
        assertTrue(masked.endsWith(" after"), masked)
    }

    @Test fun maskingIsStableWhenThereIsNothingToMask() {
        val text = "just some output"
        assertEquals(text, SecretScanner.mask(text))
        assertTrue(SecretScanner.scan("").isEmpty())
    }

    @Test fun labelSaysWhatWasHiddenRatherThanJustDots() {
        // "•••" leaves the user wondering what was hidden and whether it mattered.
        val masked = SecretScanner.mask("k=AKIAIOSFODNN7EXAMPLE")
        assertTrue("AWS access key" in masked, masked)
    }
}
