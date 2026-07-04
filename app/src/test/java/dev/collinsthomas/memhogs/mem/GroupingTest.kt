package dev.collinsthomas.memhogs.mem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {

    private val procs = listOf(
        MeminfoParser.ProcSample("com.android.chrome", 8001, 250_000),
        MeminfoParser.ProcSample(
            "com.android.chrome:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
            8123,
            120_000,
        ),
        MeminfoParser.ProcSample("com.android.chrome:privileged_process0", 8100, 80_500),
        MeminfoParser.ProcSample("system", 1780, 298_001),
        MeminfoParser.ProcSample("com.whatsapp", 9001, 60_000),
    )

    private val labels = mapOf(
        "com.android.chrome" to "Chrome",
        "com.whatsapp" to "WhatsApp",
    )

    @Test
    fun helpersRollUpIntoTheirApp() {
        val groups = groupByPackage(procs) { labels[it] }
        val chrome = groups.single { it.packageName == "com.android.chrome" }
        assertEquals("Chrome", chrome.label)
        assertTrue(chrome.isApp)
        assertEquals(450_500L, chrome.pssKb)
        assertEquals(3, chrome.members.size)
        // Members sorted by memory, main process first here.
        assertEquals(8001, chrome.members.first().pid)
    }

    @Test
    fun sortedByMemoryDescending() {
        val groups = groupByPackage(procs) { labels[it] }
        assertEquals("com.android.chrome", groups.first().packageName)
        assertTrue(groups.zipWithNext().all { (a, b) -> a.pssKb >= b.pssKb })
    }

    @Test
    fun nonAppsKeepRawNamesAsStandalone()  {
        val groups = groupByPackage(procs) { labels[it] }
        val system = groups.single { it.packageName == "system" }
        assertFalse(system.isApp)
        assertEquals("system", system.label)
        assertEquals(1, system.members.size)
    }

    @Test
    fun dotSuffixProcessesGroupUnderTheirPackage() {
        // Google Play services declares extra processes with dot suffixes
        // instead of the usual "pkg:suffix" form.
        val gms = listOf(
            MeminfoParser.ProcSample("com.google.android.gms", 500, 62_000),
            MeminfoParser.ProcSample("com.google.android.gms.persistent", 501, 99_300),
            MeminfoParser.ProcSample("com.google.android.gms.unstable", 502, 20_000),
            MeminfoParser.ProcSample("com.android.systemui", 503, 87_700),
        )
        val groups = groupByPackage(gms) {
            if (it == "com.google.android.gms") "Google Play services" else null
        }
        val play = groups.single { it.packageName == "com.google.android.gms" }
        assertEquals(181_300L, play.pssKb)
        assertEquals(3, play.members.size)
        // Unresolvable names must not collapse into each other.
        assertTrue(groups.any { it.packageName == "com.android.systemui" && !it.isApp })
    }

    @Test
    fun nativeDaemonsDoNotCollapseIntoTheFrameworkPackage() {
        // "android" (framework-res) is an installed package on every device.
        // HAL daemons named android.hardware.* must not prefix-walk into it.
        val samples = listOf(
            MeminfoParser.ProcSample("android.hardware.graphics.composer3-service.ranchu", 390, 6_400),
            MeminfoParser.ProcSample("android.hardware.audio.service", 377, 3_900),
            MeminfoParser.ProcSample("android.process.acore", 1712, 12_506),
        )
        val groups = groupByPackage(samples) {
            if (it == "android") "Android System" else null
        }
        assertEquals(3, groups.size)
        assertTrue(groups.none { it.isApp })
    }

    @Test
    fun humanKbMatchesCliFormatting() {
        assertEquals("0 B", humanKb(0))
        assertEquals("10.0 KiB", humanKb(10))
        assertEquals("117.0 MiB", humanKb(117L * 1024))
        assertEquals("7.1 GiB", humanKb(7278L * 1024))
        assertEquals("439.9 MiB", humanKb(450_500))
    }
}
