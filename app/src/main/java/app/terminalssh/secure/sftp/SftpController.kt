package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.net.Uri
import app.terminalssh.secure.ssh.SshSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    scope: CoroutineScope,
    private val workspaceStore: SftpWorkspaceStore,
    /**
     * Bytes per second ceiling, re-read per transfer so a settings change takes effect on
     * the next file rather than needing a restart. 0 means unlimited.
     */
    private val rateLimitBytesPerSecond: () -> Long = { 0L },
    /**
     * Whether the queue may start work right now. False holds everything QUEUED — used by
     * the Wi-Fi-only setting, so a large upload cannot quietly spend a mobile data plan.
     * Queued transfers resume on their own once this goes true again.
     */
    private val mayStartTransfers: () -> Boolean = { true },
    private val persistentDir: File = cacheDir,
) {
    private val parentScope = scope
    private val controllerJob = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + controllerJob)
    private val closeMutex = Mutex()
    @Volatile private var closing = false
    private var closed = false

    data class BrowserState(
        val path: String = RemotePath.ROOT,
        /** Already sorted and filtered for display; [rawEntries] is what the server sent. */
        val entries: List<RemoteEntry> = emptyList(),
        val loading: Boolean = false,
        val errorKind: TransferErrorKind? = null,
        val rawEntries: List<RemoteEntry> = emptyList(),
        val sortMode: EntrySort.Mode = EntrySort.Mode.NAME,
        val sortDescending: Boolean = false,
        val showHidden: Boolean = false,
    ) {
        /** How many entries the hidden-files toggle is currently keeping out of sight. */
        val hiddenCount: Int get() = if (showHidden) 0 else rawEntries.count { EntrySort.isHidden(it) }
    }

    /** Re-sorts and re-filters what is already loaded. No network call. */
    private fun BrowserState.withView(
        mode: EntrySort.Mode = sortMode,
        descending: Boolean = sortDescending,
        hidden: Boolean = showHidden,
    ): BrowserState = copy(
        entries = EntrySort.apply(rawEntries, mode, descending, hidden),
        sortMode = mode,
        sortDescending = descending,
        showHidden = hidden,
    )

    /** Changes the order; re-selecting the same column flips direction. */
    fun setSortMode(mode: EntrySort.Mode) {
        _browser.value = _browser.value.let {
            it.withView(mode = mode, descending = if (it.sortMode == mode) !it.sortDescending else false)
        }
    }

    fun setShowHidden(show: Boolean) {
        _browser.value = _browser.value.withView(hidden = show)
    }

    fun toggleShowHidden() = setShowHidden(!_browser.value.showHidden)

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

    /** Session-isolated durable ledger; legacy shared ledgers are never uploaded to an unknown host. */
    private val persistFile = TransferStatePaths.queueFile(persistentDir, session.profile.id, session.id)
    val queue = TransferQueue.fromPersisted(persistFile)

    /** Explicit recovery never starts workers. The user reviews paused items in Files. */
    suspend fun importRecoveredQueue(file: File): Int = withContext(Dispatchers.IO) {
        closeMutex.withLock {
        synchronized(TransferRecoveryStore.importLock) {
            check(!closing && !closed) { "Session is closing" }
            check(TransferRecoveryStore(persistentDir).matches(file, session.profile)) { "Recovery server mismatch" }
            check(file.length() <= 8_388_608L) { "Recovery ledger exceeds limit" }
            val raw = org.json.JSONArray(file.readText())
            for (index in 0 until raw.length()) {
                val item = raw.getJSONObject(index)
                check(item.getString("id").matches(Regex("[A-Za-z0-9_-]{1,128}")))
                check(item.getString("remotePath").startsWith("/"))
                check(android.net.Uri.parse(item.getString("localUri")).scheme == "content")
                TransferDirection.valueOf(item.getString("direction"))
                TransferState.valueOf(item.getString("state"))
            }
            val recovered = TransferQueue.fromPersisted(file).transfers.value.filter { !it.state.isTerminal }
            check(recovered.isNotEmpty()) { "No recoverable transfers" }
            queue.importPaused(recovered)
            // Commit the new owner before removing the previous ledger. A failed write
            // keeps the original on disk and leaves the copies paused (never overwrite).
            queue.persist(persistFile)
            if (!file.delete()) throw IOException("Cannot retire recovered ledger")
            recovered.size
        }
        }
    }

    private val _uploadConflict = MutableStateFlow<UploadConflict?>(null)
    val uploadConflict: StateFlow<UploadConflict?> = _uploadConflict.asStateFlow()

    // Written under `pumpLock`-independent monitor `this`; @Volatile so the close path
    // below (a different thread) cannot observe a stale reference and leak the channel.
    @Volatile
    private var client: SftpClient? = null

    private val _persistenceFailed = MutableStateFlow(false)
    val persistenceFailed: StateFlow<Boolean> = _persistenceFailed.asStateFlow()

    private fun writeState(write: () -> Unit) {
        try {
            write()
            _persistenceFailed.value = false
        } catch (failure: IOException) {
            // Expose a recoverability failure; teardown still retries and throws if
            // its final durable snapshot cannot be committed.
            _persistenceFailed.value = true
            android.util.Log.e("TransferState", "Transfer state persistence failed")
        }
    }

    /** Auto-persist queue whenever it changes so pending transfers survive process death. */
    private val persistJob = scope.launch {
        queue.transfers.collect {
            withContext(Dispatchers.IO) {
                synchronized(TransferRecoveryStore.importLock) {
                    writeState {
                        if (queue.transfers.value.any { t -> !t.state.isTerminal }) {
                            queue.persist(persistFile)
                        } else {
                            queue.clearPersisted(persistFile)
                        }
                    }
                }
            }
        }
    }

    /** Persist transfer history to disk. */
    private val historyFile = TransferStatePaths.historyFile(persistentDir, session.profile.id, session.id)

    init {
        scope.launch(Dispatchers.IO) {
            writeState { TransferStatePaths.registerSession(persistentDir, session.profile.id, session.id, session.profile) }
        }
        queue.loadHistory(historyFile)
        scope.launch {
            queue.history.collect {
                withContext(Dispatchers.IO) {
                    writeState { queue.persistHistory(historyFile) }
                }
            }
        }
    }

    /**
     * Per-transfer SftpClient instances for parallel transfers. Each concurrent
     * transfer gets its own ChannelSftp channel so they don't block each other.
     */
    private val transferClients = ConcurrentHashMap<String, SftpClient>()

    /** Thread-safe cancellation flags keyed by transfer id. */
    private val cancelled = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * True once [openHome] has been called for this controller's lifetime. Lets the UI
     * layer avoid re-navigating to the home directory — and losing the user's current
     * browsed path — every time the Files tab is revisited for a still-live session.
     */
    var hasOpened: Boolean = false
        private set

    private suspend fun client(): SftpClient = withContext(Dispatchers.IO) {
        synchronized(this@SftpController) {
            check(!closing) { "SFTP controller is closing" }
            client?.let { return@withContext it }
            val opened = session.openSftp() ?: throw IllegalStateException("session is not connected")
            client = opened
            opened
        }
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
                    // Carry the user's sort and hidden-files choice across navigation;
                    // resetting them on every directory change would make both useless.
                    val previous = _browser.value
                    BrowserState(
                        path = RemotePath.normalize(path),
                        loading = false,
                        rawEntries = entries,
                        sortMode = previous.sortMode,
                        sortDescending = previous.sortDescending,
                        showHidden = previous.showHidden,
                    ).let { it.copy(entries = EntrySort.apply(entries, it.sortMode, it.sortDescending, it.showHidden)) }
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
                val sourceFingerprint = sftp.editFingerprint(entry.path)
                val guard = sftp.destinationGuard(target)
                temp.outputStream().use { sink -> sftp.download(entry.path, sink, resumeFrom = 0L) {} }
                check(temp.length() == sourceFingerprint.sizeBytes &&
                    temp.inputStream().use { SyncComparison.sha256(it) }.joinToString("") { "%02x".format(it) } ==
                    sourceFingerprint.sha256) { "Source changed while copying" }
                sftp.requireFingerprint(entry.path, sourceFingerprint)
                temp.inputStream().use { source -> sftp.atomicUpload(source, target, beforeCommit = guard) }
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
            val exists = runCatching {
                withContext(Dispatchers.IO) { client().exists(remoteFilePath) }
            }.getOrElse { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                showBrowserError(failure)
                return@launch
            }
            if (exists) {
                val renamed = runCatching { nonCollidingName(RemotePath.parent(remoteFilePath), displayName) }
                    .getOrElse { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        showBrowserError(failure)
                        return@launch
                    }
                val renamedPath = RemotePath.join(RemotePath.parent(remoteFilePath), renamed)
                enqueueUploadNow(android.net.Uri.fromFile(localFile), renamed, renamedPath)
            } else {
                enqueueUploadNow(android.net.Uri.fromFile(localFile), displayName, remoteFilePath)
            }
        }
    }

    /**
     * Downloads a remote file as raw bytes, refusing anything past [maxBytes].
     *
     * The limit is enforced on the stream, not on the stat size the server reports:
     * stat-then-read is a race, and a server can always send more than it advertised.
     */
    suspend fun downloadFileBytes(
        remotePath: String,
        maxBytes: Long = BoundedImage.MAX_PREVIEW_BYTES,
    ): Result<ByteArray> =
        runCatching {
            withContext(Dispatchers.IO) {
                val sftp = client()
                val baos = java.io.ByteArrayOutputStream()
                val limited = LimitedOutputStream(baos, maxBytes)
                sftp.download(remotePath, limited, 0L) {}
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
    private val editFingerprints = ConcurrentHashMap<String, EditFingerprint>()

    /** Downloads text AND records the mtime for concurrent-edit detection. */
    suspend fun downloadFileTextForEdit(remotePath: String, maxBytes: Long = 512_000): Result<Pair<String, Long>> {
        return runCatching {
            withContext(Dispatchers.IO) {
                // A failed or oversized reopen must invalidate an earlier editable snapshot.
                editFingerprints.remove(remotePath)
                val sftp = client()
                val loaded = sftp.downloadTextResult(remotePath, maxBytes)
                loaded.requireEditable()
                val baseline = sftp.editFingerprint(remotePath)
                val text = loaded.content
                check(EditConflict.sha256(text) == baseline.sha256) { "Remote file changed while opening" }
                sftp.requireFingerprint(remotePath, baseline)
                editFingerprints[remotePath] = baseline
                text to baseline.mtimeEpochSeconds
            }
        }
    }

    /** Returns true when the remote file changed since this editor loaded it. */
    suspend fun checkFileTextConflict(remotePath: String, maxBytes: Long = 512_000): Result<Boolean> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val saved = editFingerprints[remotePath]
                    ?: error("no fingerprint recorded for $remotePath")
                val sftp = client()
                val currentMtime = sftp.mtime(remotePath)
                val currentSize = sftp.size(remotePath)

                // Only pay for a re-read when stat cannot already prove a change. When it
                // can, we are done; when it cannot, the hash is the only thing that
                // separates "identical" from "same length, different bytes".
                val currentSha = if (EditConflict.statProvesChange(saved, currentMtime, currentSize)) {
                    null
                } else {
                    sftp.downloadTextResult(remotePath, maxBytes).let { loaded ->
                        loaded.requireEditable()
                        EditConflict.sha256(loaded.content)
                    }
                }

                when (EditConflict.verdict(saved, currentMtime, currentSize, currentSha)) {
                    ConflictVerdict.UNCHANGED -> false
                    ConflictVerdict.CHANGED -> true
                    // Unreadable remote is not permission to overwrite it.
                    ConflictVerdict.UNKNOWN -> error("cannot verify remote state for $remotePath")
                }
            }
        }
    }

    /** No caller, including force-save, may write a partial or failed editor load. */
    internal suspend fun writeOpenedFileText(
        remotePath: String,
        text: String,
        forceOverwrite: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val opened = editFingerprints[remotePath] ?: error("No complete editable snapshot")
        val sftp = client()
        // Force is only authorized by the separate confirmation action, and still rejects
        // changes made while this upload is staging. It never bypasses failed reads.
        val expected = if (forceOverwrite) sftp.editFingerprint(remotePath) else opened
        sftp.requireFingerprint(remotePath, expected)
        val bytes = text.toByteArray(Charsets.UTF_8)
        try {
            bytes.inputStream().use { source ->
                sftp.atomicUpload(source, remotePath, beforeCommit = {
                    sftp.requireFingerprint(remotePath, expected)
                })
            }
        } finally { bytes.fill(0) }
    }

    /** The caller keeps its editor open until the verified replacement succeeds. */
    suspend fun uploadFileText(remotePath: String, text: String, forceOverwrite: Boolean = false): Result<Unit> {
        val result = runCatching { writeOpenedFileText(remotePath, text, forceOverwrite) }
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        if (result.isSuccess) {
            editFingerprints.remove(remotePath)
            refresh()
        } else {
            showBrowserError(result.exceptionOrNull()!!)
        }
        return result
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
                val guard = sftp.destinationGuard(remotePath)
                java.io.FileInputStream(zipFile).use { inp ->
                    sftp.atomicUpload(inp, remotePath, beforeCommit = guard)
                }
                zipFile.delete()
                remotePath
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    // ---- one-way sync (#28/#29) ----

    // Bookmarks and presets are host-scoped durable metadata, independent of sessions.
    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    data class SyncPreset(
        val id: String,
        val name: String,
        val localDir: String,
        val remoteDir: String,
        val deleteRemote: Boolean = false,
    )

    private val _syncPresets = MutableStateFlow<List<SyncPreset>>(emptyList())
    val syncPresets: StateFlow<List<SyncPreset>> = _syncPresets.asStateFlow()

    private val workspaceMutex = Mutex()

    init {
        scope.launch {
            try {
                workspaceMutex.withLock {
                    publishWorkspace(withContext(Dispatchers.IO) { workspaceStore.load() })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showBrowserError(failure)
            }
        }
    }

    private fun publishWorkspace(state: SftpWorkspaceState) {
        _bookmarks.value = state.bookmarks
        _syncPresets.value = state.presets.map { SyncPreset(it.id, it.name, it.localDir, it.remoteDir, it.deleteRemote) }
    }

    private fun updateWorkspace(change: (SftpWorkspaceState) -> SftpWorkspaceState) {
        scope.launch {
            try {
                // Commit first: a failed disk write must not look like a saved bookmark.
                workspaceMutex.withLock {
                    publishWorkspace(withContext(Dispatchers.IO) { workspaceStore.update(change) })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showBrowserError(failure)
            }
        }
    }

    fun toggleBookmark(path: String) {
        val normalized = RemotePath.normalize(path)
        updateWorkspace { state ->
            state.copy(bookmarks = if (normalized in state.bookmarks) state.bookmarks - normalized else state.bookmarks + normalized)
        }
    }

    fun isBookmarked(path: String): Boolean = RemotePath.normalize(path) in _bookmarks.value

    fun saveSyncPreset(preset: SyncPreset) {
        updateWorkspace { state ->
            val durable = WorkspaceSyncPreset(preset.id, preset.name, preset.localDir, RemotePath.normalize(preset.remoteDir), preset.deleteRemote)
            state.copy(presets = state.presets.filter { it.id != preset.id } + durable)
        }
    }

    fun deleteSyncPreset(id: String) {
        updateWorkspace { state -> state.copy(presets = state.presets.filter { it.id != id }) }
    }

    // ---- folder size (#45) ----
    private val _folderSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val folderSizes: StateFlow<Map<String, Long>> = _folderSizes.asStateFlow()

    fun computeFolderSize(path: String) {
        scope.launch {
            val size = withContext(Dispatchers.IO) {
                runCatching { client().recursiveSize(path) }.getOrDefault(0L)
            }
            _folderSizes.value = _folderSizes.value + (path to size)
        }
    }

    /** Describes one change in a sync plan. */
    data class SyncAction(
        val relativePath: String,
        val kind: Kind,
        val localSize: Long = 0L,
        val remoteSize: Long = 0L,
        val localSnapshot: SyncFileSnapshot? = null,
        val remoteSnapshot: SyncFileSnapshot? = null,
        val snapshotVerified: Boolean = false,
    ) {
        enum class Kind { UPLOAD, DELETE_REMOTE, SKIP_IDENTICAL }
    }

    /**
     * Computes a one-way sync plan: local → remote. SAFE hashes every equal-sized
     * candidate; FAST is an explicit path/size/mtime heuristic, not a content guarantee.
     * Any remote stat/hash failure aborts planning rather than authorizing destructive work.
     */
    suspend fun computeSyncPlan(
        localDir: File,
        remoteDir: String,
        deleteRemote: Boolean = false,
        mode: SyncMode = SyncMode.SAFE,
    ): List<SyncAction> = withContext(Dispatchers.IO) {
        require(localDir.isDirectory) { "Sync source must be an existing directory" }
        val sftp = client()
        val localFiles = localDir.walkTopDown().onFail { _, failure -> throw failure }.filter { it.isFile }.associateBy {
            it.relativeTo(localDir).invariantSeparatorsPath
        }
        // listRecursive and metadata/hash reads propagate permission/network failures.
        val remoteFiles = sftp.listRecursive(remoteDir).associate { (path, relative) ->
            val size = sftp.size(path)
            check(size >= 0) { "Remote size could not be verified" }
            relative to Triple(path, size, sftp.mtime(path))
        }
        val actions = mutableListOf<SyncAction>()
        for ((relative, file) in localFiles) {
            val localSize = file.length()
            val remote = remoteFiles[relative]
            val localSnapshot = syncLocalSnapshot(file) ?: throw IOException("Sync source disappeared")
            val remoteSnapshot = remote?.let { syncRemoteSnapshot(sftp, it.first) }
            check(remote == null || remoteSnapshot != null) { "Remote disappeared during planning" }
            val identical = remoteSnapshot != null && SyncComparison.identical(
                mode, localSnapshot.bytes, localSnapshot.mtimeSeconds,
                remoteSnapshot.bytes, remoteSnapshot.mtimeSeconds,
                localHash = { localSnapshot.sha256.toByteArray(Charsets.US_ASCII) },
                remoteHash = { remoteSnapshot.sha256.toByteArray(Charsets.US_ASCII) },
            )
            actions += SyncAction(
                relative,
                if (identical) SyncAction.Kind.SKIP_IDENTICAL else SyncAction.Kind.UPLOAD,
                localSize,
                remote?.second ?: 0L,
                localSnapshot, remoteSnapshot, snapshotVerified = true,
            )
        }
        if (deleteRemote) {
            for ((relative, remote) in remoteFiles) {
                if (relative !in localFiles) actions += SyncAction(
                    relative, SyncAction.Kind.DELETE_REMOTE, 0L, remote.second,
                    remoteSnapshot = syncRemoteSnapshot(sftp, remote.first), snapshotVerified = true,
                )
            }
        }
        actions.sortedBy { it.relativePath }
    }

    /**
     * Executes a sync plan: uploads new/changed files, deletes remote-only files.
     */
    private fun syncLocalSnapshot(file: File): SyncFileSnapshot? {
        if (!file.exists()) return null
        check(file.isFile) { "Sync source is no longer a regular file" }
        val size = file.length()
        val mtime = file.lastModified() / 1000L
        val hash = file.inputStream().use { ContentIdentity.hex(SyncComparison.sha256(it)) }
        check(file.length() == size && file.lastModified() / 1000L == mtime) { "Sync source changed during inspection" }
        return SyncFileSnapshot(size, mtime, hash)
    }

    private fun syncRemoteSnapshot(sftp: SftpClient, path: String): SyncFileSnapshot? {
        if (!sftp.exists(path)) return null
        val size = sftp.size(path)
        check(size >= 0) { "Remote size could not be verified" }
        val mtime = sftp.mtime(path)
        val hash = ContentIdentity.hex(sftp.sha256(path))
        check(sftp.size(path) == size && sftp.mtime(path) == mtime) { "Remote changed during inspection" }
        return SyncFileSnapshot(size, mtime, hash)
    }

    suspend fun executeSyncPlan(localDir: File, remoteDir: String, actions: List<SyncAction>) {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val root = localDir.canonicalFile
            // Validate the entire plan before starting any destructive operation.
            for (action in actions) {
                SyncPlanGuard.requireRelativePath(action.relativePath)
                check(action.snapshotVerified) { "Sync plan must be recomputed before execution" }
                val file = File(root, action.relativePath).canonicalFile
                check(file.path.startsWith(root.path + File.separator)) { "Sync source escapes its root" }
                SyncPlanGuard.requireUnchanged(action.localSnapshot, syncLocalSnapshot(file))
                SyncPlanGuard.requireUnchanged(action.remoteSnapshot,
                    syncRemoteSnapshot(sftp, RemotePath.join(remoteDir, action.relativePath)))
            }
            for (action in actions) {
                val localFile = File(root, action.relativePath).canonicalFile
                val remotePath = RemotePath.join(remoteDir, action.relativePath)
                SyncPlanGuard.requireUnchanged(action.localSnapshot, syncLocalSnapshot(localFile))
                SyncPlanGuard.requireUnchanged(action.remoteSnapshot, syncRemoteSnapshot(sftp, remotePath))
                when (action.kind) {
                    SyncAction.Kind.UPLOAD -> {
                        val snapshot = File.createTempFile("sync-source-", ".tmp", cacheDir)
                        try {
                            localFile.inputStream().use { input -> snapshot.outputStream().use { input.copyTo(it) } }
                            val stagedHash = snapshot.inputStream().use { ContentIdentity.hex(SyncComparison.sha256(it)) }
                            check(stagedHash == action.localSnapshot?.sha256) { "Sync source changed before upload" }
                            sftp.ensureDirectories(RemotePath.parent(remotePath))
                            snapshot.inputStream().use { input ->
                                sftp.atomicUpload(input, remotePath, beforeCommit = {
                                    SyncPlanGuard.requireUnchanged(action.remoteSnapshot, syncRemoteSnapshot(sftp, remotePath))
                                })
                            }
                        } finally {
                            if (!snapshot.delete() && snapshot.exists()) throw IOException("Could not remove sync staging file")
                        }
                    }
                    SyncAction.Kind.DELETE_REMOTE -> sftp.delete(remotePath, false)
                    SyncAction.Kind.SKIP_IDENTICAL -> Unit
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
    /**
     * A decision the user asked to apply to every remaining collision in this batch.
     *
     * Queueing a hundred files used to mean answering the same dialog a hundred times,
     * and the answer is almost always the same for the whole batch. Cleared by
     * [clearConflictPolicy] so a later, unrelated upload asks again rather than silently
     * inheriting a choice made minutes ago.
     */
    private val standingConflictPolicy = java.util.concurrent.atomic.AtomicReference<ConflictResolution?>(null)

    fun setConflictPolicy(resolution: ConflictResolution?) = standingConflictPolicy.set(resolution)

    fun clearConflictPolicy() = standingConflictPolicy.set(null)

    fun enqueueUpload(source: Uri, displayName: String, remoteDirectory: String) {
        scope.launch {
            val remotePath = RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(displayName))
            val collides = runCatching {
                withContext(Dispatchers.IO) { client().exists(remotePath) }
            }.getOrElse { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                showBrowserError(failure)
                return@launch
            }
            if (!collides) {
                enqueueUploadNow(source, displayName, remotePath)
                return@launch
            }
            when (standingConflictPolicy.get()) {
                null, ConflictResolution.CANCEL ->
                    _uploadConflict.value = UploadConflict(source, displayName, remoteDirectory, remotePath)
                ConflictResolution.OVERWRITE ->
                    enqueueUploadNow(source, displayName, remotePath)
                ConflictResolution.SKIP -> Unit
                ConflictResolution.RENAME -> {
                    val renamed = runCatching { nonCollidingName(remoteDirectory, displayName) }
                        .getOrElse { failure ->
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            showBrowserError(failure)
                            return@launch
                        }
                    enqueueUploadNow(
                        source,
                        renamed,
                        RemotePath.join(remoteDirectory, RemotePath.sanitizeDownloadName(renamed)),
                    )
                }
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
    /**
     * @param applyToAll remembers [resolution] for the rest of this batch, so a large
     *   queue is answered once instead of per file.
     */
    fun resolveConflict(resolution: ConflictResolution, applyToAll: Boolean = false) {
        val conflict = _uploadConflict.value ?: return
        _uploadConflict.value = null
        if (applyToAll) standingConflictPolicy.set(resolution)
        when (resolution) {
            ConflictResolution.OVERWRITE ->
                enqueueUploadNow(conflict.source, conflict.displayName, conflict.remotePath)
            ConflictResolution.RENAME -> scope.launch {
                val renamed = runCatching { nonCollidingName(conflict.remoteDirectory, conflict.displayName) }
                    .getOrElse { failure ->
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        showBrowserError(failure)
                        return@launch
                    }
                val renamedPath = RemotePath.join(conflict.remoteDirectory, RemotePath.sanitizeDownloadName(renamed))
                enqueueUploadNow(conflict.source, renamed, renamedPath)
            }
            ConflictResolution.SKIP, ConflictResolution.CANCEL -> Unit
        }
    }

    /**
     * `file.txt` -> `file (1).txt` -> `file (2).txt`, probing the server for each
     * candidate until one is free. Fails after [MAX_RENAME_ATTEMPTS] probes: an
     * untested timestamp suffix is not evidence that overwriting it is safe.
     */
    private suspend fun nonCollidingName(remoteDirectory: String, displayName: String): String {
        val sanitized = RemotePath.sanitizeDownloadName(displayName)
        for (n in 1..MAX_RENAME_ATTEMPTS) {
            val candidate = RemotePath.withCollisionSuffix(sanitized, n)
            val taken = withContext(Dispatchers.IO) { client().exists(RemotePath.join(remoteDirectory, candidate)) }
            if (!taken) return candidate
        }
        error("No verified unused destination name")
    }

    fun pause(id: String) {
        cancelled += id
        queue.pause(id)
    }

    fun resume(id: String) {
        // Keep the interrupted attempt marked until it actually exits. The scheduler
        // removes this marker only when the next worker owns the transfer.
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
    private val schedulerDelegate = lazy {
        TransferScheduler(scope, queue, { !closing && session.state.value.isLive && mayStartTransfers() }) { transfer ->
            cancelled -= transfer.id
            try {
                runTransfer(transfer)
            } finally {
                transferClients.remove(transfer.id)?.close()
            }
        }
    }

    private val scheduler by schedulerDelegate

    init {
        scope.launch {
            session.state.collect { state ->
                if (state.isLive) onSchedulingConditionsChanged()
            }
        }
    }

    private fun pump() = scheduler.wake()

    /** Call after network capabilities or the Wi-Fi-only setting changes. */
    fun onSchedulingConditionsChanged() = pump()

    private suspend fun runTransfer(transfer: Transfer) {
        // Each concurrent transfer gets its own SFTP channel for true parallel I/O.
        val transferClient = runCatching {
            withContext(Dispatchers.IO) { session.openSftp() }
        }.getOrNull()
        if (transferClient != null) {
            transferClients[transfer.id] = transferClient
            if (closing) {
                transferClients.remove(transfer.id)?.close()
                throw CancellationException("SFTP controller is closing")
            }
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
        // Verify before the channel is closed. Doing it after left verifyIntegrity
        // holding a closed client, so every check silently degraded to UNVERIFIED and
        // the feature never actually ran.
        val integrity = if (result.isSuccess && transfer.id !in cancelled) {
            runCatching {
                withContext(Dispatchers.IO) { verifyIntegrity(transferClient, transfer) }
            }.getOrDefault(IntegrityResult.UNVERIFIED)
        } else {
            IntegrityResult.UNVERIFIED
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
            result.isSuccess -> {
                if (integrity == IntegrityResult.MISMATCH) {
                    queue.fail(transfer.id, TransferErrorKind.INTEGRITY_MISMATCH)
                } else {
                    queue.markCompleted(transfer.id)
                }
            }
            else -> queue.fail(transfer.id, SftpClient.classify(result.exceptionOrNull()!!))
        }
    }

    // ---- Phase 3: second pane (24) ----

    private val _secondPane = MutableStateFlow<BrowserState?>(null)

    /**
     * An optional second directory view on the same session.
     *
     * Moving a file between two distant paths meant navigating there, remembering, coming
     * back. Two panes make it one drag. Null means the pane is closed, which is also the
     * signal the UI uses to fall back to the single-pane layout on a phone.
     */
    val secondPane: StateFlow<BrowserState?> = _secondPane.asStateFlow()

    fun openSecondPane(path: String = _browser.value.path) {
        _secondPane.value = BrowserState(path = RemotePath.normalize(path), loading = true)
        navigateSecondPane(path)
    }

    fun closeSecondPane() {
        _secondPane.value = null
    }

    fun navigateSecondPane(path: String) {
        scope.launch {
            val current = _secondPane.value ?: return@launch
            _secondPane.value = current.copy(loading = true, errorKind = null)
            val result = runCatching { withContext(Dispatchers.IO) { client().list(path) } }
            _secondPane.value = result.fold(
                onSuccess = { entries ->
                    val previous = _secondPane.value ?: current
                    BrowserState(
                        path = RemotePath.normalize(path),
                        loading = false,
                        rawEntries = entries,
                        sortMode = previous.sortMode,
                        sortDescending = previous.sortDescending,
                        showHidden = previous.showHidden,
                    ).let { it.copy(entries = EntrySort.apply(entries, it.sortMode, it.sortDescending, it.showHidden)) }
                },
                onFailure = { failure ->
                    (_secondPane.value ?: current).copy(loading = false, errorKind = SftpClient.classify(failure))
                },
            )
        }
    }

    /** Copies from whichever pane holds [entry] into the other one, on this session. */
    suspend fun copyBetweenPanes(entry: RemoteEntry, toSecondPane: Boolean): Result<String> = runCatching {
        val destination = if (toSecondPane) {
            _secondPane.value?.path ?: error("the second pane is not open")
        } else {
            _browser.value.path
        }
        withContext(Dispatchers.IO) {
            val sftp = client()
            val name = nonCollidingName(destination, entry.name)
            val target = RemotePath.join(destination, RemotePath.sanitizeDownloadName(name))
            // cp -a on the server: never round-trips the bytes through the phone.
            sftp.exec(RemoteOps.duplicateCommand(entry.path, target)) ?: error("the server did not allow copy")
            target
        }
    }.onSuccess {
        refresh()
        _secondPane.value?.let { navigateSecondPane(it.path) }
    }

    // ---- Phase 3: browsing, recovery and previews (22-30) ----

    /**
     * Recoverable delete: moves into the trash instead of unlinking.
     *
     * Falls back to a real delete only when the entry is already inside the trash —
     * emptying it has to actually remove things.
     */
    suspend fun trash(entry: RemoteEntry): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val home = sftp.home()
            if (RemoteTrash.isInTrash(entry.path, home)) {
                sftp.delete(entry.path, entry.isDirectory)
                return@withContext entry.path
            }
            val dir = RemoteTrash.trashDir(home)
            if (!sftp.exists(dir)) sftp.makeDirectory(dir)
            val target = RemoteTrash.trashedPath(home, entry.path, System.currentTimeMillis())
            sftp.rename(entry.path, target)
            target
        }
    }.onSuccess { refresh() }

    /** Everything currently recoverable, newest first. */
    suspend fun listTrash(): Result<List<Pair<RemoteEntry, String>>> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val dir = RemoteTrash.trashDir(sftp.home())
            if (!sftp.exists(dir)) return@withContext emptyList()
            sftp.list(dir)
                .mapNotNull { e -> RemoteTrash.parseTrashedName(e.name)?.let { (stamp, name) -> Triple(e, name, stamp) } }
                .sortedByDescending { it.third }
                .map { it.first to it.second }
        }
    }

    /** Puts a trashed entry back, into [destinationDir] or the current directory. */
    suspend fun restoreFromTrash(entry: RemoteEntry, destinationDir: String? = null): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val original = RemoteTrash.parseTrashedName(entry.name)?.second
                ?: error("not a trashed entry")
            val dir = destinationDir ?: _browser.value.path
            val target = RemotePath.join(dir, nonCollidingName(dir, original))
            sftp.rename(entry.path, target)
            target
        }
    }.onSuccess { refresh() }

    /**
     * First [maxBytes] of a file without downloading the rest (#28).
     *
     * Opening a one-gigabyte log used to mean transferring all of it. This reads a window
     * and decodes it with the file's own charset, so a non-UTF-8 config is readable too.
     */
    suspend fun previewHead(path: String, maxBytes: Long = PREVIEW_WINDOW): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val out = java.io.ByteArrayOutputStream()
            runCatching {
                client().download(path, LimitedOutputStream(out, maxBytes), 0L) {}
            }.exceptionOrNull()?.let { if (it !is TransferTooLargeException) throw it }
            val bytes = out.toByteArray()
            TextEncoding.decode(bytes, TextEncoding.detect(bytes))
        }
    }

    /** Last [maxBytes] of a file, for the end of a long log. */
    suspend fun previewTail(path: String, maxBytes: Long = PREVIEW_WINDOW): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val size = sftp.size(path)
            val from = if (size > maxBytes) size - maxBytes else 0L
            val out = java.io.ByteArrayOutputStream()
            runCatching {
                sftp.download(path, LimitedOutputStream(out, maxBytes), from) {}
            }.exceptionOrNull()?.let { if (it !is TransferTooLargeException) throw it }
            val bytes = out.toByteArray()
            TextEncoding.decode(bytes, TextEncoding.detect(bytes))
        }
    }

    /**
     * Copies straight from another server to this one (#25), without staging the whole
     * file on the phone.
     *
     * A pipe rather than a temp file: a 4 GB database dump moved between two servers
     * should not need 4 GB of free space on a handset, and the phone is only the relay.
     */
    suspend fun copyFromRemote(
        sourceController: SftpController,
        sourceEntry: RemoteEntry,
        destinationDir: String,
    ): Result<String> = runCatching {
        require(!sourceEntry.isDirectory) { "only files can be copied between servers" }
        withContext(Dispatchers.IO) {
            val source = sourceController.client()
            val destination = client()
            val name = nonCollidingName(destinationDir, sourceEntry.name)
            val target = RemotePath.join(destinationDir, RemotePath.sanitizeDownloadName(name))

            val sourceFingerprint = source.editFingerprint(sourceEntry.path)
            val guard = destination.destinationGuard(target)
            val sourceFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            val streamedDigest = java.security.MessageDigest.getInstance("SHA-256")
            val pipeIn = java.io.PipedInputStream(PIPE_BUFFER)
            val pipeOut = java.io.PipedOutputStream(pipeIn)
            val pump = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    pipeOut.use { out -> source.download(sourceEntry.path, out, 0L) {} }
                } catch (failure: Exception) {
                    sourceFailure.set(failure)
                    try { pipeOut.close() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                }
            }
            try {
                pipeIn.use { input ->
                    java.security.DigestInputStream(input, streamedDigest).use { checkedInput ->
                        destination.atomicUpload(checkedInput, target, beforeCommit = {
                            sourceFailure.get()?.let { throw java.io.IOException("Source transfer failed", it) }
                            check(streamedDigest.digest().joinToString("") { "%02x".format(it) } ==
                                sourceFingerprint.sha256) { "Incomplete or changed source transfer" }
                            source.requireFingerprint(sourceEntry.path, sourceFingerprint)
                            guard()
                        })
                    }
                }
            } finally {
                pipeIn.close()
                pipeOut.close()
                pump.cancel()
            }
            target
        }
    }.onSuccess { refresh() }

    /**
     * Downloads [entry] into the share staging directory and returns the local copy, so
     * the UI can hand it to another app (#30). Bounded: a share is a convenience, not a
     * reason to fill the cache with a database dump.
     */
    suspend fun stageForShare(entry: RemoteEntry, cacheRoot: File): Result<File> = runCatching {
        require(!entry.isDirectory) { "a directory cannot be shared as a file" }
        withContext(Dispatchers.IO) {
            val dir = File(cacheRoot, "shared").apply { mkdirs() }
            val target = File(dir, RemotePath.sanitizeDownloadName(entry.name))
            java.io.FileOutputStream(target).use { out ->
                client().download(entry.path, LimitedOutputStream(out, MAX_SHARE_BYTES), 0L) {}
            }
            target
        }
    }

    // ---- Phase 2: server-side operations (13-21) ----

    /**
     * Result of a shell operation. [output] is whatever the command printed; a null
     * result means the server would not run it at all, which callers must surface rather
     * than treat as success — "exec is disabled" and "the command did nothing" look
     * identical otherwise.
     */
    suspend fun runRemote(command: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            client().exec(command) ?: error("the server did not allow this command")
        }
    }

    /** Last lines of a remote file, for the follow view. */
    suspend fun tailFile(path: String, lines: Int = 200): Result<String> =
        runRemote(RemoteOps.tailCommand(path, lines))

    /**
     * Lines appended since [fromOffset], plus the new offset to poll from next time.
     * Returns an empty string when nothing was appended.
     */
    suspend fun tailSince(path: String, fromOffset: Long): Result<Pair<String, Long>> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            val size = sftp.size(path)
            // A file that shrank was rotated: start over rather than reading from an
            // offset that now points into the middle of a different file.
            val from = if (size in 0 until fromOffset) 0L else fromOffset
            if (size <= from) return@withContext "" to from
            val text = sftp.exec(RemoteOps.tailSinceCommand(path, from)) ?: error("exec unavailable")
            text to size
        }
    }

    /** Recursive fixed-string search under [dir]. */
    suspend fun searchInFiles(
        dir: String,
        needle: String,
        ignoreCase: Boolean = true,
    ): Result<List<RemoteOps.SearchHit>> = runCatching {
        require(needle.isNotBlank()) { "empty search" }
        withContext(Dispatchers.IO) {
            val out = client().exec(RemoteOps.grepCommand(dir, needle, ignoreCase))
                ?: error("the server did not allow search")
            RemoteOps.parseGrepOutput(out)
        }
    }

    /** Runs [template] against every selected path, stopping at the first failure. */
    suspend fun runOnSelection(template: String, entries: List<RemoteEntry>): Result<String> {
        val command = RemoteOps.buildSelectionCommand(template, entries.map { it.path })
            ?: return Result.failure(IllegalArgumentException("nothing to run"))
        return runRemote(command).onSuccess { refresh() }
    }

    /** Unpacks an archive on the server, next to itself unless [destDir] says otherwise. */
    suspend fun extractArchive(entry: RemoteEntry, destDir: String? = null): Result<String> {
        val target = destDir ?: RemotePath.parentOf(entry.path)
        val command = RemoteOps.extractCommand(entry.path, target)
            ?: return Result.failure(IllegalArgumentException("not a supported archive"))
        return runRemote(command).onSuccess { refresh() }
    }

    fun canExtract(entry: RemoteEntry): Boolean = !entry.isDirectory && RemoteOps.isExtractable(entry.name)

    /** Changes owner, and group when given. */
    suspend fun chown(
        entry: RemoteEntry,
        owner: String,
        group: String? = null,
        recursive: Boolean = false,
    ): Result<String> {
        val command = RemoteOps.chownCommand(entry.path, owner, group, recursive)
            ?: return Result.failure(IllegalArgumentException("invalid user or group name"))
        return runRemote(command).onSuccess { refresh() }
    }

    suspend fun createSymlink(targetPath: String, linkPath: String): Result<String> =
        runRemote(RemoteOps.symlinkCommand(targetPath, linkPath)).onSuccess { refresh() }

    suspend fun createHardLink(targetPath: String, linkPath: String): Result<String> =
        runRemote(RemoteOps.hardLinkCommand(targetPath, linkPath)).onSuccess { refresh() }

    suspend fun createEmptyFile(path: String): Result<String> =
        runRemote(RemoteOps.touchCommand(path)).onSuccess { refresh() }

    suspend fun duplicate(entry: RemoteEntry): Result<String> {
        val copyName = nonCollidingName(RemotePath.parentOf(entry.path), entry.name)
        val destination = RemotePath.join(RemotePath.parentOf(entry.path), copyName)
        return runRemote(RemoteOps.duplicateCommand(entry.path, destination)).onSuccess { refresh() }
    }

    /** Diffs the remote file against [newText] so a save can be reviewed before it lands. */
    suspend fun diffAgainstRemote(
        remotePath: String,
        newText: String,
        maxBytes: Long = 512_000,
    ): Result<TextDiff.Result> = runCatching {
        withContext(Dispatchers.IO) {
            TextDiff.diff(client().downloadText(remotePath, maxBytes), newText)
        }
    }

    /**
     * Applies a rename plan. Stops at the first failure and reports how far it got, so a
     * partially applied batch is visible rather than silently mixed.
     */
    suspend fun applyBatchRename(directory: String, plan: BatchRename.Plan): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            val sftp = client()
            var done = 0
            for (change in plan.applicable) {
                sftp.rename(
                    RemotePath.join(directory, change.from),
                    RemotePath.join(directory, change.to),
                )
                done++
            }
            done
        }
    }.onSuccess { refresh() }

    private fun stagingFile(transfer: Transfer): File = File(cacheDir, "sftp-download-${transfer.id}")

    enum class IntegrityResult { MATCH, MISMATCH, UNVERIFIED }

    /**
     * Compares what the server has against what this device has, using the server's own
     * sha256 and a local hash of the same bytes.
     *
     * [IntegrityResult.UNVERIFIED] is not a failure. Servers that disable `exec`, and
     * files too large to be worth hashing twice, both land here; the transfer stands.
     * Only a hash that actually disagrees fails the transfer, because that is the one
     * case where reporting success would be a lie.
     */
    private fun verifyIntegrity(sftp: SftpClient?, transfer: Transfer): IntegrityResult {
        val client = sftp ?: return IntegrityResult.UNVERIFIED
        val size = transfer.totalBytes
        if (size <= 0L || size > MAX_VERIFY_BYTES) return IntegrityResult.UNVERIFIED

        val remote = client.remoteSha256(transfer.remotePath) ?: return IntegrityResult.UNVERIFIED
        val local = when (transfer.direction) {
            TransferDirection.DOWNLOAD -> {
                val staged = stagingFile(transfer)
                // On the success path the staging file is already copied out and deleted,
                // so a download is verified from the destination the user actually got.
                if (staged.exists()) sha256Of(staged.inputStream())
                else runCatching {
                    contentResolver.openInputStream(Uri.parse(transfer.localUri))?.use { sha256Of(it) }
                }.getOrNull()
            }
            TransferDirection.UPLOAD -> runCatching {
                contentResolver.openInputStream(Uri.parse(transfer.localUri))?.use { sha256Of(it) }
            }.getOrNull()
        } ?: return IntegrityResult.UNVERIFIED

        return if (local.equals(remote, ignoreCase = true)) IntegrityResult.MATCH else IntegrityResult.MISMATCH
    }

    private fun sha256Of(stream: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun download(sftp: SftpClient, transfer: Transfer) {
        val staging = stagingFile(transfer)
        // Pre-resume consistency check: trust transfer.transferredBytes only if the
        // staging file on disk actually holds that many bytes already. A mismatch (no
        // staging file, a shorter one than expected) means the recorded offset can't be
        // trusted, so restart this transfer from zero rather than risk corrupting the
        // resumed copy.
        val actualStagedBytes = if (staging.exists()) staging.length() else 0L
        // The staging file matching the recorded offset only proves the local half is
        // intact; it says nothing about the file those bytes came from. Re-stat the
        // remote too, or a file replaced between attempts gets its tail appended to the
        // old file's head and the user receives a corrupted download reported as success.
        val currentRemoteBytes = sftp.size(transfer.remotePath)
        check(currentRemoteBytes >= 0) { "Remote size could not be verified" }
        check(transfer.totalBytes < 0 || currentRemoteBytes == transfer.totalBytes) { "Remote file size changed" }
        val expectedHash = sftp.sha256(transfer.remotePath)
        val candidate = transfer.transferredBytes
        val resumeFrom = if (candidate > 0 && candidate <= currentRemoteBytes &&
            canTrustResume(candidate, actualStagedBytes) && staging.inputStream().use {
                ContentIdentity.matches(ContentIdentity.prefix(it, candidate), sftp.sha256Prefix(transfer.remotePath, candidate))
            }
        ) candidate else 0L
        if (resumeFrom == 0L) {
            if (transfer.transferredBytes != 0L) queue.resetProgress(transfer.id)
            staging.delete()
        }

        // A fresh, large download is worth splitting across channels; a resumed one is
        // not, because the ranges were planned against the original offsets.
        val plan = if (resumeFrom == 0L) MultipartPlan.planFor(transfer.totalBytes) else emptyList()
        if (plan.size > 1 && MultipartPlan.covers(plan, transfer.totalBytes)) {
            downloadMultipart(transfer, staging, plan)
        } else {
            downloadSingleStream(sftp, transfer, staging, resumeFrom)
        }
        // Verification happens before opening the user destination in truncate mode.
        check(staging.length() == currentRemoteBytes && staging.inputStream().use {
            ContentIdentity.matches(expectedHash, SyncComparison.sha256(it))
        }) { "Downloaded content changed or is incomplete" }
        check(ContentIdentity.matches(expectedHash, sftp.sha256(transfer.remotePath))) { "Remote changed during download" }
        deliverStagedDownload(transfer, staging)
    }

    /**
     * Downloads [plan]'s ranges concurrently, each on its own channel, into one
     * pre-sized file.
     *
     * Every part writes at its own absolute offset through its own RandomAccessFile
     * handle, so the parts never contend and a partial failure leaves a file that is
     * simply incomplete rather than interleaved. Any part failing fails the whole
     * transfer: a file assembled from some new ranges and some missing ones is exactly
     * the silent corruption the resume guard exists to prevent.
     */
    private suspend fun downloadMultipart(transfer: Transfer, staging: File, plan: List<ByteRange>) {
        staging.delete()
        java.io.RandomAccessFile(staging, "rw").use { it.setLength(transfer.totalBytes) }

        // One shared counter, advanced by each part's delta. Reporting
        // "completed parts + my own bytes" made every running part report only its own
        // progress, and markProgress takes the max — so a four-way split showed roughly
        // a quarter of the real figure until parts began finishing.
        val done = java.util.concurrent.atomic.AtomicLong(0L)
        coroutineScope {
            plan.map { range ->
                async(Dispatchers.IO) {
                    val channel = session.openSftp() ?: throw IllegalStateException("session is not connected")
                    try {
                        java.io.RandomAccessFile(staging, "rw").use { raf ->
                            raf.seek(range.start)
                            val limiter = RateLimiter(rateLimitBytesPerSecond())
                            val sink = RangeOutputStream(raf, range.length)
                            val throttled = if (limiter.unlimited) sink else ThrottledOutputStream(sink, limiter)
                            var reported = 0L
                            runCatching {
                                channel.download(transfer.remotePath, throttled, resumeFrom = range.start) { _ ->
                                    if (transfer.id in cancelled) throw InterruptedTransfer()
                                    val delta = sink.written - reported
                                    if (delta > 0) {
                                        reported = sink.written
                                        queue.markProgress(transfer.id, done.addAndGet(delta))
                                    }
                                }
                            }.exceptionOrNull()?.let { failure ->
                                // The range filling up is how a range download ends: JSch
                                // has no "read n bytes" call, so the stream stops it.
                                if (failure !is RangeCompleteException) throw failure
                            }
                            if (sink.written != range.length) {
                                throw java.io.IOException(
                                    "part ${range.index} got ${sink.written} of ${range.length} bytes",
                                )
                            }
                            // Settle any bytes the progress callback did not see.
                            val unreported = sink.written - reported
                            if (unreported > 0) queue.markProgress(transfer.id, done.addAndGet(unreported))
                        }
                    } finally {
                        runCatching { channel.close() }
                    }
                }
            }.awaitAll()
        }
    }

    private fun downloadSingleStream(sftp: SftpClient, transfer: Transfer, staging: File, resumeFrom: Long) {
        val limiter = RateLimiter(rateLimitBytesPerSecond())
        FileOutputStream(staging, resumeFrom > 0L).use { rawSink ->
            val sink = if (limiter.unlimited) rawSink else ThrottledOutputStream(rawSink, limiter)
            sftp.download(transfer.remotePath, sink, resumeFrom = resumeFrom) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }
    }

    /**
     * Copies the completed staging file to the user's chosen SAF destination, then cleans
     * up. Cleanup only happens here and on a genuine cancel (see runTransfer) — a paused
     * or failed transfer keeps its staging file so a later resume/retry can reuse the
     * bytes already on disk.
     */
    private fun deliverStagedDownload(transfer: Transfer, staging: File) {
        val uri = Uri.parse(transfer.localUri)
        // A queue restored after process death can hold a URI whose grant is gone. Say so
        // in terms the queue understands instead of letting SecurityException escape.
        if (!SafPermissions.isAccessible(contentResolver, uri)) {
            throw LocalUriUnavailableException(transfer.localUri)
        }
        val sink = contentResolver.openOutputStream(uri, "wt")
            ?: throw LocalUriUnavailableException(transfer.localUri)
        sink.use { out -> staging.inputStream().use { it.copyTo(out) } }
        staging.delete()
    }

    private fun upload(sftp: SftpClient, transfer: Transfer) {
        require(transfer.id.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid transfer identifier" }
        sftp.requireAtomicReplace()
        sftp.requirePrivateStagingDirectory(transfer.remotePath)
        val uri = Uri.parse(transfer.localUri)
        if (!SafPermissions.isAccessible(contentResolver, uri)) throw LocalUriUnavailableException(transfer.localUri)
        // Hash the source before modifying anything; keep the authorization across retries.
        val currentSourceHash = contentResolver.openInputStream(uri)?.use {
            ContentIdentity.hex(SyncComparison.sha256(it))
        } ?: throw LocalUriUnavailableException(transfer.localUri)
        val checkpointFile = File(persistentDir, "sftp-upload-${transfer.id}.checkpoint")
        val hadCheckpoint = checkpointFile.exists()
        val checkpoint = if (hadCheckpoint) UploadCheckpoint.load(checkpointFile) else {
            check(transfer.transferredBytes == 0L) { "Legacy upload has no safe resume authorization" }
            UploadCheckpoint(currentSourceHash, syncRemoteSnapshot(sftp, transfer.remotePath)).also { it.save(checkpointFile) }
        }
        check(currentSourceHash == checkpoint.sourceSha256) { "Upload source changed; resume refused" }
        val stagedPath = RemotePath.join(RemotePath.parent(transfer.remotePath), checkpoint.stagingName)
        val currentTarget = syncRemoteSnapshot(sftp, transfer.remotePath)
        // Process death can occur after atomic rename but before queue completion. Reconcile
        // only an exact desired-content match with the private staging already consumed.
        if (hadCheckpoint && currentTarget?.sha256 == checkpoint.sourceSha256 && !sftp.exists(stagedPath)) {
            if (!checkpointFile.delete() && checkpointFile.exists()) throw IOException("Could not clean upload authorization")
            return
        }
        SyncPlanGuard.requireUnchanged(checkpoint.target, currentTarget)
        val source = contentResolver.openInputStream(uri) ?: throw LocalUriUnavailableException(transfer.localUri)
        source.use { input ->
            val stageExists = sftp.exists(stagedPath)
            val resumeFrom = if (stageExists) {
                check(hadCheckpoint) { "Unrecorded upload staging exists; refusing overwrite" }
                val stagedBytes = sftp.regularFileSize(stagedPath)
                check(stagedBytes >= 0 && (transfer.totalBytes < 0 || stagedBytes <= transfer.totalBytes)) {
                    "Upload staging size is invalid"
                }
                val localPrefix = contentResolver.openInputStream(uri)?.use { prefixInput ->
                    ContentIdentity.prefix(prefixInput, stagedBytes)
                } ?: throw LocalUriUnavailableException(transfer.localUri)
                check(ContentIdentity.matches(localPrefix, sftp.sha256Prefix(stagedPath, stagedBytes))) {
                    "Upload prefix content changed; resume refused"
                }
                // JSch RESUME skips the remote size itself. The upload input must remain
                // at byte zero; prefix validation uses its own separately opened stream.
                stagedBytes
            } else 0L
            // Progress callbacks may lag remote acknowledgements at disconnect. A verified
            // prefix is authoritative even when persisted progress is slightly different.
            queue.resetProgress(transfer.id)
            if (resumeFrom > 0L) queue.markProgress(transfer.id, resumeFrom)
            sftp.preparePrivateStaging(stagedPath, create = !stageExists)
            val outstanding = (transfer.totalBytes - resumeFrom).coerceAtLeast(0L)
            val free = sftp.freeSpaceBytes(RemotePath.parentOf(transfer.remotePath))
            if (free != null && transfer.totalBytes > 0 && !RemoteCommands.fitsInFreeSpace(outstanding, free)) {
                throw NotEnoughRemoteSpaceException(outstanding, free)
            }
            val limiter = RateLimiter(rateLimitBytesPerSecond())
            val throttled = if (limiter.unlimited) input else ThrottledInputStream(input, limiter)
            sftp.upload(throttled, stagedPath, resumeFrom = resumeFrom) { total ->
                if (transfer.id in cancelled) throw InterruptedTransfer()
                queue.markProgress(transfer.id, total)
            }
        }
        check(ContentIdentity.hex(sftp.sha256(stagedPath)) == checkpoint.sourceSha256) { "Uploaded source changed or is incomplete" }
        if (transfer.id in cancelled) throw InterruptedTransfer()
        sftp.commitStagedUpload(stagedPath, transfer.remotePath) {
            SyncPlanGuard.requireUnchanged(checkpoint.target, syncRemoteSnapshot(sftp, transfer.remotePath))
        }
        if (!checkpointFile.delete() && checkpointFile.exists()) throw IOException("Could not clean upload authorization")
    }

    /** Signals a user-requested stop, distinguishing it from a real transfer failure. */
    private class InterruptedTransfer : RuntimeException("transfer interrupted by the user")

    fun onSessionLost() {
        cancelled.addAll(queue.active.map { it.id })
        queue.onConnectionLost()
        // Keep the dispatcher alive; a Connected state event will release the queue.
        transferClients.values.forEach { it.close() }
        transferClients.clear()
        synchronized(this) {
            client?.close()
            client = null
        }
    }

    /** Convenience for callers that cannot suspend; lifecycle teardown awaits closeAndJoin. */
    fun close() {
        parentScope.launch(Dispatchers.IO) { closeAndJoin() }
    }

    suspend fun closeAndJoin() = closeMutex.withLock {
        if (closed) return@withLock
        closing = true
        cancelled.addAll(queue.active.map { it.id })
        if (schedulerDelegate.isInitialized()) scheduler.close()
        controllerJob.cancel()
        var closeFailure: Exception? = null
        // Close channels before joining: blocking SFTP I/O needs its channel closed to exit.
        transferClients.values.forEach {
            try { it.close() } catch (failure: Exception) { if (closeFailure == null) closeFailure = failure }
        }
        synchronized(this) {
            try { client?.close() } catch (failure: Exception) { if (closeFailure == null) closeFailure = failure }
            client = null
        }
        controllerJob.join()
        transferClients.clear()
        // Workers and persistence observers have stopped. This final snapshot cannot
        // race a late progress/completion callback or an earlier disk write.
        queue.persist(persistFile)
        queue.persistHistory(historyFile)
        closed = true
        closeFailure?.let { throw it }
    }

    private companion object {
        const val MAX_RENAME_ATTEMPTS = 500

        /** How much of a large file a preview reads before stopping. */
        const val PREVIEW_WINDOW = 256L * 1024

        /** Relay buffer for a server-to-server copy; the phone never holds the whole file. */
        const val PIPE_BUFFER = 1 shl 16

        /** Ceiling for a file staged into the share cache. */
        const val MAX_SHARE_BYTES = 256L * 1024 * 1024

        /** Past this, hashing the file twice costs more than the assurance is worth. */
        const val MAX_VERIFY_BYTES = 512L * 1024 * 1024

    }
}
