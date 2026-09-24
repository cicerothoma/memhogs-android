package dev.collinsthomas.memhogs.ui

data class PendingReclaim(val packageName: String, val label: String, val beforeKb: Long) {

    fun resultAgainst(after: UiSnapshot): ReclaimResult {
        val afterKb = after.groups.find { it.key == packageName }?.memKb ?: 0L
        val freedKb = beforeKb - afterKb
        return if (freedKb > 0) {
            ReclaimResult.Reclaimed(label, freedKb)
        } else {
            ReclaimResult.NothingToReclaim(label)
        }
    }
}
