package app.terminalssh.secure.sftp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraversalGuardTest {

    private fun entry(name: String, path: String, dir: Boolean, link: Boolean = false) =
        RemoteEntry(name, path, dir, link, 10L, 0L, if (dir) "drwxr-xr-x" else "-rw-r--r--")

    /**
     * Walks a synthetic tree exactly the way SftpClient.walkRecursive does, so a cycle
     * that would hang the real walker hangs this one too — unless the guard stops it.
     */
    private fun walk(
        tree: Map<String, List<RemoteEntry>>,
        root: String,
        guard: TraversalGuard,
    ): List<String> {
        val files = mutableListOf<String>()
        guard.enter(root, 0)
        fun recurse(path: String, depth: Int) {
            for (e in tree[path].orEmpty()) {
                if (guard.canDescend(e)) {
                    if (guard.enter(e.path, depth)) recurse(e.path, depth + 1)
                } else if (!e.isDirectory) {
                    if (!guard.countFile()) return
                    files += e.path
                }
            }
        }
        recurse(root, 1)
        return files
    }

    @Test fun symlinkCycleTerminates() {
        // /a contains a real dir /a/b, and /a/b contains a symlink back to /a.
        // Following it is an infinite descent; the old walker had nothing to stop it.
        val tree = mapOf(
            "/a" to listOf(entry("b", "/a/b", dir = true), entry("f1", "/a/f1", dir = false)),
            "/a/b" to listOf(entry("loop", "/a", dir = true, link = true), entry("f2", "/a/b/f2", dir = false)),
        )
        val guard = TraversalGuard()
        val files = walk(tree, "/a", guard)

        assertEquals(setOf("/a/f1", "/a/b/f2"), files.toSet(), "every real file is still collected exactly once")
        assertEquals(2, files.size, "and none of them twice")
        assertTrue(guard.entriesSeen < 10, "the walk must not spin: ${guard.entriesSeen} entries")
    }

    @Test fun symlinkedDirectoryIsNeverDescended() {
        assertFalse(TraversalGuard().canDescend(entry("l", "/x", dir = true, link = true)))
        assertTrue(TraversalGuard().canDescend(entry("d", "/x", dir = true)))
        assertFalse(TraversalGuard().canDescend(entry("f", "/x", dir = false)))
    }

    @Test fun cycleWithoutAnySymlinkStillTerminates() {
        // A server that resolves links before reporting them, or a bind mount: two real
        // directories that contain each other. Only the visited set catches this.
        val tree = mapOf(
            "/p" to listOf(entry("q", "/q", dir = true)),
            "/q" to listOf(entry("p", "/p", dir = true), entry("f", "/q/f", dir = false)),
        )
        val guard = TraversalGuard()
        val files = walk(tree, "/p", guard)

        assertEquals(listOf("/q/f"), files)
        assertTrue(guard.truncated, "revisiting a directory must mark the result partial")
    }

    @Test fun depthCapStopsAnInfinitelyDeepSyntheticTree() {
        // A hostile server can answer every ls with a fresh child forever. No path
        // repeats, so only the depth cap ends it.
        val guard = TraversalGuard(maxDepth = 8)
        var depth = 0
        var path = "/d"
        while (guard.enter(path, depth)) { depth++; path += "/d" }

        assertTrue(guard.truncated)
        assertTrue(depth <= 9, "descent must stop at the cap, stopped at $depth")
    }

    @Test fun entryCapBoundsAnEnormousFlatDirectory() {
        val guard = TraversalGuard(maxEntries = 5)
        var counted = 0
        while (guard.countFile()) counted++

        assertEquals(5, counted)
        assertTrue(guard.truncated)
    }

    @Test fun aCleanTreeIsNotMarkedTruncated() {
        val tree = mapOf(
            "/r" to listOf(entry("s", "/r/s", dir = true), entry("a", "/r/a", dir = false)),
            "/r/s" to listOf(entry("b", "/r/s/b", dir = false)),
        )
        val guard = TraversalGuard()
        val files = walk(tree, "/r", guard)

        assertEquals(setOf("/r/a", "/r/s/b"), files.toSet())
        assertEquals(2, files.size)
        assertFalse(guard.truncated, "an ordinary tree must complete without truncation")
    }

    @Test fun theSamePathInDifferentFormsIsStillOneVisit() {
        val guard = TraversalGuard()
        assertTrue(guard.enter("/a/b", 1))
        assertFalse(guard.enter("/a/b/", 1), "a trailing slash is the same directory")
    }
}
