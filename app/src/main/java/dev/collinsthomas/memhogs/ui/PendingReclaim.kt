package dev.collinsthomas.memhogs.ui

data class ReclaimTarget(val packageName: String, val label: String, val beforeKb: Long)

data class PendingReclaim(val targets: List<ReclaimTarget>) {

    val packageNames: List<String> get() = targets.map { it.packageName }

    fun resultAgainst(after: UiSnapshot): ReclaimResult {
        val labels = targets.map { it.label }
        val freedKb = targets.sumOf { it.beforeKb - after.memKbOf(it.packageName) }
        return if (freedKb > 0) {
            ReclaimResult.Reclaimed(labels, freedKb)
        } else {
            ReclaimResult.NothingToReclaim(labels)
        }
    }

    companion object {
        fun of(snapshot: UiSnapshot, packageNames: Set<String>): PendingReclaim? = snapshot.groups
            .filter { it.canReclaim && it.key in packageNames }
            .map { ReclaimTarget(it.key, it.label, it.memKb) }
            .takeIf { it.isNotEmpty() }
            ?.let(::PendingReclaim)
    }
}

private fun UiSnapshot.memKbOf(packageName: String): Long = groups.find { it.key == packageName }?.memKb ?: 0L
