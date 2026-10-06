package app.terminalssh.secure.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Uses the real Android locale resolver, including script tags, on the API matrix. */
@RunWith(AndroidJUnit4::class)
class LocaleDirectionTest {
    @Test
    fun supportedLanguagesUseLocaleDirection() {
        for (tag in listOf("fa", "ar")) {
            assertEquals(tag, LayoutDirection.Rtl, terminalLayoutDirection(Locale.forLanguageTag(tag)))
        }
        for (tag in listOf("en", "fr", "es", "ru")) {
            assertEquals(tag, LayoutDirection.Ltr, terminalLayoutDirection(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun scriptsOverrideLanguageDefaults() {
        assertEquals(LayoutDirection.Ltr, terminalLayoutDirection(Locale.forLanguageTag("ar-Latn")))
        assertEquals(LayoutDirection.Rtl, terminalLayoutDirection(Locale.forLanguageTag("en-Arab")))
        assertEquals(LayoutDirection.Ltr, terminalLayoutDirection(Locale.ROOT))
    }
}
