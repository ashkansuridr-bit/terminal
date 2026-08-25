package app.terminalssh.secure.storage

import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import java.util.UUID

/**
 * Parses an OpenSSH `~/.ssh/config` into host profiles.
 *
 * Anyone who already uses SSH from a desktop has this file, and retyping a dozen servers
 * on a phone keyboard is the reason they give up on a mobile client. Only the directives
 * that map onto a [HostProfile] are read; everything else is ignored rather than treated
 * as an error, because a real config is full of options this app has no concept of.
 *
 * Deliberately not read: `IdentityFile`. The referenced private key is not reachable from
 * Android's sandbox, and silently creating a key-auth host whose key does not exist would
 * produce a profile that always fails to connect.
 */
object SshConfigImport {

    private const val DEFAULT_PORT = 22

    /** @return one profile per named `Host` block, in file order. */
    fun parse(text: String, defaultUsername: String = ""): List<HostProfile> {
        val blocks = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue

            // OpenSSH accepts both "Key value" and "Key=value".
            val separator = line.indexOfFirst { it == ' ' || it == '\t' || it == '=' }
            if (separator <= 0) continue
            val keyword = line.substring(0, separator).lowercase()
            val value = line.substring(separator + 1).trim().trim('=').trim()
            if (value.isEmpty()) continue

            if (keyword == "host") {
                // A pattern with a wildcard is a defaults block, not a real server.
                if (value.any { it == '*' || it == '?' }) {
                    current = null
                    continue
                }
                current = mutableMapOf("host" to value)
                blocks += current
            } else {
                current?.put(keyword, value)
            }
        }

        return blocks.mapNotNull { block -> block.toProfile(defaultUsername) }
    }

    private fun Map<String, String>.toProfile(defaultUsername: String): HostProfile? {
        val alias = this["host"] ?: return null
        val hostName = this["hostname"] ?: alias
        val username = this["user"]?.takeIf { it.isNotBlank() }
            ?: defaultUsername.takeIf { it.isNotBlank() }
            ?: return null
        val port = this["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT

        return runCatching {
            HostProfile(
                id = UUID.randomUUID().toString(),
                // Keep the alias as the label only when it differs from the real address,
                // so "Host db.example.com" does not produce a redundant label.
                label = alias.takeIf { it != hostName } ?: "",
                host = hostName,
                port = port,
                username = username,
                auth = AuthMethod.Password(""),
            )
        }.getOrNull()
    }
}
