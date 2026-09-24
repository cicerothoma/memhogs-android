package dev.collinsthomas.memhogs.ui

import dev.collinsthomas.memhogs.mem.AppGroup
import dev.collinsthomas.memhogs.mem.MeminfoParser
import dev.collinsthomas.memhogs.mem.groupByOwner

fun MeminfoParser.Snapshot.toUiSnapshot(appLabelOf: (String) -> String?, ownPackage: String): UiSnapshot {
    val groups = groupByOwner(processes, memoize(appLabelOf))
    return UiSnapshot(
        totalKb = totalRamKb,
        usedKb = usedRamKb,
        totalText = humanReadableKb(totalRamKb),
        usedText = humanReadableKb(usedRamKb),
        usedFraction = fractionOf(usedRamKb, totalRamKb).toFloat(),
        processCount = processes.size,
        groups = groups.map { it.toUiGroup(totalRamKb, ownPackage) },
    )
}

private fun <K, V> memoize(compute: (K) -> V): (K) -> V {
    val cache = mutableMapOf<K, V>()
    return { key ->
        if (key in cache) cache.getValue(key) else compute(key).also { cache[key] = it }
    }
}

private fun fractionOf(kb: Long, totalKb: Long): Double = if (totalKb > 0) kb.toDouble() / totalKb else 0.0

private fun AppGroup.toUiGroup(totalRamKb: Long, ownPackage: String): UiGroup {
    val share = fractionOf(pssKb, totalRamKb)
    return UiGroup(
        key = owner,
        label = label,
        isApp = isApp,
        memText = humanReadableKb(pssKb),
        memKb = pssKb,
        shareOfRam = share,
        shareText = percent(share),
        canReclaim = isApp && owner != ownPackage,
        members = members.map { UiMember(name = it.processName, pid = it.pid, memText = humanReadableKb(it.pssKb)) },
    )
}
