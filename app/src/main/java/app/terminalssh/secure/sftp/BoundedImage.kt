package app.terminalssh.secure.sftp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import java.io.OutputStream

/**
 * Keeps remote image preview inside a memory budget.
 *
 * Two separate ways a remote image used to kill the process, both reachable by opening
 * one file:
 *
 * 1. The whole file was read into a `ByteArray` before anything looked at its size, so a
 *    200 MB file — or a small file a hostile server keeps answering forever — was an OOM
 *    during download.
 * 2. Even a modest file decoded at full resolution: a 12000×9000 JPEG is ~430 MB as an
 *    ARGB_8888 bitmap regardless of how few bytes it took on the wire.
 *
 * So the bytes are capped on the way in, and the decode is sampled down on the way out.
 */
object BoundedImage {

    /** Comfortably larger than any photo worth previewing on a phone. */
    const val MAX_PREVIEW_BYTES = 16L * 1024 * 1024

    /** Longest edge of the decoded bitmap. ~8 MB at ARGB_8888, well inside a normal heap. */
    const val MAX_DIMENSION = 1440

    /**
     * The `inSampleSize` for an image of [width]×[height] that keeps both edges within
     * [maxDimension]. Always a power of two, which is the only thing BitmapFactory
     * honours exactly; always at least 1.
     */
    fun sampleSizeFor(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2
        return sample
    }

    /**
     * Decodes [bytes] scaled down to at most [maxDimension] on the longest edge, or null
     * if the data is not a decodable image. Reads the header first so the full-size
     * bitmap is never allocated.
     */
    fun decodeSampled(bytes: ByteArray, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }
}

/** Raised when a bounded read is handed more bytes than the caller allowed. */
class TransferTooLargeException(val limitBytes: Long) :
    IOException("remote file exceeds the $limitBytes byte preview limit")

/**
 * An [OutputStream] that refuses to buffer more than [limitBytes].
 *
 * The point is to fail *during* the transfer rather than after it: checking a file's
 * stat size first is a TOCTOU race, and a server is free to send more bytes than it
 * advertised. This makes the limit true regardless of what the server claims.
 */
class LimitedOutputStream(
    private val delegate: OutputStream,
    private val limitBytes: Long,
) : OutputStream() {
    var written: Long = 0L
        private set

    private fun reserve(count: Int) {
        if (written + count > limitBytes) throw TransferTooLargeException(limitBytes)
        written += count
    }

    override fun write(b: Int) {
        reserve(1)
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        reserve(len)
        delegate.write(b, off, len)
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
