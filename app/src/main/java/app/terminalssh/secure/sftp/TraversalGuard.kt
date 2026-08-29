package app.terminalssh.secure.sftp

/**
 * Bounds a recursive walk of a remote tree.
 *
 * Without this, `a -> b -> symlink-to-a` makes [SftpClient.listRecursive] and
 * [SftpClient.recursiveSize] recurse until the stack dies, and a hostile server can
 * hand out an infinitely deep synthetic tree that never contains a cycle at all. Both
 * failure modes are reachable from ordinary UI actions (folder size, folder download,
 * sync), so the guard lives next to the walkers rather than in any one caller.
 *
 * Three independent limits, because each catches something the others miss:
 *
 * - **Symlinked directories are never followed.** This is what `find` and `rsync` do by
 *   default, and it kills the common cycle outright. The link still appears in the
 *   listing; it is just not descended into.
 * - **A visited set of real paths**, for loops that do not involve a symlink the server
 *   admits to — bind mounts, or a server that resolves links before reporting them.
 * - **A depth cap and an entry cap**, so a tree that is merely enormous fails predictably
 *   instead of exhausting memory or the call stack.
 *
 * Exceeding a limit stops that branch; it does not throw. A partial folder size is
 * useful, and a traversal that dies mid-download is worse than one that stops early.
 * Callers that need to tell the user check [truncated].
 */
class TraversalGuard(
    private val maxDepth: Int = MAX_DEPTH,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val visited = HashSet<String>()

    /** True once any limit stopped a branch, so callers can say the result is partial. */
    var truncated: Boolean = false
        private set

    var entriesSeen: Int = 0
        private set

    /**
     * Claims [path] at [depth] for this traversal. False means the caller must not
     * descend: either the path was already walked, or a limit is reached.
     */
    fun enter(path: String, depth: Int): Boolean {
        if (depth > maxDepth) { truncated = true; return false }
        if (entriesSeen >= maxEntries) { truncated = true; return false }
        if (!visited.add(RemotePath.normalize(path))) { truncated = true; return false }
        entriesSeen++
        return true
    }

    /** Counts a leaf against the entry budget. False means the budget is spent. */
    fun countFile(): Boolean {
        if (entriesSeen >= maxEntries) { truncated = true; return false }
        entriesSeen++
        return true
    }

    /**
     * Whether a listing entry is a directory this walk may descend into. A symlink is
     * never descended even when the server reports it as a directory.
     */
    fun canDescend(entry: RemoteEntry): Boolean = entry.isDirectory && !entry.isSymlink

    companion object {
        /** Deeper than any real deployment tree; shallow enough to never blow the stack. */
        const val MAX_DEPTH = 64

        /** Bounds memory and time on a pathological tree. */
        const val MAX_ENTRIES = 50_000
    }
}
