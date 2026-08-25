package app.terminalssh.secure.storage

import app.terminalssh.secure.model.HostProfile

/**
 * Writes host metadata back out as an OpenSSH config file.
 *
 * This is the escape hatch that keeps the app from being a place data goes to die: the
 * output is a plain `~/.ssh/config` any desktop SSH client reads, so a user can move to
 * another tool — or just back up their list — without a proprietary export format.
 *
 * Nothing secret is written. No password, no passphrase, and no private key ever appears
 * here; a key-auth host exports as a plain host entry, and the key stays in the vault.
 * That is deliberate: this file is meant to be safe to email to yourself.
 */
object SshConfigExport {

    fun render(hosts: List<HostProfile>): String = buildString {
        appendLine("# Exported from Terminal SSH")
        appendLine("# Contains no passwords, passphrases, or private keys.")

        hosts.forEach { profile ->
            appendLine()
            profile.notes.takeIf { it.isNotBlank() }?.let { notes ->
                // Notes are free-form and may span lines; every line has to stay a comment.
                notes.lineSequence().forEach { appendLine("# ${it.trim()}") }
            }
            appendLine("Host ${profile.aliasForExport()}")
            appendLine("    HostName ${profile.host}")
            appendLine("    User ${profile.username}")
            if (profile.port != DEFAULT_PORT) appendLine("    Port ${profile.port}")
        }
    }

    /**
     * OpenSSH treats whitespace as a separator, so a label like "Prod DB" would parse as
     * two host patterns. Fall back to the address when the label cannot be used as-is.
     */
    private fun HostProfile.aliasForExport(): String {
        val candidate = label.trim()
        val usable = candidate.isNotEmpty() &&
            candidate.none { it.isWhitespace() || it == '*' || it == '?' || it == '#' }
        return if (usable) candidate else host
    }

    private const val DEFAULT_PORT = 22
}
