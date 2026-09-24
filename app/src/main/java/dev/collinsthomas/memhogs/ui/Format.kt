package dev.collinsthomas.memhogs.ui

import java.util.Locale

/** Formats a KiB count in binary units with one decimal, like the CLI. */
fun humanKb(kb: Long): String {
    val b = kb * 1024
    if (b < 1024) return "$b B"
    var div = 1024L
    var exp = 0
    var n = b / 1024
    while (n >= 1024) {
        div *= 1024
        exp++
        n /= 1024
    }
    return String.format(Locale.US, "%.1f %ciB", b.toDouble() / div, "KMGTPE"[exp])
}

fun percent(fraction: Double): String = String.format(Locale.US, "%.1f%%", fraction * 100)
