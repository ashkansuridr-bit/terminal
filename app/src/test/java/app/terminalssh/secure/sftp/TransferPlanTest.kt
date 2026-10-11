package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferPlanTest {

    @Test fun countsFilesAndDirectoriesSeparatelyAndSumsFileBytes() {
        val preview = TransferPlan.build(
            listOf(
                TransferPlan.Item("/a/file1", sizeBytes = 100),
                TransferPlan.Item("/a/file2", sizeBytes = 200),
                TransferPlan.Item("/a/sub", isDirectory = true, sizeBytes = 4096),
            ),
            destination = "/remote/a",
        )
        assertEquals(2, preview.fileCount)
        assertEquals(1, preview.directoryCount)
        // Directory sizes are metadata, not payload.
        assertEquals(300L, preview.totalBytes)
        assertTrue(preview.estimated == false)
    }

    @Test fun reportsConflictsAndUnreadableItems() {
        val preview = TransferPlan.build(
            listOf(
                TransferPlan.Item("/a/keep", existsAtDestination = true),
                TransferPlan.Item("/a/secret", readable = false),
                TransferPlan.Item("/a/ok"),
            ),
            destination = "/remote",
        )
        assertTrue(preview.hasIssues)
        assertEquals(listOf("/a/keep"), preview.conflicts)
        assertEquals(listOf("/a/secret"), preview.unreadable)
    }

    @Test fun flagsSymlinksAndCappedListings() {
        val preview = TransferPlan.build(
            listOf(TransferPlan.Item("/a/link", symlink = true)),
            destination = "/remote",
            estimated = true,
        )
        assertEquals(1, preview.symlinks)
        assertTrue(preview.estimated)
    }

    @Test fun emptyPlanIsClean() {
        val preview = TransferPlan.build(emptyList(), destination = "/remote")
        assertFalse(preview.hasIssues)
        assertEquals(0, preview.fileCount)
        assertEquals("0 B", preview.totalLabel)
    }

    @Test fun formatsBytesReadably() {
        assertEquals("0 B", TransferPlan.formatBytes(0))
        assertEquals("1023 B", TransferPlan.formatBytes(1023))
        assertEquals("1 KB", TransferPlan.formatBytes(1024))
        assertEquals("1.5 KB", TransferPlan.formatBytes(1536))
        assertEquals("1 MB", TransferPlan.formatBytes(1024 * 1024))
        assertEquals("1 GB", TransferPlan.formatBytes(1024L * 1024 * 1024))
    }
}
