package dev.collinsthomas.memhogs.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
internal fun GroupList(snapshot: UiSnapshot, motion: Boolean, onReclaim: (Set<String>) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }

    val shown = remember(snapshot, filter) { snapshot.groups.matching(filter) }
    val selectedGroups = remember(snapshot, selected) { snapshot.groups.filter { it.key in selected } }
    val selecting = selectedGroups.isNotEmpty()
    val largestShare = (snapshot.groups.firstOrNull()?.shareOfRam ?: 1.0).coerceAtLeast(0.001)

    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize()) {
        FilterPrompt(filter, onChange = { filter = it })
        if (shown.isEmpty()) {
            NoMatch(filter, Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), state = rememberListStateKeptAtTop(shown)) {
                items(shown, key = { it.key }) { group ->
                    Box(Modifier.animateItem()) {
                        GroupRow(
                            group = group,
                            barFraction = (group.shareOfRam / largestShare).toFloat(),
                            motion = motion,
                            expanded = group.key in expanded,
                            selection = selectionOf(group, selecting, group.key in selected),
                            onToggleExpanded = { expanded = expanded.toggle(group.key) },
                            onToggleSelected = { selected = selected.toggle(group.key) },
                            onReclaim = { onReclaim(setOf(group.key)) },
                        )
                    }
                }
                item { ListFooter(shown.size, snapshot.groups.size) }
            }
        }
        if (selecting) {
            SelectionBar(
                count = selectedGroups.size,
                memText = humanReadableKb(selectedGroups.sumOf { it.memKb }),
                onReclaim = {
                    onReclaim(selected)
                    selected = emptySet()
                },
                onCancel = { selected = emptySet() },
            )
        }
    }
}

private enum class RowSelection { OFF, SELECTED, UNSELECTED, UNAVAILABLE }

private fun selectionOf(group: UiGroup, selecting: Boolean, selected: Boolean): RowSelection = when {
    !selecting -> RowSelection.OFF
    !group.canReclaim -> RowSelection.UNAVAILABLE
    selected -> RowSelection.SELECTED
    else -> RowSelection.UNSELECTED
}

@Composable
private fun NoMatch(filter: String, modifier: Modifier) {
    Text(
        stringResource(R.string.filter_no_match, filter),
        fontFamily = Mono,
        fontSize = 13.sp,
        color = Palette.Dim,
        modifier = modifier.padding(top = 18.dp),
    )
}

@Composable
private fun ListFooter(shownCount: Int, totalCount: Int) {
    Text(
        pluralStringResource(R.plurals.list_footer, totalCount, shownCount, totalCount),
        fontFamily = Mono,
        fontSize = 11.sp,
        color = Palette.Dim,
        modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
    )
}

@Composable
private fun SelectionBar(count: Int, memText: String, onReclaim: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            pluralStringResource(R.plurals.selection_summary, count, count, memText),
            fontFamily = Mono,
            fontSize = 13.sp,
            color = Palette.Amber,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TermButton(stringResource(R.string.reclaim_button), onClick = onReclaim)
            TermButton(stringResource(R.string.selection_cancel), accent = Palette.Dim, onClick = onCancel)
        }
    }
}

