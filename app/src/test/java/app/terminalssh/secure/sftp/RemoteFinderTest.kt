package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFinderTest {

    @Test fun buildsAQuotedBoundedFind() {
        val cmd = RemoteFinder.command(RemoteFinder.Query(root = "/srv/app", namePattern = "*.jar", maxDepth = 4, maxResults = 50))!!
        assertTrue(cmd.contains("find '/srv/app' -maxdepth 4 -type f -name '*.jar'"))
        assertTrue(cmd.contains("head -n 50"))
        assertTrue(cmd.contains(RemoteFinder.DIR_MARKER))
    }

    @Test fun rootWithAHostileQuoteStaysQuoted() {
        val cmd = RemoteFinder.command(RemoteFinder.Query(root = "/srv/x'; rm -rf ~; '"))!!
        assertTrue(cmd.contains("'/srv/x'\\''; rm -rf ~; '\\'''"))
    }

    @Test fun invalidRootOrPatternYieldsNoCommand() {
        assertNull(RemoteFinder.command(RemoteFinder.Query(root = "")))
        assertNull(RemoteFinder.command(RemoteFinder.Query(root = "/srv\nrm -rf /")))
        assertNull(RemoteFinder.command(RemoteFinder.Query(root = "/srv", namePattern = "x\ny")))
    }

    @Test fun fileOnlyQueryOmitsTheDirectorySection() {
        val cmd = RemoteFinder.command(RemoteFinder.Query(root = "/srv", kind = RemoteFinder.Kind.FILE))!!
        assertFalse(cmd.contains(RemoteFinder.DIR_MARKER))
    }

    @Test fun directoryOnlyQueryStartsWithTheMarker() {
        val cmd = RemoteFinder.command(RemoteFinder.Query(root = "/srv", kind = RemoteFinder.Kind.DIRECTORY))!!
        assertFalse(cmd.contains("-type f"))
        assertTrue(cmd.contains("-type d"))
    }

    @Test fun parsesFilesAndDirectoriesFromTheirSections() {
        val output = "/srv/a.jar\n/srv/b.jar\n${RemoteFinder.DIR_MARKER}\n/srv/lib.jar\n"
        val results = RemoteFinder.parse(output)
        assertEquals(3, results.size)
        assertEquals(listOf("/srv/a.jar", "/srv/b.jar", "/srv/lib.jar"), results.map { it.path })
        assertFalse(results.first { it.path == "/srv/a.jar" }.isDirectory)
        assertTrue(results.first { it.path == "/srv/lib.jar" }.isDirectory)
    }

    @Test fun fileOnlyOutputHasNoDirectories() {
        val results = RemoteFinder.parse("/srv/a.jar\n/srv/b.jar\n")
        assertTrue(results.all { !it.isDirectory })
    }

    @Test fun findErrorLinesAreDropped() {
        val results = RemoteFinder.parse("find: ‘/root’: Permission denied\n/srv/a.jar\n")
        assertEquals(listOf("/srv/a.jar"), results.map { it.path })
    }

    @Test fun duplicatePathsAreCollapsedAndSorted() {
        val results = RemoteFinder.parse("/srv/z\n/srv/a\n/srv/z\n")
        assertEquals(listOf("/srv/a", "/srv/z"), results.map { it.path })
    }

    @Test fun directoriesGetATrailingSlashForDisplay() {
        assertEquals("/srv/lib/", RemoteFinder.toQueryText(RemoteFinder.Result("/srv/lib", true)))
        assertEquals("/srv/a.jar", RemoteFinder.toQueryText(RemoteFinder.Result("/srv/a.jar", false)))
    }
}
