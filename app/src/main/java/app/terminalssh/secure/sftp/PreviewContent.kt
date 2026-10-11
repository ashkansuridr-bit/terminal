package app.terminalssh.secure.sftp

/**
 * What the read-only preview should render (F026).
 *
 * The preview used to be "decode to text, always", which renders a binary as a wall of
 * replacement characters and gives no way to tell a genuine U+FFFD from a corrupted read. This
 * decides once, from the bytes, whether the file is text or binary and hands the UI a ready
 * view: a capped text decode, or a classic hex dump. Nothing here touches the network — the
 * caller has already fetched a bounded sample — so the decision is unit-testable.
 */
object PreviewContent {

    sealed interface View {
        /** True when the sample is only part of the file, so the UI can say so. */
        val truncated: Boolean

        data class Text(val text: String, override val truncated: Boolean, val bytesDecoded: Int) : View

        /** [lines] are pre-formatted `offset  hex  |ascii|` rows so the UI stays dumb. */
        data class Hex(val lines: List<String>, override val truncated: Boolean, val bytesShown: Int) : View
    }

    /**
     * @param sourceTruncated true when the fetch itself stopped at a cap, so the view stays
     *   honest even when the sample happens to be exactly [maxBytes] long.
     */
    fun of(
        bytes: ByteArray,
        maxBytes: Int = HexPreview.DEFAULT_MAX_BYTES,
        sourceTruncated: Boolean = false,
    ): View {
        val limit = maxBytes.coerceAtLeast(0)
        val shown = minOf(bytes.size, limit)
        val truncated = sourceTruncated || bytes.size > limit
        return if (HexPreview.isProbablyBinary(bytes)) {
            View.Hex(HexPreview.hexDump(bytes, limit), truncated, shown)
        } else {
            val decoded = HexPreview.decodeText(bytes, limit)
            View.Text(decoded.text, truncated, decoded.bytesDecoded)
        }
    }
}
