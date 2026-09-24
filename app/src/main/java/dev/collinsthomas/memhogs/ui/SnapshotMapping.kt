package dev.collinsthomas.memhogs.ui

import dev.collinsthomas.memhogs.mem.AppGroup
import dev.collinsthomas.memhogs.mem.MeminfoParser
import dev.collinsthomas.memhogs.mem.groupByPackage

fun MeminfoParser.Snapshot.toUiSnapshot(labelOf: (String) -> String?, ownPackage: String): UiSnapshot {
    // The owner-package walk probes several candidate names per process,
    // so cache the answers for the duration of one snapshot.
    val labelCache = mutableMapOf<String, String?>()
    val groups = groupByPackage(processes) { pkg -> labelCache.getOrPut(pkg) { labelOf(pkg) } }
    return UiSnapshot(
        totalKb = totalRamKb,
        usedKb = usedRamKb,
        totalText = humanKb(totalRamKb),
        usedText = humanKb(usedRamKb),
        usedFrac = fractionOf(usedRamKb, totalRamKb).toFloat(),
        processCount = processes.size,
        groups = groups.map { it.toUiGroup(totalRamKb, ownPackage) },
    )
}

private fun fractionOf(kb: Long, totalKb: Long): Double = if (totalKb > 0) kb.toDouble() / totalKb else 0.0

private fun AppGroup.toUiGroup(totalRamKb: Long, ownPackage: String): UiGroup {
    val share = fractionOf(pssKb, totalRamKb)
    return UiGroup(
        key = packageName,
        label = label,
        isApp = isApp,
        mem = humanKb(pssKb),
        memKb = pssKb,
        pctFrac = share,
        pctText = percent(share),
        canReclaim = isApp && packageName != ownPackage,
        members = members.map { UiMember(name = it.processName, pid = it.pid, mem = humanKb(it.pssKb)) },
    )
}
