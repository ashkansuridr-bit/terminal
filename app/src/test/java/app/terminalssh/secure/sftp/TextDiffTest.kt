package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextDiffTest {

    @Test fun identicalTextHasNoChanges() {
        val r = TextDiff.diff("a\nb\nc", "a\nb\nc")
        assertFalse(r.hasChanges)
        assertEquals(0, r.added)
        assertEquals(0, r.removed)
        assertTrue(r.lines.all { it.kind == TextDiff.Kind.KEPT })
    }

    @Test fun aOneLineEditShowsAsOneRemovalAndOneAddition() {
        val r = TextDiff.diff("listen 80;", "listen 81;")
        assertEquals(1, r.added)
        assertEquals(1, r.removed)
        assertTrue(r.hasChanges)
    }

    @Test fun anAppendedLineIsOnlyAnAddition() {
        val r = TextDiff.diff("a\nb", "a\nb\nc")
        assertEquals(1, r.added)
        assertEquals(0, r.removed)
        assertEquals("c", r.lines.last().text)
    }

    @Test fun aDeletedLineIsOnlyARemoval() {
        val r = TextDiff.diff("a\nb\nc", "a\nc")
        assertEquals(0, r.added)
        assertEquals(1, r.removed)
        assertEquals("b", r.lines.first { it.kind == TextDiff.Kind.REMOVED }.text)
    }

    @Test fun unchangedLinesAroundAnEditAreKept() {
        val r = TextDiff.diff("a\nb\nc", "a\nX\nc")
        val kept = r.lines.filter { it.kind == TextDiff.Kind.KEPT }.map { it.text }
        assertEquals(listOf("a", "c"), kept, "context must survive so the diff is readable")
    }

    @Test fun lineNumbersPointAtTheRightSide() {
        val r = TextDiff.diff("a\nb", "a\nX")
        val removed = r.lines.first { it.kind == TextDiff.Kind.REMOVED }
        val added = r.lines.first { it.kind == TextDiff.Kind.ADDED }
        assertEquals(2, removed.oldLine)
        assertEquals(null, removed.newLine)
        assertEquals(2, added.newLine)
        assertEquals(null, added.oldLine)
    }

    @Test fun everyOriginalAndNewLineIsAccountedFor() {
        val old = "1\n2\n3\n4"
        val new = "1\n9\n3\n4\n5"
        val r = TextDiff.diff(old, new)
        val fromOld = r.lines.count { it.kind != TextDiff.Kind.ADDED }
        val fromNew = r.lines.count { it.kind != TextDiff.Kind.REMOVED }
        assertEquals(old.split('\n').size, fromOld)
        assertEquals(new.split('\n').size, fromNew)
    }

    @Test fun anEmptyFileToContentIsAllAdditions() {
        val r = TextDiff.diff("", "a\nb")
        assertTrue(r.added >= 2)
    }

    @Test fun aHugeFileIsReportedTruncatedRatherThanWedgingTheUi() {
        val huge = (1..TextDiff.MAX_LINES + 1).joinToString("\n")
        val r = TextDiff.diff(huge, huge)
        assertTrue(r.truncated)
        assertTrue(r.lines.isEmpty())
    }

    @Test fun aFileExactlyAtTheLimitStillDiffs() {
        val text = (1..TextDiff.MAX_LINES).joinToString("\n")
        assertFalse(TextDiff.diff(text, text).truncated)
    }
}
