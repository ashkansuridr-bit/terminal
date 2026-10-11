package app.terminalssh.secure.storage

import app.terminalssh.secure.ssh.KnownHostsVerifier
import java.util.Base64

/**
 * OpenSSH `known_hosts` import/export (F037).
 *
 * The app stores trusted keys in its own prefs; users coming from a desktop already have a
 * `~/.ssh/known_hosts`, and users leaving want their pins back in a format `ssh` understands.
 * This is the codec between the two, kept pure and storage-free.
 *
 * Deliberately conservative: hashed host entries (`|1|…`) cannot be reversed without the
 * server name, so they are counted and skipped rather than mis-imported; `@cert-authority`
 * and `@revoked` markers are counted but not modelled; a line whose key is not valid base64
 * is skipped instead of aborting the whole import.
 */
object KnownHostsFormat {

    private val HEADER = "# trusted host keys exported by Terminal SSH"

    data class ParseResult(
        val entries: List<KnownHostsVerifier.KnownHost>,
        val hashedHosts: Int,
        val markers: Int,
        val skippedLines: Int,
    )

    fun export(entries: List<KnownHostsVerifier.KnownHost>): String {
        val body = entries
            .sortedWith(compareBy({ it.host }, { it.port }, { it.algorithm }))
            .joinToString("\n") { entry ->
                "${hostField(entry.host, entry.port)} ${entry.algorithm} " +
                    Base64.getEncoder().withoutPadding().encodeToString(entry.key)
            }
        return if (body.isEmpty()) "$HEADER\n" else "$HEADER\n$body\n"
    }

    fun parse(text: String, defaultPort: Int = 22): ParseResult {
        val entries = ArrayList<KnownHostsVerifier.KnownHost>()
        var hashed = 0
        var markers = 0
        var skipped = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val fields = line.split(Regex("\\s+"))
            if (fields[0].startsWith("@")) {
                // @cert-authority / @revoked are not plain host keys; importing them as
                // trusted hosts would be wrong, so they are counted and left alone.
                markers++
                continue
            }
            if (fields.size < 3) {
                skipped++
                continue
            }
            val hostField = fields[0]
            val algorithm = fields[1]
            val keyField = fields[2]
            if (hostField.startsWith("|")) {
                hashed++
                continue
            }
            if (!isValidKeyType(algorithm)) {
                skipped++
                continue
            }
            val key = runCatching { Base64.getDecoder().decode(keyField) }.getOrNull()
            if (key == null || key.isEmpty()) {
                skipped++
                continue
            }
            for (host in expandHosts(hostField, defaultPort)) {
                entries += KnownHostsVerifier.KnownHost(host.first, host.second, algorithm, key)
            }
        }
        return ParseResult(entries, hashed, markers, skipped)
    }

    /** Known public-key algorithm names OpenSSH writes into a known_hosts file. */
    fun isValidKeyType(algorithm: String): Boolean =
        algorithm.isNotBlank() && !algorithm.contains(' ') &&
            KEY_PREFIXES.any { algorithm.startsWith(it) }

    fun fingerprint(entry: KnownHostsVerifier.KnownHost): String =
        KnownHostsVerifier.sha256Fingerprint(entry.key)

    private fun hostField(host: String, port: Int): String =
        if (port == 22) host else "[$host]:$port"

    /** A field may list several hosts comma-separated, e.g. `a.example,b.example`. */
    private fun expandHosts(field: String, defaultPort: Int): List<Pair<String, Int>> =
        field.split(',').mapNotNull { single -> parseHostPort(single, defaultPort) }

    private fun parseHostPort(value: String, defaultPort: Int): Pair<String, Int>? {
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close < 0) return null
            val host = value.substring(1, close)
            val port = value.substring(close + 1).removePrefix(":").toIntOrNull() ?: defaultPort
            return if (host.isBlank()) null else host to port
        }
        return if (value.isBlank()) null else value to defaultPort
    }

    private val KEY_PREFIXES = setOf(
        "ssh-ed25519", "ssh-rsa", "ssh-dss", "ecdsa-sha2-", "sk-ssh-", "sk-ecdsa-", "rsa-sha2-", "ssh-",
    )
}
