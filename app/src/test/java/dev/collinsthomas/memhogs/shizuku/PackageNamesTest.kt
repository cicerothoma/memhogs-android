package dev.collinsthomas.memhogs.shizuku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNamesTest {

    @Test
    fun acceptsRealPackageNames() {
        assertTrue(isValidPackageName("com.android.chrome"))
        assertTrue(isValidPackageName("com.google.android.gms"))
        assertTrue(isValidPackageName("org.mozilla.firefox_beta"))
    }

    @Test
    fun rejectsNamesThatAreNotPackages() {
        assertFalse(isValidPackageName("system"))
        assertFalse(isValidPackageName(""))
        assertFalse(isValidPackageName("com.android.chrome:sandboxed_process0"))
        assertFalse(isValidPackageName("1com.example"))
    }

    @Test
    fun rejectsShellMetacharacters() {
        assertFalse(isValidPackageName("com.example; reboot"))
        assertFalse(isValidPackageName("com.example && rm -rf /sdcard"))
        assertFalse(isValidPackageName("com.example\$(id)"))
    }
}
