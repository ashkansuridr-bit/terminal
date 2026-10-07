package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * Bridges a SAF folder the user picked (a document *tree*) to a plain [File] directory
 * the existing sync engine can read. Files are streamed one at a time into a scratch
 * cache under [cacheDir]; the returned root is the same relative shape as the tree.
 *
 * The copy is transient: it exists only long enough to plan and execute one sync. On
 * any failure every byte already staged is zero-filled before the tree is deleted, so a
 * partially mirrored source never lands in storage as plaintext residue.
 */
object SyncSourceMirror {

    private const val COLUMN_DOC_ID = DocumentsContract.Document.COLUMN_DOCUMENT_ID
    private const val COLUMN_MIME = DocumentsContract.Document.COLUMN_MIME_TYPE
    private const val COLUMN_DISPLAY = DocumentsContract.Document.COLUMN_DISPLAY_NAME

    /**
     * Mirrors every readable file under [treeUri] into a fresh root under [cacheDir].
     *
     * @throws IOException when the tree is unreadable, empty, or any single file fails
     * to copy; the partial mirror is wiped (with zero-fill) before the error surfaces.
     */
    fun mirrorTree(cacheDir: File, resolver: ContentResolver, treeUri: Uri, label: String): File {
        require(cacheDir.isDirectory || cacheDir.mkdirs()) { "Sync scratch directory unavailable" }
        val destRoot = File(cacheDir, "sync_${label}_${System.currentTimeMillis()}")
        require(destRoot.mkdirs()) { "Sync scratch directory unavailable" }
        var copiedAny = false
        try {
            copiedAny = mirrorInto(resolver, treeUri, DocumentsContract.getTreeDocumentId(treeUri), destRoot)
            if (!copiedAny) throw IOException("The chosen folder contains no readable files")
            return destRoot
        } catch (failure: Throwable) {
            wipe(destRoot)
            if (failure is IOException) throw failure
            throw IOException("Sync source could not be read", failure)
        }
    }

    private fun mirrorInto(
        resolver: ContentResolver,
        treeUri: Uri,
        documentId: String,
        destRoot: File,
    ): Boolean {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        var copiedAny = false
        resolver.query(
            childrenUri,
            arrayOf(COLUMN_DOC_ID, COLUMN_MIME, COLUMN_DISPLAY),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0) ?: continue
                val mime = cursor.getString(1) ?: "application/octet-stream"
                val name = sanitize(cursor.getString(2) ?: childId)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                    val dir = File(destRoot, name)
                    require(dir.mkdirs()) { "Sync path collides with a file: $name" }
                    copiedAny = mirrorInto(resolver, treeUri, childId, dir) || copiedAny
                } else {
                    copyFile(resolver, childUri, File(destRoot, name))
                    copiedAny = true
                }
            }
        } ?: throw IOException("The chosen folder is not readable")
        return copiedAny
    }

    private fun copyFile(resolver: ContentResolver, uri: Uri, dest: File) {
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
        } ?: throw IOException("A file in the chosen folder could not be opened: ${dest.name}")
    }

    /** Keeps names inside the destination tree; a hostile document name cannot escape. */
    private fun sanitize(name: String): String {
        val cleaned = name.replace('/', '_').replace('\\', '_')
        val trimmed = cleaned.trim()
        return when (trimmed) {
            "", ".", ".." -> "_"
            else -> trimmed
        }
    }

    /** Zero-fills and removes a mirrored scratch tree. */
    fun wipe(root: File) {
        if (!root.exists()) return
        root.walkBottomUp().forEach { file ->
            if (file.isFile) runCatching { file.readBytes().also { it.fill(0) } }
            runCatching { file.delete() }
        }
    }
}