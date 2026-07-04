package dev.collinsthomas.memhogs.mem

/**
 * Parses the output of `dumpsys meminfo` (no arguments), which reports PSS
 * per process. PSS charges memory shared by N processes as 1/N to each, the
 * same fair-share metric the memhogs CLI uses on Linux, so summing a group
 * of processes never counts a physical page twice.
 */
object MeminfoParser {

    data class ProcSample(val name: String, val pid: Int, val pssKb: Long)

    data class Snapshot(
        val totalRamKb: Long,
        val freeRamKb: Long,
        val usedRamKb: Long,
        val processes: List<ProcSample>,
    )

    // e.g. "    354,123K: com.android.chrome (pid 8001 / activities)"
    private val procLine = Regex("""^\s*([\d,]+)K:\s+(.+?)\s+\(pid\s+(\d+)[^)]*\)\s*$""")
    private val firstKb = Regex("""([\d,]+)K""")

    fun parse(text: String): Snapshot {
        val procs = mutableListOf<ProcSample>()
        var total = 0L
        var free = 0L
        var used = 0L
        var inProcSection = false

        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t == "Total PSS by process:") {
                inProcSection = true
                continue
            }
            // The process section ends at the first blank line or the next
            // "Total PSS by ..." header. The OOM-adjustment section that
            // follows uses the same line format, so the boundary matters.
            if (inProcSection && (t.isEmpty() || t.startsWith("Total PSS by"))) {
                inProcSection = false
            }
            if (inProcSection) {
                procLine.matchEntire(line)?.let { m ->
                    procs += ProcSample(
                        name = m.groupValues[2],
                        pid = m.groupValues[3].toInt(),
                        pssKb = kb(m.groupValues[1]),
                    )
                }
                continue
            }
            when {
                t.startsWith("Total RAM:") -> total = leadingKb(t)
                t.startsWith("Free RAM:") -> free = leadingKb(t)
                t.startsWith("Used RAM:") -> used = leadingKb(t)
            }
        }
        return Snapshot(total, free, used, procs.sortedByDescending { it.pssKb })
    }

    private fun kb(s: String): Long = s.replace(",", "").toLong()

    private fun leadingKb(s: String): Long =
        firstKb.find(s)?.groupValues?.get(1)?.let(::kb) ?: 0
}
