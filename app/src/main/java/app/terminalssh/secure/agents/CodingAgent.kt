package app.terminalssh.secure.agents

/**
 * A coding agent that can be installed on a remote server and driven from the terminal.
 *
 * The agent runs on the server, not on the phone. That is the whole reason this belongs
 * in an SSH client: the phone only has to provide a stable terminal and a usable keyboard,
 * which is exactly what it already does.
 */
enum class CodingAgent(
    val id: String,
    val displayName: String,
    /** Command that reports the installed version, used for detection. */
    val versionCommand: String,
    /** What the user types to start it once installed. */
    val launchCommand: String,
    /** Environment variable holding its API credential, or null if it authenticates another way. */
    val apiKeyVariable: String?,
    val docsUrl: String,
) {
    CLAUDE_CODE(
        id = "claude-code",
        displayName = "Claude Code",
        versionCommand = "claude --version",
        launchCommand = "claude",
        apiKeyVariable = "ANTHROPIC_API_KEY",
        docsUrl = "https://code.claude.com/docs",
    ),
    OPENCODE(
        id = "opencode",
        displayName = "OpenCode",
        versionCommand = "opencode --version",
        launchCommand = "opencode",
        apiKeyVariable = null,
        docsUrl = "https://opencode.ai",
    ),
    AIDER(
        id = "aider",
        displayName = "Aider",
        versionCommand = "aider --version",
        launchCommand = "aider",
        apiKeyVariable = "ANTHROPIC_API_KEY",
        docsUrl = "https://aider.chat",
    ),
    ;

    companion object {
        fun byId(id: String): CodingAgent? = entries.firstOrNull { it.id == id }
    }
}

/** What a package manager check found on the server. */
enum class PackageManager(val id: String, val detectCommand: String, val installPrefix: String) {
    APT("apt", "command -v apt-get", "sudo apt-get install -y"),
    DNF("dnf", "command -v dnf", "sudo dnf install -y"),
    PACMAN("pacman", "command -v pacman", "sudo pacman -S --noconfirm"),
    APK("apk", "command -v apk", "sudo apk add"),
    ;

    companion object {
        /** Ordered as they are probed; the first hit wins. */
        val probeOrder: List<PackageManager> = entries.toList()
    }
}
