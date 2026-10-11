package app.terminalssh.secure.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerResourcesTest {

    private val marker = ServerResources.MARKER

    private val linuxSample = listOf(
        "Linux",
        "0.15 0.10 0.05 1/234 5678",
        "MemTotal:        2048000 kB\nMemAvailable:     512000 kB",
        "/dev/sda1      10240000   4096000   6144000  40% /",
        " 10:00:00 up 3 days,  2:14,  1 user,  load average: 0.15, 0.10, 0.05",
        "",
    ).joinToString(marker)

    @Test fun parsesLinuxSnapshot() {
        val snap = ServerResources.parse(linuxSample)
        assertEquals(ServerResources.OsFamily.LINUX, snap.os)
        assertTrue(snap.supported)
        assertEquals(0.15, snap.load1)
        assertEquals(0.10, snap.load5)
        assertEquals(0.05, snap.load15)
        assertEquals(2048000L, snap.memTotalKb)
        assertEquals(512000L, snap.memAvailableKb)
        assertEquals(10240000L, snap.diskTotalKb)
        assertEquals(4096000L, snap.diskUsedKb)
        assertTrue(snap.uptime.contains("up 3 days"))
    }

    @Test fun computesPercentages() {
        val snap = ServerResources.parse(linuxSample)
        // used = 2048000 - 512000 = 1536000 -> 75%
        assertEquals(75, snap.memoryUsedPercent)
        // 4096000 / 10240000 -> 40%
        assertEquals(40, snap.diskUsedPercent)
    }

    @Test fun detectsOtherOperatingSystems() {
        assertEquals(ServerResources.OsFamily.MACOS, ServerResources.detectOs("Darwin\n"))
        assertEquals(ServerResources.OsFamily.BSD, ServerResources.detectOs("FreeBSD\n"))
        assertEquals(ServerResources.OsFamily.UNKNOWN, ServerResources.detectOs(""))
        assertFalse(ServerResources.Snapshot(os = ServerResources.OsFamily.MACOS).supported)
    }

    @Test fun missingSectionsDoNotCrash() {
        val partial = listOf("Linux", "0.01 0.02 0.03 1/1 1", "", "", "", "").joinToString(marker)
        val snap = ServerResources.parse(partial)
        assertEquals(ServerResources.OsFamily.LINUX, snap.os)
        assertEquals(0.01, snap.load1)
        assertEquals(null, snap.memTotalKb)
        assertEquals(null, snap.memoryUsedPercent)
    }

    @Test fun nonMarkerOutputIsReportedAsAnError() {
        val snap = ServerResources.parse("bash: cat: command not found")
        assertTrue(snap.rawError.isNotEmpty())
        assertEquals(ServerResources.OsFamily.UNKNOWN, snap.os)
    }

    @Test fun commandIsReadOnly() {
        val cmd = ServerResources.command()
        assertTrue(cmd.contains("uname -s"))
        assertTrue(cmd.contains("df -Pk"))
        // Nothing that writes or installs.
        assertFalse(cmd.contains("apt"))
        assertFalse(cmd.contains("rm "))
    }
}
