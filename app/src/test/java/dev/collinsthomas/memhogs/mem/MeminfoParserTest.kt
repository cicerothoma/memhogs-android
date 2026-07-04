package dev.collinsthomas.memhogs.mem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Shaped like real `dumpsys meminfo` output on Android 8+. The OOM
// adjustment section deliberately repeats the "NK: name (pid N)" format to
// prove the parser respects section boundaries.
private val FIXTURE = """
Applications Memory Usage (in Kilobytes):
Uptime: 86054838 Realtime: 172805286

Total PSS by process:
    354,123K: com.google.android.gms.persistent (pid 3456)
    298,001K: system (pid 1780)
    250,000K: com.android.chrome (pid 8001 / activities)
    145,332K: com.android.systemui (pid 2100)
    120,000K: com.android.chrome:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0 (pid 8123)
     80,500K: com.android.chrome:privileged_process0 (pid 8100)
     60,000K: com.whatsapp (pid 9001)

Total PSS by OOM adjustment:
    500,000K: Native
        123,456K: surfaceflinger (pid 900)
         98,765K: android.hardware.graphics.composer@2.4-service (pid 910)
    354,123K: Persistent
        354,123K: com.google.android.gms.persistent (pid 3456)

Total PSS by category:
    300,000K: Dalvik
    250,000K: Native
     80,000K: .so mmap

Total RAM: 7,876,544K (status normal)
 Free RAM: 3,456,789K (  823,456K cached pss + 2,345,678K cached kernel +   287,655K free)
 Used RAM: 3,987,654K (3,456,789K used pss +   530,865K kernel)
 Lost RAM:   432,101K
ZRAM:   123,456K physical used for   456,789K in swap (1,048,576K total swap)
 Tuning: 256 (large 512), oom   322,560K, restore limit   107,520K (high-end-gfx)
""".trimIndent()

class MeminfoParserTest {

    @Test
    fun parsesOnlyTheProcessSection() {
        val snap = MeminfoParser.parse(FIXTURE)
        assertEquals(7, snap.processes.size)
        assertFalse(
            "OOM adjustment lines must not leak into the process list",
            snap.processes.any { it.name == "surfaceflinger" },
        )
    }

    @Test
    fun sortsByPssDescending() {
        val snap = MeminfoParser.parse(FIXTURE)
        assertEquals("com.google.android.gms.persistent", snap.processes.first().name)
        assertEquals(354_123L, snap.processes.first().pssKb)
        assertTrue(snap.processes.zipWithNext().all { (a, b) -> a.pssKb >= b.pssKb })
    }

    @Test
    fun keepsFullProcessNamesAndPids() {
        val snap = MeminfoParser.parse(FIXTURE)
        val renderer = snap.processes.single { it.pid == 8123 }
        assertEquals(
            "com.android.chrome:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
            renderer.name,
        )
        assertEquals(120_000L, renderer.pssKb)
        // "(pid 8001 / activities)" still parses to pid 8001.
        assertTrue(snap.processes.any { it.pid == 8001 && it.name == "com.android.chrome" })
    }

    @Test
    fun parsesRamTotals() {
        val snap = MeminfoParser.parse(FIXTURE)
        assertEquals(7_876_544L, snap.totalRamKb)
        assertEquals(3_456_789L, snap.freeRamKb)
        assertEquals(3_987_654L, snap.usedRamKb)
    }

    @Test
    fun emptyInputYieldsEmptySnapshot() {
        val snap = MeminfoParser.parse("")
        assertEquals(0, snap.processes.size)
        assertEquals(0L, snap.totalRamKb)
    }
}
