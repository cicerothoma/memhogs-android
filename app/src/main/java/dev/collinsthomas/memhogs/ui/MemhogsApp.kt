package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.collinsthomas.memhogs.R
import dev.collinsthomas.memhogs.shizuku.ShizukuAccess
import kotlinx.coroutines.delay

@Composable
fun MemhogsApp(
    state: MemhogsUiState,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onReclaim: (Set<String>) -> Unit,
    onForceStop: (String) -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.Background,
            surface = Palette.Surface,
            primary = Palette.Amber,
            onPrimary = Palette.Background,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Palette.Background) {
            val access = state.access
            val motion = state.motion
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
                    .padding(horizontal = 16.dp),
            ) {
                Header(
                    snapshot = state.snapshot,
                    gauge = state.gauge,
                    refreshing = state.refreshing,
                    live = live && access == ShizukuAccess.READY,
                    showLive = access == ShizukuAccess.READY,
                    motion = motion,
                    onRefresh = onRefresh,
                    onToggleLive = { live = !live },
                )
                AnimatedVisibility(visible = state.reclaimResult != null) {
                    Text(
                        state.reclaimResult?.message() ?: "",
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        color = Palette.Green,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                when (access) {
                    ShizukuAccess.NOT_RUNNING -> SetupScreen(
                        state.shizukuInstalled,
                        motion,
                        onOpenShizuku,
                        onGetShizuku,
                        onRefresh,
                    )
                    ShizukuAccess.NEEDS_PERMISSION -> PermissionScreen(motion, onRequestPermission)
                    ShizukuAccess.READY -> when {
                        state.error != null -> ErrorScreen(state.error.message(), state.error.hint(), motion, onRefresh)
                        state.snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EatingLoader(motion)
                        }
                        else -> GroupList(state.snapshot, motion, onReclaim, onForceStop)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReclaimResult.message(): String {
    val subject = labels.singleOrNull() ?: pluralStringResource(R.plurals.app_count, labels.size, labels.size)
    return when (this) {
        is ReclaimResult.Reclaimed -> stringResource(R.string.reclaim_freed, subject, humanReadableKb(freedKb))
        is ReclaimResult.NothingToReclaim -> stringResource(R.string.reclaim_nothing, subject)
    }
}

@Composable
private fun LoadError.message(): String = when (this) {
    LoadError.EmptyMeminfo -> stringResource(R.string.error_empty_meminfo)
    LoadError.ShellDidNotStart -> stringResource(R.string.error_shell_did_not_start)
    is LoadError.Failed -> detail
}

@Composable
private fun LoadError.hint(): String? = when (this) {
    LoadError.ShellDidNotStart -> stringResource(R.string.error_shell_did_not_start_hint)
    LoadError.EmptyMeminfo, is LoadError.Failed -> null
}
