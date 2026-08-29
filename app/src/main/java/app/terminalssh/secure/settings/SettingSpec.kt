package app.terminalssh.secure.settings

/**
 * One setting, described once.
 *
 * The previous approach spread each setting across three places: a property on the store,
 * a row in the settings screen, and nothing at all for search, reset, or export. Adding a
 * setting meant touching all of them and still getting no search or reset.
 *
 * Declaring the setting instead — key, type, default, where it belongs — lets search,
 * reset-to-default, changed-from-default marking, export and import all be written once
 * against the schema rather than once per setting.
 */
sealed interface SettingSpec<T : Any> {
    val key: String
    val default: T

    /** Title string resource, used for display and for search matching. */
    val titleRes: Int

    /** Optional one-line explanation, also searched. */
    val summaryRes: Int?

    val group: SettingGroup

    /** Hidden until the user turns on advanced mode; the default view stays short. */
    val advanced: Boolean

    /** Whether [value] is acceptable, so import and restore cannot poison the store. */
    fun isValid(value: T): Boolean

    /** Nearest acceptable value, used when clamping imported or legacy data. */
    fun coerce(value: T): T
}

enum class SettingGroup { APPEARANCE, TERMINAL, BEHAVIOR, SECURITY, ADVANCED }

data class BoolSetting(
    override val key: String,
    override val default: Boolean,
    override val titleRes: Int,
    override val summaryRes: Int? = null,
    override val group: SettingGroup,
    override val advanced: Boolean = false,
) : SettingSpec<Boolean> {
    override fun isValid(value: Boolean) = true
    override fun coerce(value: Boolean) = value
}

data class IntSetting(
    override val key: String,
    override val default: Int,
    val min: Int,
    val max: Int,
    /** Values snap to a multiple of this, so a slider cannot produce 47 when 45 was meant. */
    val step: Int = 1,
    override val titleRes: Int,
    override val summaryRes: Int? = null,
    override val group: SettingGroup,
    override val advanced: Boolean = false,
) : SettingSpec<Int> {
    init {
        require(min <= max) { "$key: min must not exceed max" }
        require(default in min..max) { "$key: default must be inside the range" }
        require(step > 0) { "$key: step must be positive" }
        require((default.toLong() - min) % step == 0L) { "$key: default must align with step" }
    }

    override fun isValid(value: Int) =
        value in min..max && (value.toLong() - min) % step == 0L
    override fun coerce(value: Int): Int {
        val clamped = value.coerceIn(min, max)
        // Snap relative to min so a range like 10..24 step 2 yields 10, 12, 14 — not 10, 11, 13.
        val snapped = min + ((clamped - min) + step / 2) / step * step
        return snapped.coerceIn(min, max)
    }
}

/**
 * A fixed set of named options. [values] are the stored identifiers and never change;
 * their labels are looked up for display, so renaming a label cannot orphan stored data.
 */
data class ChoiceSetting(
    override val key: String,
    override val default: String,
    val values: List<String>,
    override val titleRes: Int,
    override val summaryRes: Int? = null,
    override val group: SettingGroup,
    override val advanced: Boolean = false,
) : SettingSpec<String> {
    init {
        require(values.isNotEmpty()) { "$key: needs at least one option" }
        require(default in values) { "$key: default must be one of the options" }
        require(values.toSet().size == values.size) { "$key: duplicate option identifiers" }
    }

    override fun isValid(value: String) = value in values
    override fun coerce(value: String) = if (value in values) value else default
}

/** Free text, length-bounded so a pasted file cannot become a setting. */
data class TextSetting(
    override val key: String,
    override val default: String,
    val maxLength: Int = 256,
    /** Additional domain validation beyond the storage length bound. */
    val accepts: (String) -> Boolean = { true },
    override val titleRes: Int,
    override val summaryRes: Int? = null,
    override val group: SettingGroup,
    override val advanced: Boolean = false,
) : SettingSpec<String> {
    init {
        require(maxLength > 0) { "$key: maxLength must be positive" }
        require(default.length <= maxLength && accepts(default)) { "$key: invalid default" }
    }

    override fun isValid(value: String) = value.length <= maxLength && accepts(value)
    override fun coerce(value: String): String {
        val bounded = value.take(maxLength)
        return if (accepts(bounded)) bounded else default
    }
}
