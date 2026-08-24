package app.terminalssh.secure.storage

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeName: String
        get() = prefs.getString("theme", "persian_neon") ?: "persian_neon"
        set(value) = prefs.edit().putString("theme", value).apply()

    var fontSizeSp: Int
        get() = prefs.getInt("font_size", 14)
        set(value) = prefs.edit().putInt("font_size", value.coerceIn(10, 24)).apply()

    var biometricLock: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(value) = prefs.edit().putBoolean("biometric", value).apply()

    var confirmMultilinePaste: Boolean
        get() = prefs.getBoolean("paste_confirm", true)
        set(value) = prefs.edit().putBoolean("paste_confirm", value).apply()

    var keepAlive: Boolean
        get() = prefs.getBoolean("keepalive", true)
        set(value) = prefs.edit().putBoolean("keepalive", value).apply()

    /**
     * Seconds before a clipboard copy made from the terminal is wiped, or 0 to keep it.
     * Terminal output is where passwords and tokens get copied from, and on Android the
     * clipboard is readable by the foreground app.
     */
    var clipboardClearSeconds: Int
        get() = prefs.getInt("clipboard_clear_seconds", DEFAULT_CLIPBOARD_CLEAR_SECONDS)
        set(value) = prefs.edit()
            .putInt("clipboard_clear_seconds", value.coerceIn(0, MAX_CLIPBOARD_CLEAR_SECONDS))
            .apply()

    companion object {
        private const val PREFS = "settings_v1"
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 45
        const val MAX_CLIPBOARD_CLEAR_SECONDS = 600
    }
}
