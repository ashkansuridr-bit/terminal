package app.terminalssh.secure.model

/**
 * Smart snippet templates (F017).
 *
 * A snippet like `systemctl restart {{name}}` is reusable across servers only if the
 * placeholder is filled in at send time with the right server and path, and only if the
 * substituted value cannot turn into a second command. Both halves live here: the
 * placeholder grammar is parsed once, and every value is shell-quoted before it lands in
 * the rendered command, so a path named `x; rm -rf ~` is inserted as data, not as code.
 *
 * Pure functions only — the encrypted command bytes and the vault stay elsewhere.
 */
object SnippetTemplate {

    /** Names the renderer understands. Kept explicit so `unknown()` can warn on typos. */
    val known: Set<String> = setOf("host", "user", "port", "path", "label", "name")

    data class Context(
        val host: String = "",
        val user: String = "",
        val port: Int = 22,
        val path: String = "",
        val label: String = "",
        val name: String = "",
    ) {
        fun asMap(): Map<String, String> = mapOf(
            "host" to host,
            "user" to user,
            "port" to port.toString(),
            "path" to path,
            "label" to label.ifBlank { host },
            "name" to name,
        )
    }

    private val TOKEN = Regex("""\{\{\s*([a-zA-Z0-9_]+)\s*}}""")

    /** Placeholders present in [template], in first-seen order, de-duplicated. */
    fun placeholdersIn(template: String): List<String> =
        TOKEN.findAll(template).map { it.groupValues[1].lowercase() }.distinct().toList()

    /** Placeholders a user typed that will not be substituted — surfacing a typo before sending. */
    fun unknown(template: String): List<String> = placeholdersIn(template).filterNot { it in known }

    /**
     * Renders [template] with [context].
     *
     * @param escape when true (the default) every substituted value is shell-quoted. Code
     *   snippets that intentionally build flags can opt out, but the default must be safe.
     *   Unknown placeholders are left verbatim so the user sees what did not resolve.
     */
    fun render(template: String, context: Context, escape: Boolean = true): String {
        val values = context.asMap()
        return TOKEN.replace(template) { match ->
            val key = match.groupValues[1].lowercase()
            val value = values[key] ?: return@replace match.value
            if (escape) shellQuote(value) else value
        }
    }

    /**
     * POSIX single-quote quoting: wrap in single quotes and turn an embedded quote into
     * `'\''`. Nothing inside single quotes is special to the shell, so this neutralises
     * `;`, `$()`, backticks and newlines in one pass.
     */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** One-line description of what will be typed, for the confirmation preview. */
    fun describe(template: String, context: Context): RenderPreview {
        val unknown = unknown(template)
        return RenderPreview(
            rendered = render(template, context),
            unknownPlaceholders = unknown,
            resolved = placeholdersIn(template).filter { it in known },
        )
    }

    data class RenderPreview(
        val rendered: String,
        val unknownPlaceholders: List<String>,
        val resolved: List<String>,
    ) {
        val isSafeToSend: Boolean get() = unknownPlaceholders.isEmpty()
    }
}
