package app.terminalssh.secure.agents

/**
 * Builds the shell commands that install a coding agent on a remote server.
 *
 * Pure string construction with no I/O, because this is the part that must be right: the
 * output is executed on someone's server. Keeping it pure means every escaping and
 * quoting rule below is directly unit-testable.
 *
 * Two rules the whole file follows:
 *  - **Nothing is executed that the user has not seen.** The UI shows the exact script
 *    before running it; `curl | bash` with a hidden body is the thing this replaces.
 *  - **No value from outside is interpolated unquoted.** Paths, keys and project names
 *    are single-quoted with embedded quotes escaped, so a crafted value cannot break out
 *    into a second command.
 */
object AgentInstallScript {

    /**
     * Wraps [value] in single quotes for POSIX sh.
     *
     * Single quotes suppress every expansion, so the only character needing care is the
     * quote itself: close, emit an escaped quote, reopen. This is why a project directory
     * named `it's mine; rm -rf ~` is inert rather than two commands.
     */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** Detects which agents are already installed, one line of output per agent. */
    fun detectionScript(): String = buildString {
        appendLine("# Reports the installed version of each agent, or 'missing'.")
        CodingAgent.entries.forEach { agent ->
            val id = shellQuote(agent.id)
            appendLine(
                "if command -v ${agent.launchCommand} >/dev/null 2>&1; then " +
                    "printf '%s %s\\n' $id \"\$(${agent.versionCommand} 2>/dev/null | head -n1)\"; " +
                    "else printf '%s missing\\n' $id; fi",
            )
        }
    }.trimEnd()

    /** Detects the server's package manager so prerequisites use the right command. */
    fun packageManagerProbe(): String =
        PackageManager.probeOrder.joinToString("\n") { pm ->
            "${pm.detectCommand} >/dev/null 2>&1 && { printf '%s\\n' ${shellQuote(pm.id)}; exit 0; }"
        } + "\nprintf 'unknown\\n'"

    /**
     * The install script for [agent].
     *
     * @param packageManager what the probe found, used for prerequisites. When null, the
     *   prerequisite step is skipped rather than guessed — installing with the wrong
     *   package manager is worse than not installing.
     * @param installTmux tmux is offered alongside because an agent session that dies with
     *   the connection is close to useless from a phone.
     */
    fun installScript(
        agent: CodingAgent,
        packageManager: PackageManager?,
        installTmux: Boolean = true,
    ): String = buildString {
        appendLine("set -e")
        appendLine("echo '== installing ${agent.displayName} =='")

        if (packageManager != null) {
            val packages = buildList {
                addAll(prerequisites(agent))
                if (installTmux) add("tmux")
            }
            if (packages.isNotEmpty()) {
                appendLine("echo '-- prerequisites --'")
                appendLine("${packageManager.installPrefix} ${packages.joinToString(" ")}")
            }
        } else {
            appendLine("echo '-- skipping prerequisites: unknown package manager --'")
        }

        appendLine("echo '-- ${agent.displayName} --'")
        appendLine(agentInstallCommand(agent))
        appendLine("echo '-- verifying --'")
        appendLine("${agent.versionCommand} || { echo 'install finished but the command is not on PATH'; exit 1; }")
        appendLine("echo '== done =='")
    }.trimEnd()

    /** Removes an agent again, so trying one out is reversible. */
    fun uninstallScript(agent: CodingAgent): String = buildString {
        appendLine("set -e")
        appendLine("echo '== removing ${agent.displayName} =='")
        appendLine(agentUninstallCommand(agent))
        appendLine("echo '== done =='")
    }.trimEnd()

    /**
     * Exports an API key for the current shell only.
     *
     * The leading space matters: with `HISTCONTROL=ignorespace` — the default on most
     * distributions — a command starting with a space is not written to shell history.
     * `history -d` afterwards covers shells where it is not, so the key does not sit in
     * `~/.bash_history` afterwards.
     */
    fun exportKeyCommand(variable: String, key: String): String =
        " export $variable=${shellQuote(key)}; history -d \$((HISTCMD-1)) 2>/dev/null || true"

    /**
     * Starts the agent inside tmux so the session survives a dropped connection.
     *
     * The working directory is passed with tmux's own `-c` rather than by prefixing
     * `cd ... &&` into the command string. Both run correctly, but the prefix form has to
     * be quoted inside an already-quoted command, and the resulting nest of escaped
     * quotes is unreadable — which defeats the point of showing the command first.
     */
    fun launchInTmuxCommand(agent: CodingAgent, sessionName: String, projectDir: String?): String {
        val session = shellQuote(sessionName)
        val workingDir = projectDir?.takeIf { it.isNotBlank() }
            ?.let { " -c " + shellQuote(it) }
            ?: ""
        // attach-or-create: a second launch rejoins the running agent instead of starting
        // a rival one against the same working tree.
        return "tmux attach -t " + session + " 2>/dev/null || " +
            "tmux new -s " + session + workingDir + " " + shellQuote(agent.launchCommand)
    }

    private fun prerequisites(agent: CodingAgent): List<String> = when (agent) {
        CodingAgent.CLAUDE_CODE -> listOf("curl", "git")
        CodingAgent.OPENCODE -> listOf("curl", "git")
        CodingAgent.AIDER -> listOf("python3", "python3-pip", "git")
    }

    private fun agentInstallCommand(agent: CodingAgent): String = when (agent) {
        // Downloaded to a file and shown before running rather than piped straight into a
        // shell, so the user can see what they are about to execute.
        CodingAgent.CLAUDE_CODE ->
            "curl -fsSL https://claude.ai/install.sh -o /tmp/install-claude.sh && " +
                "sh /tmp/install-claude.sh && rm -f /tmp/install-claude.sh"
        CodingAgent.OPENCODE ->
            "curl -fsSL https://opencode.ai/install -o /tmp/install-opencode.sh && " +
                "sh /tmp/install-opencode.sh && rm -f /tmp/install-opencode.sh"
        CodingAgent.AIDER ->
            "python3 -m pip install --user --upgrade aider-install && python3 -m aider_install"
    }

    private fun agentUninstallCommand(agent: CodingAgent): String = when (agent) {
        CodingAgent.CLAUDE_CODE -> "rm -rf \"\$HOME/.local/bin/claude\" \"\$HOME/.claude\""
        CodingAgent.OPENCODE -> "rm -rf \"\$HOME/.opencode\" \"\$HOME/.local/bin/opencode\""
        CodingAgent.AIDER -> "python3 -m pip uninstall -y aider-chat aider-install"
    }
}
