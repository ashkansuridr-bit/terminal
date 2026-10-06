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
        signal?.throwIfCanceled()
        val session = liveSessions().firstOrNull { it.id == sessionId }
            ?: throw java.io.FileNotFoundException(documentId)
        val writable = mode.contains('w')
        val store = SafStagingStore(File(context!!.filesDir, "saf-edits"))
        val edit = store.create(sessionId, session.profile.id, session.profile.subtitle, path, writable)
        val staging = edit.file
        try {
            val original = if (writable) client.editFingerprint(path) else null
            original?.let { store.recordFingerprint(edit, it) }
            // "w"/"wt" truncate; rw and append preserve the existing file.
            if (!writable || (mode.contains('r') && !mode.contains('t')) || mode.contains('a')) {
                FileOutputStream(staging).use { out ->
                    client.download(path, LimitedOutputStream(out, MAX_STAGED_BYTES), 0L) {
                        signal?.throwIfCanceled()
                    }
                }
            }
            signal?.throwIfCanceled()
            original?.let { client.requireFingerprint(path, it) }
            store.activate(edit)
            return ParcelFileDescriptor.open(
                staging, ParcelFileDescriptor.parseMode(mode), android.os.Handler(app.mainLooper),
            ) { error ->
                // Never perform network IO on the main-thread close listener.
                SAVE_EXECUTOR.execute {
                    if (!writable) {
                        store.complete(edit)
                    } else if (error != null) {
                        retainFailedEdit(store, edit)
                    } else {
                        try {
                            val expected = store.fingerprint(edit)
                            client.requireFingerprint(path, expected)
                            staging.inputStream().use { input ->
                                client.atomicUpload(input, path, beforeCommit = {
                                    client.requireFingerprint(path, expected)
                                })
                            }
                            store.complete(edit)
                        } catch (failure: Exception) {
                            // The local copy and metadata remain available even after process death.
                            retainFailedEdit(store, edit)
                        }
                    }
                }
            }
        } catch (failure: Exception) {
            store.complete(edit)
            throw failure
        }
    }

    private fun retainFailedEdit(store: SafStagingStore, edit: SafStagingStore.Edit) {
        try { store.failed(edit) }
        finally {
            // Metadata errors must still notify. Neither the edit nor metadata is deleted.
            FailedSaveActivity.notifyRecovery(context!!)
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
            check(!client.exists(path)) { "Document already exists" }
            byteArrayOf().inputStream().use { source ->
                client.atomicUpload(source, path, beforeCommit = {
                    check(!client.exists(path)) { "Document already exists" }
                })
            }
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
        return parentSession == childSession && SafStagingStore.isChild(parentPath, childPath)
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
        val SAVE_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor()
        /** Split only on the first separator; subsequent separators are legal path text. */
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
