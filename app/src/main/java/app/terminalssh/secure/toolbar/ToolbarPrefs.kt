package app.terminalssh.secure.toolbar

/**
 * The key/value backing store for toolbar configuration.
 *
 * Deliberately narrow: a single JSON document under one key. The Android implementation
 * sits on `SharedPreferences`; tests use an in-memory map. Because the interface has no
 * Android types, every rule in [ToolbarRepository] — load, fallback, migrate, persist — is
 * exercised on a plain JVM.
 */
interface ToolbarPrefs {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** In-memory implementation, used by tests and as a safe fallback. */
class InMemoryToolbarPrefs(initial: Map<String, String> = emptyMap()) : ToolbarPrefs {
    private val map = LinkedHashMap(initial)
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) {
        map[key] = value
    }
}
