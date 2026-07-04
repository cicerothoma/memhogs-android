package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.collinsthomas.memhogs.mem.humanKb
import kotlinx.coroutines.delay
import kotlin.math.ceil

enum class ShizukuAccess { NOT_RUNNING, NEEDS_PERMISSION, READY }

data class Gauge(val totalBytes: Long, val availBytes: Long, val low: Boolean)

data class UiMember(val name: String, val pid: Int, val mem: String)

data class UiGroup(
    val key: String,
    val label: String,
    val isApp: Boolean,
    val mem: String,
    val memKb: Long,
    val pctFrac: Double,
    val pctText: String,
    /** True when `am kill` can safely reclaim this group's background memory. */
    val canReclaim: Boolean,
    val members: List<UiMember>,
)

data class UiSnapshot(
    val totalKb: Long,
    val usedKb: Long,
    val totalText: String,
    val usedText: String,
    val usedFrac: Float,
    val processCount: Int,
    val groups: List<UiGroup>,
)

/** Per-group share of RAM at which a row is flagged red, like the CLI. */
const val HOT_SHARE = 0.15

/** Device-wide bar turns red at this used fraction. */
const val HOT_BAR = 0.85f

private val mono = FontFamily.Monospace

@Composable
fun MemhogsApp(
    access: ShizukuAccess,
    snapshot: UiSnapshot?,
    gauge: Gauge?,
    refreshing: Boolean,
    error: String?,
    reclaimMsg: String?,
    shizukuInstalled: Boolean,
    motion: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onReclaim: (String) -> Unit,
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
            var live by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(live, access) {
                while (live && access == ShizukuAccess.READY) {
                    onRefresh()
                    delay(5000)
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Header(
                    snapshot = snapshot,
                    gauge = gauge,
                    refreshing = refreshing,
                    live = live && access == ShizukuAccess.READY,
                    showLive = access == ShizukuAccess.READY,
                    motion = motion,
                    onRefresh = onRefresh,
                    onToggleLive = { live = !live },
                )
                AnimatedVisibility(visible = reclaimMsg != null) {
                    Text(
                        reclaimMsg ?: "",
                        fontFamily = mono,
                        fontSize = 12.sp,
                        color = Palette.Green,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                when (access) {
                    ShizukuAccess.NOT_RUNNING -> SetupScreen(
                        shizukuInstalled, motion, onOpenShizuku, onGetShizuku, onRefresh,
                    )
                    ShizukuAccess.NEEDS_PERMISSION -> PermissionScreen(motion, onRequestPermission)
                    ShizukuAccess.READY -> when {
                        error != null -> ErrorScreen(error, motion, onRefresh)
                        snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EatingLoader(motion)
                        }
                        else -> GroupList(snapshot, motion, onReclaim)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(
    snapshot: UiSnapshot?,
    gauge: Gauge?,
    refreshing: Boolean,
    live: Boolean,
    showLive: Boolean,
    motion: Boolean,
    onRefresh: () -> Unit,
    onToggleLive: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pac(size = 20.dp, chomping = motion && (refreshing || live))
        Spacer(Modifier.width(8.dp))
        Bits(active = refreshing || live, motion = motion)
        Spacer(Modifier.width(10.dp))
        Text(
            "memhogs",
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Palette.Amber,
        )
        Spacer(Modifier.weight(1f))
        if (showLive) {
            LiveChip(live, motion, onToggleLive)
            Spacer(Modifier.width(14.dp))
        }
        RefreshGlyph(refreshing, motion, onRefresh)
    }

    val (frac, line) = when {
        snapshot != null -> {
            val used by animateFloatAsState(
                targetValue = snapshot.usedKb.toFloat(),
                animationSpec = tween(900),
                label = "used",
            )
            snapshot.usedFrac to
                "${humanKb(used.toLong())} of ${snapshot.totalText} used · ${snapshot.processCount} processes"
        }
        gauge != null -> {
            val used = gauge.totalBytes - gauge.availBytes
            val f = if (gauge.totalBytes > 0) used.toFloat() / gauge.totalBytes else 0f
            f to "${humanKb(used / 1024)} of ${humanKb(gauge.totalBytes / 1024)} used"
        }
        else -> 0f to ""
    }
    if (line.isNotEmpty()) {
        BitBar(frac = frac)
        Text(
            line,
            fontFamily = mono,
            fontSize = 12.sp,
            color = Palette.Dim,
            modifier = Modifier.padding(top = 7.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun LiveChip(live: Boolean, motion: Boolean, onToggle: () -> Unit) {
    val color = if (live) Palette.Green else Palette.Dim
    Row(
        Modifier
            .clickable(onClick = onToggle)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha: Float = if (live && motion) {
            val t = rememberInfiniteTransition(label = "live")
            t.animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulse",
            ).value
        } else if (live) 1f else 0.5f
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text("live", fontFamily = mono, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun RefreshGlyph(refreshing: Boolean, motion: Boolean, onRefresh: () -> Unit) {
    // Spins smoothly while a refresh is in flight; when it ends, the icon
    // finishes its current turn and eases to rest instead of snapping.
    val angle = remember { Animatable(0f) }
    LaunchedEffect(refreshing, motion) {
        if (refreshing && motion) {
            while (true) {
                angle.animateTo(angle.value + 360f, tween(750, easing = LinearEasing))
            }
        } else if (angle.value != 0f) {
            angle.animateTo(ceil(angle.value / 360f) * 360f, tween(400))
            angle.snapTo(0f)
        }
    }
    Icon(
        Icons.Filled.Refresh,
        contentDescription = "Refresh",
        tint = if (refreshing) Palette.Amber else Palette.Dim,
        modifier = Modifier
            .clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(6.dp)
            .size(22.dp)
            .rotate(angle.value),
    )
}

// ------------------------------------------------------------------ list

@Composable
private fun GroupList(snapshot: UiSnapshot, motion: Boolean, onReclaim: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val shown = remember(snapshot, filter) {
        if (filter.isBlank()) snapshot.groups
        else snapshot.groups.filter {
            it.label.contains(filter, ignoreCase = true) ||
                it.key.contains(filter, ignoreCase = true)
        }
    }
    val topFrac = (snapshot.groups.firstOrNull()?.pctFrac ?: 1.0).coerceAtLeast(0.001)

    FilterPrompt(filter, onChange = { filter = it })

    if (shown.isEmpty()) {
        Text(
            "no groups match \"$filter\"",
            fontFamily = mono,
            fontSize = 13.sp,
            color = Palette.Dim,
            modifier = Modifier.padding(top = 18.dp),
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.key }) { g ->
            Box(Modifier.animateItem()) {
                GroupRow(
                    g = g,
                    relFrac = (g.pctFrac / topFrac).toFloat(),
                    motion = motion,
                    expanded = g.key in expanded,
                    onToggle = {
                        expanded = if (g.key in expanded) expanded - g.key else expanded + g.key
                    },
                    onReclaim = { onReclaim(g.key) },
                )
            }
        }
        item {
            Text(
                "${shown.size} of ${snapshot.groups.size} groups · " +
                    "metric: PSS, shared pages charged fairly",
                fontFamily = mono,
                fontSize = 11.sp,
                color = Palette.Dim,
                modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun FilterPrompt(filter: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$", fontFamily = mono, fontSize = 14.sp, color = Palette.Green)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (filter.isEmpty()) {
                Text(
                    "filter by name",
                    fontFamily = mono,
                    fontSize = 14.sp,
                    color = Palette.Dim.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = filter,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = mono,
                    fontSize = 14.sp,
                    color = Palette.Text,
                ),
                cursorBrush = SolidColor(Palette.Amber),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filter.isNotEmpty()) {
            Text(
                "×",
                fontFamily = mono,
                fontSize = 16.sp,
                color = Palette.Dim,
                modifier = Modifier
                    .clickable { onChange("") }
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupRow(
    g: UiGroup,
    relFrac: Float,
    motion: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onReclaim: () -> Unit,
) {
    val expandable = g.members.size > 1 || g.canReclaim
    val hot = g.pctFrac >= HOT_SHARE
    val bar by animateFloatAsState(
        targetValue = relFrac.coerceIn(0.02f, 1f),
        animationSpec = tween(700),
        label = "bar",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable, onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Proportional bar behind the row, scaled to the largest group.
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 2.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(bar)
                        .fillMaxHeight()
                        .background(
                            (if (hot) Palette.Red else Palette.Amber).copy(alpha = 0.07f),
                            RoundedCornerShape(6.dp),
                        )
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                    Text(g.mem, fontFamily = mono, fontSize = 15.sp, color = Palette.Amber)
                    HotPct(g.pctText, hot, motion)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        g.label,
                        fontSize = 15.sp,
                        color = if (g.isApp) Palette.Cyan else Palette.Green,
                    )
                    if (expandable) {
                        ExpandChip(expanded, g.members.size)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)),
        ) {
            Column(Modifier.padding(start = 14.dp, bottom = 8.dp)) {
                g.members.forEachIndexed { i, m ->
                    val glyph = if (i == g.members.lastIndex) "└─" else "├─"
                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(glyph, fontFamily = mono, fontSize = 12.sp, color = Palette.Dim)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            m.mem,
                            fontFamily = mono,
                            fontSize = 12.sp,
                            color = Palette.Amber,
                            modifier = Modifier.width(78.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${shortProcessName(m.name)} [${m.pid}]",
                            fontFamily = mono,
                            fontSize = 12.sp,
                            color = Palette.Dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (g.canReclaim) {
                    Row(Modifier.padding(top = 12.dp)) {
                        TermButton("reclaim memory", onClick = onReclaim)
                    }
                    Text(
                        "kills background processes only, the same reclaim " +
                            "android does under memory pressure. nothing you " +
                            "have open is touched.",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Palette.Dim,
                        modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

/** The tap target for expansion: an explicit chip, not a lone chevron. */
@Composable
private fun ExpandChip(expanded: Boolean, count: Int) {
    Row(
        Modifier
            .padding(top = 5.dp)
            .border(1.dp, Palette.Dim.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "▾" else "▸",
            fontFamily = mono,
            fontSize = 11.sp,
            color = Palette.Amber,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (count == 1) "1 process" else "$count processes",
            fontFamily = mono,
            fontSize = 11.sp,
            color = Palette.Text.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun HotPct(text: String, hot: Boolean, motion: Boolean) {
    val alpha: Float = if (hot && motion) {
        val t = rememberInfiniteTransition(label = "hot")
        t.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "hotpulse",
        ).value
    } else 1f
    Text(
        text,
        fontFamily = mono,
        fontSize = 12.sp,
        color = (if (hot) Palette.Red else Palette.Dim).copy(alpha = alpha),
    )
}

// --------------------------------------------------------------- screens

@Composable
private fun SetupScreen(
    shizukuInstalled: Boolean,
    motion: Boolean,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    val lines = remember(shizukuInstalled) {
        buildList {
            add(TermLine("$ memhogs", Palette.Green))
            add(TermLine("error: shell access required", Palette.Red, 260))
            add(TermLine("", Palette.Dim, 0))
            add(TermLine("android hides other apps' memory from", Palette.Text, 0))
            add(TermLine("normal apps. shizuku unlocks it the way", Palette.Text, 0))
            add(TermLine("a usb debugger would. no root needed.", Palette.Text, 200))
            add(TermLine("", Palette.Dim, 0))
            add(TermLine("  1. get shizuku", Palette.Text, 60))
            add(TermLine("  2. start its service (once per boot)", Palette.Text, 60))
            add(TermLine("  3. come back and grant access", Palette.Text, 200))
            if (!shizukuInstalled) {
                add(TermLine("", Palette.Dim, 0))
                add(TermLine("note: if the play store calls shizuku", Palette.Dim, 0))
                add(TermLine("incompatible with your android version,", Palette.Dim, 0))
                add(TermLine("install the apk from its github releases.", Palette.Dim, 120))
            }
        }
    }
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        if (shizukuInstalled) {
            TermButton("open shizuku", onClick = onOpenShizuku)
        } else {
            TermButton("get shizuku · github", onClick = onGetShizuku)
        }
        TermButton("check again", accent = Palette.Dim, onClick = onRetry)
    }
}

@Composable
private fun PermissionScreen(motion: Boolean, onRequestPermission: () -> Unit) {
    val lines = listOf(
        TermLine("$ shizuku status", Palette.Green),
        TermLine("running · memhogs not authorized", Palette.Amber, 200),
        TermLine("", Palette.Dim, 0),
        TermLine("one tap left. grant access and every", Palette.Text, 0),
        TermLine("app on this phone lines up by memory.", Palette.Text, 150),
    )
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        TermButton("grant access", onClick = onRequestPermission)
    }
}

@Composable
private fun ErrorScreen(error: String, motion: Boolean, onRetry: () -> Unit) {
    val lines = listOf(
        TermLine("error: $error", Palette.Red, 150),
    )
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        TermButton("retry", onClick = onRetry)
    }
}

/**
 * Shortens "com.android.chrome:sandboxed_process0:org.chromium..." to its
 * suffix, since the group header already names the package.
 */
internal fun shortProcessName(name: String): String {
    val suffix = name.substringAfter(':', missingDelimiterValue = "")
    return if (suffix.isEmpty()) name else suffix
}
