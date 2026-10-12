package app.terminalssh.secure.storage

import android.content.Context
import android.content.SharedPreferences
import app.terminalssh.secure.toolbar.ToolbarPrefs

/**
 * Android backing for [ToolbarPrefs]: one JSON document in its own preferences file.
 *
 * Kept apart from the main settings file on purpose. Toolbar configuration is a structured
 * document with its own schema and its own corrupt-data recovery, not a flat pile of scalar
 * preferences; mixing the two would put a large JSON string in the same file that
 * [app.terminalssh.secure.settings.SettingsStore] scans and exports.
 */
class AndroidToolbarPrefs(context: Context) : ToolbarPrefs {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        // apply() persists asynchronously but is ordered; toolbar edits are small and the
        // in-memory state is already updated by the repository, so a blocking commit would
        // only add jank to dragging keys around.
        prefs.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFS = "tcs_toolbar_v1"
    }
}
