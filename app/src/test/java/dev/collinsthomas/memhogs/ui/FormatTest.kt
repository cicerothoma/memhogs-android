package dev.collinsthomas.memhogs.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun humanKbMatchesCliFormatting() {
        assertEquals("0 B", humanKb(0))
        assertEquals("10.0 KiB", humanKb(10))
        assertEquals("117.0 MiB", humanKb(117L * 1024))
        assertEquals("7.1 GiB", humanKb(7278L * 1024))
        assertEquals("439.9 MiB", humanKb(450_500))
    }

    @Test
    fun humanKbIgnoresTheDeviceLocale() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("439.9 MiB", humanKb(450_500))
        } finally {
            Locale.setDefault(original)
        }
    }
}
