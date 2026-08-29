package app.terminalssh.secure.sftp

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoundedImageTest {

    // ---- sampling math: what keeps a huge image from becoming a huge bitmap ----

    @Test fun aTwelveThousandPixelImageIsSampledFarDown() {
        // 12000x9000 ARGB_8888 is ~432 MB undecoded-down. At this sample size the
        // decoded bitmap is under 1500px on the long edge.
        val sample = BoundedImage.sampleSizeFor(12000, 9000)
        assertTrue(sample >= 8, "expected aggressive sampling, got $sample")
        assertTrue(12000 / sample <= BoundedImage.MAX_DIMENSION)
        assertTrue(9000 / sample <= BoundedImage.MAX_DIMENSION)
    }

    @Test fun sampleSizeIsAlwaysAPowerOfTwo() {
        for (w in listOf(1, 100, 1441, 3000, 5000, 12000, 40000)) {
            val s = BoundedImage.sampleSizeFor(w, w)
            assertTrue(s > 0 && (s and (s - 1)) == 0, "sample $s for width $w is not a power of two")
        }
    }

    @Test fun anImageAlreadyWithinBudgetIsNotSampled() {
        assertEquals(1, BoundedImage.sampleSizeFor(800, 600))
        assertEquals(1, BoundedImage.sampleSizeFor(BoundedImage.MAX_DIMENSION, BoundedImage.MAX_DIMENSION))
    }

    @Test fun extremeAspectRatioIsBoundedOnTheLongEdge() {
        // A 60000x10 banner: the long edge is what blows the allocation.
        val sample = BoundedImage.sampleSizeFor(60000, 10)
        assertTrue(60000 / sample <= BoundedImage.MAX_DIMENSION)
    }

    @Test fun degenerateBoundsDoNotDivideByZeroOrLoopForever() {
        assertEquals(1, BoundedImage.sampleSizeFor(0, 0))
        assertEquals(1, BoundedImage.sampleSizeFor(-5, 100))
    }

    // ---- the byte cap: enforced on the stream, not on the server's stat ----

    @Test fun writingPastTheLimitFails() {
        val sink = ByteArrayOutputStream()
        val limited = LimitedOutputStream(sink, limitBytes = 10)
        limited.write(ByteArray(10))
        assertEquals(10L, limited.written)

        assertFailsWith<TransferTooLargeException> { limited.write(ByteArray(1)) }
    }

    @Test fun aServerSendingMoreThanItAdvertisedIsStillStopped() {
        // The whole point of enforcing on the stream: stat said small, the bytes are not.
        val sink = ByteArrayOutputStream()
        val limited = LimitedOutputStream(sink, limitBytes = 1024)
        assertFailsWith<TransferTooLargeException> {
            repeat(1000) { limited.write(ByteArray(64)) }
        }
        assertTrue(sink.size() <= 1024, "no more than the limit may reach the buffer")
    }

    @Test fun singleByteWritesAreCountedToo() {
        val limited = LimitedOutputStream(ByteArrayOutputStream(), limitBytes = 3)
        repeat(3) { limited.write(1) }
        assertFailsWith<TransferTooLargeException> { limited.write(1) }
    }

    @Test fun aFileInsideTheLimitPassesThroughUntouched() {
        val sink = ByteArrayOutputStream()
        val limited = LimitedOutputStream(sink, limitBytes = 100)
        val payload = ByteArray(64) { it.toByte() }
        limited.write(payload)
        limited.flush()
        assertTrue(payload.contentEquals(sink.toByteArray()))
    }

    @Test fun theExceptionReportsTheLimitItEnforced() {
        val e = assertFailsWith<TransferTooLargeException> {
            LimitedOutputStream(ByteArrayOutputStream(), limitBytes = 42).write(ByteArray(43))
        }
        assertEquals(42L, e.limitBytes)
    }
}
