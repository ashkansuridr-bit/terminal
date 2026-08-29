package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteCommandsTest {

    private val hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    // ---- checksum parsing: the output differs per server ----

    @Test fun parsesGnuSha256sumOutput() {
        assertEquals(hash, RemoteCommands.parseChecksum("$hash  /var/log/app.log\n"))
    }

    @Test fun parsesBsdShasumAndBareHash() {
        assertEquals(hash, RemoteCommands.parseChecksum("$hash /tmp/x"))
        assertEquals(hash, RemoteCommands.parseChecksum(hash))
    }

    @Test fun skipsAShellBannerBeforeTheHash() {
        // Plenty of servers print a MOTD on every exec channel.
        val output = "Welcome to Ubuntu 24.04\nLast login: today\n$hash  /tmp/x\n"
        assertEquals(hash, RemoteCommands.parseChecksum(output))
    }

    @Test fun uppercaseHashIsNormalised() {
        assertEquals(hash, RemoteCommands.parseChecksum(hash.uppercase() + "  /tmp/x"))
    }

    @Test fun rejectsOutputThatIsNotAHash() {
        assertNull(RemoteCommands.parseChecksum("sha256sum: command not found"))
        assertNull(RemoteCommands.parseChecksum(""))
        assertNull(RemoteCommands.parseChecksum("deadbeef  /tmp/x"), "a short digest is not sha256")
        assertNull(RemoteCommands.parseChecksum("z".repeat(64) + "  /tmp/x"), "non-hex is not a hash")
    }

    // ---- df parsing ----

    @Test fun parsesPosixDfOutput() {
        val out = """
            Filesystem     1024-blocks     Used Available Capacity Mounted on
            /dev/sda1        103080224 41528100  56294748      43% /
        """.trimIndent()
        assertEquals(56294748L * 1024, RemoteCommands.parseAvailableBytes(out))
    }

    @Test fun ignoresABannerAboveTheTable() {
        val out = "MOTD line\nFilesystem 1024-blocks Used Available Capacity Mounted on\n/dev/sda1 100 40 60 40% /"
        assertEquals(60L * 1024, RemoteCommands.parseAvailableBytes(out))
    }

    @Test fun returnsNullWhenDfSaidNothingUsable() {
        assertNull(RemoteCommands.parseAvailableBytes(""))
        assertNull(RemoteCommands.parseAvailableBytes("df: command not found"))
    }

    // ---- the decision the parse feeds ----

    @Test fun anUploadLargerThanFreeSpaceIsRefused() {
        assertFalse(RemoteCommands.fitsInFreeSpace(uploadBytes = 100L, availableBytes = 50L))
    }

    @Test fun anUploadThatWouldFillTheDiskExactlyIsAlsoRefused() {
        // Leaving a server with zero bytes free breaks logging and often sshd itself.
        assertFalse(RemoteCommands.fitsInFreeSpace(uploadBytes = 1000L, availableBytes = 1000L))
    }

    @Test fun anUploadWithRoomToSpareIsAllowed() {
        val available = RemoteCommands.SAFETY_MARGIN + 10_000L
        assertTrue(RemoteCommands.fitsInFreeSpace(uploadBytes = 1_000L, availableBytes = available))
    }

    // ---- quoting: a filename is attacker-influenced input ----

    @Test fun quotesPathsWithSpacesAndShellMetacharacters() {
        assertEquals("'/tmp/my file'", RemoteCommands.shellQuote("/tmp/my file"))
        assertEquals("'/tmp/\$HOME'", RemoteCommands.shellQuote("/tmp/\$HOME"))
        assertEquals("'/tmp/a;rm -rf /'", RemoteCommands.shellQuote("/tmp/a;rm -rf /"))
    }

    @Test fun aQuoteInTheNameCannotEscapeTheQuoting() {
        // The classic break-out: a single quote in the filename.
        val quoted = RemoteCommands.shellQuote("/tmp/it's")
        assertEquals("'/tmp/it'\\''s'", quoted)
        assertTrue(quoted.startsWith("'") && quoted.endsWith("'"))
    }

    @Test fun theBuiltCommandsCarryTheQuotedPath() {
        assertTrue(RemoteCommands.checksumCommand("/tmp/a b").contains("'/tmp/a b'"))
        assertTrue(RemoteCommands.freeSpaceCommand("/var/www").contains("'/var/www'"))
    }
}
