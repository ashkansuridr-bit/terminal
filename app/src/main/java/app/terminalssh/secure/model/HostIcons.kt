package app.terminalssh.secure.model

/**
 * The built-in host icon set (F005).
 *
 * Ships as glyphs so there is no drawable, no download and no network access: a server
 * you recognise by sight is one tap faster to find, and an icon picker that phones home
 * to fetch avatars is exactly the kind of dependency this app does not take.
 *
 * The stored [key] is the stable identifier; the glyph is presentation only, so a future
 * vector-icon set can replace the rendering without migrating saved profiles.
 */
object HostIcons {

    data class Icon(val key: String, val glyph: String, val label: String)

    val all: List<Icon> = listOf(
        Icon("server", "\uD83D\uDDA5", "Server"),
        Icon("database", "\uD83D\uDDC4", "Database"),
        Icon("cloud", "\u2601", "Cloud"),
        Icon("router", "\uD83D\uDCF6", "Router"),
        Icon("container", "\uD83D\uDC33", "Container"),
        Icon("git", "\uD83C\uDF3F", "Git"),
        Icon("web", "\uD83C\uDF10", "Web"),
        Icon("lock", "\uD83D\uDD12", "Locked"),
        Icon("terminal", "\u2328", "Terminal"),
        Icon("storage", "\uD83D\uDCBE", "Storage"),
        Icon("mail", "\u2709", "Mail"),
        Icon("home", "\uD83C\uDFE0", "Home"),
        Icon("tools", "\uD83D\uDEE0", "Tools"),
        Icon("lab", "\uD83E\uDDEA", "Lab"),
        Icon("prod", "\uD83D\uDE80", "Production"),
    )

    /** Empty key (the default for existing profiles) renders the host's initial. */
    const val NONE = ""

    fun fromKey(key: String): Icon? = all.firstOrNull { it.key == key }

    /**
     * The avatar text for a host: the chosen glyph, or the first letter of the display
     * name so an icon-less host still gets a stable, recognisable marker.
     */
    fun avatarText(iconKey: String, fallbackName: String): String {
        fromKey(iconKey)?.let { return it.glyph }
        val first = fallbackName.trim().firstOrNull()
        return first?.uppercaseChar()?.toString() ?: "?"
    }

    fun isKnown(key: String): Boolean = key == NONE || fromKey(key) != null
}
