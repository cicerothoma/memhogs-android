package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.collinsthomas.memhogs.R
import kotlin.math.ceil

@Composable
internal fun Header(
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
        MemoryBits(active = refreshing || live, motion = motion)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.app_name),
            fontFamily = Mono,
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

    val (usedFraction, line) = when {
        snapshot != null -> {
            val used by animateFloatAsState(
                targetValue = snapshot.usedKb.toFloat(),
                animationSpec = tween(900),
                label = "used",
            )
            snapshot.usedFraction to pluralStringResource(
                R.plurals.header_used_with_processes,
                snapshot.processCount,
                humanReadableKb(used.toLong()),
                snapshot.totalText,
                snapshot.processCount,
            )
        }
        gauge != null -> {
            val used = gauge.totalBytes - gauge.availBytes
            val fraction = if (gauge.totalBytes > 0) used.toFloat() / gauge.totalBytes else 0f
            fraction to
                stringResource(
                    R.string.header_used,
                    humanReadableKb(used / 1024),
                    humanReadableKb(
                        gauge.totalBytes / 1024,
                    ),
                )
        }
        else -> 0f to ""
    }
    if (line.isNotEmpty()) {
        RamGauge(usedFraction = usedFraction)
        Text(
            line,
            fontFamily = Mono,
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
            val transition = rememberInfiniteTransition(label = "live")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulse",
            ).value
        } else if (live) {
            1f
        } else {
            0.5f
        }
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.live), fontFamily = Mono, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun RefreshGlyph(refreshing: Boolean, motion: Boolean, onRefresh: () -> Unit) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(refreshing, motion) {
        if (refreshing && motion) angle.spinForever() else angle.settleAtNextFullTurn()
    }
    Icon(
        Icons.Filled.Refresh,
        contentDescription = stringResource(R.string.refresh),
        tint = if (refreshing) Palette.Amber else Palette.Dim,
        modifier = Modifier
            .clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(6.dp)
            .size(22.dp)
            .rotate(angle.value),
    )
}

private const val FULL_TURN_DEGREES = 360f

private suspend fun Animatable<Float, AnimationVector1D>.spinForever() {
    while (true) {
        animateTo(value + FULL_TURN_DEGREES, tween(750, easing = LinearEasing))
    }
}

private suspend fun Animatable<Float, AnimationVector1D>.settleAtNextFullTurn() {
    if (value == 0f) return
    animateTo(ceil(value / FULL_TURN_DEGREES) * FULL_TURN_DEGREES, tween(400))
    snapTo(0f)
}
