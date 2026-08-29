package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntrySortTest {

    private fun e(name: String, dir: Boolean = false, size: Long = 0, mtime: Long = 0) =
        RemoteEntry(name, "/srv/$name", dir, false, size, mtime, if (dir) "drwxr-xr-x" else "-rw-r--r--")

    private val listing = listOf(
        e("readme.md", size = 300, mtime = 30),
        e("logs", dir = true, mtime = 10),
        e(".env", size = 50, mtime = 40),
        e("App.kt", size = 2000, mtime = 20),
        e(".git", dir = true, mtime = 5),
        e("backup.tar.gz", size = 900_000, mtime = 1),
    )

    @Test fun dotfilesAreHiddenByDefault() {
        val names = EntrySort.apply(listing).map { it.name }
        assertFalse(names.contains(".env"))
        assertFalse(names.contains(".git"))
        assertEquals(4, names.size)
    }

    @Test fun theToggleRevealsThemAgain() {
        val names = EntrySort.apply(listing, showHidden = true).map { it.name }
        assertTrue(names.contains(".env"), "on a server most of what matters starts with a dot")
        assertTrue(names.contains(".git"))
        assertEquals(6, names.size)
    }

    @Test fun directoriesLeadInEveryMode() {
        for (mode in EntrySort.Mode.entries) {
            for (desc in listOf(false, true)) {
                val sorted = EntrySort.apply(listing, mode, desc, showHidden = true)
                val lastDir = sorted.indexOfLast { it.isDirectory }
                val firstFile = sorted.indexOfFirst { !it.isDirectory }
                assertTrue(lastDir < firstFile, "$mode/$desc scattered directories through the files")
            }
        }
    }

    @Test fun nameOrderIsCaseInsensitive() {
        val names = EntrySort.apply(listing, EntrySort.Mode.NAME).filterNot { it.isDirectory }.map { it.name }
        assertEquals(listOf("App.kt", "backup.tar.gz", "readme.md"), names)
    }

    @Test fun sizeOrderPutsTheBiggestLastThenFirstWhenReversed() {
        val asc = EntrySort.apply(listing, EntrySort.Mode.SIZE).filterNot { it.isDirectory }.map { it.name }
        assertEquals("backup.tar.gz", asc.last())
        val desc = EntrySort.apply(listing, EntrySort.Mode.SIZE, descending = true)
            .filterNot { it.isDirectory }.map { it.name }
        assertEquals("backup.tar.gz", desc.first())
    }

    @Test fun modifiedOrderIsNewestFirstWhenReversed() {
        val newest = EntrySort.apply(listing, EntrySort.Mode.MODIFIED, descending = true, showHidden = true)
            .filterNot { it.isDirectory }.first()
        assertEquals(".env", newest.name, "newest-first is the reason this mode exists")
    }

    @Test fun typeOrderGroupsByExtension() {
        val byType = EntrySort.apply(listing, EntrySort.Mode.TYPE).filterNot { it.isDirectory }.map { it.name }
        assertEquals(listOf("backup.tar.gz", "App.kt", "readme.md"), byType)
    }

    @Test fun extensionOfHandlesDirectoriesDotfilesAndTrailingDots() {
        assertEquals("", EntrySort.extensionOf(e("logs", dir = true)))
        assertEquals("", EntrySort.extensionOf(e(".env")))
        assertEquals("", EntrySort.extensionOf(e("Makefile")))
        assertEquals("", EntrySort.extensionOf(e("weird.")))
        assertEquals("gz", EntrySort.extensionOf(e("a.tar.gz")))
    }

    @Test fun reversingDoesNotReverseTheDirectoryGrouping() {
        val sorted = EntrySort.apply(listing, EntrySort.Mode.NAME, descending = true, showHidden = true)
        assertTrue(sorted.first().isDirectory, "descending must not push directories to the bottom")
    }

    @Test fun anEmptyListingSortsToNothing() {
        assertTrue(EntrySort.apply(emptyList()).isEmpty())
    }
}
