package app.terminalssh.secure.tools

/**
 * Read-only server resource snapshot (F041).
 *
 * Everything here is a pure function over command output, so the parsing is unit-tested
 * against real `free`/`df`/`uptime` shapes without a server. The command is deliberately
 * the cheapest portable set: `/proc` for memory and load average, `df -Pk` for the root
 * filesystem, `uname` for the OS. Nothing writes, nothing installs, and an unsupported
 * platform produces an explicit [OsFamily.UNKNOWN] rather than a fabricated zero.
 */
object ServerResources {

    const val MARKER = "\u0000" // never appears in output; used to split sections

    enum class OsFamily { LINUX, MACOS, BSD, UNKNOWN }

    data class Snapshot(
        val os: OsFamily = OsFamily.UNKNOWN,
        val load1: Double? = null,
        val load5: Double? = null,
        val load15: Double? = null,
        val memTotalKb: Long? = null,
        val memAvailableKb: Long? = null,
        val diskTotalKb: Long? = null,
        val diskUsedKb: Long? = null,
        val uptime: String = "",
        val rawError: String = "",
    ) {
        val supported: Boolean get() = os == OsFamily.LINUX

        val memUsedKb: Long?
            get() = if (memTotalKb != null && memAvailableKb != null) memTotalKb - memAvailableKb else null

        val memoryUsedPercent: Int?
            get() {
                val total = memTotalKb ?: return null
                val used = memUsedKb ?: return null
                if (total <= 0) return null
                return ((used * 100) / total).toInt().coerceIn(0, 100)
            }

        val diskUsedPercent: Int?
            get() {
                val total = diskTotalKb ?: return null
                val used = diskUsedKb ?: return null
                if (total <= 0) return null
                return ((used * 100) / total).toInt().coerceIn(0, 100)
            }
    }

    fun command(): String = buildString {
        append("LC_ALL=C; ")
        append("uname -s 2>/dev/null; echo ").append(quote(MARKER)).append("; ")
        append("cat /proc/loadavg 2>/dev/null; echo ").append(quote(MARKER)).append("; ")
        append("grep -E '^(MemTotal|MemAvailable):' /proc/meminfo 2>/dev/null; echo ").append(quote(MARKER)).append("; ")
        append("df -Pk / 2>/dev/null | tail -n +2; echo ").append(quote(MARKER)).append("; ")
        append("uptime 2>/dev/null; echo ").append(quote(MARKER))
    }

    fun parse(output: String): Snapshot {
        val parts = output.split(MARKER)
        if (parts.size < 5) return Snapshot(rawError = output.trim())
        val os = detectOs(parts[0])
        val load = parseLoad(parts[1])
        val mem = parseMem(parts[2])
        val disk = parseDisk(parts[3])
        return Snapshot(
            os = os,
            load1 = load.getOrNull(0),
            load5 = load.getOrNull(1),
            load15 = load.getOrNull(2),
            memTotalKb = mem["MemTotal"],
            memAvailableKb = mem["MemAvailable"] ?: mem["MemFree"],
            diskTotalKb = disk?.first,
            diskUsedKb = disk?.second,
            uptime = parts[4].trim().trimEnd(',', '.'),
        )
    }

    fun detectOs(unameOutput: String): OsFamily {
        val name = unameOutput.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        return when {
            name.startsWith("Linux", ignoreCase = true) -> OsFamily.LINUX
            name.startsWith("Darwin", ignoreCase = true) -> OsFamily.MACOS
            name.contains("BSD", ignoreCase = true) -> OsFamily.BSD
            else -> OsFamily.UNKNOWN
        }
    }

    private fun parseLoad(section: String): List<Double> {
        val first = section.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return emptyList()
        return first.split(' ').take(3).mapNotNull { it.toDoubleOrNull() }
    }

    private fun parseMem(section: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (line in section.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val colon = trimmed.indexOf(':')
            if (colon <= 0) continue
            val key = trimmed.substring(0, colon)
            val value = trimmed.substring(colon + 1).trim().removeSuffix("kB").trim().toLongOrNull()
            if (value != null) out[key] = value
        }
        return out
    }

    private fun parseDisk(section: String): Pair<Long, Long>? {
        val line = section.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        // df -Pk: Filesystem 1024-blocks Used Available Capacity Mounted-on
        val cols = line.split(Regex("\\s+"))
        if (cols.size < 4) return null
        val total = cols[1].toLongOrNull() ?: return null
        val used = cols[2].toLongOrNull() ?: return null
        return total to used
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
