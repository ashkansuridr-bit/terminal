package app.terminalssh.secure.settings

data class SettingChange(
    val spec: SettingSpec<*>,
    val oldValue: Any,
    val newValue: Any,
)

data class SettingsImportPreview(
    val changes: List<SettingChange>,
    val invalidKeys: List<String>,
    val unknownKeys: List<String>,
    val unchangedCount: Int,
    val baseRevision: Int,
) {
    val hasProblems: Boolean get() = invalidKeys.isNotEmpty() || unknownKeys.isNotEmpty()
}

/** Pure validation and diff planning; persistence is deliberately handled by [SettingsStore]. */
object SettingsImportPlanner {
    fun plan(
        rawValues: Map<String, Any?>,
        currentValue: (SettingSpec<*>) -> Any,
        baseRevision: Int,
    ): SettingsImportPreview {
        val changes = mutableListOf<SettingChange>()
        val invalid = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        var unchanged = 0

        rawValues.toSortedMap().forEach { (key, raw) ->
            val spec = SettingsRegistry.byKey(key)
            if (spec == null) {
                unknown += key
                return@forEach
            }

            val validated = validatedValue(spec, raw)
            if (validated == null) {
                invalid += key
                return@forEach
            }

            val old = currentValue(spec)
            if (old == validated) {
                unchanged++
            } else {
                changes += SettingChange(spec, old, validated)
            }
        }

        return SettingsImportPreview(
            changes = changes,
            invalidKeys = invalid,
            unknownKeys = unknown,
            unchangedCount = unchanged,
            baseRevision = baseRevision,
        )
    }

    private fun validatedValue(spec: SettingSpec<*>, raw: Any?): Any? = when (spec) {
        is BoolSetting -> raw.takeIf { it is Boolean }
        is IntSetting -> {
            val number = raw as? Number ?: return null
            val long = number.toLong()
            val isWhole = number.toDouble().isFinite() && number.toDouble() == long.toDouble()
            long.takeIf { isWhole && it in Int.MIN_VALUE..Int.MAX_VALUE }
                ?.toInt()
                ?.takeIf(spec::isValid)
        }
        is ChoiceSetting -> (raw as? String)?.takeIf(spec::isValid)
        is TextSetting -> (raw as? String)?.takeIf(spec::isValid)
    }
}
