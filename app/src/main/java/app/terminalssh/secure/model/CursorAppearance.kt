package app.terminalssh.secure.model

/**
 * How the terminal cursor is drawn (F009), as a value the rest of the app can agree on.
 *
 * The stored setting is a bare lowercase string, which is fine until someone restores an old
 * backup or hand-edits it: `"Block"`, `"bar "` or `"whatever"` all reach us eventually. Parsing
 * therefore has one tolerant entry point and never throws — an unknown shape falls back to the
 * default instead of leaving the terminal without a cursor.
 */
enum class CursorShape(val stored: String) {
    BLOCK("block"),
    BAR("bar"),
    UNDERLINE("underline"),
    ;

    companion object {
        val default = BLOCK

        /** Tolerant parse: blank or unknown values fall back to [default]. */
        fun fromStored(value: String?): CursorShape {
            val trimmed = value?.trim() ?: return default
            return entries.firstOrNull { it.stored.equals(trimmed, ignoreCase = true) } ?: default
        }
    }
}

data class CursorAppearance(
    val shape: CursorShape = CursorShape.default,
    val blink: Boolean = true,
) {
    companion object {
        val default = CursorAppearance()

        /** Builds from the two stored values, with the same tolerance as [CursorShape.fromStored]. */
        fun fromStored(shape: String?, blink: Boolean): CursorAppearance =
            CursorAppearance(CursorShape.fromStored(shape), blink)
    }
}
