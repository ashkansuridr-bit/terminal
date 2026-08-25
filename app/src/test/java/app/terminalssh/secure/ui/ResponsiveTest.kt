package app.terminalssh.secure.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponsiveTest {

    @Test fun realDeviceWidthsLandInTheExpectedBands() {
        // Widths taken from devices this app actually has to fit.
        assertEquals(WidthClass.COMPACT, widthClassFor(320.dp))  // Galaxy Fold, folded
        assertEquals(WidthClass.COMPACT, widthClassFor(360.dp))  // the most common Android width
        assertEquals(WidthClass.COMPACT, widthClassFor(412.dp))  // Pixel 8 Pro
        assertEquals(WidthClass.MEDIUM, widthClassFor(674.dp))   // Galaxy Fold, unfolded
        assertEquals(WidthClass.MEDIUM, widthClassFor(800.dp))   // 7" tablet landscape
        assertEquals(WidthClass.EXPANDED, widthClassFor(1280.dp)) // 10" tablet landscape
    }

    @Test fun breakpointsAreInclusiveAtTheLowerBound() {
        assertEquals(WidthClass.COMPACT, widthClassFor(599.dp))
        assertEquals(WidthClass.MEDIUM, widthClassFor(600.dp))
        assertEquals(WidthClass.MEDIUM, widthClassFor(839.dp))
        assertEquals(WidthClass.EXPANDED, widthClassFor(840.dp))
    }

    @Test fun zeroAndTinyWidthsDoNotFallOffTheBottom() {
        // A freeform or split-screen window can be arbitrarily small; it must still
        // resolve to a real band rather than throwing.
        assertEquals(WidthClass.COMPACT, widthClassFor(0.dp))
        assertEquals(WidthClass.COMPACT, widthClassFor(1.dp))
    }

    @Test fun landscapePhoneHeightIsTreatedAsVerticallyScarce() {
        // A 360x780 phone rotated is 780x360: the keyboard would cover most of it.
        assertEquals(HeightClass.COMPACT, heightClassFor(360.dp))
        assertTrue(WindowSize(WidthClass.MEDIUM, HeightClass.COMPACT).prefersDenseVertical)
    }

    @Test fun portraitPhoneIsNotTreatedAsVerticallyScarce() {
        assertEquals(HeightClass.MEDIUM, heightClassFor(780.dp))
        assertTrue(!WindowSize(WidthClass.COMPACT, HeightClass.MEDIUM).prefersDenseVertical)
    }

    @Test fun marginsGrowWithAvailableWidth() {
        val compact = WidthClass.COMPACT.pageMargin()
        val medium = WidthClass.MEDIUM.pageMargin()
        val expanded = WidthClass.EXPANDED.pageMargin()
        assertTrue(compact < medium, "$compact !< $medium")
        assertTrue(medium < expanded, "$medium !< $expanded")
    }

    @Test fun onlyCompactLetsContentSpanTheFullWidth() {
        // On a phone the screen IS the measure; wider windows get a reading-width cap.
        assertEquals(Dp.Unspecified, WidthClass.COMPACT.contentMaxWidth())
        assertTrue(WidthClass.MEDIUM.contentMaxWidth() < WidthClass.EXPANDED.contentMaxWidth())
    }

    @Test fun compactAndAtLeastMediumAreExactOpposites() {
        WidthClass.entries.forEach { widthClass ->
            assertEquals(!widthClass.isCompact, widthClass.isAtLeastMedium, "$widthClass")
        }
    }
}
