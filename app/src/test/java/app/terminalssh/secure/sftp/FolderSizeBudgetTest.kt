package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderSizeBudgetTest {

    @Test fun continuesUntilTheEntryLimitThenStops() {
        val tracker = FolderSizeBudget.Tracker(FolderSizeBudget.Limits(maxEntries = 3, maxMillis = 60_000))
        assertEquals(FolderSizeBudget.Decision.CONTINUE, tracker.recordEntry(10))
        assertEquals(FolderSizeBudget.Decision.CONTINUE, tracker.recordEntry(10))
        assertEquals(FolderSizeBudget.Decision.STOP_ENTRY_LIMIT, tracker.recordEntry(10))
        assertTrue(tracker.isStopped)
        assertTrue(tracker.progress().truncated)
        assertEquals("entry limit", tracker.progress().stopReason)
    }

    @Test fun stopsWhenTheWallClockBudgetIsExhausted() {
        var now = 0L
        val tracker = FolderSizeBudget.Tracker(
            FolderSizeBudget.Limits(maxEntries = 1_000, maxMillis = 1_000),
            clock = { now },
        )
        assertEquals(FolderSizeBudget.Decision.CONTINUE, tracker.recordEntry(5))
        now = 2_000
        assertEquals(FolderSizeBudget.Decision.STOP_TIME_LIMIT, tracker.recordEntry(5))
        assertEquals("time limit", tracker.progress().stopReason)
    }

    @Test fun cancelStopsTheWalk() {
        val tracker = FolderSizeBudget.Tracker()
        assertEquals(FolderSizeBudget.Decision.STOP_CANCELLED, tracker.cancel())
        assertTrue(tracker.isStopped)
        assertEquals("cancelled", tracker.progress().stopReason)
    }

    @Test fun progressAccumulatesEntriesAndBytes() {
        var now = 0L
        val tracker = FolderSizeBudget.Tracker(clock = { now })
        tracker.recordEntry(100)
        tracker.recordEntry(0)
        tracker.recordEntry(400)
        now = 42
        val progress = tracker.progress()
        assertEquals(3, progress.entries)
        assertEquals(500, progress.bytes)
        assertEquals(42, progress.elapsedMillis)
        assertFalse(progress.truncated)
    }

    @Test fun aFreshTrackerIsNotStopped() {
        val tracker = FolderSizeBudget.Tracker()
        assertFalse(tracker.isStopped)
        assertEquals("", tracker.progress().stopReason)
    }

    @Test fun commandQuotesThePathAndRejectsUnsafeOnes() {
        assertEquals(
            "LC_ALL=C du -sk '/srv/app' 2>/dev/null",
            FolderSizeBudget.command("/srv/app"),
        )
        assertNull(FolderSizeBudget.command(""))
        assertNull(FolderSizeBudget.command("/srv/app\nrm -rf /"))
        assertNull(FolderSizeBudget.command("/srv/app\u0000"))
    }

    @Test fun commandEscapesEmbeddedSingleQuotes() {
        val cmd = FolderSizeBudget.command("/srv/x'; rm -rf ~; '")!!
        assertTrue(cmd.contains("'/srv/x'\\''; rm -rf ~; '\\'''"), cmd)
    }

    @Test fun parsesTheLastWellFormedLineAsTheTotal() {
        val output = "4\t/sub\n8\t/sub/nested\n12\t/root\n"
        assertEquals(12L * 1024, FolderSizeBudget.parseTotalBytes(output))
        assertNull(FolderSizeBudget.parseTotalBytes("du: cannot access\n"))
    }

    @Test fun parsesPerChildSizesExcludingTheRootTotal() {
        val output = "4\t/sub\n8\t/sub/nested\n12\t/root\n"
        val sizes = FolderSizeBudget.parseChildSizes(output, "/root")
        assertEquals(4L * 1024, sizes["/sub"])
        assertEquals(8L * 1024, sizes["/sub/nested"])
        assertFalse(sizes.containsKey("/root"))
    }
}
