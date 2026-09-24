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
    val usedFraction: Float,
    val processCount: Int,
    val groups: List<UiGroup>,
)

data class UiGroup(
    val key: String,
    val label: String,
    val isApp: Boolean,
    val memText: String,
    val memKb: Long,
    val shareOfRam: Double,
    val shareText: String,
    val canReclaim: Boolean,
    val canForceStop: Boolean,
    val members: List<UiMember>,
)

data class UiMember(val name: String, val pid: Int, val memText: String)

sealed interface LoadError {
    data object EmptyMeminfo : LoadError
    data object ShellDidNotStart : LoadError
    data class Failed(val detail: String) : LoadError
}

sealed interface ReclaimResult {
    val labels: List<String>

    data class Reclaimed(override val labels: List<String>, val freedKb: Long) : ReclaimResult
    data class NothingToReclaim(override val labels: List<String>) : ReclaimResult
}
