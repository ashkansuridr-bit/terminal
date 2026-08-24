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

    companion object { private const val PREFS = "settings_v1" }
}
