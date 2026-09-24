package dev.collinsthomas.memhogs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingReclaimTest {

    private val pending = PendingReclaim(packageName = "com.android.chrome", label = "Chrome", beforeKb = 200_000)

    @Test
    fun reportsTheMemoryThatCameBack() {
        assertEquals(
            ReclaimResult.Reclaimed("Chrome", 120_000),
            pending.resultAgainst(snapshotWithChromeAt(80_000)),
        )
    }

    @Test
    fun reportsNothingWhenUsageDidNotDrop() {
        assertEquals(
            ReclaimResult.NothingToReclaim("Chrome"),
            pending.resultAgainst(snapshotWithChromeAt(210_000)),
        )
    }

    @Test
    fun countsAVanishedAppAsFullyReclaimed() {
        assertEquals(
            ReclaimResult.Reclaimed("Chrome", 200_000),
            pending.resultAgainst(snapshotWithGroups(emptyList())),
        )
    }

    private fun snapshotWithChromeAt(memKb: Long) = snapshotWithGroups(
        listOf(
            UiGroup(
                key = "com.android.chrome",
                label = "Chrome",
                isApp = true,
                mem = "",
                memKb = memKb,
                pctFrac = 0.0,
                pctText = "",
                canReclaim = true,
                members = emptyList(),
            ),
        ),
    )

    private fun snapshotWithGroups(groups: List<UiGroup>) = UiSnapshot(
        totalKb = 0,
        usedKb = 0,
        totalText = "",
        usedText = "",
        usedFrac = 0f,
        processCount = 0,
        groups = groups,
    )
}
