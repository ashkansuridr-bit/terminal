package app.terminalssh.secure.sftp

/**
 * Pre-transfer summary for a folder operation (F022).
 *
 * Before a recursive upload/download the user should see what they are about to do: how many
 * files, how much data, where it lands, what will collide, and what cannot be read. Building
 * it from an already-listed set of items keeps it pure and testable; the listing itself is the
 * caller's (bounded) job.
 */
object TransferPlan {

    data class Item(
        val path: String,
        val sizeBytes: Long = 0L,
        val isDirectory: Boolean = false,
        val existsAtDestination: Boolean = false,
        val readable: Boolean = true,
        val symlink: Boolean = false,
    )

    data class Preview(
        val destination: String,
        val fileCount: Int,
        val directoryCount: Int,
        val totalBytes: Long,
        val conflicts: List<String>,
        val unreadable: List<String>,
        val symlinks: Int,
        val estimated: Boolean,
    ) {
        val hasIssues: Boolean get() = conflicts.isNotEmpty() || unreadable.isNotEmpty()
        val totalLabel: String get() = formatBytes(totalBytes)
    }

    /**
     * @param estimated true when the listing was capped, so the counts are a lower bound.
     *   Reporting a capped listing as exact would be the dishonest default.
     */
    fun build(items: List<Item>, destination: String, estimated: Boolean = false): Preview {
        var files = 0
        var dirs = 0
        var total = 0L
        var symlinks = 0
        val conflicts = ArrayList<String>()
        val unreadable = ArrayList<String>()
        for (item in items) {
            if (item.isDirectory) dirs++ else files++
            // A directory's own size is metadata, not payload; only count file bytes.
            if (!item.isDirectory && item.sizeBytes > 0) total += item.sizeBytes
            if (item.symlink) symlinks++
            if (item.existsAtDestination) conflicts += item.path
            if (!item.readable) unreadable += item.path
        }
        return Preview(
            destination = destination,
            fileCount = files,
            directoryCount = dirs,
            totalBytes = total,
            conflicts = conflicts,
            unreadable = unreadable,
            symlinks = symlinks,
            estimated = estimated,
        )
    }

    /** Human-readable size with the usual binary units. */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "?"
        if (bytes < 1_024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return if (value >= 100 || value == value.toLong().toDouble()) {
            "${value.toLong()} ${units[unit]}"
        } else {
            String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
        }
    }
}
