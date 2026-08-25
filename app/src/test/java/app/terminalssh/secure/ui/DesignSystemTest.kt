package app.terminalssh.secure.ui

import androidx.compose.ui.unit.Dp
import app.terminalssh.secure.ui.theme.Size
import app.terminalssh.secure.ui.theme.Space
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the design system against the drift that made the app look unfinished in the
 * first place: values that are almost on the grid, and Material styles left undefined so
 * they silently fall back to a Latin-first font.
 *
 * These read the source rather than the running UI, which is what makes them cheap enough
 * to run on every commit.
 */
class DesignSystemTest {

    private val uiDir = File("src/main/java/app/terminalssh/secure/ui")
    private val themeFile = File(uiDir, "theme/Theme.kt")

    private fun uiSources(): List<File> =
        uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun sourcesAreWhereTheTestExpects() {
        // A wrong path would make every other test in here vacuously pass.
        assertTrue(uiDir.isDirectory, "ui source directory not found at ${uiDir.absolutePath}")
        assertTrue(themeFile.isFile, "Theme.kt not found")
        assertTrue(uiSources().size > 10, "only found ${uiSources().size} ui sources")
    }

    @Test fun everySpacingTokenIsOnTheFourDpGrid() {
        val tokens = mapOf(
            "xxs" to Space.xxs, "xs" to Space.xs, "sm" to Space.sm, "md" to Space.md,
            "lg" to Space.lg, "xl" to Space.xl, "xxl" to Space.xxl, "xxxl" to Space.xxxl,
        )
        tokens.forEach { (name, value) ->
            // xxs is the deliberate exception: 2dp hairline gaps have no 4dp equivalent.
            if (name != "xxs") {
                assertTrue(value.value % 4 == 0f, "Space.$name is ${value.value}dp, off the 4dp grid")
            }
        }
    }

    @Test fun spacingTokensIncreaseMonotonically() {
        val ordered = listOf(Space.xxs, Space.xs, Space.sm, Space.md, Space.lg, Space.xl, Space.xxl, Space.xxxl)
        ordered.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(smaller < larger, "$smaller is not smaller than $larger")
        }
    }

    @Test fun touchTargetsMeetThePlatformMinimum() {
        assertTrue(Size.touchTarget >= Dp(48f), "touch target below the 48dp minimum")
        assertTrue(Size.touchTargetLarge > Size.touchTarget)
    }

    @Test fun everyMaterialTextStyleIsDefined() {
        // An undefined style falls back to a Latin-first face with non-zero letterSpacing,
        // which visibly breaks connected Persian script. Two were missing before this test.
        val source = themeFile.readText()
        val required = listOf(
            "displayLarge", "displayMedium", "displaySmall",
            "headlineLarge", "headlineMedium", "headlineSmall",
            "titleLarge", "titleMedium", "titleSmall",
            "bodyLarge", "bodyMedium", "bodySmall",
            "labelLarge", "labelMedium", "labelSmall",
        )
        val missing = required.filterNot { style -> Regex("""\b$style\s*=\s*persian\(""").containsMatchIn(source) }
        assertTrue(missing.isEmpty(), "text styles fall back to the Material default font: $missing")
    }

    @Test fun everyTextStyleUsedIsAlsoDefined() {
        // Catches the reverse direction: a screen reaching for a style nobody defined.
        val used = uiSources()
            .flatMap { file -> Regex("""typography\.([a-zA-Z]+)""").findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        val source = themeFile.readText()
        val undefined = used.filterNot { Regex("""\b$it\s*=\s*persian\(""").containsMatchIn(source) }
        assertTrue(undefined.isEmpty(), "used but not defined in AppTypography: $undefined")
    }

    @Test fun persianTextNeverCarriesLetterSpacing() {
        // Tracking pulls joined Persian letterforms apart. The shared builder sets it to
        // zero; this catches anyone overriding it at a call site.
        //
        // The value is captured and inspected rather than matched with a negative
        // lookahead: `\s*` backtracks, letting the lookahead land on whitespace and match
        // every assignment including the correct ones.
        val assignment = Regex("""letterSpacing\s*=\s*([^,\n)]+)""")
        val offenders = uiSources().flatMap { file ->
            assignment.findAll(file.readText())
                .map { it.groupValues[1].trim() }
                .filterNot { it.startsWith("0") }
                .map { "${file.name}: $it" }
        }
        assertTrue(offenders.isEmpty(), "non-zero letterSpacing on Persian text: $offenders")
    }

    @Test fun cornerRadiiSitOnTheGrid() {
        val source = themeFile.readText()
        val radii = Regex("""RoundedCornerShape\((\d+)\.dp\)""")
            .findAll(source)
            .map { it.groupValues[1].toInt() }
            .toList()
        assertTrue(radii.isNotEmpty(), "no shape scale found")
        val offGrid = radii.filter { it % 4 != 0 }
        assertTrue(offGrid.isEmpty(), "corner radii off the 4dp grid: $offGrid")
    }
}
