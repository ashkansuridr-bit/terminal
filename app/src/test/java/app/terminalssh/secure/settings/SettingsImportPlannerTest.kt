package app.terminalssh.secure.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsImportPlannerTest {

    private val current = mutableMapOf<String, Any>(
        SettingsRegistry.theme.key to SettingsRegistry.theme.default,
        SettingsRegistry.fontSize.key to SettingsRegistry.fontSize.default,
        SettingsRegistry.keepAlive.key to SettingsRegistry.keepAlive.default,
    )

    @Test fun validChangesArePlannedWithoutMutatingCurrentValues() {
        val preview = SettingsImportPlanner.plan(
            rawValues = mapOf(
                SettingsRegistry.theme.key to "oled",
                SettingsRegistry.fontSize.key to 18,
                SettingsRegistry.keepAlive.key to false,
            ),
            currentValue = { current.getValue(it.key) },
            baseRevision = 7,
        )

        assertEquals(3, preview.changes.size)
        assertEquals(7, preview.baseRevision)
        assertTrue(preview.invalidKeys.isEmpty())
        assertTrue(preview.unknownKeys.isEmpty())
        assertEquals(SettingsRegistry.theme.default, current.getValue(SettingsRegistry.theme.key))
    }

    @Test fun unknownAndInvalidValuesAreReportedAndNeverPlanned() {
        val preview = SettingsImportPlanner.plan(
            rawValues = mapOf(
                "future_setting" to true,
                SettingsRegistry.theme.key to "not-a-theme",
                SettingsRegistry.fontSize.key to 999,
                SettingsRegistry.keepAlive.key to "true",
            ),
            currentValue = { current[it.key] ?: it.default },
            baseRevision = 0,
        )

        assertTrue(preview.changes.isEmpty())
        assertEquals(listOf("future_setting"), preview.unknownKeys)
        assertEquals(
            listOf(
                SettingsRegistry.fontSize.key,
                SettingsRegistry.keepAlive.key,
                SettingsRegistry.theme.key,
            ),
            preview.invalidKeys,
        )
    }

    @Test fun unchangedValidValuesAreCountedButNotAppliedAgain() {
        val preview = SettingsImportPlanner.plan(
            rawValues = mapOf(SettingsRegistry.theme.key to SettingsRegistry.theme.default),
            currentValue = { current.getValue(it.key) },
            baseRevision = 1,
        )

        assertTrue(preview.changes.isEmpty())
        assertEquals(1, preview.unchangedCount)
        assertFalse(preview.hasProblems)
    }

    @Test fun fractionalNumbersAreInvalidForIntegerSettings() {
        val preview = SettingsImportPlanner.plan(
            rawValues = mapOf(SettingsRegistry.fontSize.key to 14.5),
            currentValue = { current.getValue(it.key) },
            baseRevision = 0,
        )

        assertEquals(listOf(SettingsRegistry.fontSize.key), preview.invalidKeys)
        assertTrue(preview.changes.isEmpty())
    }
}
