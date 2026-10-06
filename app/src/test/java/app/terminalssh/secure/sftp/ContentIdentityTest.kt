package app.terminalssh.secure.sftp

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException

class ContentIdentityTest {
    @Test fun sameSizeReplacementRefusesResume() {
        val prior = "old-prefixTAIL".byteInputStream()
        val replacement = "new-prefixTAIL".byteInputStream()
        assertFalse(ContentIdentity.matches(ContentIdentity.prefix(prior, 10), ContentIdentity.prefix(replacement, 10)))
    }

    @Test fun prefixConsumesExactlyAcknowledgedBytes() {
        val input = "prefixTAIL".byteInputStream()
        val actual = ContentIdentity.prefix(input, 6)
        assertArrayEquals(SyncComparison.sha256("prefix".byteInputStream()), actual)
        assertEquals("TAIL", input.readBytes().toString(Charsets.UTF_8))
    }

    @Test(expected = EOFException::class) fun truncatedSourceCannotAuthorizeResume() {
        ContentIdentity.prefix("short".byteInputStream(), 8)
    }

    @Test fun changedContentWithPreservedMetadataInvalidatesSync() {
        val before = SyncFileSnapshot(10, 100, "old-hash")
        val after = before.copy(sha256 = "new-hash")
        try {
            SyncPlanGuard.requireUnchanged(before, after)
            fail("Stale plan accepted")
        } catch (_: IOException) { }
    }

    @Test fun newlyCreatedTargetInvalidatesMissingSnapshot() {
        try {
            SyncPlanGuard.requireUnchanged(null, SyncFileSnapshot(10, 100, "hash"))
            fail("New target accepted")
        } catch (_: IOException) { }
    }

    @Test fun unchangedSnapshotAccepted() {
        val snapshot = SyncFileSnapshot(2, 3, "hash")
        SyncPlanGuard.requireUnchanged(snapshot, snapshot.copy())
        SyncPlanGuard.requireUnchanged(null, null)
    }

    @Test fun relativePathRejectsTraversal() {
        for (path in listOf("../a", "/a", "a/../../b", "a\\b", "", "a//b", "a/./b")) {
            try {
                SyncPlanGuard.requireRelativePath(path)
                fail("Unsafe path accepted: $path")
            } catch (_: IllegalArgumentException) { }
        }
        SyncPlanGuard.requireRelativePath("a/b.txt")
    }
}
