package app.terminalssh.secure.sftp

/**
 * Plans a multi-file rename and shows the result before anything is renamed.
 *
 * Renaming is destructive and irreversible over SFTP, so the plan is computed and
 * validated in full first: a batch that would collide, escape its directory, or produce
 * an empty name is reported as such rather than half-applied and then discovered.
 */
object BatchRename {

    data class Change(val from: String, val to: String, val error: String? = null) {
        val isValid: Boolean get() = error == null && from != to
    }

    data class Plan(val changes: List<Change>) {
        val applicable: List<Change> get() = changes.filter { it.isValid }
        val hasErrors: Boolean get() = changes.any { it.error != null }
    }

    /**
     * Builds new names from [pattern].
     *
     * Placeholders: `{name}` the name without extension, `{ext}` the extension without
     * its dot, `{n}` a counter from [startAt] padded to the width of the largest number,
     * and `{i}` the same counter unpadded.
     */
    fun plan(
        names: List<String>,
        pattern: String,
        startAt: Int = 1,
        find: String? = null,
        replace: String = "",
    ): Plan {
        val width = (names.size + startAt - 1).toString().length
        val seen = HashSet<String>()
        val changes = names.mapIndexed { index, original ->
            val stem = original.substringBeforeLast('.', original)
            val ext = if (original.contains('.')) original.substringAfterLast('.') else ""
            val counter = startAt + index

            var candidate = if (find.isNullOrEmpty()) {
                pattern
                    .replace("{name}", stem)
                    .replace("{ext}", ext)
                    .replace("{n}", counter.toString().padStart(width, '0'))
                    .replace("{i}", counter.toString())
            } else {
                original.replace(find, replace)
            }
            candidate = candidate.trim()

            val error = when {
                candidate.isEmpty() -> "empty"
                candidate.contains('/') -> "separator"
                candidate == "." || candidate == ".." -> "reserved"
                !seen.add(candidate) -> "duplicate"
                else -> null
            }
            Change(original, candidate, error)
        }
        return Plan(changes)
    }
}
