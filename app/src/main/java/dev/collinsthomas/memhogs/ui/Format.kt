package dev.collinsthomas.memhogs.ui

import java.util.Locale

private const val BYTES_PER_KIB = 1024L
private const val BINARY_UNIT_PREFIXES = "KMGTPE"

fun humanReadableKb(kb: Long): String {
    val bytes = kb * BYTES_PER_KIB
    if (bytes < BYTES_PER_KIB) return "$bytes B"
    var unitBytes = BYTES_PER_KIB
    var unitIndex = 0
    while (bytes / unitBytes >= BYTES_PER_KIB) {
        unitBytes *= BYTES_PER_KIB
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %ciB", bytes.toDouble() / unitBytes, BINARY_UNIT_PREFIXES[unitIndex])
}

fun percent(fraction: Double): String = String.format(Locale.US, "%.1f%%", fraction * 100)
