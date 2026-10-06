package app.terminalssh.secure.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingSecretMaskerTest {
    private fun masked(bytes: ByteArray, split: Int): String {
        val scanner = StreamingSecretMasker()
        return (scanner.accept(bytes.copyOfRange(0, split)) +
            scanner.accept(bytes.copyOfRange(split, bytes.size)) + scanner.finish()).toString(Charsets.UTF_8)
    }

    @Test fun everyByteSplitMasksProviderTokensAndPreservesUnicode() {
        val tokens = listOf(
            "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789",
            "sk-ant-api03-abcdefghijklmnopqrstuvwxyz012345",
            "ghp_abcdefghijklmnopqrstuvwxyz0123456789AB",
            "AKIAIOSFODNN7EXAMPLE",
            "AIzaSyA1234567890abcdefghijklmnopqrstuvw",
            "xoxb-1234567890-abcdefghij",
        )
        for (token in tokens) {
            val bytes = "سلام🙂 token=$token پایان\n".toByteArray()
            for (split in 0..bytes.size) {
                val output = masked(bytes, split)
                assertTrue(output.startsWith("سلام🙂 token="), "split=$split: $output")
                assertTrue(output.endsWith(" پایان\n"), "split=$split: $output")
                assertFalse(token in output)
                assertFalse(token.take(5) in output, "prefix escaped split=$split: $output")
                assertTrue("hidden]" in output)
            }
        }
    }

    @Test fun ordinaryInteractiveTextAndAnsiArePreserved() {
        val text = "\u001b[32muser@host:~$ \u001b[0mسلام🙂 test sk- AKIA\n"
        val bytes = text.toByteArray()
        for (split in 0..bytes.size) assertEquals(text, masked(bytes, split))
        assertEquals("user@host:~$ ", StreamingSecretMasker().accept("user@host:~$ ".toByteArray()).toString(Charsets.UTF_8))
    }

    @Test fun noPotentialPrefixIsReleasedBeforeCredentialCompletes() {
        val scanner = StreamingSecretMasker()
        assertEquals("value=", scanner.accept("value=sk-proj-ab".toByteArray()).toString(Charsets.UTF_8))
        val output = scanner.accept("cdefghijklmnopqrstuvwxyz012345\n".toByteArray()).toString(Charsets.UTF_8)
        assertFalse("abcdef" in output)
        assertTrue("hidden]" in output)
        assertEquals("", scanner.finish().toString(Charsets.UTF_8))
    }

    @Test fun longKeyContinuationRemainsBoundedAndHidden() {
        val scanner = StreamingSecretMasker()
        val output = StringBuilder()
        for (part in listOf("key=sk-proj-", "a".repeat(100_000), " end\n")) {
            output.append(scanner.accept(part.toByteArray()).toString(Charsets.UTF_8))
            assertTrue(scanner.bufferedCharacters <= 128)
        }
        output.append(scanner.finish().toString(Charsets.UTF_8))
        assertEquals("key=[OpenAI API key hidden] end\n", output.toString())
    }

    @Test fun pemBodyDoesNotLeakAcrossChunks() {
        val text = "before -----BEGIN OPENSSH PRIVATE KEY-----\nVERYSECRETBODY\n-----END OPENSSH PRIVATE KEY-----\nafter\n"
        val bytes = text.toByteArray()
        for (split in 0..bytes.size) {
            val output = masked(bytes, split)
            assertEquals("before [private key hidden]\nafter\n", output)
            assertFalse("VERYSECRET" in output)
        }
    }
}
