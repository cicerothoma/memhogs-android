package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** One line of a typed terminal transcript. */
data class TermLine(val text: String, val color: Color, val pauseAfter: Long = 140L)

/**
 * Types [lines] out character by character with a blinking block cursor,
 * then reveals [after] (buttons, extra content). With motion off, the
 * whole transcript appears instantly.
 */
@Composable
fun TypedTerminal(
    lines: List<TermLine>,
    motion: Boolean,
    modifier: Modifier = Modifier,
    after: @Composable () -> Unit,
) {
    var lineIdx by remember(lines) { mutableIntStateOf(if (motion) 0 else lines.size) }
    var charIdx by remember(lines) { mutableIntStateOf(0) }
    val done = lineIdx >= lines.size

    if (motion) {
        LaunchedEffect(lines) {
            for ((i, line) in lines.withIndex()) {
                if (i < lineIdx) continue
                while (charIdx < line.text.length) {
                    delay(11)
                    charIdx++
                }
                delay(line.pauseAfter)
                lineIdx++
                charIdx = 0
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEachIndexed { i, line ->
            when {
                i < lineIdx -> TermText(line.text, line.color)
                i == lineIdx -> TermText(line.text.take(charIdx), line.color, cursor = true)
            }
        }
        if (done) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { after() }
            }
        }
    }
}

@Composable
private fun TermText(text: String, color: Color, cursor: Boolean = false) {
    Box {
        Text(
            text = if (cursor) "$text█" else text,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = color,
        )
        if (cursor) {
            // The block glyph above reserves space; pulse it by overdrawing.
            val t = rememberInfiniteTransition(label = "cursor")
            val a by t.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
                label = "blink",
            )
            Text(
                text = text + "█",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = color,
                modifier = Modifier.alpha(a),
            )
        }
    }
}

/** A terminal-styled button: "[ label ]" in a thin accent border. */
@Composable
fun TermButton(label: String, accent: Color = Palette.Amber, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            "[ $label ]",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = accent,
        )
    }
}
