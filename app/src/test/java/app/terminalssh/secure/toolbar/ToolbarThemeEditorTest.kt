package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ToolbarThemeEditorTest {
    @Test fun parsesOnlyEightDigitArgbAndFormatsUnsignedValues() {
        assertEquals(0xFFABCDEFL, ToolbarThemeEditor.parseArgb("#FFABCDEF"))
        assertEquals("#FFABCDEF", ToolbarThemeEditor.formatArgb(0xFFABCDEF))
        assertNull(ToolbarThemeEditor.parseArgb("#ABCDEF"))
        assertNull(ToolbarThemeEditor.parseArgb("#FFABCG00"))
    }

    @Test fun updatesColorsAndKeepsGeometryFromSourceTheme() {
        val source = ToolbarThemes.nord
        val edited = ToolbarThemeEditor.updated(
            source, "mine", "Mine", "#FF010203", "#FF040506", "#FF070809",
            "#FF0A0B0C", "#FF0D0E0F", "#FF101112",
        )
        assertNotNull(edited)
        assertEquals(0xFF010203L, edited.keyBackground)
        assertEquals("Mine", edited.name)
        assertEquals(source.cornerRadiusDp, edited.cornerRadiusDp)
        assertEquals(source.horizontalGapDp, edited.horizontalGapDp)
    }

    @Test fun rejectsBlankNameAndInvalidColor() {
        assertNull(ToolbarThemeEditor.updated(ToolbarThemes.nord, "mine", " ", "#FF010203", "#FF040506", "#FF070809", "#FF0A0B0C", "#FF0D0E0F", "#FF101112"))
        assertNull(ToolbarThemeEditor.updated(ToolbarThemes.nord, "mine", "Mine", "red", "#FF040506", "#FF070809", "#FF0A0B0C", "#FF0D0E0F", "#FF101112"))
    }
}
