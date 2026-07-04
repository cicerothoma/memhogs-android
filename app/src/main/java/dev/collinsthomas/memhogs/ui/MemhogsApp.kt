package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ShizukuAccess { NOT_RUNNING, NEEDS_PERMISSION, READY }

data class Gauge(val totalBytes: Long, val availBytes: Long, val low: Boolean)

data class UiMember(val name: String, val pid: Int, val mem: String)

data class UiGroup(
    val key: String,
    val label: String,
    val isApp: Boolean,
    val mem: String,
    val pctFrac: Double,
    val pctText: String,
    val members: List<UiMember>,
)

data class UiSnapshot(
    val totalText: String,
    val usedText: String,
    val usedFrac: Float,
    val processCount: Int,
    val groups: List<UiGroup>,
)

@Composable
fun MemhogsApp(
    access: ShizukuAccess,
    snapshot: UiSnapshot?,
    gauge: Gauge?,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.Background,
            surface = Palette.Surface,
            primary = Palette.Amber,
            onPrimary = Palette.Background,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Palette.Background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Header(snapshot, gauge, refreshing, onRefresh)
                when (access) {
                    ShizukuAccess.NOT_RUNNING -> SetupScreen(onOpenShizuku, onRefresh)
                    ShizukuAccess.NEEDS_PERMISSION -> PermissionScreen(onRequestPermission)
                    ShizukuAccess.READY -> when {
                        error != null -> Message("error: $error", Palette.Red)
                        snapshot == null -> Loading()
                        else -> GroupList(snapshot)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    snapshot: UiSnapshot?,
    gauge: Gauge?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "memhogs",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Palette.Amber,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Palette.Dim)
        }
    }

    // RAM bar: from the parsed snapshot when Shizuku is up, from
    // ActivityManager otherwise.
    val (frac, line) = when {
        snapshot != null ->
            snapshot.usedFrac to "${snapshot.usedText} of ${snapshot.totalText} used · ${snapshot.processCount} processes"
        gauge != null -> {
            val used = gauge.totalBytes - gauge.availBytes
            val f = if (gauge.totalBytes > 0) used.toFloat() / gauge.totalBytes else 0f
            f to "${humanBytes(used)} of ${humanBytes(gauge.totalBytes)} used"
        }
        else -> 0f to ""
    }
    if (line.isNotEmpty()) {
        val barColor = if (frac >= HOT_SHARE_BAR) Palette.Red else Palette.Amber
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Palette.Surface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
        Text(
            line,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Palette.Dim,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun GroupList(snapshot: UiSnapshot) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(snapshot.groups, key = { it.key }) { g ->
            GroupRow(
                g,
                expanded = g.key in expanded,
                onToggle = {
                    expanded = if (g.key in expanded) expanded - g.key else expanded + g.key
                },
            )
        }
    }
}

@Composable
private fun GroupRow(g: UiGroup, expanded: Boolean, onToggle: () -> Unit) {
    val expandable = g.members.size > 1
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable, onClick = onToggle)
            .padding(vertical = 8.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                Text(
                    g.mem,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = Palette.Amber,
                )
                Text(
                    g.pctText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (g.pctFrac >= HOT_SHARE) Palette.Red else Palette.Dim,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.label,
                    fontSize = 15.sp,
                    color = if (g.isApp) Palette.Cyan else Palette.Green,
                )
                if (expandable) {
                    Text(
                        "${g.members.size} processes",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Palette.Dim,
                    )
                }
            }
            if (expandable) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Palette.Dim,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }
        if (expanded) {
            g.members.forEach { m ->
                Row(Modifier.padding(start = 24.dp, top = 6.dp)) {
                    Text(
                        m.mem,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Palette.Amber,
                        modifier = Modifier.width(80.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${shortProcessName(m.name)} [${m.pid}]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Palette.Dim,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(onOpenShizuku: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Per-app memory needs shell access",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
        )
        Text(
            "Android does not let a normal app see other apps' memory. " +
                "memhogs reads it the same way a USB debugger would, through " +
                "Shizuku, which you start once per boot.",
            fontSize = 14.sp,
            color = Palette.Dim,
        )
        Text(
            "1. Install Shizuku\n" +
                "2. In Shizuku, start the service via wireless debugging\n" +
                "3. Come back here",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = Palette.Text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenShizuku,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Amber,
                    contentColor = Palette.Background,
                ),
            ) { Text("Open Shizuku") }
            OutlinedButton(onClick = onRetry) { Text("Check again", color = Palette.Text) }
        }
        Text(
            "Until then, only the device-wide bar above is available.",
            fontSize = 12.sp,
            color = Palette.Dim,
        )
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Shizuku is running",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
        )
        Text(
            "Grant memhogs permission to use it and the per-app breakdown appears.",
            fontSize = 14.sp,
            color = Palette.Dim,
        )
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Amber,
                contentColor = Palette.Background,
            ),
        ) { Text("Grant access") }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Amber)
    }
}

@Composable
private fun Message(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxSize().padding(top = 24.dp), contentAlignment = Alignment.TopStart) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = color)
    }
}

/** Device-wide bar turns red at 85% used; per-group threshold is HOT_SHARE. */
private const val HOT_SHARE_BAR = 0.85f

/**
 * Shortens "com.android.chrome:sandboxed_process0:org.chromium..." to its
 * suffix, since the group header already names the package.
 */
internal fun shortProcessName(name: String): String {
    val suffix = name.substringAfter(':', missingDelimiterValue = "")
    return if (suffix.isEmpty()) name else suffix
}

private fun humanBytes(b: Long): String {
    if (b < 1024) return "$b B"
    var div = 1024L
    var exp = 0
    var n = b / 1024
    while (n >= 1024) {
        div *= 1024
        exp++
        n /= 1024
    }
    return String.format(java.util.Locale.US, "%.1f %ciB", b.toDouble() / div, "KMGTPE"[exp])
}
