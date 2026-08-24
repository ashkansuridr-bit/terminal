package app.terminalssh.secure.storage

import android.content.Context
import android.util.Base64
import app.terminalssh.secure.ssh.KnownHostsVerifier
import java.util.Locale

class KnownHostsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(host: String, port: Int): KnownHostsVerifier.KnownHost? {
        val prefix = identity(host, port)
        val algorithm = prefs.getString("$prefix.algorithm", null) ?: return null
        val key = prefs.getString("$prefix.key", null) ?: return null
        return KnownHostsVerifier.KnownHost(host, port, algorithm, Base64.decode(key, Base64.NO_WRAP))
    }

    fun put(host: String, port: Int, algorithm: String, key: ByteArray) {
        val prefix = identity(host, port)
        prefs.edit()
            .putString("$prefix.algorithm", algorithm)
            .putString("$prefix.key", Base64.encodeToString(key, Base64.NO_WRAP))
            .apply()
    }

    fun remove(host: String, port: Int) {
        val prefix = identity(host, port)
        prefs.edit().remove("$prefix.algorithm").remove("$prefix.key").apply()
    }

    /** Every trusted entry, for the Settings screen. */
    fun all(): List<KnownHostsVerifier.KnownHost> =
        prefs.all.keys.filter { it.endsWith(".algorithm") }.mapNotNull { entry ->
            val identity = entry.removePrefix("host.").removeSuffix(".algorithm")
            val port = identity.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
            val host = identity.substringBeforeLast(':')
            get(host, port)
        }

    private fun identity(host: String, port: Int) = knownHostIdentity(host, port)

    companion object { private const val PREFS = "known_hosts_v1" }
}

internal fun knownHostIdentity(host: String, port: Int): String =
    "host.${host.lowercase(Locale.ROOT)}:$port"
