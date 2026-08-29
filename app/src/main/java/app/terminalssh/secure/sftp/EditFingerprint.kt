package app.terminalssh.secure.sftp

import java.security.MessageDigest

/**
 * What the editor remembers about a remote file between opening it and saving it.
 *
 * mtime alone is not enough. An editor that preserves timestamps, an `rsync` that
 * restores them, or a `touch -r` after the fact all leave mtime unchanged while the
 * bytes differ — and the old check let that through, so the user's work silently
 * overwrote someone else's. Size catches most of the rest, but not an edit that happens
 * to preserve length, which is exactly what a one-character config change does.
 *
 * So the content hash is the authority and the cheap fields are the fast path: if mtime
 * or size already differ there is no point downloading anything to prove it.
 */
data class EditFingerprint(
    val mtimeEpochSeconds: Long,
    val sizeBytes: Long,
    val sha256: String,
)

/** Result of comparing what was opened against what is on the server now. */
enum class ConflictVerdict {
    /** Nothing changed; the save may proceed. */
    UNCHANGED,

    /** Remote changed since it was opened. Never overwrite without asking. */
    CHANGED,

    /** The remote could not be read. Fail closed: treat as unsafe, keep the editor open. */
    UNKNOWN,
}

object EditConflict {

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Cheap half of the check: does stat alone already prove a change?
     *
     * Returning false does **not** mean unchanged — it means stat could not tell, and
     * the caller still has to compare content hashes.
     */
    fun statProvesChange(saved: EditFingerprint, currentMtime: Long, currentSize: Long): Boolean =
        saved.mtimeEpochSeconds != currentMtime || saved.sizeBytes != currentSize

    /**
     * Full verdict. [currentSha256] is null when the content could not be read, which is
     * [ConflictVerdict.UNKNOWN] rather than "fine" — a save that cannot be proven safe is
     * not safe.
     */
    fun verdict(
        saved: EditFingerprint?,
        currentMtime: Long?,
        currentSize: Long?,
        currentSha256: String?,
    ): ConflictVerdict {
        if (saved == null) return ConflictVerdict.UNKNOWN
        if (currentMtime == null || currentSize == null) return ConflictVerdict.UNKNOWN
        if (statProvesChange(saved, currentMtime, currentSize)) return ConflictVerdict.CHANGED
        if (currentSha256 == null) return ConflictVerdict.UNKNOWN
        return if (currentSha256 == saved.sha256) ConflictVerdict.UNCHANGED else ConflictVerdict.CHANGED
    }
}
