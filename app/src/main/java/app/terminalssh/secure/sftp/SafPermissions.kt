package app.terminalssh.secure.sftp

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * Keeps SAF grants alive as long as the transfer queue references them.
 *
 * [TransferQueue] writes `localUri` to disk so a transfer survives process death, but the
 * grant a document picker hands back is scoped to the *process*. After a restart the URI
 * string is still there and the permission behind it is gone, so resuming threw
 * `SecurityException` from `openInputStream`/`openOutputStream` — the queue looked
 * healthy and every resumed transfer failed.
 *
 * Taking the grant persistably is what makes the persisted URI mean something. The take
 * is best-effort by design: a provider is free to refuse persistence, and a transfer that
 * only has to survive until the app is next backgrounded should still run. What must not
 * happen is failing later without saying why, which is what [isAccessible] is for.
 */
object SafPermissions {

    private const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Asks to keep access to [uri] across process death. Returns whether the grant is
     * now persisted; false means the transfer works now but will not survive a restart.
     */
    fun takePersistable(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { resolver.takePersistableUriPermission(uri, RW) }.isSuccess

    /** Same, for a tree picked with `OpenDocumentTree`. */
    fun takePersistableTree(resolver: ContentResolver, treeUri: Uri): Boolean =
        takePersistable(resolver, treeUri)

    /**
     * Whether [uri] can still be opened. Checks the persisted grant list first — cheap
     * and exact — and falls back to the in-process grant, which covers a URI taken this
     * session that the provider declined to persist.
     */
    fun isAccessible(resolver: ContentResolver, uri: Uri): Boolean {
        val persisted = runCatching {
            resolver.persistedUriPermissions.any { it.uri == uri }
        }.getOrDefault(false)
        if (persisted) return true
        // Not persisted: it may still be a live in-process grant. Only an actual open
        // answers that, and it is the same call the transfer is about to make anyway.
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    /**
     * Gives a grant back once nothing references it. Android caps how many an app may
     * hold, so a long-lived queue that never released would eventually stop being able
     * to take new ones.
     */
    fun release(resolver: ContentResolver, uri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(uri, RW) }
    }
}

/**
 * The document behind a queued transfer can no longer be opened — typically a SAF grant
 * lost to a reboot, or a removable volume that went away. Distinct from a server error
 * so the queue can mark it [TransferErrorKind.LOCAL_UNAVAILABLE] and stop retrying
 * something no retry can fix.
 */
class LocalUriUnavailableException(uri: String) :
    java.io.IOException("the app can no longer open $uri")
