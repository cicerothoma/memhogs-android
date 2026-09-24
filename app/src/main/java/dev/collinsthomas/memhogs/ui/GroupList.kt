package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.collinsthomas.memhogs.R

@Composable
internal fun GroupList(snapshot: UiSnapshot, motion: Boolean, onReclaim: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    val shown = remember(snapshot, filter) {
        if (filter.isBlank()) {
            snapshot.groups
        } else {
            snapshot.groups.filter {
                it.label.contains(filter, ignoreCase = true) ||
                    it.key.contains(filter, ignoreCase = true)
            }
        }
    }
    val topFrac = (snapshot.groups.firstOrNull()?.pctFrac ?: 1.0).coerceAtLeast(0.001)

    FilterPrompt(filter, onChange = { filter = it })

    if (shown.isEmpty()) {
        Text(
            stringResource(R.string.filter_no_match, filter),
            fontFamily = Mono,
            fontSize = 13.sp,
            color = Palette.Dim,
            modifier = Modifier.padding(top = 18.dp),
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), state = rememberListStateKeptAtTop(shown)) {
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
                pluralStringResource(
                    R.plurals.list_footer,
                    snapshot.groups.size,
                    shown.size,
                    snapshot.groups.size,
                ),
                fontFamily = Mono,
                fontSize = 11.sp,
                color = Palette.Dim,
                modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun rememberListStateKeptAtTop(groups: List<UiGroup>): LazyListState {
    val state = rememberLazyListState()
    remember(groups) {
        if (state.firstVisibleItemIndex == 0) state.requestScrollToItem(0)
    }
    return state
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
        Text("$", fontFamily = Mono, fontSize = 14.sp, color = Palette.Green)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (filter.isEmpty()) {
                Text(
                    stringResource(R.string.filter_hint),
                    fontFamily = Mono,
                    fontSize = 14.sp,
                    color = Palette.Dim.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = filter,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Mono,
                    fontSize = 14.sp,
                    color = Palette.Text,
                ),
                cursorBrush = SolidColor(Palette.Amber),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filter.isNotEmpty()) {
            val clearLabel = stringResource(R.string.filter_clear)
            Text(
                "×",
                fontFamily = Mono,
                fontSize = 16.sp,
                color = Palette.Dim,
                modifier = Modifier
                    .semantics { contentDescription = clearLabel }
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
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Proportional bar behind the row, scaled to the largest group.
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(bar)
                        .fillMaxHeight()
                        .background(
                            (if (hot) Palette.Red else Palette.Amber).copy(alpha = 0.07f),
                            RoundedCornerShape(6.dp),
                        ),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                    Text(g.mem, fontFamily = Mono, fontSize = 15.sp, color = Palette.Amber)
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
                        Text(glyph, fontFamily = Mono, fontSize = 12.sp, color = Palette.Dim)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            m.mem,
                            fontFamily = Mono,
                            fontSize = 12.sp,
                            color = Palette.Amber,
                            modifier = Modifier.width(78.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${shortProcessName(m.name)} [${m.pid}]",
                            fontFamily = Mono,
                            fontSize = 12.sp,
                            color = Palette.Dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (g.canReclaim) {
                    Row(Modifier.padding(top = 12.dp)) {
                        TermButton(stringResource(R.string.reclaim_button), onClick = onReclaim)
                    }
                    Text(
                        stringResource(R.string.reclaim_explanation),
                        fontFamily = Mono,
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
            fontFamily = Mono,
            fontSize = 11.sp,
            color = Palette.Amber,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            pluralStringResource(R.plurals.process_count, count, count),
            fontFamily = Mono,
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
    } else {
        1f
    }
    Text(
        text,
        fontFamily = Mono,
        fontSize = 12.sp,
        color = (if (hot) Palette.Red else Palette.Dim).copy(alpha = alpha),
    )
}

/**
 * Shortens "com.android.chrome:sandboxed_process0:org.chromium..." to its
 * suffix, since the group header already names the package.
 */
internal fun shortProcessName(name: String): String {
    val suffix = name.substringAfter(':', missingDelimiterValue = "")
    return if (suffix.isEmpty()) name else suffix
}
