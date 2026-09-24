package dev.collinsthomas.memhogs.ui

import dev.collinsthomas.memhogs.mem.MeminfoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotMappingTest {

    private val parsed = MeminfoParser.Snapshot(
        totalRamKb = 8_000_000,
        freeRamKb = 3_000_000,
        usedRamKb = 5_000_000,
        processes = listOf(
            MeminfoParser.ProcSample("com.android.chrome", 8001, 1_200_000),
            MeminfoParser.ProcSample("com.android.chrome:privileged_process0", 8100, 400_000),
            MeminfoParser.ProcSample("system", 1780, 300_000),
            MeminfoParser.ProcSample(OWN_PACKAGE, 9100, 50_000),
        ),
    )

    private val labels = mapOf(
        "com.android.chrome" to "Chrome",
        OWN_PACKAGE to "memhogs",
    )

    private val snapshot = parsed.toUiSnapshot(labelOf = { labels[it] }, ownPackage = OWN_PACKAGE)

    @Test
    fun carriesDeviceTotals() {
        assertEquals("7.6 GiB", snapshot.totalText)
        assertEquals("4.8 GiB", snapshot.usedText)
        assertEquals(0.625f, snapshot.usedFrac)
        assertEquals(4, snapshot.processCount)
    }

    @Test
    fun formatsEachGroupForDisplay() {
        val chrome = snapshot.groups.first()
        assertEquals("Chrome", chrome.label)
        assertEquals(1_600_000L, chrome.memKb)
        assertEquals("1.5 GiB", chrome.mem)
        assertEquals("20.0%", chrome.pctText)
        assertEquals(listOf(8001, 8100), chrome.members.map { it.pid })
    }

    @Test
    fun offersReclaimOnlyForOtherInstalledApps() {
        val reclaimable = snapshot.groups.associate { it.key to it.canReclaim }
        assertTrue(reclaimable.getValue("com.android.chrome"))
        assertFalse(reclaimable.getValue("system"))
        assertFalse(reclaimable.getValue(OWN_PACKAGE))
    }

    @Test
    fun zeroTotalRamYieldsZeroShares() {
        val empty = parsed.copy(totalRamKb = 0).toUiSnapshot({ labels[it] }, OWN_PACKAGE)
        assertEquals(0f, empty.usedFrac)
        assertTrue(empty.groups.all { it.pctFrac == 0.0 })
    }

    private companion object {
        const val OWN_PACKAGE = "dev.collinsthomas.memhogs"
    }
}
