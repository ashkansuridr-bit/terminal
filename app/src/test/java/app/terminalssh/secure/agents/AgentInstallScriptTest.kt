package app.terminalssh.secure.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentInstallScriptTest {

    // ---- quoting: the part that runs on someone's server ----

    @Test fun plainValuesAreSingleQuoted() {
        assertEquals("'project'", AgentInstallScript.shellQuote("project"))
        assertEquals("'/var/www/site'", AgentInstallScript.shellQuote("/var/www/site"))
    }

    @Test fun embeddedSingleQuoteCannotEscapeTheQuoting() {
        // The classic break-out: close, escaped quote, reopen.
        assertEquals("""'it'\''s mine'""", AgentInstallScript.shellQuote("it's mine"))
    }

    @Test fun commandSeparatorsStayInert() {
        val dangerous = listOf(
            "a; rm -rf /",
            "a && rm -rf /",
            "a | tee /etc/passwd",
            "a\nrm -rf /",
            "\$(rm -rf /)",
            "`rm -rf /`",
            "a > /etc/hosts",
        )
        dangerous.forEach { value ->
            val quoted = AgentInstallScript.shellQuote(value)
            assertTrue(quoted.startsWith("'"), "$value did not start quoted")
            assertTrue(quoted.endsWith("'"), "$value did not end quoted")
            // Inside single quotes nothing expands, so the only way out is an unescaped
            // quote — and there must be none.
            val inner = quoted.substring(1, quoted.length - 1)
            assertFalse(
                Regex("(?<!\\\\)'(?!\\\\'')").containsMatchIn(inner.replace("'\\''", "")),
                "$value produced an unescaped quote",
            )
        }
    }

    @Test fun quotingIsIdempotentlySafeForEmptyAndWhitespace() {
        assertEquals("''", AgentInstallScript.shellQuote(""))
        assertEquals("'   '", AgentInstallScript.shellQuote("   "))
    }

    // ---- detection ----

    @Test fun detectionCoversEveryAgent() {
        val script = AgentInstallScript.detectionScript()
        CodingAgent.entries.forEach { agent ->
            assertTrue(agent.id in script, "${agent.id} is not detected")
            assertTrue(agent.launchCommand in script, "${agent.launchCommand} is not probed")
        }
    }

    @Test fun detectionReportsMissingRatherThanFailing() {
        // A missing agent must produce a line, not a non-zero exit that aborts the probe.
        val script = AgentInstallScript.detectionScript()
        assertTrue("missing" in script)
        assertFalse("set -e" in script, "detection must not abort on the first missing agent")
    }

    @Test fun packageManagerProbeFallsBackToUnknown() {
        val probe = AgentInstallScript.packageManagerProbe()
        PackageManager.entries.forEach { assertTrue(it.id in probe, "${it.id} is not probed") }
        assertTrue(probe.trimEnd().endsWith("'unknown'\n".trimEnd()) || "unknown" in probe)
    }

    // ---- install ----

    @Test fun installScriptStopsOnTheFirstFailure() {
        val script = AgentInstallScript.installScript(CodingAgent.CLAUDE_CODE, PackageManager.APT)
        assertTrue(script.startsWith("set -e"), "a failed step must not continue to the next")
    }

    @Test fun installScriptVerifiesTheCommandIsActuallyOnPath() {
        CodingAgent.entries.forEach { agent ->
            val script = AgentInstallScript.installScript(agent, PackageManager.APT)
            assertTrue(agent.versionCommand in script, "${agent.id} is never verified")
        }
    }

    @Test fun installerIsDownloadedThenRunRatherThanPipedIntoAShell() {
        // The point of the whole feature: the user can read what they are about to run.
        listOf(CodingAgent.CLAUDE_CODE, CodingAgent.OPENCODE).forEach { agent ->
            val script = AgentInstallScript.installScript(agent, PackageManager.APT)
            assertFalse(
                Regex("curl[^\\n]*\\|\\s*(ba)?sh").containsMatchIn(script),
                "${agent.id} pipes a remote script straight into a shell",
            )
        }
    }

    @Test fun unknownPackageManagerSkipsPrerequisitesInsteadOfGuessing() {
        val script = AgentInstallScript.installScript(CodingAgent.AIDER, packageManager = null)
        assertFalse("apt-get" in script)
        assertFalse("dnf" in script)
        assertTrue("skipping prerequisites" in script)
        // The agent itself is still installed.
        assertTrue("aider" in script)
    }

    @Test fun eachPackageManagerUsesItsOwnInstallCommand() {
        assertTrue("apt-get" in AgentInstallScript.installScript(CodingAgent.CLAUDE_CODE, PackageManager.APT))
        assertTrue("dnf" in AgentInstallScript.installScript(CodingAgent.CLAUDE_CODE, PackageManager.DNF))
        assertTrue("pacman" in AgentInstallScript.installScript(CodingAgent.CLAUDE_CODE, PackageManager.PACMAN))
        assertTrue("apk" in AgentInstallScript.installScript(CodingAgent.CLAUDE_CODE, PackageManager.APK))
    }

    @Test fun tmuxIsIncludedByDefaultAndCanBeDeclined() {
        assertTrue("tmux" in AgentInstallScript.installScript(CodingAgent.CLAUDE_CODE, PackageManager.APT))
        val without = AgentInstallScript.installScript(
            CodingAgent.CLAUDE_CODE, PackageManager.APT, installTmux = false,
        )
        assertFalse("tmux" in without)
    }

    @Test fun everyAgentCanBeUninstalled() {
        CodingAgent.entries.forEach { agent ->
            val script = AgentInstallScript.uninstallScript(agent)
            assertTrue(script.isNotBlank(), "${agent.id} has no uninstall path")
            assertTrue("set -e" in script)
        }
    }

    @Test fun uninstallNeverTargetsAnUnboundedPath() {
        // A stray "rm -rf $HOME" or "rm -rf /" would be catastrophic and is easy to
        // introduce when the variable is empty.
        CodingAgent.entries.forEach { agent ->
            val script = AgentInstallScript.uninstallScript(agent)
            assertFalse(Regex("""rm -rf\s+/\s*($|\n)""").containsMatchIn(script), "${agent.id}: bare rm -rf /")
            val homePattern = Regex("rm -rf\\s+\"?\\\u0024HOME\"?\\s*(\u0024|\\n)")
            assertFalse(homePattern.containsMatchIn(script), "${agent.id}: unbounded rm -rf of HOME")
        }
    }

    // ---- API key ----

    @Test fun keyExportStartsWithASpaceToStayOutOfHistory() {
        val command = AgentInstallScript.exportKeyCommand("ANTHROPIC_API_KEY", "sk-test-123")
        assertTrue(command.startsWith(" "), "a key export must not land in shell history")
    }

    @Test fun keyExportQuotesTheKeyAndScrubsHistory() {
        val command = AgentInstallScript.exportKeyCommand("ANTHROPIC_API_KEY", "sk-a'b;c")
        assertTrue("""'sk-a'\''b;c'""" in command, "the key was not safely quoted: $command")
        assertTrue("history -d" in command, "history is not scrubbed as a fallback")
    }

    @Test fun keyExportSurvivesAKeyThatLooksLikeAShellCommand() {
        val command = AgentInstallScript.exportKeyCommand("K", "\$(curl evil.example)")
        // Inside single quotes, command substitution is literal text.
        assertTrue("'\$(curl evil.example)'" in command, command)
    }

    // ---- launching ----

    @Test fun launchAttachesToAnExistingSessionBeforeCreatingOne() {
        val command = AgentInstallScript.launchInTmuxCommand(CodingAgent.CLAUDE_CODE, "dev", "/srv/app")
        assertTrue(command.startsWith("tmux attach"), "a second launch must rejoin, not duplicate")
        assertTrue("tmux new -s" in command)
    }

    @Test fun launchQuotesTheProjectDirectory() {
        val command = AgentInstallScript.launchInTmuxCommand(
            CodingAgent.CLAUDE_CODE, "dev", "/srv/it's mine",
        )
        assertTrue("""'/srv/it'\''s mine'""" in command, command)
    }

    @Test fun launchPassesTheDirectoryToTmuxRatherThanPrefixingCd() {
        // Prefixing "cd ... &&" means quoting a quoted string, and the resulting nest of
        // escaped quotes is unreadable — which defeats showing the command to the user.
        val command = AgentInstallScript.launchInTmuxCommand(
            CodingAgent.CLAUDE_CODE, "dev", "/srv/it's mine",
        )
        assertTrue(" -c " in command, command)
        assertFalse("cd " in command, "the working directory is prefixed instead of passed: $command")
        assertFalse(NESTED_QUOTES in command, "quoting was nested: $command")
    }

    @Test fun launchWithoutAProjectDirectoryOmitsTheChangeDirectory() {
        val command = AgentInstallScript.launchInTmuxCommand(CodingAgent.OPENCODE, "dev", null)
        assertFalse("cd " in command)
        assertTrue("opencode" in command)
        // A blank string is the same as none, not an empty cd.
        assertFalse("cd " in AgentInstallScript.launchInTmuxCommand(CodingAgent.OPENCODE, "dev", "   "))
    }

    @Test fun launchQuotesTheSessionName() {
        val command = AgentInstallScript.launchInTmuxCommand(CodingAgent.AIDER, "my session", null)
        assertTrue("'my session'" in command, command)
    }

    @Test fun agentIdsAreUniqueAndResolvable() {
        val ids = CodingAgent.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, CodingAgent.byId(it)?.id) }
        assertEquals(null, CodingAgent.byId("not-an-agent"))
    }

    private companion object {
        /**
         * The signature of double-escaped quoting — an escaped quote nested inside
         * another. Valid shell, but unreadable, and the user is meant to read the command
         * before running it.
         */
        const val NESTED_QUOTES = """'\''\''"""
    }
}
