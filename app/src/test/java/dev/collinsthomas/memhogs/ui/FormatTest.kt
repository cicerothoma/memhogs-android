package dev.collinsthomas.memhogs.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun humanReadableKbMatchesCliFormatting() {
        assertEquals("0 B", humanReadableKb(0))
        assertEquals("10.0 KiB", humanReadableKb(10))
        assertEquals("117.0 MiB", humanReadableKb(117L * 1024))
        assertEquals("7.1 GiB", humanReadableKb(7278L * 1024))
        assertEquals("439.9 MiB", humanReadableKb(450_500))
    }

    @Test
    fun humanReadableKbIgnoresTheDeviceLocale() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("439.9 MiB", humanReadableKb(450_500))
        } finally {
            Locale.setDefault(original)
        }
    }
}
