package app.terminalssh.secure.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The export/import contract, tested against the JSON itself rather than through Android's
 * SharedPreferences — the format is what has to survive across versions and devices.
 */
class SettingsExportFormatTest {

    // org.json is a non-functional stub in JVM unit tests, so the JSON round trip itself
    // is covered by instrumentation; what is checked here is the schema contract the
    // format depends on.

    @Test fun everyExportedKeyResolvesToARealSetting() {
        // Guards the round trip: an exported key the registry does not know is dead data.
        SettingsRegistry.all.forEach { spec ->
            assertTrue(
                SettingsRegistry.byKey(spec.key) != null,
                "${spec.key} would export but not import",
            )
        }
    }

    @Test fun outOfRangeImportedValuesAreClampedNotStored() {
        val spec = SettingsRegistry.fontSize
        assertEquals(spec.max, spec.coerce(9_999))
        assertEquals(spec.min, spec.coerce(-1))
    }

    @Test fun unknownChoiceValuesAreRejectedRatherThanSilentlyDefaulted() {
        val spec = SettingsRegistry.cursorStyle
        assertFalse(spec.isValid("spinning-donut"))
        // Import skips invalid choices so the user's existing value survives a bad file.
        assertTrue(spec.isValid(spec.default))
    }

    @Test fun noFreeTextSettingLooksLikeAPlaceToStoreACredential() {
        // The export is meant to be safe to email to yourself. Only a TextSetting could
        // actually hold a secret — a boolean like "mask_secrets_output" is a preference
        // about secrets, not one — so the rule is scoped to free text.
        val forbidden = listOf("password", "passphrase", "secret", "token", "credential", "vault", "key")
        SettingsRegistry.all.filterIsInstance<TextSetting>().forEach { spec ->
            forbidden.forEach { word ->
                assertFalse(
                    word in spec.key.lowercase(),
                    "${spec.key} is free text with a credential-shaped name and would be exported",
                )
            }
        }
    }

    @Test fun onlyDeclaredTypesExist() {
        // The renderer, export and reset all switch exhaustively over these four; a fifth
        // type added without updating them would fail to compile there, not here — this
        // just records the assumption.
        SettingsRegistry.all.forEach { spec ->
            val known = spec is BoolSetting || spec is IntSetting ||
                spec is ChoiceSetting || spec is TextSetting
            assertTrue(known, "${spec.key} has an unhandled spec type")
        }
    }

    @Test fun textSettingsCannotGrowUnbounded() {
        // A pasted file must not become a setting value.
        SettingsRegistry.all.filterIsInstance<TextSetting>().forEach { spec ->
            assertTrue(spec.maxLength in 1..1024, "${spec.key} has an unreasonable maxLength")
            assertEquals(spec.maxLength, spec.coerce("x".repeat(spec.maxLength * 4)).length)
        }
    }

    @Test fun everyIntSettingDefaultSitsOnAValidStep() {
        // Otherwise the slider can never return to the shipped default.
        SettingsRegistry.all.filterIsInstance<IntSetting>().forEach { spec ->
            assertEquals(
                spec.default,
                spec.coerce(spec.default),
                "${spec.key}: default ${spec.default} is not reachable with step ${spec.step}",
            )
        }
    }
}
