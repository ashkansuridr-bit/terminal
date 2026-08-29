package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchRenameTest {

    @Test fun aCounterPatternNumbersInOrderAndPads() {
        val plan = BatchRename.plan(listOf("a.jpg", "b.jpg", "c.jpg"), "photo-{n}.{ext}")
        assertEquals(listOf("photo-1.jpg", "photo-2.jpg", "photo-3.jpg"), plan.changes.map { it.to })
    }

    @Test fun paddingWidthFollowsTheLargestNumber() {
        val names = (1..12).map { "f$it.txt" }
        val plan = BatchRename.plan(names, "{n}.txt")
        assertEquals("01.txt", plan.changes.first().to, "single digits pad to the widest number")
        assertEquals("12.txt", plan.changes.last().to)
    }

    @Test fun theUnpaddedCounterIsAvailableToo() {
        assertEquals("1-a.txt", BatchRename.plan(listOf("a.txt"), "{i}-{name}.{ext}").changes[0].to)
    }

    @Test fun theOriginalStemAndExtensionAreAvailable() {
        val plan = BatchRename.plan(listOf("report.pdf"), "{name}-final.{ext}")
        assertEquals("report-final.pdf", plan.changes[0].to)
    }

    @Test fun aFileWithNoExtensionIsHandled() {
        val plan = BatchRename.plan(listOf("README"), "{name}.md")
        assertEquals("README.md", plan.changes[0].to)
    }

    @Test fun findAndReplaceIsAnAlternativeToThePattern() {
        val plan = BatchRename.plan(listOf("draft_a.txt", "draft_b.txt"), "", find = "draft_", replace = "final_")
        assertEquals(listOf("final_a.txt", "final_b.txt"), plan.changes.map { it.to })
    }

    @Test fun startAtLetsTheCounterBeginAnywhere() {
        val plan = BatchRename.plan(listOf("a", "b"), "{i}", startAt = 100)
        assertEquals(listOf("100", "101"), plan.changes.map { it.to })
    }

    // ---- validation: a rename batch is destructive and irreversible ----

    @Test fun aCollidingResultIsFlaggedNotApplied() {
        // Two files mapping onto one name would destroy the second.
        val plan = BatchRename.plan(listOf("a.txt", "b.txt"), "same.txt")
        assertTrue(plan.hasErrors)
        assertEquals("duplicate", plan.changes[1].error)
        assertEquals(1, plan.applicable.size, "only the first may be applied")
    }

    @Test fun aPatternProducingAPathSeparatorIsRejected() {
        val plan = BatchRename.plan(listOf("a.txt"), "../escaped/{name}")
        assertEquals("separator", plan.changes[0].error)
        assertTrue(plan.applicable.isEmpty(), "a rename must never move a file out of its directory")
    }

    @Test fun anEmptyResultIsRejected() {
        assertEquals("empty", BatchRename.plan(listOf("a.txt"), "   ").changes[0].error)
    }

    @Test fun dotAndDotDotAreRejected() {
        assertEquals("reserved", BatchRename.plan(listOf("a"), ".").changes[0].error)
        assertEquals("reserved", BatchRename.plan(listOf("a"), "..").changes[0].error)
    }

    @Test fun anUnchangedNameIsNotAnApplicableChange() {
        val plan = BatchRename.plan(listOf("a.txt"), "{name}.{ext}")
        assertEquals("a.txt", plan.changes[0].to)
        assertFalse(plan.changes[0].isValid, "renaming a file to its own name is a no-op")
        assertTrue(plan.applicable.isEmpty())
    }

    @Test fun anEmptySelectionPlansNothing() {
        assertTrue(BatchRename.plan(emptyList(), "{n}").changes.isEmpty())
    }
}
