package dev.collinsthomas.memhogs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingReclaimTest {

    private val chrome = ReclaimTarget(packageName = "com.android.chrome", label = "Chrome", beforeKb = 200_000)
    private val maps = ReclaimTarget(packageName = "com.google.android.apps.maps", label = "Maps", beforeKb = 100_000)

    @Test
    fun reportsTheMemoryThatCameBack() {
        assertEquals(
            ReclaimResult.Reclaimed(listOf("Chrome"), 120_000),
            PendingReclaim(listOf(chrome)).resultAgainst(snapshotWithGroups(group(chrome, memKb = 80_000))),
        )
    }

    @Test
    fun reportsNothingWhenUsageDidNotDrop() {
        assertEquals(
            ReclaimResult.NothingToReclaim(listOf("Chrome")),
            PendingReclaim(listOf(chrome)).resultAgainst(snapshotWithGroups(group(chrome, memKb = 210_000))),
        )
    }

    @Test
    fun countsAVanishedAppAsFullyReclaimed() {
        assertEquals(
            ReclaimResult.Reclaimed(listOf("Chrome"), 200_000),
            PendingReclaim(listOf(chrome)).resultAgainst(snapshotWithGroups()),
        )
    }

    @Test
    fun addsUpTheMemoryFreedAcrossEveryTarget() {
        val after = snapshotWithGroups(group(chrome, memKb = 150_000), group(maps, memKb = 110_000))
        assertEquals(
            ReclaimResult.Reclaimed(listOf("Chrome", "Maps"), 40_000),
            PendingReclaim(listOf(chrome, maps)).resultAgainst(after),
        )
    }

    @Test
    fun targetsOnlyTheSelectedGroupsThatCanBeReclaimed() {
        val snapshot = snapshotWithGroups(
            group(chrome, memKb = 200_000),
            group(maps, memKb = 100_000),
            group(ReclaimTarget("surfaceflinger", "surfaceflinger", 0), memKb = 90_000, canReclaim = false),
        )
        assertEquals(
            PendingReclaim(listOf(chrome)),
            PendingReclaim.of(snapshot, setOf("com.android.chrome", "surfaceflinger")),
        )
    }

    @Test
    fun hasNothingToTargetWhenNoSelectedGroupCanBeReclaimed() {
        val snapshot = snapshotWithGroups(
            group(ReclaimTarget("surfaceflinger", "surfaceflinger", 0), memKb = 90_000, canReclaim = false),
        )
        assertNull(PendingReclaim.of(snapshot, setOf("surfaceflinger", "com.gone.app")))
    }

    private fun group(target: ReclaimTarget, memKb: Long, canReclaim: Boolean = true) = UiGroup(
        key = target.packageName,
        label = target.label,
        isApp = canReclaim,
        memText = "",
        memKb = memKb,
        shareOfRam = 0.0,
        shareText = "",
        canReclaim = canReclaim,
        members = emptyList(),
    )

    private fun snapshotWithGroups(vararg groups: UiGroup) = UiSnapshot(
        totalKb = 0,
        usedKb = 0,
        totalText = "",
        usedText = "",
        usedFraction = 0f,
        processCount = 0,
        groups = groups.toList(),
    )
}
