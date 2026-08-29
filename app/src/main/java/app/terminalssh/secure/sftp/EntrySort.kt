package app.terminalssh.secure.sftp

/**
 * Ordering and visibility for a directory listing (#22, #23).
 *
 * The browser had exactly one order — directories first, then name — and no way to see
 * dotfiles at all. On a Linux server most of what a developer needs starts with a dot
 * (`.env`, `.ssh`, `.gitignore`), so hiding them by default while offering no toggle made
 * whole directories look empty.
 *
 * Directories stay grouped ahead of files in every mode. Sorting a deployment directory
 * by size is useful; having the folders scattered through it is not.
 */
object EntrySort {

    enum class Mode { NAME, SIZE, MODIFIED, TYPE }

    /** A dotfile by POSIX convention. `..` is navigation and filtered earlier. */
    fun isHidden(entry: RemoteEntry): Boolean = entry.name.startsWith(".")

    fun apply(
        entries: List<RemoteEntry>,
        mode: Mode = Mode.NAME,
        descending: Boolean = false,
        showHidden: Boolean = false,
    ): List<RemoteEntry> {
        val visible = if (showHidden) entries else entries.filterNot { isHidden(it) }

        val within: Comparator<RemoteEntry> = when (mode) {
            Mode.NAME -> compareBy { it.name.lowercase() }
            Mode.SIZE -> compareBy { it.sizeBytes }
            Mode.MODIFIED -> compareBy { it.modifiedEpochSeconds }
            Mode.TYPE -> compareBy<RemoteEntry> { extensionOf(it) }.thenBy { it.name.lowercase() }
        }
        // Reverse only the field being sorted on, never the directory grouping.
        val ordered = if (descending) within.reversed() else within
        return visible.sortedWith(
            compareByDescending<RemoteEntry> { it.isDirectory }.then(ordered),
        )
    }

    /** Lowercase extension, or empty for a directory or a name without one. */
    fun extensionOf(entry: RemoteEntry): String {
        if (entry.isDirectory) return ""
        val dot = entry.name.lastIndexOf('.')
        return if (dot <= 0 || dot == entry.name.length - 1) "" else entry.name.substring(dot + 1).lowercase()
    }
}
