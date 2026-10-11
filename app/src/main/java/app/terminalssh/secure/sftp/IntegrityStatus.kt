package app.terminalssh.secure.sftp

/**
 * Honest post-transfer integrity reporting (F028).
 *
 * A transfer that finished without an error is not the same as a transfer whose bytes were
 * proven correct. SFTP does not checksum by itself: the server only proves correctness when
 * it can hand us a hash we can compare. So the truthful default is UNVERIFIED, never a
 * silent "done".
 *
 * That distinction is the whole point. A corrupted upload that reports success is worse than
 * one that reports nothing at all, because the user stops watching — so a check mark is only
 * ever shown for a comparison that actually happened and matched.
 */
object IntegrityStatus {

    enum class State {
        /** Both sides presented the same SHA-256. */
        VERIFIED,

        /** Both sides presented a hash and they differ. */
        MISMATCH,

        /** No comparison could be made (a side gave no hash, or could not be read). */
        UNVERIFIED,
    }

    data class Report(
        val state: State,
        val expected: String?,
        val actual: String?,
        val reason: String,
    ) {
        /** Only a proven match may ever be presented as success. */
        val isProven: Boolean get() = state == State.VERIFIED

        /** True when the caller must show a warning/unknown badge rather than a check. */
        val needsAttention: Boolean get() = state != State.VERIFIED
    }

    /**
     * Compares an optional expected hash (what the source said) with an optional actual hash
     * (what the destination reported). Anything short of two equal hashes is UNVERIFIED or
     * MISMATCH — never VERIFIED.
     */
    fun verify(expected: String?, actual: String?): Report {
        val e = normalize(expected)
        val a = normalize(actual)
        return when {
            e == null && a == null ->
                Report(State.UNVERIFIED, null, null, "no hash available on either side")
            e == null ->
                Report(State.UNVERIFIED, null, a, "source did not report a hash")
            a == null ->
                Report(State.UNVERIFIED, e, null, "destination did not report a hash")
            e.equals(a, ignoreCase = true) ->
                Report(State.VERIFIED, e, a, "hashes match")
            else ->
                Report(State.MISMATCH, e, a, "hashes differ")
        }
    }

    /**
     * Accepts `sha256:<hex>`, `SHA256:<hex>` or a bare hex string, trims, and lowercases it.
     * A blank value means "no hash" (UNVERIFIED), not an error.
     */
    fun normalize(hash: String?): String? {
        val value = hash?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withoutPrefix =
            if (value.startsWith("sha256:", ignoreCase = true)) value.substring(7) else value
        return withoutPrefix.trim().lowercase().takeIf { it.isNotEmpty() }
    }

    /** A stable localization key for the state; the UI supplies the prose. */
    fun labelKey(state: State): String = when (state) {
        State.VERIFIED -> "integrity.state.verified"
        State.MISMATCH -> "integrity.state.mismatch"
        State.UNVERIFIED -> "integrity.state.unverified"
    }
}
