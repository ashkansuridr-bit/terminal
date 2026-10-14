package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToolbarImportPreviewTest {

    @Test fun importPreviewCountsOnlyMacrosContainingEnter() {
        val imported = ToolbarConfigState(
            customMacros = listOf(
                MacroDef("macro.submit", "Submit", steps = listOf(
                    MacroStep.Text("git status"),
                    MacroStep.Special(ToolbarSpecialKey.ENTER),
                )),
                MacroDef("macro.prepare", "Prepare", steps = listOf(MacroStep.Text("git status"))),
                MacroDef("macro.control", "Control", steps = listOf(MacroStep.Controls('C'))),
            ),
        )
        val preview = ToolbarRepository(InMemoryToolbarPrefs())
            .prepareImport(ToolbarCodec.encode(imported))
            ?.preview

        assertNotNull(preview)
        assertEquals(3, preview.macros)
        assertEquals(1, preview.enterMacros)
    }

    @Test fun importPreviewHasNoEnterWarningWhenNoImportedMacroSubmits() {
        val imported = ToolbarConfigState(
            customMacros = listOf(
                MacroDef("macro.prepare", "Prepare", steps = listOf(MacroStep.Text("git status"))),
            ),
        )
        val preview = ToolbarRepository(InMemoryToolbarPrefs())
            .prepareImport(ToolbarCodec.encode(imported))
            ?.preview

        assertNotNull(preview)
        assertEquals(0, preview.enterMacros)
    }
}
