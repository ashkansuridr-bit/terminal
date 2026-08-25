package app.terminalssh.secure.settings

import app.terminalssh.secure.ui.theme.TerminalPalettes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The theme setting offers a list of identifiers; the theme layer defines what they look
 * like. If those two drift, the user picks a palette and nothing happens — a control that
 * silently does nothing, which is worse than not offering it.
 */
class PaletteCoverageTest {

    @Test fun everyOfferedThemeHasAPalette() {
        val defined = TerminalPalettes.map { it.id }.toSet()
        val offered = SettingsRegistry.theme.values
        val missing = offered - defined
        assertTrue(missing.isEmpty(), "settings offer palettes that do not exist: $missing")
    }

    @Test fun everyPaletteIsReachableFromSettings() {
        val defined = TerminalPalettes.map { it.id }
        val offered = SettingsRegistry.theme.values.toSet()
        val unreachable = defined.filterNot { it in offered }
        assertTrue(unreachable.isEmpty(), "palettes exist but cannot be selected: $unreachable")
    }

    @Test fun paletteKeysAreUnique() {
        val keys = TerminalPalettes.map { it.id }
        assertEquals(keys.size, keys.toSet().size, "duplicate palette keys")
    }

    @Test fun defaultThemeExists() {
        assertTrue(TerminalPalettes.any { it.id == SettingsRegistry.theme.default })
    }

    @Test fun everyPaletteSeparatesForegroundFromBackground() {
        // A palette whose text matches its background is unreadable; cheap to get wrong
        // when adding one by copying another.
        TerminalPalettes.forEach { palette ->
            assertTrue(
                palette.foreground != palette.background,
                "${palette.id}: foreground and background are identical",
            )
        }
    }
}
