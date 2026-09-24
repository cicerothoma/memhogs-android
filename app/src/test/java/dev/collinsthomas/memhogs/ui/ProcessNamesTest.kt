package dev.collinsthomas.memhogs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessNamesTest {

    @Test
    fun helperProcessesDropTheirOwnersPrefix() {
        assertEquals(
            "sandboxed_process0",
            nameWithinGroup("com.android.chrome:sandboxed_process0", "com.android.chrome"),
        )
    }

    @Test
    fun mainProcessKeepsItsFullName() {
        assertEquals("com.android.chrome", nameWithinGroup("com.android.chrome", "com.android.chrome"))
    }

    @Test
    fun hostedProcessesFromAnotherPackageKeepTheirFullName() {
        val sandbox = "com.google.android.webview:sandboxed_process0"
        assertEquals(sandbox, nameWithinGroup(sandbox, "com.whatsapp"))
    }
}
