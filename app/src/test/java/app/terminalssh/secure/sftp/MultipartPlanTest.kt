package app.terminalssh.secure.sftp

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultipartPlanTest {

    private val big = 200L * 1024 * 1024

    @Test fun aSmallFileIsNotSplit() {
        assertEquals(1, MultipartPlan.partCountFor(1024))
        assertEquals(1, MultipartPlan.partCountFor(MultipartPlan.MIN_MULTIPART_BYTES - 1))
        assertEquals(listOf(ByteRange(0, 0, 1024)), MultipartPlan.planFor(1024))
    }

    @Test fun anUnknownSizeIsNeverSplit() {
        // A range plan needs an end. Transfer.UNKNOWN_SIZE is negative.
        assertEquals(1, MultipartPlan.partCountFor(Transfer.UNKNOWN_SIZE))
        assertTrue(MultipartPlan.planFor(Transfer.UNKNOWN_SIZE).isEmpty())
    }

    @Test fun aLargeFileIsSplitUpToTheCap() {
        assertEquals(MultipartPlan.MAX_PARTS, MultipartPlan.partCountFor(big))
    }

    @Test fun rangesTileTheFileExactlyWithNoGapOrOverlap() {
        for (size in listOf(big, big + 1, big + 7, 33L * 1024 * 1024, 99_999_999L)) {
            val plan = MultipartPlan.planFor(size)
            assertTrue(MultipartPlan.covers(plan, size), "plan for $size does not tile the file")
            assertEquals(size, plan.sumOf { it.length }, "lengths must add up for $size")
        }
    }

    @Test fun theRemainderGoesToTheLastRangeSoNoByteIsLost() {
        // The corruption case: size not divisible by part count.
        val size = big + 3
        val plan = MultipartPlan.planFor(size)
        assertEquals(size, plan.last().endExclusive)
        assertEquals(size, plan.sumOf { it.length })
    }

    @Test fun coversRejectsAGapAnOverlapAndAWrongEnd() {
        assertFalse(MultipartPlan.covers(listOf(ByteRange(0, 0, 10), ByteRange(1, 20, 30)), 30), "gap")
        assertFalse(MultipartPlan.covers(listOf(ByteRange(0, 0, 20), ByteRange(1, 10, 30)), 30), "overlap")
        assertFalse(MultipartPlan.covers(listOf(ByteRange(0, 0, 10)), 30), "short")
        assertFalse(MultipartPlan.covers(listOf(ByteRange(0, 5, 30)), 30), "does not start at zero")
    }

    @Test fun everyRangeIsNonEmpty() {
        val plan = MultipartPlan.planFor(MultipartPlan.MIN_MULTIPART_BYTES)
        assertTrue(plan.all { it.length > 0 }, "an empty range would stall forever waiting for bytes")
    }

    // ---- the stream that actually bounds a range ----

    @Test fun aRangeStreamStopsExactlyAtItsLength() {
        val file = kotlin.io.path.createTempFile("range", ".bin").toFile()
        try {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                val out = RangeOutputStream(raf, length = 10)
                out.write(ByteArray(6))
                assertEquals(6L, out.written)
                // JSch keeps pushing; the stream is what ends the range.
                assertFailsWith<RangeCompleteException> { out.write(ByteArray(20)) }
                assertEquals(10L, out.written, "never more than the range owns")
            }
        } finally { file.delete() }
    }

    @Test fun aRangeStreamWritesAtTheSeekedOffset() {
        val file = kotlin.io.path.createTempFile("range", ".bin").toFile()
        try {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(20)
                raf.seek(10)
                val out = RangeOutputStream(raf, length = 5)
                // Filling a range exactly is also how a range ends, so this signals too.
                assertFailsWith<RangeCompleteException> { out.write(byteArrayOf(1, 2, 3, 4, 5)) }
                assertEquals(5L, out.written, "the bytes are written before the signal")
            }
            val bytes = file.readBytes()
            assertEquals(20, bytes.size)
            assertEquals(0, bytes[9], "nothing written before the range start")
            assertEquals(1, bytes[10], "the range lands at its own offset")
            assertEquals(5, bytes[14])
        } finally { file.delete() }
    }

    @Test fun writingPastAFullRangeThrowsImmediately() {
        val file = kotlin.io.path.createTempFile("range", ".bin").toFile()
        try {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                val out = RangeOutputStream(raf, length = 4)
                assertFailsWith<RangeCompleteException> { out.write(ByteArray(4)) }
                assertFailsWith<RangeCompleteException> { out.write(1) }
            }
        } finally { file.delete() }
    }

    @Test fun theLimitedStreamAndRangeStreamAreDifferentTools() {
        // LimitedOutputStream fails a transfer; RangeOutputStream ends one cleanly.
        assertFailsWith<TransferTooLargeException> {
            LimitedOutputStream(ByteArrayOutputStream(), 2).write(ByteArray(3))
        }
    }
}
