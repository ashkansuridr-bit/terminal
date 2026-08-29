package app.terminalssh.secure.sftp

/** One contiguous byte range of a file, downloaded on its own SFTP channel. */
data class ByteRange(val index: Int, val start: Long, val endExclusive: Long) {
    val length: Long get() = endExclusive - start
}

/**
 * Splits a single large download across several channels.
 *
 * Existing concurrency is per *file*, which does nothing for the case that actually
 * hurts: one multi-gigabyte file on a high-latency link, where a single SFTP stream
 * spends most of its time waiting for round trips rather than saturating the pipe.
 * Several channels reading disjoint ranges fill that dead time.
 *
 * Splitting is deliberately conservative. Below [MIN_MULTIPART_BYTES] the extra channels
 * cost more in setup than they recover, and a file of unknown length cannot be split at
 * all because the last range would have no end. Both cases fall back to a single stream,
 * which is why [planFor] can always return a usable plan.
 */
object MultipartPlan {

    /** Below this, one stream is faster than the handshakes for several. */
    const val MIN_MULTIPART_BYTES = 32L * 1024 * 1024

    /** More channels than this starves each one and annoys the server. */
    const val MAX_PARTS = 4

    /** No range smaller than this; tiny tail ranges are pure overhead. */
    const val MIN_PART_BYTES = 8L * 1024 * 1024

    /**
     * How many channels to use for a file of [sizeBytes]. 1 means "download it the
     * ordinary way" — an unknown or small size always lands here.
     */
    fun partCountFor(sizeBytes: Long, maxParts: Int = MAX_PARTS): Int {
        if (sizeBytes < MIN_MULTIPART_BYTES) return 1
        val byMinimum = (sizeBytes / MIN_PART_BYTES).toInt()
        return byMinimum.coerceIn(1, maxParts)
    }

    /**
     * Contiguous, non-overlapping ranges covering exactly `0 until sizeBytes`.
     *
     * The remainder goes to the last range rather than being spread, so every range but
     * one is the same size and the arithmetic stays exact — a rounding error here writes
     * bytes at the wrong offset, which is silent corruption.
     */
    fun planFor(sizeBytes: Long, maxParts: Int = MAX_PARTS): List<ByteRange> {
        if (sizeBytes <= 0) return emptyList()
        val parts = partCountFor(sizeBytes, maxParts)
        if (parts <= 1) return listOf(ByteRange(0, 0, sizeBytes))

        val base = sizeBytes / parts
        return (0 until parts).map { i ->
            val start = i * base
            val end = if (i == parts - 1) sizeBytes else start + base
            ByteRange(i, start, end)
        }
    }

    /** Sanity check a plan actually tiles the file. Cheap, and catches an off-by-one. */
    fun covers(ranges: List<ByteRange>, sizeBytes: Long): Boolean {
        if (ranges.isEmpty()) return sizeBytes <= 0
        val sorted = ranges.sortedBy { it.start }
        if (sorted.first().start != 0L) return false
        if (sorted.last().endExclusive != sizeBytes) return false
        for (i in 1 until sorted.size) {
            if (sorted[i].start != sorted[i - 1].endExclusive) return false
        }
        return sorted.all { it.length > 0 }
    }
}

/** Signals that a range has received every byte it was asked for. */
class RangeCompleteException : java.io.IOException("range complete")

/**
 * Writes into a [java.io.RandomAccessFile] at wherever it is currently positioned, and
 * stops hard once [length] bytes have been taken.
 *
 * JSch can start a download at an offset but has no way to end one early, so the stream
 * is what bounds the range: it throws [RangeCompleteException] rather than letting the
 * channel keep pulling the rest of the file into a range that does not own it.
 */
class RangeOutputStream(
    private val file: java.io.RandomAccessFile,
    private val length: Long,
) : java.io.OutputStream() {
    var written: Long = 0L
        private set

    override fun write(b: Int) {
        if (written >= length) throw RangeCompleteException()
        file.write(b)
        written++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (written >= length) throw RangeCompleteException()
        val take = minOf(len.toLong(), length - written).toInt()
        file.write(b, off, take)
        written += take
        if (written >= length) throw RangeCompleteException()
    }
}
