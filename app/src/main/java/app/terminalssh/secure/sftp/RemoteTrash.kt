package app.terminalssh.secure.sftp

/**
 * Paths and commands for a recoverable delete (#26).
 *
 * `delete` and `deleteAll` are irreversible over SFTP, and the confirm dialog is the only
 * thing between a mis-tap and gone. Moving into a trash directory turns that into
 * something a user can undo, at the cost of the space until they empty it.
 *
 * The trash lives under the user's home rather than `/tmp` so it survives a reboot, and
 * each item keeps a timestamp prefix so two files with the same name do not collide.
 */
object RemoteTrash {

    const val DIR_NAME = ".terminalssh-trash"

    fun trashDir(home: String): String = RemotePath.join(home, DIR_NAME)

    /**
     * Where [originalPath] goes. The timestamp prefix keeps repeated deletes of the same
     * name distinct, and the original name stays readable so the list means something.
     */
    fun trashedPath(home: String, originalPath: String, timestampMillis: Long): String {
        val name = originalPath.trimEnd('/').substringAfterLast('/')
        return RemotePath.join(trashDir(home), "$timestampMillis-$name")
    }

    /** Splits a trashed entry name back into when it was deleted and what it was called. */
    fun parseTrashedName(name: String): Pair<Long, String>? {
        val dash = name.indexOf('-')
        if (dash <= 0) return null
        val stamp = name.substring(0, dash).toLongOrNull() ?: return null
        val original = name.substring(dash + 1)
        if (original.isEmpty()) return null
        return stamp to original
    }

    /**
     * True when [path] is already inside the trash. Deleting from the trash must be a
     * real delete, not a move into itself.
     */
    fun isInTrash(path: String, home: String): Boolean =
        RemotePath.normalize(path).startsWith(RemotePath.normalize(trashDir(home)))
}
