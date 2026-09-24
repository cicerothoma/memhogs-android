package dev.collinsthomas.memhogs.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.collinsthomas.memhogs.R

@Composable
internal fun SetupScreen(
    shizukuInstalled: Boolean,
    motion: Boolean,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    val lines = buildList {
        add(TermLine("$ memhogs", Palette.Green))
        add(TermLine(stringResource(R.string.setup_error), Palette.Red, 260))
        add(BlankLine)
        addAll(termParagraph(stringResource(R.string.setup_explanation), Palette.Text, pauseAfter = 200))
        add(BlankLine)
        addAll(termParagraph(stringResource(R.string.setup_steps), Palette.Text, pauseAfter = 200, linePause = 60))
        if (!shizukuInstalled) {
            add(BlankLine)
            addAll(termParagraph(stringResource(R.string.setup_play_store_note), Palette.Dim, pauseAfter = 120))
        }
    }
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        if (shizukuInstalled) {
            TermButton(stringResource(R.string.setup_open_shizuku), onClick = onOpenShizuku)
        } else {
            TermButton(stringResource(R.string.setup_get_shizuku), onClick = onGetShizuku)
        }
        TermButton(stringResource(R.string.setup_check_again), accent = Palette.Dim, onClick = onRetry)
    }
}

@Composable
internal fun PermissionScreen(motion: Boolean, onRequestPermission: () -> Unit) {
    val lines = buildList {
        add(TermLine("$ shizuku status", Palette.Green))
        add(TermLine(stringResource(R.string.permission_status), Palette.Amber, 200))
        add(BlankLine)
        addAll(termParagraph(stringResource(R.string.permission_explanation), Palette.Text, pauseAfter = 150))
    }
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        TermButton(stringResource(R.string.permission_grant), onClick = onRequestPermission)
    }
}

@Composable
internal fun ErrorScreen(error: String, hint: String?, motion: Boolean, onRetry: () -> Unit) {
    val lines = buildList {
        add(TermLine(stringResource(R.string.error_line, error), Palette.Red, 150))
        if (hint != null) {
            add(BlankLine)
            addAll(termParagraph(hint, Palette.Text, pauseAfter = 150))
        }
    }
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        TermButton(stringResource(R.string.error_retry), onClick = onRetry)
    }
}
