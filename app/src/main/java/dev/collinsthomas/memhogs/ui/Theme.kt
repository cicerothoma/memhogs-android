package dev.collinsthomas.memhogs.ui

import androidx.compose.ui.graphics.Color

/**
 * The CLI's terminal palette: amber for memory values, cyan for recognized
 * apps, green for standalone daemons, red for entries worth worrying about.
 */
object Palette {
    val Background = Color(0xFF0B0E11)
    val Surface = Color(0xFF12161B)
    val Cell = Color(0xFF1A212A)
    val Amber = Color(0xFFFFB454)
    val Cyan = Color(0xFF56C9DB)
    val Green = Color(0xFF7EC699)
    val Red = Color(0xFFFF5555)
    val Dim = Color(0xFF6B7280)
    val Text = Color(0xFFD7DEE8)
}

/** Per-group share of RAM at which a row is flagged red, like the CLI. */
const val HOT_SHARE = 0.15

/** Device-wide bar turns red at this used fraction. */
const val HOT_BAR = 0.85f
