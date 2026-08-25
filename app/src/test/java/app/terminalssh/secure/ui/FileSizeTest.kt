package app.terminalssh.secure.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSizeTest {

    private fun format(bytes: Long) = FileSize.format(bytes, Locale.US)

    @Test fun bytesBelowOneKilobyteAreExact() {
        assertEquals("0 B", format(0))
        assertEquals("1 B", format(1))
        assertEquals("1023 B", format(1023))
    }

    @Test fun scalesThroughBinaryUnits() {
        assertEquals("1.0 KB", format(1024))
        assertEquals("1.0 MB", format(1024L * 1024))
        assertEquals("1.0 GB", format(1024L * 1024 * 1024))
        assertEquals("1.0 TB", format(1024L * 1024 * 1024 * 1024))
    }

    @Test fun keepsOneDecimalBelowTenAndDropsItAbove() {
        assertEquals("9.4 MB", format((9.4 * 1024 * 1024).toLong()))
        assertEquals("421 MB", format(421L * 1024 * 1024))
    }

    @Test fun unknownSizeRendersAsADash() {
        // Servers do not always report a size; a "-1 B" would be nonsense.
        assertEquals("—", format(-1))
    }

    @Test fun veryLargeValuesStopAtTheLargestUnit() {
        val huge = Long.MAX_VALUE
        assertEquals(true, format(huge).endsWith("PB"), format(huge))
    }

    @Test fun localeWithCommaDecimalSeparatorIsRespected() {
        // A Persian or German user reads "1,5 MB", not "1.5 MB".
        val german = FileSize.format(1_572_864L, Locale.GERMANY)
        assertEquals("1,5 MB", german)
    }
}
