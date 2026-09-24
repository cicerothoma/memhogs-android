package dev.collinsthomas.memhogs.mem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {

    private val processes = listOf(
        MeminfoParser.ProcessSample("com.android.chrome", 8001, 250_000),
        MeminfoParser.ProcessSample(
            "com.android.chrome:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
            8123,
            120_000,
        ),
        MeminfoParser.ProcessSample("com.android.chrome:privileged_process0", 8100, 80_500),
        MeminfoParser.ProcessSample("system", 1780, 298_001),
        MeminfoParser.ProcessSample("com.whatsapp", 9001, 60_000),
    )

    private val labels = mapOf(
        "com.android.chrome" to "Chrome",
        "com.whatsapp" to "WhatsApp",
    )

    @Test
    fun helpersRollUpIntoTheirApp() {
        val groups = groupByOwner(processes) { labels[it] }
        val chrome = groups.single { it.owner == "com.android.chrome" }
        assertEquals("Chrome", chrome.label)
        assertTrue(chrome.isApp)
        assertEquals(450_500L, chrome.pssKb)
        assertEquals(3, chrome.members.size)
    }

    @Test
    fun membersAreSortedByMemory() {
        val chrome = groupByOwner(processes) { labels[it] }.single { it.owner == "com.android.chrome" }
        assertEquals(listOf(8001, 8123, 8100), chrome.members.map { it.pid })
    }

    @Test
    fun sortedByMemoryDescending() {
        val groups = groupByOwner(processes) { labels[it] }
        assertEquals("com.android.chrome", groups.first().owner)
        assertTrue(groups.zipWithNext().all { (a, b) -> a.pssKb >= b.pssKb })
    }

    @Test
    fun nonAppsKeepRawNamesAsStandalone() {
        val groups = groupByOwner(processes) { labels[it] }
        val system = groups.single { it.owner == "system" }
        assertFalse(system.isApp)
        assertEquals("system", system.label)
        assertEquals(1, system.members.size)
    }

    @Test
    fun dotSuffixProcessesGroupUnderTheirPackage() {
        val gms = listOf(
            MeminfoParser.ProcessSample("com.google.android.gms", 500, 62_000),
            MeminfoParser.ProcessSample("com.google.android.gms.persistent", 501, 99_300),
            MeminfoParser.ProcessSample("com.google.android.gms.unstable", 502, 20_000),
            MeminfoParser.ProcessSample("com.android.systemui", 503, 87_700),
        )
        val groups = groupByOwner(gms) {
            if (it == "com.google.android.gms") "Google Play services" else null
        }
        val play = groups.single { it.owner == "com.google.android.gms" }
        assertEquals(181_300L, play.pssKb)
        assertEquals(3, play.members.size)
        assertTrue(
            "unresolvable names stay in their own groups",
            groups.any { it.owner == "com.android.systemui" && !it.isApp },
        )
    }

    @Test
    fun nativeDaemonsDoNotCollapseIntoTheFrameworkPackage() {
        val samples = listOf(
            MeminfoParser.ProcessSample("android.hardware.graphics.composer3-service.ranchu", 390, 6_400),
            MeminfoParser.ProcessSample("android.hardware.audio.service", 377, 3_900),
            MeminfoParser.ProcessSample("android.process.acore", 1712, 12_506),
        )
        val frameworkPackage = "android"
        val groups = groupByOwner(samples) {
            if (it == frameworkPackage) "Android System" else null
        }
        assertEquals(3, groups.size)
        assertTrue(groups.none { it.isApp })
    }

    @Test
    fun isolatedProcessesGroupUnderTheirHostApp() {
        val webViewSandbox = MeminfoParser.ProcessSample(
            "com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
            6496,
            18_000,
        )
        val groups = groupByOwner(processes + webViewSandbox, mapOf(6496 to "com.whatsapp")) { labels[it] }
        val whatsapp = groups.single { it.owner == "com.whatsapp" }
        assertEquals(78_000L, whatsapp.pssKb)
        assertTrue(groups.none { it.owner == "com.google.android.webview" })
    }
}
