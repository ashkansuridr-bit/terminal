package app.terminalssh.secure.model

/**
 * The customisable extra-keys toolbar (F010).
 *
 * A phone keyboard has no Esc, no Ctrl and no arrow keys, so the app draws its own row — but
 * which keys are worth the width depends on what the user does: a shell user wants `|` and
 * `-`, a vim user wants Esc and the arrows, a tmux user wants Ctrl. This models the bar as an
 * ordered, editable list of keys, serialised as a short token string so it round-trips through
 * settings without a schema.
 *
 * The row stays LTR even in an RTL locale — the terminal grid itself is LTR — so the order is
 * preserved exactly as stored.
 */
object KeyToolbarConfig {

    enum class Key(val token: String) {
        ESC("esc"),
        TAB("tab"),
        CTRL("ctrl"),
        ALT("alt"),
        SHIFT("shift"),
        LEFT("left"),
        UP("up"),
        DOWN("down"),
        RIGHT("right"),
        HOME("home"),
        END("end"),
        PAGE_UP("pgup"),
        PAGE_DOWN("pgdn"),
        PIPE("pipe"),
        SLASH("slash"),
        DASH("dash"),
        STAR("star"),
        TILDE("tilde"),
    }

    /** A sensible shell-oriented default, not every key (that would not fit). */
    val default: List<Key> = listOf(
        Key.ESC, Key.TAB, Key.CTRL, Key.ALT,
        Key.LEFT, Key.UP, Key.DOWN, Key.RIGHT,
        Key.PIPE, Key.SLASH, Key.DASH, Key.TILDE,
    )

    /**
     * Parses a token list, dropping unknown tokens (a key removed in a future build must not
     * crash an upgraded install) and duplicates. An empty or unusable value yields [default].
     */
    fun parse(serialized: String): List<Key> {
        val parsed = serialized.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token -> Key.entries.firstOrNull { it.token == token } }
            .distinct()
        return parsed.ifEmpty { default }
    }

    fun serialize(keys: List<Key>): String = keys.joinToString(",") { it.token }

    fun contains(keys: List<Key>, key: Key): Boolean = key in keys

    /** Adds [key] at the end when absent, removes it when present. */
    fun toggle(keys: List<Key>, key: Key): List<Key> =
        if (key in keys) keys - key else keys + key

    /** Moves the key at [from] to [to], clamping the destination. No-op on a bad index. */
    fun move(keys: List<Key>, from: Int, to: Int): List<Key> {
        if (from !in keys.indices) return keys
        val mutable = keys.toMutableList()
        val moved = mutable.removeAt(from)
        val target = to.coerceIn(0, mutable.size)
        mutable.add(target, moved)
        return mutable
    }

    fun reset(): List<Key> = default
}
