package dev.collinsthomas.memhogs.mem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val LRU_WITH_ISOLATED_PROCESSES = """
ACTIVITY MANAGER LRU PROCESSES (dumpsys activity lru)
  Activities:
  #99: fg       TPSL -------TI 27235:com.example.notes/u0a392 act:activities|recents
  #98: vis      IMPB -------TI 30235:com.example.keyboard/u0a179 act:treated
  Other:
  #82: vis  + 1 FGS  ---NFUATI 26051:com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0/u0a281i542
  #77: vis  + 1 IMPF -------TI 6496:com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0/u0a364i961
  #40: cch  + 5 CEM  ---------- 4410:com.example.browser:sandboxed_process1/u10a120i12
  #12: pers     PER  LCMNFUA-- 1780:system/1000
""".trimIndent()

class ActivityLruParserTest {

    private val hosts = ActivityLruParser.hostUidsOfIsolatedProcesses(LRU_WITH_ISOLATED_PROCESSES)

    @Test
    fun mapsIsolatedProcessesToTheirHostAppUid() {
        assertEquals(10_281, hosts[26051])
        assertEquals(10_364, hosts[6496])
    }

    @Test
    fun includesTheUserIdInTheHostUid() {
        assertEquals(1_010_120, hosts[4410])
    }

    @Test
    fun ignoresProcessesThatAreNotIsolated() {
        assertEquals(setOf(26051, 6496, 4410), hosts.keys)
    }

    @Test
    fun emptyInputYieldsNoHosts() {
        assertTrue(ActivityLruParser.hostUidsOfIsolatedProcesses("").isEmpty())
    }
}
