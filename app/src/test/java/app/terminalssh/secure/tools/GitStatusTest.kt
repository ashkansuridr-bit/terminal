package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitStatusTest {

    private val sample =
        "# branch.oid abc123\n" +
            "# branch.head main\n" +
            "# branch.upstream origin/main\n" +
            "# branch.ab +2 -1\n" +
            "1 .M N... 100644 100644 100644 aaaa bbbb src/Main.kt\n" +
            "? newfile.txt\n"

    @Test fun parsesBranchAndDivergence() {
        val status = GitStatus.parse(sample)
        assertEquals("main", status.branch)
        assertEquals("origin/main", status.upstream)
        assertEquals(2, status.ahead)
        assertEquals(1, status.behind)
        assertTrue(status.hasDiverged)
    }

    @Test fun parsesChanges() {
        val status = GitStatus.parse(sample)
        assertEquals(2, status.changes.size)
        assertEquals(".M", status.changes[0].code)
        assertEquals("src/Main.kt", status.changes[0].path)
        assertTrue(status.changes[1].isUntracked)
        assertFalse(status.isClean)
    }

    @Test fun parsesRenameEntries() {
        val rename = "2 R. N... 100644 100644 100644 aaaa bbbb R100 new/path.kt\told/path.kt\n"
        val change = GitStatus.parse(rename).changes.single()
        assertEquals("new/path.kt", change.path)
        assertEquals('R', change.x)
    }

    @Test fun detectsConflicts() {
        val conflict = "1 UU N... 100644 100644 100644 aaaa bbbb conflicted.txt\n"
        assertTrue(GitStatus.parse(conflict).changes.single().isConflict)
    }

    @Test fun cleanTreeHasNoChanges() {
        val clean = "# branch.head main\n# branch.ab +0 -0\n"
        assertTrue(GitStatus.parse(clean).isClean)
    }

    @Test fun directoryValidation() {
        assertTrue(GitStatus.isValidDir("/srv/app"))
        assertFalse(GitStatus.isValidDir(""))
        assertFalse(GitStatus.isValidDir("/srv/app\nrm -rf /"))
        assertNull(GitStatus.statusCommand("/srv/app\nrm -rf /"))
    }

    @Test fun commandsQuoteTheDirectory() {
        assertEquals("git -C '/srv/app' status --porcelain=v2 --branch 2>&1", GitStatus.statusCommand("/srv/app"))
        val pull = GitStatus.actionCommand("/srv/app", GitStatus.Action.PULL)!!
        assertEquals("git -C '/srv/app' pull --ff-only 2>&1", pull.text)
        assertTrue(pull.mutating)
    }

    @Test fun errorOutputYieldsAnEmptyStatusRatherThanGarbage() {
        // "fatal: not a git repository" is neither a branch header nor a change entry.
        val status = GitStatus.parse("fatal: not a git repository (or any parent)\n")
        assertEquals("", status.branch)
        assertTrue(status.isClean)
        assertNull(GitStatus.statusCommand(""))
    }
}
