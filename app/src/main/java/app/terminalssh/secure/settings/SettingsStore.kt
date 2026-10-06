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
class SettingsStore internal constructor(
    private val prefs: SharedPreferences,
    private val commit: (SharedPreferences.Editor) -> Boolean = { it.commit() },
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
    )

    private var quarantinedSnapshot: Map<String, *>? = null
    private val _persistenceFailure = MutableStateFlow<SettingsPersistenceFailure?>(
        if (prefs.contains(WRITE_INTENT)) SettingsPersistenceFailure.ROLLBACK_FAILED else null,
    )
    val persistenceFailure: StateFlow<SettingsPersistenceFailure?> = _persistenceFailure.asStateFlow()

    /** Bumped on every write so Compose recomposes without each screen holding its own copy. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // ---- typed access ----

    @Synchronized
    fun get(spec: BoolSetting): Boolean {
        // Unresolved writes require authentication in this process and reconstructed
        // stores. Reads never retire the persisted write-intent marker.
        if (spec.key == SettingsRegistry.biometricLock.key &&
            (quarantinedSnapshot != null || prefs.contains(WRITE_INTENT))) return true
        val value = rawValue(spec.key) ?: return spec.default
        return value as? Boolean
            ?: if (spec.key == SettingsRegistry.biometricLock.key) true else spec.default
    }

    @Synchronized
    fun get(spec: IntSetting): Int = spec.coerce(rawValue(spec.key) as? Int ?: spec.default)

    @Synchronized
    fun get(spec: ChoiceSetting): String = spec.coerce(rawValue(spec.key) as? String ?: spec.default)

    @Synchronized
    fun get(spec: TextSetting): String = spec.coerce(rawValue(spec.key) as? String ?: spec.default)

    private fun rawValue(key: String): Any? = (quarantinedSnapshot ?: prefs.all)[key]

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
     * Returns a preview without mutating preferences. Unknown keys are reported for
     * compatibility; any invalid known value prevents the entire import from applying.
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
        if (preview.baseRevision != _revision.value || preview.invalidKeys.isNotEmpty()) return null
        // The preview is public data: validate it again before creating an editor.
        if (preview.changes.map { it.spec.key }.distinct().size != preview.changes.size) return null
        if (preview.changes.any { change ->
            SettingsRegistry.byKey(change.spec.key) != change.spec ||
                SettingsImportPlanner.validatedValue(change.spec, change.newValue) != change.newValue ||
                valueOf(change.spec) != change.oldValue
        }) return null
        if (preview.changes.isEmpty()) return 0

        try {
            write { preview.changes.forEach { change -> put(change.spec, change.newValue) } }
        } catch (failure: SettingsPersistenceException) {
            return null
        }
        return preview.changes.size
    }

    /** Compatibility entry point for non-UI callers; validation and persistence stay atomic. */
    fun importJson(json: String): Int {
        val preview = previewImport(json) ?: return 0
        return applyImport(preview) ?: 0
    }

    @Synchronized
    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        val recovering = quarantinedSnapshot != null || prefs.contains(WRITE_INTENT)
        val previous = (quarantinedSnapshot ?: prefs.all).filterKeys { it != WRITE_INTENT }
            .mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
            .toMutableMap()
        // An unrelated recovery write must not adopt a failed lock-disable value.
        // Only an explicit lock change/reset in this new transaction can disable it.
        if (recovering) previous[SettingsRegistry.biometricLock.key] = true
        // Establish recoverable security state on disk BEFORE changing a lock flag.
        // A present marker (even malformed) requires authentication after process death.
        if (!commitChecked(prefs.edit().putBoolean(WRITE_INTENT, true))) {
            quarantinedSnapshot = previous
            _persistenceFailure.value = SettingsPersistenceFailure.ROLLBACK_FAILED
            throw SettingsPersistenceException(SettingsPersistenceFailure.ROLLBACK_FAILED)
        }
        val editor = prefs.edit().clear().putBoolean(WRITE_INTENT, true)
        previous.forEach { (key, value) -> editor.putRaw(key, value) }
        editor.apply(block)
        if (!commitChecked(editor)) {
            // Both target and rollback transactions retain the durable intent marker.
            // Never clear it until a complete old or chosen state has been verified.
            val rollback = prefs.edit().clear().putBoolean(WRITE_INTENT, true)
            previous.forEach { (key, value) -> rollback.putRaw(key, value) }
            val restored = commitChecked(rollback)
            val retired = restored && retireIntent()
            val failure = if (retired) {
                quarantinedSnapshot = null
                SettingsPersistenceFailure.WRITE_FAILED
            } else {
                quarantinedSnapshot = previous
                SettingsPersistenceFailure.ROLLBACK_FAILED
            }
            _persistenceFailure.value = failure
            throw SettingsPersistenceException(failure)
        }
        val chosen = prefs.all.filterKeys { it != WRITE_INTENT }.toMap()
        if (!retireIntent()) {
            quarantinedSnapshot = chosen
            _persistenceFailure.value = SettingsPersistenceFailure.ROLLBACK_FAILED
            throw SettingsPersistenceException(SettingsPersistenceFailure.ROLLBACK_FAILED)
        }
        quarantinedSnapshot = null
        _persistenceFailure.value = null
        _revision.value++
    }

    private fun commitChecked(editor: SharedPreferences.Editor): Boolean =
        try { commit(editor) } catch (failure: RuntimeException) { false }

    private fun retireIntent(): Boolean {
        if (commitChecked(prefs.edit().remove(WRITE_INTENT))) return true
        // A false return may already have removed the marker from memory. Restore it
        // conservatively; a verified full-state commit always preceded this removal.
        commitChecked(prefs.edit().putBoolean(WRITE_INTENT, true))
        return false
    }

    private fun SharedPreferences.Editor.putRaw(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            null -> remove(key)
            else -> error("Unsupported preference value type")
        }
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
        internal const val WRITE_INTENT = "_settings_write_intent_v1"
        private const val FORMAT_VERSION = 1
        private const val FIELD_VERSION = "version"
        private const val FIELD_SETTINGS = "settings"
    }
}

/** No value is included in persistence failures: preferences may gain sensitive fields later. */
enum class SettingsPersistenceFailure { WRITE_FAILED, ROLLBACK_FAILED }

class SettingsPersistenceException(val failure: SettingsPersistenceFailure) :
    IllegalStateException("Settings could not be durably saved: ${failure.name}")
