package app.terminalssh.secure.sftp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Disk-backed cache for image thumbnails to avoid re-downloading the same remote image.
 * Thumbnails are stored as JPEG at [MAX_DIMENSION]px on the longest side.
 */
object ThumbnailCache {
    private const val MAX_DIMENSION = 300
    private const val QUALITY = 75

    private var cacheDir: File? = null

    fun init(cacheDir: File) {
        this.cacheDir = File(cacheDir, "thumbnails").also { it.mkdirs() }
    }

    fun get(remotePath: String): Bitmap? {
        val file = fileFor(remotePath) ?: return null
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun put(remotePath: String, bitmap: Bitmap) {
        val file = fileFor(remotePath) ?: return
        runCatching {
            val scaled = scaleDown(bitmap, MAX_DIMENSION)
            java.io.FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
        }
    }

    fun remove(remotePath: String) {
        fileFor(remotePath)?.delete()
    }

    fun clear() {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    /** Evict oldest files when cache exceeds [MAX_ENTRIES]. */
    fun evict() {
        val dir = cacheDir ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_ENTRIES) {
            files.take(files.size - MAX_ENTRIES).forEach { it.delete() }
        }
    }

    private fun fileFor(remotePath: String): File? {
        val dir = cacheDir ?: return null
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(remotePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(dir, "$hash.jpg")
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    private const val MAX_ENTRIES = 100
}
