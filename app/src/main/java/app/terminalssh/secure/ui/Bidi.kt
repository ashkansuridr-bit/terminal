package app.terminalssh.secure.ui

/**
 * Wraps a left-to-right technical value so it keeps its own direction inside Persian text.
 *
 * Without this, the bidirectional algorithm treats trailing punctuation as neutral and
 * reorders it against the surrounding paragraph: "0.4.0-debug" renders as "debug-0.4.0",
 * "192.168.1.10:22" loses its port to the wrong end, and a fingerprint reads backwards.
 * Isolates — rather than embeddings — are used so the wrapped run cannot affect the
 * ordering of the text around it.
 */
private const val LTR_ISOLATE = '⁦'
private const val POP_ISOLATE = '⁩'

/** Host names, ports, versions, fingerprints, algorithms — anything read left to right. */
fun ltr(value: String): String =
    if (value.isEmpty()) value else "$LTR_ISOLATE$value$POP_ISOLATE"
