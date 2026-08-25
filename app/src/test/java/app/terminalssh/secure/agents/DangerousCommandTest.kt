package app.terminalssh.secure.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DangerousCommandTest {

    @Test fun catchesRecursiveDeletesOfRootPaths() {
        listOf(
            "rm -rf /",
            "rm -rf /etc",
            "sudo rm -rf /var",
            "rm -fr /",
        ).forEach { assertTrue(DangerousCommand.isDangerous(it), "missed: $it") }
    }

    @Test fun catchesHomeDirectoryDeletes() {
        listOf("rm -rf ~", "rm -rf \$HOME", "rm -rf ~/").forEach {
            assertTrue(DangerousCommand.isDangerous(it), "missed: $it")
        }
    }

    @Test fun catchesDiskAndFilesystemDestruction() {
        assertTrue(DangerousCommand.isDangerous("dd if=/dev/zero of=/dev/sda bs=1M"))
        assertTrue(DangerousCommand.isDangerous("mkfs.ext4 /dev/sdb1"))
        assertTrue(DangerousCommand.isDangerous("mkfs /dev/sdb1"))
    }

    @Test fun catchesGitCommandsThatDiscardWork() {
        assertTrue(DangerousCommand.isDangerous("git reset --hard HEAD~3"))
        assertTrue(DangerousCommand.isDangerous("git clean -fd"))
        assertTrue(DangerousCommand.isDangerous("git push --force origin main"))
        assertTrue(DangerousCommand.isDangerous("git push -f"))
    }

    @Test fun forceWithLeaseIsNotTreatedAsAForcePush() {
        // --force-with-lease refuses when the remote moved, which is the safe form and
        // must not be trained out of people by an unnecessary prompt.
        assertFalse(DangerousCommand.isDangerous("git push --force-with-lease origin main"))
    }

    @Test fun catchesDatabaseDrops() {
        assertTrue(DangerousCommand.isDangerous("psql -c 'DROP DATABASE production'"))
        assertTrue(DangerousCommand.isDangerous("mysql -e \"drop table users\""))
    }

    @Test fun catchesDisruptiveButReversibleCommands() {
        listOf(
            "sudo reboot",
            "shutdown -h now",
            "systemctl stop nginx",
            "chmod -R 777 /var/www",
            "kill -9 -1",
        ).forEach {
            val finding = DangerousCommand.inspect(it)
            assertNotNull(finding, "missed: $it")
        }
    }

    @Test fun destructiveOutranksDisruptiveWhenBothMatch() {
        val finding = DangerousCommand.inspect("systemctl stop nginx && rm -rf /var")
        assertEquals(DangerousCommand.Severity.DESTRUCTIVE, finding?.severity)
    }

    @Test fun everydayCommandsAreNotFlagged() {
        // Prompting for ordinary work trains people to dismiss the prompt, which is worse
        // than never showing one.
        listOf(
            "ls -la",
            "rm -rf ./build",
            "rm -rf node_modules",
            "rm -rf target/classes",
            "git status",
            "git commit -am 'fix'",
            "git push origin feature",
            "systemctl status nginx",
            "systemctl restart nginx",
            "chmod 644 file.txt",
            "chmod +x script.sh",
            "docker ps",
            "tail -f /var/log/syslog",
            "grep -r pattern .",
            "cd /var/www && ls",
            "SELECT * FROM users",
        ).forEach {
            assertFalse(DangerousCommand.isDangerous(it), "false positive on: $it")
        }
    }

    @Test fun relativeAndProjectLocalDeletesAreNotFlagged() {
        // The overwhelmingly common case: cleaning a build directory.
        listOf(
            "rm -rf dist",
            "rm -rf ./tmp",
            "rm -rf ~/project/build",
            "rm -rf \$HOME/project/node_modules",
        ).forEach { assertFalse(DangerousCommand.isDangerous(it), "false positive on: $it") }
    }

    @Test fun blankInputIsNotDangerous() {
        assertFalse(DangerousCommand.isDangerous(""))
        assertFalse(DangerousCommand.isDangerous("   "))
        assertFalse(DangerousCommand.isDangerous("\n"))
    }

    @Test fun findingsExplainThemselves() {
        val finding = DangerousCommand.inspect("rm -rf /")
        assertNotNull(finding)
        assertTrue(finding.reason.isNotBlank(), "a prompt with no reason is just an obstacle")
    }
}
