package dev.collinsthomas.memhogs.mem

object MeminfoParser {

    data class ProcessSample(val name: String, val pid: Int, val pssKb: Long)

    data class Snapshot(
        val totalRamKb: Long,
        val freeRamKb: Long,
        val usedRamKb: Long,
        val processes: List<ProcessSample>,
    )

    private const val PROCESS_SECTION_HEADER = "Total PSS by process:"
    private const val ANY_SECTION_HEADER_PREFIX = "Total PSS by"

    private val processLine = Regex("""([\d,]+)K:\s+(.+?)\s+\(pid\s+(\d+)[^)]*\)""")
    private val kbValue = Regex("""([\d,]+)K""")

    fun parse(text: String): Snapshot {
        val lines = text.lines().map(String::trim)
        return Snapshot(
            totalRamKb = kbOnLineStartingWith("Total RAM:", lines),
            freeRamKb = kbOnLineStartingWith("Free RAM:", lines),
            usedRamKb = kbOnLineStartingWith("Used RAM:", lines),
            processes = processSectionOf(lines)
                .mapNotNull(::parseProcessLine)
                .sortedByDescending { it.pssKb },
        )
    }

    private fun processSectionOf(lines: List<String>): List<String> = lines
        .dropWhile { it != PROCESS_SECTION_HEADER }
        .drop(1)
        .takeWhile { it.isNotEmpty() && !it.startsWith(ANY_SECTION_HEADER_PREFIX) }

    private fun parseProcessLine(line: String): ProcessSample? = processLine.matchEntire(line)?.let { match ->
        val (pss, name, pid) = match.destructured
        ProcessSample(name = name, pid = pid.toInt(), pssKb = parseKb(pss))
    }

    private fun kbOnLineStartingWith(label: String, lines: List<String>): Long {
        val line = lines.firstOrNull { it.startsWith(label) } ?: return 0
        val digits = kbValue.find(line)?.groupValues?.get(1) ?: return 0
        return parseKb(digits)
    }

    private fun parseKb(digits: String): Long = digits.replace(",", "").toLong()
}