private fun List<UiGroup>.matching(filter: String): List<UiGroup> = if (filter.isBlank()) {
    this
} else {
    filter { it.label.contains(filter, ignoreCase = true) || it.key.contains(filter, ignoreCase = true) }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun rememberListStateKeptAtTop(groups: List<UiGroup>): LazyListState {
    val state = rememberLazyListState()
    DisposableEffect(groups) {
        if (state.firstVisibleItemIndex == 0) state.requestScrollToItem(0)
        onDispose {}
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: UiGroup,
    barFraction: Float,
    motion: Boolean,
    expanded: Boolean,
    selection: RowSelection,
    onToggleExpanded: () -> Unit,
    onToggleSelected: () -> Unit,
    onReclaim: () -> Unit,
) {
    val expandable = group.members.size > 1 || group.canReclaim
    val hot = group.shareOfRam >= HOT_GROUP_SHARE_OF_RAM
    val selecting = selection != RowSelection.OFF

    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (selection == RowSelection.UNAVAILABLE) 0.4f else 1f)
            .combinedClickable(
                enabled = if (selecting) group.canReclaim else expandable,
                onClick = if (selecting) onToggleSelected else onToggleExpanded,
                onLongClick = onToggleSelected.takeIf { group.canReclaim },
            )
            .padding(vertical = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth().selectedOutline(selection == RowSelection.SELECTED)) {
            ShareOfRamBar(barFraction, hot, Modifier.matchParentSize())
            GroupSummary(group, hot, motion, expandable, expanded, selection)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)),
        ) {
            Column(Modifier.padding(start = 14.dp, bottom = 8.dp)) {
                MemberTree(group.members, group.key)
                if (group.canReclaim) ReclaimPanel(onReclaim)
            }
        }
    }
}

private fun Modifier.selectedOutline(selected: Boolean): Modifier = if (selected) {
    border(1.dp, Palette.Amber.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
} else {
    this
}

@Composable
private fun ShareOfRamBar(fraction: Float, hot: Boolean, modifier: Modifier) {
    val width by animateFloatAsState(
        targetValue = fraction.coerceIn(0.02f, 1f),
        animationSpec = tween(700),
        label = "bar",
    )
    Box(modifier.padding(vertical = 2.dp)) {
        Box(
            Modifier
                .fillMaxWidth(width)
                .fillMaxHeight()
                .background(
                    (if (hot) Palette.Red else Palette.Amber).copy(alpha = 0.07f),
                    RoundedCornerShape(6.dp),
                ),
        )
    }
}

@Composable
private fun GroupSummary(
    group: UiGroup,
    hot: Boolean,
    motion: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    selection: RowSelection,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selection)
        Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
            Text(group.memText, fontFamily = Mono, fontSize = 15.sp, color = Palette.Amber)
            ShareOfRamText(group.shareText, hot, motion)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.label,
                fontSize = 15.sp,
                color = if (group.isApp) Palette.Cyan else Palette.Green,
            )
            if (expandable) {
                ExpandChip(expanded, group.members.size)
            }
        }
    }
}

@Composable
private fun SelectionMark(selection: RowSelection) {
    val mark = when (selection) {
        RowSelection.SELECTED -> "[x]"
        RowSelection.UNSELECTED -> "[ ]"
        RowSelection.OFF, RowSelection.UNAVAILABLE -> return
    }
    Text(mark, fontFamily = Mono, fontSize = 14.sp, color = Palette.Amber, modifier = Modifier.padding(end = 8.dp))
}

@Composable
private fun MemberTree(members: List<UiMember>, owner: String) {
    members.forEachIndexed { index, member ->
        val branch = if (index == members.lastIndex) "└─" else "├─"
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(branch, fontFamily = Mono, fontSize = 12.sp, color = Palette.Dim)
            Spacer(Modifier.width(8.dp))
            Text(
                member.memText,
                fontFamily = Mono,
                fontSize = 12.sp,
                color = Palette.Amber,
                modifier = Modifier.width(78.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${nameWithinGroup(member.name, owner)} [${member.pid}]",
                fontFamily = Mono,
                fontSize = 12.sp,
                color = Palette.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReclaimPanel(onReclaim: () -> Unit) {
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
    Text(
        stringResource(R.string.reclaim_bulk_hint),
        fontFamily = Mono,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = Palette.Dim,
        modifier = Modifier.padding(top = 4.dp, end = 12.dp),
    )
}

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
private fun ShareOfRamText(text: String, hot: Boolean, motion: Boolean) {
    val alpha: Float = if (hot && motion) {
        val transition = rememberInfiniteTransition(label = "hot")
        transition.animateFloat(
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

internal fun nameWithinGroup(processName: String, owner: String): String = processName.removePrefix("$owner:")
