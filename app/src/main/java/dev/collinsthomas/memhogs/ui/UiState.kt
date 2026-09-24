package dev.collinsthomas.memhogs.ui

import dev.collinsthomas.memhogs.shizuku.ShizukuAccess

data class MemhogsUiState(
    val access: ShizukuAccess = ShizukuAccess.NOT_RUNNING,
    val shizukuInstalled: Boolean = false,
    val motion: Boolean = true,
    val gauge: Gauge? = null,
    val snapshot: UiSnapshot? = null,
    val refreshing: Boolean = false,
    val error: LoadError? = null,
    val reclaimResult: ReclaimResult? = null,
)

data class Gauge(val totalBytes: Long, val availBytes: Long, val low: Boolean)

data class UiSnapshot(
    val totalKb: Long,
    val usedKb: Long,
    val totalText: String,
    val usedText: String,
    val usedFrac: Float,
    val processCount: Int,
    val groups: List<UiGroup>,
)

data class UiGroup(
    val key: String,
    val label: String,
    val isApp: Boolean,
    val mem: String,
    val memKb: Long,
    val pctFrac: Double,
    val pctText: String,
    /** True when `am kill` can safely reclaim this group's background memory. */
    val canReclaim: Boolean,
    val members: List<UiMember>,
)

data class UiMember(val name: String, val pid: Int, val mem: String)

sealed interface LoadError {
    data object EmptyMeminfo : LoadError
    data class Failed(val detail: String) : LoadError
}

sealed interface ReclaimResult {
    val label: String

    data class Reclaimed(override val label: String, val freedKb: Long) : ReclaimResult
    data class NothingToReclaim(override val label: String) : ReclaimResult
}
