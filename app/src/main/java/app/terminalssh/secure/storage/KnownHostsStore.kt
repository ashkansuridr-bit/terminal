package app.terminalssh.secure.storage

import android.content.Context
import android.util.Base64
import app.terminalssh.secure.ssh.KnownHostsVerifier
import java.util.Locale

class KnownHostsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var durabilityUncertain = false

    @Synchronized
    fun get(host: String, port: Int): KnownHostsVerifier.KnownHost? {
        check(!durabilityUncertain) { "known-host durability uncertain" }
        val prefix = identity(host, port)
        val algorithmPresent = prefs.contains("$prefix.algorithm")
        val keyPresent = prefs.contains("$prefix.key")
        if (!algorithmPresent && !keyPresent) return null
        check(algorithmPresent && keyPresent) { "incomplete known-host record" }
        val algorithm = prefs.getString("$prefix.algorithm", null)
        val encoded = prefs.getString("$prefix.key", null)
        check(!algorithm.isNullOrBlank() && !encoded.isNullOrBlank()) { "invalid known-host record" }
        val key = Base64.decode(encoded, Base64.NO_WRAP)
        check(key.isNotEmpty() && Base64.encodeToString(key, Base64.NO_WRAP) == encoded) {
            "invalid known-host encoding"
        }
        return KnownHostsVerifier.KnownHost(host, port, algorithm, key)
    }

    @Synchronized
    fun put(host: String, port: Int, algorithm: String, key: ByteArray) {
        require(host.isNotBlank() && port in 1..65535 && algorithm.isNotBlank() && key.isNotEmpty())
        val prefix = identity(host, port)
        commitRecords(mapOf(
            "$prefix.algorithm" to algorithm,
            "$prefix.key" to Base64.encodeToString(key, Base64.NO_WRAP),
        ))
    }

    @Synchronized
    fun remove(host: String, port: Int) {
        val prefix = identity(host, port)
        commitRecords(mapOf("$prefix.algorithm" to null, "$prefix.key" to null))
    }

    /** Never silently omit a corrupt trust record or treat it as first use. */
    @Synchronized
    fun all(): List<KnownHostsVerifier.KnownHost> {
        check(!durabilityUncertain) { "known-host durability uncertain" }
        return prefs.all.keys.map { entry ->
            check(entry.startsWith("host.") && (entry.endsWith(".algorithm") || entry.endsWith(".key"))) {
                "invalid known-host record name"
            }
            entry.removeSuffix(".algorithm").removeSuffix(".key")
        }.distinct().map { prefix ->
            val identity = prefix.removePrefix("host.")
            val port = identity.substringAfterLast(':').toIntOrNull()
            val host = identity.substringBeforeLast(':')
            check(port != null && port in 1..65535 && host.isNotBlank()) { "invalid known-host identity" }
            checkNotNull(get(host, port)) { "incomplete known-host record" }
        }
    }

    private fun commitRecords(records: Map<String, String?>) {
        check(!durabilityUncertain) { "known-host durability uncertain" }
        val previous = records.keys.associateWith { prefs.getString(it, null) }
        val editor = prefs.edit()
        records.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) {
            val rollback = prefs.edit()
            previous.forEach { (key, value) -> rollback.putString(key, value) }
            val restored = rollback.commit()
            durabilityUncertain = !restored
            throw KnownHostCommitException(restored)
        }
    }

    private fun identity(host: String, port: Int) = knownHostIdentity(host, port)

    companion object { private const val PREFS = "known_hosts_v1" }
}

class KnownHostCommitException(val rollbackSucceeded: Boolean) :
    IllegalStateException(if (rollbackSucceeded) "known-host commit failed" else "known-host rollback failed")

internal fun knownHostIdentity(host: String, port: Int): String =
    "host.${host.lowercase(Locale.ROOT)}:$port"
