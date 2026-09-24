package dev.collinsthomas.memhogs.ui

import dev.collinsthomas.memhogs.R
import androidx.compose.ui.res.stringResource
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
import dev.collinsthomas.memhogs.shizuku.ShizukuAccess
import kotlinx.coroutines.delay
import kotlin.math.ceil

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
internal fun ErrorScreen(error: String, motion: Boolean, onRetry: () -> Unit) {
    val lines = listOf(TermLine(stringResource(R.string.error_line, error), Palette.Red, 150))
    TypedTerminal(lines, motion, Modifier.padding(top = 14.dp)) {
        TermButton(stringResource(R.string.error_retry), onClick = onRetry)
    }
}
