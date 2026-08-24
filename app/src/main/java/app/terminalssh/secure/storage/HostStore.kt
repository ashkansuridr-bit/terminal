package app.terminalssh.secure.storage

import android.content.Context
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostKeyPolicy
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.model.KeyEntry
import app.terminalssh.secure.model.SnippetEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Non-secret metadata for hosts and keys, kept as JSON in private SharedPreferences.
 * Deliberately dependency-free: no Room, no codegen, nothing to break a market build.
 */
class HostStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- hosts ----

    fun hosts(): List<HostProfile> = readArray(KEY_HOSTS).mapNotNull { runCatching { it.toHost() }.getOrNull() }
        .sortedWith(compareByDescending<HostProfile> { it.favorite }.thenByDescending { it.lastConnectedAt })

    fun upsert(profile: HostProfile) {
        val list = hosts().filterNot { it.id == profile.id } + profile
        writeArray(KEY_HOSTS, list.map { it.toJson() })
    }

    fun delete(id: String) = writeArray(KEY_HOSTS, hosts().filterNot { it.id == id }.map { it.toJson() })

    fun touch(id: String) {
        hosts().firstOrNull { it.id == id }?.let { upsert(it.copy(lastConnectedAt = System.currentTimeMillis())) }
    }

    // ---- keys ----

    fun keys(): List<KeyEntry> = readArray(KEY_KEYS).mapNotNull { runCatching { it.toKey() }.getOrNull() }

    fun upsertKey(entry: KeyEntry) {
        val list = keys().filterNot { it.id == entry.id } + entry
        writeArray(KEY_KEYS, list.map { it.toJson() })
    }

    fun deleteKey(id: String) = writeArray(KEY_KEYS, keys().filterNot { it.id == id }.map { it.toJson() })

    // ---- snippets ----

    fun snippets(): List<SnippetEntry> =
        readArray(KEY_SNIPPETS)
            .mapNotNull { runCatching { it.toSnippet() }.getOrNull() }
            .sortedByDescending { it.createdAt }

    fun upsertSnippet(entry: SnippetEntry) {
        val list = snippets().filterNot { it.id == entry.id } + entry
        writeArray(KEY_SNIPPETS, list.map { it.toJson() })
    }

    fun deleteSnippet(id: String) =
        writeArray(KEY_SNIPPETS, snippets().filterNot { it.id == id }.map { it.toJson() })

    // ---- json ----

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val array = JSONArray().apply { items.forEach { put(it) } }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun HostProfile.toJson() = JSONObject().apply {
        put("id", id); put("label", label); put("host", host); put("port", port)
        put("username", username); put("group", group); put("favorite", favorite)
        put("lastConnectedAt", lastConnectedAt); put("policy", hostKeyPolicy.name)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        when (val a = auth) {
            is AuthMethod.Password -> { put("authType", "password"); put("vaultRef", a.vaultRef) }
            is AuthMethod.PrivateKey -> {
                put("authType", "key"); put("keyVaultRef", a.keyVaultRef)
                a.passphraseVaultRef?.let { put("passphraseVaultRef", it) }
            }
        }
    }

    private fun JSONObject.toHost(): HostProfile {
        val tagArray = optJSONArray("tags") ?: JSONArray()
        return HostProfile(
            id = getString("id"),
            label = optString("label", ""),
            host = getString("host"),
            port = optInt("port", 22),
            username = getString("username"),
            auth = if (optString("authType") == "key") {
                AuthMethod.PrivateKey(
                    keyVaultRef = optString("keyVaultRef"),
                    passphraseVaultRef = optString("passphraseVaultRef").takeIf { it.isNotBlank() },
                )
            } else {
                AuthMethod.Password(optString("vaultRef", ""))
            },
            hostKeyPolicy = runCatching { HostKeyPolicy.valueOf(optString("policy")) }
                .getOrDefault(HostKeyPolicy.TRUST_ON_FIRST_USE),
            group = optString("group", ""),
            tags = (0 until tagArray.length()).map { tagArray.getString(it) },
            favorite = optBoolean("favorite", false),
            lastConnectedAt = optLong("lastConnectedAt", 0L),
        )
    }

    private fun KeyEntry.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("fingerprint", fingerprint)
        put("algorithm", algorithm); put("createdAt", createdAt); put("hasPassphrase", hasPassphrase)
    }

    private fun JSONObject.toKey() = KeyEntry(
        id = getString("id"),
        name = optString("name", "key"),
        fingerprint = optString("fingerprint", ""),
        algorithm = optString("algorithm", ""),
        createdAt = optLong("createdAt", 0L),
        hasPassphrase = optBoolean("hasPassphrase", false),
    )

    private fun SnippetEntry.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toSnippet() = SnippetEntry(
        id = getString("id"),
        name = optString("name", "snippet"),
        createdAt = optLong("createdAt", 0L),
    )

    companion object {
        private const val PREFS = "hosts_v1"
        private const val KEY_HOSTS = "hosts"
        private const val KEY_KEYS = "keys"
        private const val KEY_SNIPPETS = "snippets"
    }
}
