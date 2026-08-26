package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.net.Uri
import app.terminalssh.secure.ssh.SshSession
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Drives the browser and the transfer queue against one SSH session.
 *
 * The scheduling rules live in [TransferQueue], which is pure and unit-tested; this
 * class is the thin layer that does the actual I/O and feeds results back into it.
 *
 * Owned by [app.terminalssh.secure.vm.AppViewModel] on a per-session basis and kept
 * alive for as long as that session is — not tied to the Files tab's composition — so a
 * transfer keeps running while the user is on another tab. See
 * `AppViewModel.sftpControllerFor`/`closeSession`.
 */
class SftpController(
    private val session: SshSession,
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) {
    data class BrowserState(
        val path: String = RemotePath.ROOT,
        val entries: List<RemoteEntry> = emptyList(),
        val loading: Boolean = false,
        val errorKind: TransferErrorKind? = null,
    )

    /** A queued upload whose remote target already exists, awaiting the user's decision. */
    data class UploadConflict(
        val source: Uri,
        val displayName: String,
        val remoteDirectory: String,
        val remotePath: String,
    )

    enum class ConflictResolution { OVERWRITE, RENAME, SKIP, CANCEL }

    private val _browser = MutableStateFlow(BrowserState())
    val browser: StateFlow<BrowserState> = _browser.asStateFlow()

    /** Persists pending transfers across process death. Stored in cacheDir for simplicity. */
    private val persistFile = java.io.File(cacheDir, "transfer_queue.json")
    val queue = TransferQueue.fromPersisted(persistFile)

    private val _uploadConflict = MutableStateFlow<UploadConflict?>(null)
    val uploadConflict: StateFlow<UploadConflict?> = _uploadConflict.asStateFlow()

    private var client: SftpClient? = null
    private var pumpJob: Job? = null

    /** Auto-persist queue whenever it changes so pending transfers survive process death. */
    private val persistJob = scope.launch {
        queue.transfers.collect {
            if (it.any { t -> !t.state.isTerminal }) {
                queue.persist(persistFile)
            } else {
                queue.clearPersisted(persistFile)
            }
        }
    }

    /**
     * Per-transfer SftpClient instances for parallel transfers. Each concurrent
     * transfer gets its own ChannelSftp channel so they don't block each other.
     */
    private val transferClients = ConcurrentHashMap<String, SftpClient>()

    /** Cancellation flags keyed by transfer id, read by the copy loops. */
    private val cancelled = mutableSetOf<String>()

    /**
     * True once [openHome] has been called for this controller's lifetime. Lets the UI
     * layer avoid re-navigating to the home directory — and losing the user's current
     * browsed path — every time the Files tab is revisited for a still-live session.
     */
    var hasOpened: Boolean = false
        private set

    private suspend fun client(): SftpClient = withContext(Dispatchers.IO) {
        client?.let { return@withContext it }
        val opened = session.openSftp() ?: throw IllegalStateException("session is not connected")
        client = opened
        opened
    }

    fun openHome() {
        hasOpened = true
        scope.launch {
            val start = runCatching { withContext(Dispatchers.IO) { client().home() } }
                .getOrDefault(RemotePath.ROOT)
            navigate(start)
        }
    }

    fun navigate(path: String) {
        scope.launch {
            _browser.value = _browser.value.copy(loading = true, errorKind = null)
            val result = runCatching {
                withContext(Dispatchers.IO) { client().list(path) }
            }
            _browser.value = result.fold(
                onSuccess = { entries ->
                    BrowserState(path = RemotePath.normalize(path), entries = entries, loading = false)
                },
                onFailure = { failure ->
                    // Keep the previous listing on screen rather than blanking it; an
                    // error banner over stale content beats an empty directory.
                    _browser.value.copy(loading = false, errorKind = SftpClient.classify(failure))
                },
            )
        }
    }

    fun navigateUp() = navigate(RemotePath.parent(_browser.value.path))

    fun refresh() = navigate(_browser.value.path)

    /** Creates [name] as a new subdirectory of the current directory, then refreshes. */
    fun createDirectory(name: String) = scope.launch {
        val path = RemotePath.join(_browser.value.path, RemotePath.sanitizeDownloadName(name))
        val result = runCatching { withContext(Dispatchers.IO) { client().makeDirectory(path) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** Renames [entry] to [newName] within its current directory, then refreshes. */
    fun rename(entry: RemoteEntry, newName: String) = scope.launch {
        val target = RemotePath.join(RemotePath.parent(entry.path), RemotePath.sanitizeDownloadName(newName))
        val result = runCatching { withContext(Dispatchers.IO) { client().rename(entry.path, target) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** Deletes [entry] (file or directory) from the server, then refreshes. */
    fun delete(entry: RemoteEntry) = scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) { client().delete(entry.path, entry.isDirectory) }
        }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /**
     * Deletes every entry in [entries] (files or directories), then refreshes once rather
     * than once per item. Best-effort: one failure doesn't stop the rest from being
     * attempted, and only the first failure is surfaced, since the refreshed listing
     * itself shows exactly what actually got removed.
     */
    fun deleteAll(entries: List<RemoteEntry>) = scope.launch {
        val failures = mutableListOf<Throwable>()
        withContext(Dispatchers.IO) {
            val sftp = client()
            entries.forEach { entry ->
                runCatching { sftp.delete(entry.path, entry.isDirectory) }
                    .onFailure { failures += it }
            }
        }
        refresh()
        failures.firstOrNull()?.let { showBrowserError(it) }
    }

    /** Directory-only listing for the "move/copy to" folder picker; doesn't touch [browser]. */
    suspend fun listDirectories(path: String): List<RemoteEntry> =
        runCatching { withContext(Dispatchers.IO) { client().list(path).filter { it.isDirectory } } }
            .getOrDefault(emptyList())

    /** Moves [entry] into [destinationDirectory], keeping its filename, then refreshes. */
    fun moveTo(entry: RemoteEntry, destinationDirectory: String) = scope.launch {
        val target = RemotePath.join(destinationDirectory, RemotePath.name(entry.path))
        val result = runCatching { withContext(Dispatchers.IO) { client().rename(entry.path, target) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /**
     * Copies [entry] into [destinationDirectory]. SFTP has no server-side copy, so this
     * streams the file through a private temp file (download, then upload) rather than
     * transferring it through the device twice over the network. Files only — copying a
     * directory would mean walking and copying every descendant, out of scope here.
     */
    fun copyTo(entry: RemoteEntry, destinationDirectory: String) = scope.launch {
        if (entry.isDirectory) return@launch
        val target = RemotePath.join(destinationDirectory, RemotePath.name(entry.path))
        val temp = File(cacheDir, "sftp-copy-${UUID.randomUUID()}")
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val sftp = client()
                temp.outputStream().use { sink -> sftp.download(entry.path, sink, resumeFrom = 0L) {} }
                temp.inputStream().use { source -> sftp.upload(source, target, resumeFrom = 0L) {} }
            }
        }
        temp.delete()
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** The target of a symlink, for the properties panel; null for anything else. */
    suspend fun symlinkTarget(path: String): String? =
        runCatching { withContext(Dispatchers.IO) { client().readlink(path) } }.getOrNull()

    /**
     * Counts files in a remote directory recursively. Used by the confirmation
     * dialog for recursive folder download (#26).
     */
    suspend fun countRemoteFiles(path: String): Int =
        withContext(Dispatchers.IO) { client().listRecursive(path).size }

    /**
     * Recursively downloads a remote folder: walks the directory tree, queues each
     * file as its own resumable Transfer. The destination tree is mirrored under
     * [destinationTreeUri] using SAF's DocumentsContract.
     */
    fun downloadFolder(remotePath: String, destinationTreeUri: android.net.Uri, displayName: String) = scope.launch {
        val entries = withContext(Dispatchers.IO) { client().listRecursive(remotePath) }
        val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            destinationTreeUri,
            android.provider.DocumentsContract.getTreeDocumentId(destinationTreeUri),
        )
        // Create subdirectories
        val subdirs = entries.map { it.second.substringBeforeLast('/', "") }.filter { it.isNotEmpty() }.toSet()
        val dirMap = mutableMapOf<String, android.net.Uri>()
        dirMap[""] = parentDocUri

        for (subdir in subdirs) {
            val parts = subdir.split("/")
            var currentParent = parentDocUri
            var currentPath = ""
            for (part in parts) {
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                if (currentPath !in dirMap) {
                    val dirUri = runCatching {
                        android.provider.DocumentsContract.createDocument(
                            contentResolver, currentParent,
                            android.provider.DocumentsContract.Document.MIME_TYPE_DIR, part,
                        )
                    }.getOrNull()
                    if (dirUri != null) {
                        dirMap[currentPath] = dirUri
                        currentParent = dirUri
                    }
                } else {
                    currentParent = dirMap[currentPath]!!
                }
            }
        }

        // Queue downloads
        for ((remoteFilePath, relativePath) in entries) {
            val parentDir = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val targetParent = dirMap[parentDir] ?: parentDocUri
            val destination = runCatching {
                android.provider.DocumentsContract.createDocument(
                    contentResolver, targetParent,
                    "application/octet-stream", fileName,
                )
            }.getOrNull() ?: continue
            val entry = RemoteEntry(
                name = fileName,
                path = remoteFilePath,
                isDirectory = false,
                isSymlink = false,
                sizeBytes = withContext(Dispatchers.IO) { client().size(remoteFilePath) },
                modifiedEpochSeconds = 0L,
                permissions = "",
            )
            enqueueDownload(entry, destination)
        }
    }

    /**
     * Counts local files in a directory recursively. Used by the confirmation
     * dialog for recursive folder upload (#27).
     */
    fun countLocalFiles(path: java.io.File): Int = path.walkTopDown().filter { it.isFile }.count()

    /**
     * Recursively uploads a local folder to the remote server: mirrors the
     * directory structure via makeDirectory, then queues each file.
     */
    fun uploadFolder(localPath: java.io.File, remotePath: String) = scope.launch {
        val entries = withContext(Dispatchers.IO) { client().listLocalRecursive(localPath) }
        val remoteBase = RemotePath.join(remotePath, localPath.name)

        // Create subdirectories
        val subdirs = entries.map { it.second.substringBeforeLast('/', "") }.filter { it.isNotEmpty() }.toSet()
        withContext(Dispatchers.IO) {
            val sftp = client()
            for (subdir in subdirs) {
                val fullRemoteDir = RemotePath.join(remoteBase, subdir)
                // Create each path segment
                val parts = subdir.split("/")
                var current = remoteBase
                for (part in parts) {
                    current = RemotePath.join(current, part)
                    runCatching { sftp.makeDirectory(current) }
                }
            }
        }

        // Queue uploads
        for ((localFile, relativePath) in entries) {
            val remoteFilePath = RemotePath.join(remoteBase, relativePath)
            val displayName = localFile.name
            val size = localFile.length()
            val entry = RemoteEntry(
                name = displayName,
                path = remoteFilePath,
                isDirectory = false,
                isSymlink = false,
                sizeBytes = size,
                modifiedEpochSeconds = localFile.lastModified() / 1000,
                permissions = "",
            )
            // Check for conflict
            val exists = withContext(Dispatchers.IO) { client().exists(remoteFilePath) }
            if (exists) {
                val renamed = nonCollidingName(RemotePath.parent(remoteFilePath), displayName)
                val renamedPath = RemotePath.join(RemotePath.parent(remoteFilePath), renamed)
                enqueueUploadNow(android.net.Uri.fromFile(localFile), renamed, renamedPath)
            } else {
                enqueueUploadNow(android.net.Uri.fromFile(localFile), displayName, remoteFilePath)
            }
        }
    }

    /** Downloads a remote file as raw bytes (for image preview, etc.). */
    suspend fun downloadFileBytes(remotePath: String): Result<ByteArray> =
        runCatching {
            withContext(Dispatchers.IO) {
                val sftp = client()
                val baos = java.io.ByteArrayOutputStream()
                sftp.download(remotePath, baos, 0L) {}
                baos.toByteArray()
            }
        }

    /** Downloads a remote text file's content (for the in-app editor / preview). */
    suspend fun downloadFileText(remotePath: String, maxBytes: Long = 512_000): Result<String> =
        runCatching { withContext(Dispatchers.IO) { client().downloadText(remotePath, maxBytes) } }

    /**
     * Records the mtime of a file when the user starts editing it. Used by
     * [uploadFileText] to detect concurrent edits (#37).
     */
    private val editMtimes = ConcurrentHashMap<String, Long>()

    /** Downloads text AND records the mtime for concurrent-edit detection. */
    suspend fun downloadFileTextForEdit(remotePath: String, maxBytes: Long = 512_000): Result<Pair<String, Long>> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val text = client().downloadText(remotePath, maxBytes)
                val mtime = client().mtime(remotePath)
                editMtimes[remotePath] = mtime
                text to mtime
            }
        }
    }

    /**
     * Uploads text content back to a remote path (after editing).
     * If the mtime changed since download, the user is warned about a possible
     * concurrent edit (#37) — returns true when the file was modified externally.
     */
    suspend fun uploadFileTextChecked(remotePath: String, text: String): Result<Boolean> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val currentMtime = client().mtime(remotePath)
                val savedMtime = editMtimes.remove(remotePath)
                val modifiedExternally = savedMtime != null && currentMtime != savedMtime
                client().uploadText(remotePath, text)
                modifiedExternally
            }
        }
    }

    /** Uploads text content back to a remote path (after editing). */
    fun uploadFileText(remotePath: String, text: String) = scope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { client().uploadText(remotePath, text) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /** Changes POSIX mode bits on a remote file/directory, then refreshes. */
    fun chmod(entry: RemoteEntry, mode: Int) = scope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { client().chmod(entry.path, mode) } }
        if (result.isSuccess) refresh() else showBrowserError(result.exceptionOrNull()!!)
    }

    /**
     * Recursive chmod: applies [mode] to all files under a directory.
     * Shows consequences before action — matches the app's security principle.
     */
    fun chmodRecursive(path: String, mode: Int) = scope.launch {
        val failures = mutableListOf<Throwable>()
        withContext(Dispatchers.IO) {
            val sftp = client()
            sftp.chmod(path, mode)
            recursiveChmod(sftp, path, mode, failures)
        }
        refresh()
        failures.firstOrNull()?.let { showBrowserError(it) }
    }

    private fun recursiveChmod(sftp: SftpClient, path: String, mode: Int, failures: MutableList<Throwable>) {
        val entries = runCatching { sftp.list(path) }.getOrDefault(emptyList())
        for (entry in entries) {
            if (entry.isDirectory) {
                runCatching { sftp.chmod(entry.path, mode) }.onFailure { failures += it }
                recursiveChmod(sftp, entry.path, mode, failures)
            } else {
                runCatching { sftp.chmod(entry.path, mode) }.onFailure { failures += it }
            }
        }
    }

    /**
     * Compresses the selected remote files into a .zip on the server side:
     * downloads them to a temp dir, zips them, and uploads the result.
     * Returns the remote path of the created zip, or throws on failure.
     */
    suspend fun compressSelection(entries: List<RemoteEntry>, remoteDestDir: String): String {
        return withContext(Dispatchers.IO) {
            val sftp = client()
            val tempDir = java.io.File(cacheDir, "compress_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                // Download all files to temp dir
                for (entry in entries) {
                    if (entry.isDirectory) continue
                    val localFile = java.io.File(tempDir, entry.name)
                    java.io.FileOutputStream(localFile).use { out ->
                        sftp.download(entry.path, out, 0L) {}
                    }
                }
                // Create zip from temp files
                val zipName = "archive_${System.currentTimeMillis()}.zip"
                val zipFile = java.io.File(cacheDir, zipName)
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
                    tempDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                        java.util.zip.ZipEntry(file.name).also { zos.putNextEntry(it) }
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                // Upload zip to remote destination
                val remotePath = RemotePath.join(remoteDestDir, zipName)
                java.io.FileInputStream(zipFile).use { inp ->
                    sftp.upload(inp, remotePath, 0L) {}
                }
                zipFile.delete()
                remotePath
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    // ---- one-way sync (#28/#29) ----

    /** Describes one change in a sync plan. */
    data class SyncAction(
        val relativePath: String,
        val kind: Kind,
        val localSize: Long = 0L,
        val remoteSize: Long = 0L,
    ) {
        enum class Kind { UPLOAD, DELETE_REMOTE, SKIP_IDENTICAL }
    }

    /**
     * Computes a one-way sync plan: local → remote.
     * Compares files by relative path and size (fast heuristic; mtime not available via SFTP universally).
     */
    suspend fun computeSyncPlan(localDir: java.io.File, remoteDir: String, deleteRemote: Boolean = false): List<SyncAction> {
        return withContext(Dispatchers.IO) {
            val sftp = client()
            // Local files
            val localFiles = localDir.walkTopDown().filter { it.isFile }.map { file ->
                file.relativeTo(localDir).path to file.length()
            }.toMap()
            // Remote files
            val remoteFiles = sftp.listRecursive(remoteDir).map { (remotePath, relative) ->
                val size = runCatching { sftp.size(remotePath) }.getOrDefault(0L)
                relative to size
            }.toMap()

            val actions = mutableListOf<SyncAction>()

            // Files to upload: new or different size
            for ((relPath, localSize) in localFiles) {
                val remoteSize = remoteFiles[relPath]
                if (remoteSize == null) {
                    actions.add(SyncAction(relPath, SyncAction.Kind.UPLOAD, localSize, 0L))
                } else if (remoteSize != localSize) {
                    actions.add(SyncAction(relPath, SyncAction.Kind.UPLOAD, localSize, remoteSize))
                } else {
                    actions.add(SyncAction(relPath, SyncAction.Kind.SKIP_IDENTICAL, localSize, remoteSize))
                }
            }

            // Files to delete on remote (remote has files not in local)
            if (deleteRemote) {
                for ((relPath, remoteSize) in remoteFiles) {
                    if (relPath !in localFiles) {
                        actions.add(SyncAction(relPath, SyncAction.Kind.DELETE_REMOTE, 0L, remoteSize))
                    }
                }
            }

            actions.sortedBy { it.relativePath }
        }
    }

    /**
     * Executes a sync plan: uploads new/changed files, deletes remote-only files.
     */
    suspend fun executeSyncPlan(localDir: java.io.File, remoteDir: String, actions: List<SyncAction>) {
        withContext(Dispatchers.IO) {
            val sftp = client()
            for (action in actions) {
                when (action.kind) {
                    SyncAction.Kind.UPLOAD -> {
                        val localFile = java.io.File(localDir, action.relativePath)
                        if (!localFile.exists()) continue
                        val remotePath = RemotePath.join(remoteDir, action.relativePath)
                        // Ensure parent directory exists
                        val parentDir = RemotePath.parent(remotePath)
                        runCatching { sftp.makeDirectory(parentDir) }
                        java.io.FileInputStream(localFile).use { inp ->
                            sftp.upload(inp, remotePath, 0L) {}
                        }
                    }
                    SyncAction.Kind.DELETE_REMOTE -> {
                        val remotePath = RemotePath.join(remoteDir, action.relativePath)
                        runCatching { sftp.delete(remotePath, false) }
                    }
                    SyncAction.Kind.SKIP_IDENTICAL -> { /* no-op */ }
                }
            }
        }
    }

    /** Surfaces a failed browser-adjacent action (create/rename/delete/chmod) the same way a
     *  failed listing does: keep the current entries on screen, show an error banner. */
    private fun showBrowserError(t: Throwable) {
        _browser.value = _browser.value.copy(errorKind = SftpClient.classify(t))
    }

    // ---- transfers ----

    fun enqueueDownload(entry: RemoteEntry, destination: Uri) {
        queue.enqueue(
            Transfer(
                id = UUID.randomUUID().toString(),
                direction = TransferDirection.DOWNLOAD,
                remotePath = entry.path,
                localUri = destination.toString(),
                displayName = entry.name,
                totalBytes = entry.sizeBytes,
            ),
        )
        pump()
    }

    /**
     * Checks whether [displayName] would collide with an existing remote file before
     * queuing anything; a real conflict is surfaced via [uploadConflict] for the UI to
     * resolve rather than silently overwriting.
     */
    fun enqueueUpload(source: Uri, displayName: String, remoteDirectory: String) {
        scope.launch {
            val remotePath = RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(displayName))
            val collides = runCatching {
                withContext(Dispatchers.IO) { client().exists(remotePath) }
            }.getOrDefault(false)
            if (collides) {
                _uploadConflict.value = UploadConflict(source, displayName, remoteDirectory, remotePath)
            } else {
                enqueueUploadNow(source, displayName, remotePath)
            }
        }
    }

    private fun enqueueUploadNow(source: Uri, displayName: String, remotePath: String) {
        queue.enqueue(
            Transfer(
                id = UUID.randomUUID().toString(),
                direction = TransferDirection.UPLOAD,
                remotePath = remotePath,
                localUri = source.toString(),
                displayName = displayName,
            ),
        )
        pump()
    }

    /** Resolves a pending [uploadConflict]; a no-op if there isn't one. */
    fun resolveConflict(resolution: ConflictResolution) {
        val conflict = _uploadConflict.value ?: return
        _uploadConflict.value = null
        when (resolution) {
            ConflictResolution.OVERWRITE ->
                enqueueUploadNow(conflict.source, conflict.displayName, conflict.remotePath)
            ConflictResolution.RENAME -> scope.launch {
                val renamed = nonCollidingName(conflict.remoteDirectory, conflict.displayName)
                val renamedPath = RemotePath.join(conflict.remoteDirectory, RemotePath.sanitizeDownloadName(renamed))
                enqueueUploadNow(conflict.source, renamed, renamedPath)
            }
            ConflictResolution.SKIP, ConflictResolution.CANCEL -> Unit
        }
    }

    /**
     * `file.txt` -> `file (1).txt` -> `file (2).txt`, probing the server for each
     * candidate until one is free. Gives up after [MAX_RENAME_ATTEMPTS] probes and falls
     * back to a timestamp suffix, so a directory with hundreds of colliding names can't
     * turn this into an unbounded loop.
     */
    private suspend fun nonCollidingName(remoteDirectory: String, displayName: String): String {
        val sanitized = RemotePath.sanitizeDownloadName(displayName)
        for (n in 1..MAX_RENAME_ATTEMPTS) {
            val candidate = RemotePath.withCollisionSuffix(sanitized, n)
            val taken = withContext(Dispatchers.IO) { client().exists(RemotePath.join(remoteDirectory, candidate)) }
            if (!taken) return candidate
        }
        val (stem, ext) = RemotePath.splitExtension(sanitized)
        return if (ext.isEmpty()) "$stem-${System.currentTimeMillis()}" else "$stem-${System.currentTimeMillis()}.$ext"
    }

    fun pause(id: String) {
        cancelled += id
        queue.pause(id)
    }

    fun resume(id: String) {
        cancelled -= id
        queue.resume(id)
        pump()
    }

    fun cancel(id: String) {
        cancelled += id
        queue.cancel(id)
    }

    fun clearFinished() = queue.clearFinished()

    /**
     * Starts the next transfer if the queue allows one; re-entrant and cheap.
     * Adaptive concurrency: launches up to maxConcurrent transfers in parallel,
     * each on its own SFTP channel for true parallel I/O.
     */
    private fun pump() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch {
            while (isActive) {
                queue.adaptConcurrency()
                val next = queue.nextToStart() ?: break
                queue.markRunning(next.id)
                cancelled -= next.id
                val transfer = queue.transfers.value.first { it.id == next.id }
                // Launch each transfer as a separate coroutine for parallel execution
                launch {
                    runTransfer(transfer)
                    transferClients.remove(transfer.id)?.close()
                }
            }
            // After all transfers finish, check again (new transfers may have been queued)
            delay(50)
            if (isActive) pump()
        }
    }

    private suspend fun runTransfer(transfer: Transfer) {
        // Each concurrent transfer gets its own SFTP channel for true parallel I/O.
        val transferClient = runCatching {
            withContext(Dispatchers.IO) { session.openSftp() }
        }.getOrNull()
        if (transferClient != null) {
            transferClients[transfer.id] = transferClient
        }

        val result = runCatching {
            val sftp = transferClient ?: client()
            withContext(Dispatchers.IO) {
                when (transfer.direction) {
                    TransferDirection.DOWNLOAD -> download(sftp, transfer)
                    TransferDirection.UPLOAD -> upload(sftp, transfer)
                }
            }
        }
        transferClient?.close()
        transferClients.remove(transfer.id)
        when {
            transfer.id in cancelled -> {
                // pause() and cancel() both add to `cancelled` — they're the same
                // interrupt mechanism, so the queue's own final state is what actually
                // distinguishes them. Only a genuinely CANCELLED download's staging file
                // is dead weight; a PAUSED one is exactly what a later resume reuses.
                val finalState = queue.transfers.value.firstOrNull { it.id == transfer.id }?.state
                if (transfer.direction == TransferDirection.DOWNLOAD && finalState == TransferState.CANCELLED) {
                    stagingFile(transfer).delete()
                }
            }
            result.isSuccess -> queue.markCompleted(transfer.id)
            else -> queue.fail(transfer.id, SftpClient.classify(result.exceptionOrNull()!!))
        }
    }

    private fun stagingFile(transfer: Transfer): File = File(cacheDir, "sftp-download-${transfer.id}")

    private fun download(sftp: SftpClient, transfer: Transfer) {
        val staging = stagingFile(transfer)
        // Pre-resume consistency check: trust transfer.transferredBytes only if the
        // staging file on disk actually holds that many bytes already. A mismatch (no
        // staging file, a shorter one than expected) means the recorded offset can't be
        // trusted, so restart this transfer from zero rather than risk corrupting the
        // resumed copy.
        val actualStagedBytes = if (staging.exists()) staging.length() else 0L
        val resumeFrom = if (canTrustResume(transfer.transferredBytes, actualStagedBytes)) {
            transfer.transferredBytes
        } else {
            0L
        }
        if (resumeFrom == 0L) {
            if (transfer.transferredBytes != 0L) queue.resetProgress(transfer.id)
            staging.delete()
        }

        FileOutputStream(staging, resumeFrom > 0L).use { sink ->
            sftp.download(transfer.remotePath, sink, resumeFrom = resumeFrom) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }

        // Success: copy the completed staging file to the user's chosen SAF destination,
        // then clean up. Cleanup only happens here and on a genuine cancel (see
        // runTransfer) — a paused or failed transfer keeps its staging file so a later
        // resume/retry can reuse the bytes already on disk.
        val uri = Uri.parse(transfer.localUri)
        val sink = contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("cannot open destination")
        sink.use { out -> staging.inputStream().use { it.copyTo(out) } }
        staging.delete()
    }

    private fun upload(sftp: SftpClient, transfer: Transfer) {
        val uri = Uri.parse(transfer.localUri)
        val source = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open source")
        source.use { input ->
            // Pre-resume consistency check: trust transfer.transferredBytes only if the
            // remote file's current size still matches it. A mismatch (the file changed
            // since the last attempt, or was never actually started) means the recorded
            // offset can't be trusted, so restart from zero.
            val remoteSize = sftp.size(transfer.remotePath)
            val resumeFrom = if (canTrustResume(transfer.transferredBytes, remoteSize)) {
                transfer.transferredBytes
            } else {
                0L
            }
            if (resumeFrom == 0L && transfer.transferredBytes != 0L) queue.resetProgress(transfer.id)

            skipFully(input, resumeFrom)
            sftp.upload(input, transfer.remotePath, resumeFrom = resumeFrom) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }
    }

    /** Signals a user-requested stop, distinguishing it from a real transfer failure. */
    private class InterruptedTransfer : RuntimeException("transfer interrupted by the user")

    fun onSessionLost() {
        queue.onConnectionLost()
        close()
    }

    fun close() {
        pumpJob?.cancel()
        persistJob.cancel()
        queue.persist(persistFile)
        transferClients.values.forEach { runCatching { it.close() } }
        transferClients.clear()
        runCatching { client?.close() }
        client = null
    }

    private companion object {
        const val MAX_RENAME_ATTEMPTS = 500
    }
}
