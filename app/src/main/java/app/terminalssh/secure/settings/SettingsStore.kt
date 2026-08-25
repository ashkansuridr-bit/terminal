package app.terminalssh.secure.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Reads and writes settings through their [SettingSpec], so every value is validated and
 * clamped on the way in and out.
 *
 * Reading through the spec matters as much as writing: a value that predates a range
 * change, or arrived from an imported file, is corrected on read rather than handed to
 * the UI as-is.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Bumped on every write so Compose recomposes without each screen holding its own copy. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // ---- typed access ----

    fun get(spec: BoolSetting): Boolean = prefs.getBoolean(spec.key, spec.default)

    fun get(spec: IntSetting): Int = spec.coerce(prefs.getInt(spec.key, spec.default))

    fun get(spec: ChoiceSetting): String =
        spec.coerce(prefs.getString(spec.key, spec.default) ?: spec.default)

    fun get(spec: TextSetting): String =
        spec.coerce(prefs.getString(spec.key, spec.default) ?: spec.default)

    fun set(spec: BoolSetting, value: Boolean) = write { putBoolean(spec.key, value) }

    fun set(spec: IntSetting, value: Int) = write { putInt(spec.key, spec.coerce(value)) }

    fun set(spec: ChoiceSetting, value: String) = write { putString(spec.key, spec.coerce(value)) }

    fun set(spec: TextSetting, value: String) = write { putString(spec.key, spec.coerce(value)) }

    // ---- schema-driven operations ----

    /** True when this setting differs from its shipped default. */
    fun isChanged(spec: SettingSpec<*>): Boolean = when (spec) {
        is BoolSetting -> get(spec) != spec.default
        is IntSetting -> get(spec) != spec.default
        is ChoiceSetting -> get(spec) != spec.default
        is TextSetting -> get(spec) != spec.default
    }

    /** Current value as a display-agnostic Any, for export and for generic UI. */
    fun valueOf(spec: SettingSpec<*>): Any = when (spec) {
        is BoolSetting -> get(spec)
        is IntSetting -> get(spec)
        is ChoiceSetting -> get(spec)
        is TextSetting -> get(spec)
    }

    fun reset(spec: SettingSpec<*>) = write { remove(spec.key) }

    fun resetAll() = write { SettingsRegistry.all.forEach { remove(it.key) } }

    /** Every setting that differs from its default — what a "what did I change" view shows. */
    fun changedSettings(): List<SettingSpec<*>> = SettingsRegistry.all.filter { isChanged(it) }

    // ---- export / import ----

    /**
     * Only settings that differ from their default are written. Exporting defaults would
     * freeze today's defaults into the file, so a later release could never improve them
     * for someone who restored an old backup.
     *
     * Contains no secrets: the registry holds preferences only, never credentials.
     */
    fun exportJson(): String {
        val root = JSONObject()
        root.put(FIELD_VERSION, FORMAT_VERSION)
        val values = JSONObject()
        changedSettings().forEach { spec -> values.put(spec.key, valueOf(spec)) }
        root.put(FIELD_SETTINGS, values)
        return root.toString(2)
    }

    /**
     * @return how many settings were applied. Unknown keys and out-of-range values are
     *   skipped rather than failing the whole import, so a file from a newer version still
     *   restores everything this version understands.
     */
    fun importJson(json: String): Int {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        val values = root.optJSONObject(FIELD_SETTINGS) ?: return 0
        var applied = 0
        for (key in values.keys()) {
            val spec = SettingsRegistry.byKey(key) ?: continue
            val ok = runCatching {
                when (spec) {
                    is BoolSetting -> set(spec, values.getBoolean(key))
                    is IntSetting -> set(spec, values.getInt(key))
                    is ChoiceSetting -> {
                        val raw = values.getString(key)
                        // An unrecognised option would silently become the default;
                        // skipping instead keeps the user's existing choice.
                        if (!spec.isValid(raw)) return@runCatching false
                        set(spec, raw)
                    }
                    is TextSetting -> set(spec, values.getString(key))
                }
                true
            }.getOrDefault(false)
            if (ok) applied++
        }
        return applied
    }

    private inline fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _revision.value++
    }

    companion object {
        // Same file the previous Settings class used, so upgrades keep their values.
        private const val PREFS = "settings_v1"
        private const val FORMAT_VERSION = 1
        private const val FIELD_VERSION = "version"
        private const val FIELD_SETTINGS = "settings"
    }
}
