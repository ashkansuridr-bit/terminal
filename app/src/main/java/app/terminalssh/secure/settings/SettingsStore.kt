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

    fun get(spec: BoolSetting): Boolean =
        runCatching { prefs.getBoolean(spec.key, spec.default) }.getOrDefault(spec.default)

    fun get(spec: IntSetting): Int = spec.coerce(
        runCatching { prefs.getInt(spec.key, spec.default) }.getOrDefault(spec.default),
    )

    fun get(spec: ChoiceSetting): String =
        spec.coerce(runCatching { prefs.getString(spec.key, spec.default) }
            .getOrNull() ?: spec.default)

    fun get(spec: TextSetting): String =
        spec.coerce(runCatching { prefs.getString(spec.key, spec.default) }
            .getOrNull() ?: spec.default)

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
    @Synchronized
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
    @Synchronized
    fun previewImport(json: String): SettingsImportPreview? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val version = root.opt(FIELD_VERSION) as? Number ?: return null
        if (version.toDouble() != FORMAT_VERSION.toDouble()) return null
        val values = root.optJSONObject(FIELD_SETTINGS) ?: return null
        val raw = buildMap<String, Any?> {
            values.keys().forEach { key ->
                put(key, values.opt(key).takeUnless { it === JSONObject.NULL })
            }
        }
        return SettingsImportPlanner.plan(
            rawValues = raw,
            currentValue = ::valueOf,
            baseRevision = _revision.value,
        )
    }

    /** Applies every valid previewed change with one SharedPreferences transaction. */
    @Synchronized
    fun applyImport(preview: SettingsImportPreview): Int? {
        if (preview.baseRevision != _revision.value) return null
        if (preview.changes.isEmpty()) return 0

        val editor = prefs.edit()
        preview.changes.forEach { change -> editor.put(change.spec, change.newValue) }
        editor.apply()
        _revision.value++
        return preview.changes.size
    }

    /** Compatibility entry point for non-UI callers; validation and persistence stay atomic. */
    fun importJson(json: String): Int {
        val preview = previewImport(json) ?: return 0
        return applyImport(preview) ?: 0
    }

    @Synchronized
    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _revision.value++
    }

    private fun SharedPreferences.Editor.put(spec: SettingSpec<*>, value: Any) {
        when (spec) {
            is BoolSetting -> putBoolean(spec.key, value as Boolean)
            is IntSetting -> putInt(spec.key, value as Int)
            is ChoiceSetting -> putString(spec.key, value as String)
            is TextSetting -> putString(spec.key, value as String)
        }
    }

    companion object {
        // Same file the previous Settings class used, so upgrades keep their values.
        private const val PREFS = "settings_v1"
        private const val FORMAT_VERSION = 1
        private const val FIELD_VERSION = "version"
        private const val FIELD_SETTINGS = "settings"
    }
}
