package app.terminalssh.secure.sftp

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.ssh.SshSession
import java.io.File
import java.io.FileOutputStream

/**
 * Exposes live SSH sessions inside Android's own file picker.
 *
 * The point is to delete a workflow rather than add a feature: without this, editing a
 * remote file means download, leave the app, edit, come back, upload. With it, any editor
 * or git client can open the file straight off the server and save back to it.
 *
 * **Only sessions that are already connected appear as roots.** A DocumentsProvider is
 * invoked from other apps with no UI of its own, so a root for a disconnected host could
 * only be opened by authenticating in the background — prompting for a passphrase from
 * someone else's file picker, or worse, silently using a stored one. Requiring a live
 * session keeps every authentication where the user can see it.
 *
 * Writes are staged through a cache file and uploaded on close, because SFTP has no
 * seekable write and Android hands out a plain descriptor.
 */
class SftpDocumentsProvider : DocumentsProvider() {

    private val app: TerminalApp get() = context!!.applicationContext as TerminalApp

    override fun onCreate(): Boolean = true

    // ---- roots ----

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        for (session in liveSessions()) {
            cursor.newRow().apply {
                add(Root.COLUMN_ROOT_ID, session.id)
                add(Root.COLUMN_DOCUMENT_ID, docId(session.id, RemotePath.ROOT))
                add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
                add(Root.COLUMN_SUMMARY, session.profile.displayName)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(
                    Root.COLUMN_FLAGS,
                    Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY,
                )
            }
        }
        return cursor
    }

    // ---- documents ----

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val (sessionId, path) = parse(documentId)
        val client = clientFor(sessionId) ?: throw java.io.FileNotFoundException(documentId)

        if (path == RemotePath.ROOT) {
            addRow(cursor, documentId, "/", isDirectory = true, size = 0L, modified = 0L)
            return cursor
        }
        val parent = RemotePath.parentOf(path)
        val name = path.substringAfterLast('/')
        val entry = client.list(parent).firstOrNull { it.name == name }
            ?: throw java.io.FileNotFoundException(documentId)
        addRow(cursor, documentId, entry.name, entry.isDirectory, entry.sizeBytes, entry.modifiedEpochSeconds * 1000)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val (sessionId, path) = parse(parentDocumentId)
        val client = clientFor(sessionId) ?: throw java.io.FileNotFoundException(parentDocumentId)

        for (entry in client.list(path)) {
            addRow(
                cursor,
                docId(sessionId, entry.path),
                entry.name,
                entry.isDirectory,
                entry.sizeBytes,
                entry.modifiedEpochSeconds * 1000,
            )
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val (sessionId, path) = parse(documentId)
        val client = clientFor(sessionId) ?: throw java.io.FileNotFoundException(documentId)
        val staging = File(context!!.cacheDir, "saf-${documentId.hashCode()}-${path.substringAfterLast('/')}")

        // Read side: pull the file down once, hand back a descriptor onto the copy.
        // Bounded, because the picker will happily be pointed at a 40 GB backup and the
        // cache directory is not a place to discover that.
        if (!mode.contains('w')) {
            FileOutputStream(staging).use { out ->
                client.download(path, LimitedOutputStream(out, MAX_STAGED_BYTES), 0L) {}
            }
            return ParcelFileDescriptor.open(staging, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        // Write side: SFTP cannot be written through a seekable descriptor, so the edit
        // lands in the cache file and is uploaded when the other app closes it.
        if (mode.contains('r')) {
            runCatching {
                FileOutputStream(staging).use { out ->
                    client.download(path, LimitedOutputStream(out, MAX_STAGED_BYTES), 0L) {}
                }
            }.onFailure {
                staging.delete()
                throw it
            }
        }
        val handler = android.os.Handler(app.mainLooper)
        return ParcelFileDescriptor.open(
            staging,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE,
            handler,
        ) { error ->
            // Runs when the reader/writer closes. Upload only on a clean close: pushing a
            // half-written file back over the original is exactly the data loss this
            // whole module tries to avoid.
            if (error == null) {
                runCatching {
                    staging.inputStream().use { input -> client.upload(input, path, 0L) {} }
                }
            }
            staging.delete()
        }
    }

    // ---- mutations ----

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val (sessionId, parent) = parse(parentDocumentId)
        val client = clientFor(sessionId) ?: throw java.io.FileNotFoundException(parentDocumentId)
        val safeName = RemotePath.sanitizeDownloadName(displayName)
        val path = RemotePath.join(parent, safeName)

        if (mimeType == Document.MIME_TYPE_DIR) {
            client.makeDirectory(path)
        } else {
            client.uploadText(path, "")
        }
        return docId(sessionId, path)
    }

    override fun deleteDocument(documentId: String) {
        val (sessionId, path) = parse(documentId)
        val client = clientFor(sessionId) ?: throw java.io.FileNotFoundException(documentId)
        val parent = RemotePath.parentOf(path)
        val name = path.substringAfterLast('/')
        val entry = client.list(parent).firstOrNull { it.name == name }
            ?: throw java.io.FileNotFoundException(documentId)
        client.delete(path, entry.isDirectory)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val (sessionId, path) = parse(documentId)
        val client = clientFor(sessionId) ?: throw java.io.FileNotFoundException(documentId)
        val target = RemotePath.join(RemotePath.parentOf(path), RemotePath.sanitizeDownloadName(displayName))
        client.rename(path, target)
        return docId(sessionId, target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val (parentSession, parentPath) = parse(parentDocumentId)
        val (childSession, childPath) = parse(documentId)
        return parentSession == childSession && childPath.startsWith(parentPath)
    }

    // ---- helpers ----

    private fun liveSessions(): List<SshSession> =
        runCatching { app.sessions.sessions.value.filter { it.state.value.isLive } }.getOrDefault(emptyList())

    /**
     * The SFTP channel for a session, opened once and reused.
     *
     * This used to open a channel per call and never close it, so every directory listing
     * in the picker leaked one — a few minutes of browsing exhausted the server's channel
     * limit. Channels are cached per session and dropped as soon as the session is no
     * longer live, which is also what stops a stale channel outliving its session.
     */
    private fun clientFor(sessionId: String): SftpClient? {
        val live = liveSessions().map { it.id }.toSet()
        synchronized(clients) {
            // Drop channels whose session has gone away.
            clients.keys.filterNot { it in live }.forEach { clients.remove(it)?.let { c -> runCatching { c.close() } } }
            if (sessionId !in live) return null

            clients[sessionId]?.let { return it }
            val session = liveSessions().firstOrNull { it.id == sessionId } ?: return null
            val opened = runCatching { session.openSftp() }.getOrNull() ?: return null
            clients[sessionId] = opened
            return opened
        }
    }

    private val clients = HashMap<String, SftpClient>()

    private fun docId(sessionId: String, path: String) = "$sessionId$SEPARATOR${RemotePath.normalize(path)}"

    private fun parse(documentId: String): Pair<String, String> {
        val cut = documentId.indexOf(SEPARATOR)
        if (cut < 0) throw java.io.FileNotFoundException(documentId)
        return documentId.substring(0, cut) to documentId.substring(cut + SEPARATOR.length)
    }

    private fun addRow(
        cursor: MatrixCursor,
        documentId: String,
        name: String,
        isDirectory: Boolean,
        size: Long,
        modified: Long,
    ) {
        val flags = if (isDirectory) {
            Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        } else {
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, name)
            add(Document.COLUMN_SIZE, size)
            add(Document.COLUMN_LAST_MODIFIED, modified)
            add(Document.COLUMN_FLAGS, flags)
            add(
                Document.COLUMN_MIME_TYPE,
                if (isDirectory) Document.MIME_TYPE_DIR else MimeTypes.forFileName(name),
            )
        }
    }

    private companion object {
        /** `::` cannot appear in a POSIX path component, so it cannot be ambiguous. */
        const val SEPARATOR = "::"

        /** Ceiling for a file staged through the cache for another app to open. */
        const val MAX_STAGED_BYTES = 512L * 1024 * 1024

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_ICON, Root.COLUMN_FLAGS,
        )
        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_MIME_TYPE,
        )
    }
}
