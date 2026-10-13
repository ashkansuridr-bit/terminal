package app.terminalssh.secure.toolbar

/** Pure validation and conversion helpers used by the custom theme form. */
object ToolbarThemeEditor {
    fun parseArgb(value: String): Long? {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 8 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return hex.toLongOrNull(16)
    }

    fun formatArgb(value: Long): String = "#" + (value and 0xFFFF_FFFFL).toString(16).uppercase().padStart(8, '0')

    fun updated(
        source: ToolbarTheme,
        id: String,
        name: String,
        keyBackground: String,
        keyLabel: String,
        activeBackground: String,
        activeLabel: String,
        borderColor: String,
        keySurface: String,
    ): ToolbarTheme? {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return null
        val colors = listOf(keyBackground, keyLabel, activeBackground, activeLabel, borderColor, keySurface)
            .map { parseArgb(it) ?: return null }
        return source.copy(
            id = id,
            name = cleanName,
            keyBackground = colors[0],
            keyLabel = colors[1],
            activeBackground = colors[2],
            activeLabel = colors[3],
            borderColor = colors[4],
            keySurface = colors[5],
        )
    }
}
