package app.terminalssh.secure.storage

import android.content.Context
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.Environment
import app.terminalssh.secure.model.HostKeyPolicy
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.model.KeyEntry
import app.terminalssh.secure.model.SnippetEntry
import app.terminalssh.secure.security.CredentialReference
import app.terminalssh.secure.security.CredentialReferences
import app.terminalssh.secure.security.VaultAad
import org.json.JSONArray
import org.json.JSONObject

/**
 * Non-secret metadata for hosts and keys, kept as JSON in private SharedPreferences.
 * Deliberately dependency-free: no Room, no codegen, nothing to break a market build.
 */
class MetadataCommitException(val rollbackSucceeded: Boolean) :
    IllegalStateException(if (rollbackSucceeded) "metadata commit failed" else "metadata rollback failed")

class HostStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var metadataDurabilityUncertain = false

    // ---- hosts ----

    @Synchronized
    fun hosts(): List<HostProfile> = readArray(KEY_HOSTS).map { it.toHost() }
        .sortedWith(compareByDescending<HostProfile> { it.favorite }.thenByDescending { it.lastConnectedAt })

    @Synchronized
    fun upsert(profile: HostProfile) {
        val list = hosts().filterNot { it.id == profile.id } + profile
        writeArray(KEY_HOSTS, list.map { it.toJson() })
    }

    @Synchronized
    fun delete(id: String) = writeArray(KEY_HOSTS, hosts().filterNot { it.id == id }.map { it.toJson() })

    @Synchronized
    fun touch(id: String) {
        hosts().firstOrNull { it.id == id }?.let { upsert(it.copy(lastConnectedAt = System.currentTimeMillis())) }
    }

    // ---- keys ----

    @Synchronized
    fun keys(): List<KeyEntry> = readArray(KEY_KEYS).map { it.toKey() }

    @Synchronized
    fun upsertKey(entry: KeyEntry) {
        val list = keys().filterNot { it.id == entry.id } + entry
        writeArray(KEY_KEYS, list.map { it.toJson() })
    }

    @Synchronized
    fun deleteKey(id: String) {
        check(CredentialReferences.keyDependents(id, hosts()).isEmpty()) { "key is referenced by hosts" }
        val nextKeys = keys().filterNot { it.id == id }
        val cleanup = pendingCredentialCleanup() + CredentialReference(id, VaultAad.PRIVATE_KEY)
        commitRecords(mapOf(
            KEY_KEYS to JSONArray().apply { nextKeys.forEach { put(it.toJson()) } }.toString(),
            KEY_CLEANUP to JSONArray().apply { cleanup.forEach { put(it.toJson()) } }.toString(),
        ))
    }

    /** Metadata and obsolete-secret journal commit together before any vault deletion. */
    @Synchronized
    fun saveCredentials(profile: HostProfile) {
        val previous = hosts().firstOrNull { it.id == profile.id }
        val next = hosts().filterNot { it.id == profile.id } + profile
        val retired = CredentialReferences.unreferenced(previous?.auth, next)
        writeHostTransaction(next, retired)
    }

    @Synchronized
    fun removeHostCredentials(id: String) {
        val previous = hosts().firstOrNull { it.id == id }
        val next = hosts().filterNot { it.id == id }
        writeHostTransaction(next, CredentialReferences.unreferenced(previous?.auth, next))
    }

    @Synchronized
    fun replaceKeyForHosts(keyRef: String, replacement: AuthMethod) {
        val before = hosts()
        val next = before.map { host ->
            if ((host.auth as? AuthMethod.PrivateKey)?.keyVaultRef == keyRef) host.copy(auth = replacement) else host
        }
        val retired = before.flatMap { CredentialReferences.unreferenced(it.auth, next) }.toSet()
        writeHostTransaction(next, retired)
    }

    @Synchronized
    fun pendingCredentialCleanup(): Set<CredentialReference> = readArray(KEY_CLEANUP).map {
        CredentialReference(it.getString("ref"), VaultAad.valueOf(it.getString("aad")))
    }.toSet()

    @Synchronized
    fun finishCredentialCleanup(ref: CredentialReference) {
        writeArray(KEY_CLEANUP, (pendingCredentialCleanup() - ref).map { it.toJson() })
    }

    private fun CredentialReference.toJson() = JSONObject().apply {
        put("ref", ref); put("aad", aad.name)
    }

    private fun writeHostTransaction(next: List<HostProfile>, retired: Set<CredentialReference>) {
        val used = next.flatMap { CredentialReferences.ownedBy(it.auth) }.toSet()
        val cleanup = (pendingCredentialCleanup() + retired) - used
        commitRecords(mapOf(
            KEY_HOSTS to JSONArray().apply { next.forEach { put(it.toJson()) } }.toString(),
            KEY_CLEANUP to JSONArray().apply { cleanup.forEach { put(it.toJson()) } }.toString(),
        ))
    }

    /** Register before writing a fresh secret: even process death retains cleanup intent. */
    @Synchronized
    fun scheduleCredentialCleanup(ref: CredentialReference) {
        writeArray(KEY_CLEANUP, (pendingCredentialCleanup() + ref).map { it.toJson() })
    }

    @Synchronized
    fun credentialIsReferenced(ref: CredentialReference): Boolean {
        check(!metadataDurabilityUncertain) { "reload metadata before credential cleanup" }
        return hosts().any { ref in CredentialReferences.ownedBy(it.auth) } ||
            (ref.aad == VaultAad.PRIVATE_KEY && (keys().any { it.id == ref.ref } ||
                hosts().any { (it.auth as? AuthMethod.PrivateKey)?.keyVaultRef == ref.ref }))
    }

    private fun commitRecords(records: Map<String, String>) {
        check(!metadataDurabilityUncertain) { "reload metadata before writing" }
        val previous = records.keys.associateWith { prefs.getString(it, null) }
        val editor = prefs.edit()
        records.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) {
            val rollback = prefs.edit()
            previous.forEach { (key, value) -> rollback.putString(key, value) }
            val restored = rollback.commit()
            metadataDurabilityUncertain = !restored
            throw MetadataCommitException(restored)
        }
    }

    // ---- snippets ----

    @Synchronized
    fun snippets(): List<SnippetEntry> =
        readArray(KEY_SNIPPETS)
            .mapNotNull { runCatching { it.toSnippet() }.getOrNull() }
            .sortedByDescending { it.createdAt }

    @Synchronized
    fun upsertSnippet(entry: SnippetEntry) {
        val list = snippets().filterNot { it.id == entry.id } + entry
        writeArray(KEY_SNIPPETS, list.map { it.toJson() })
    }

    @Synchronized
    fun deleteSnippet(id: String) =
        writeArray(KEY_SNIPPETS, snippets().filterNot { it.id == id }.map { it.toJson() })

    /** Recovery export keeps original JSON strings, including malformed ones, as JSON values. */
    @Synchronized
    fun rawMetadataSnapshot(): String = JSONObject().apply {
        put("format", "terminalssh-host-metadata-recovery-v1")
        put("records", JSONObject(prefs.all))
    }.toString()

    // ---- json ----

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        check(!metadataDurabilityUncertain) { "reload metadata before writing" }
        val array = JSONArray().apply { items.forEach { put(it) } }
        val previous = prefs.getString(key, null)
        if (!prefs.edit().putString(key, array.toString()).commit()) {
            val restored = prefs.edit().putString(key, previous).commit()
            metadataDurabilityUncertain = !restored
            throw MetadataCommitException(restored)
        }
    }

    private fun HostProfile.toJson() = JSONObject().apply {
        put("id", id); put("label", label); put("host", host); put("port", port)
        put("username", username); put("group", group); put("favorite", favorite)
        put("lastConnectedAt", lastConnectedAt); put("policy", hostKeyPolicy.name)
        put("notes", notes); put("environment", environment.name)
        put("maxReconnectAttempts", maxReconnectAttempts)
        put("jumpHostId", jumpHostId)
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
            hostKeyPolicy = decodeHostKeyPolicy(if (has("policy")) getString("policy") else null),
            group = optString("group", ""),
            tags = (0 until tagArray.length()).map { tagArray.getString(it) },
            favorite = optBoolean("favorite", false),
            lastConnectedAt = optLong("lastConnectedAt", 0L),
            notes = optString("notes", ""),
            // Records written before these fields existed fall back to the defaults.
            environment = runCatching { Environment.valueOf(optString("environment")) }
                .getOrDefault(Environment.NONE),
            maxReconnectAttempts = optInt(
                "maxReconnectAttempts",
                HostProfile.DEFAULT_RECONNECT_ATTEMPTS,
            ).coerceIn(0, HostProfile.MAX_RECONNECT_ATTEMPTS),
            jumpHostId = optString("jumpHostId", ""),
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
        private const val KEY_CLEANUP = "credential_cleanup"
    }
}

/** Only records from before the policy field existed migrate to TOFU. Explicit damage is rejected. */
internal fun decodeHostKeyPolicy(value: String?): HostKeyPolicy =
    if (value == null) HostKeyPolicy.TRUST_ON_FIRST_USE else HostKeyPolicy.valueOf(value)
