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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class TermLine(val text: String, val color: Color, val pauseAfter: Long = 140L)

val BlankLine = TermLine("", Palette.Dim, 0)

fun termParagraph(text: String, color: Color, pauseAfter: Long, linePause: Long = 0): List<TermLine> {
    val lines = text.lines()
    return lines.mapIndexed { lineIndex, line ->
        TermLine(line, color, if (lineIndex == lines.lastIndex) pauseAfter else linePause)
    }
}

@Composable
fun TypedTerminal(
    lines: List<TermLine>,
    motion: Boolean,
    modifier: Modifier = Modifier,
    onceTyped: @Composable () -> Unit,
) {
    var typedLineCount by remember(lines) { mutableIntStateOf(if (motion) 0 else lines.size) }
    var typedCharCount by remember(lines) { mutableIntStateOf(0) }
    val done = typedLineCount >= lines.size

    if (motion) {
        LaunchedEffect(lines) {
            for ((lineIndex, line) in lines.withIndex()) {
                if (lineIndex < typedLineCount) continue
                while (typedCharCount < line.text.length) {
                    delay(11)
                    typedCharCount++
                }
                delay(line.pauseAfter)
                typedLineCount++
                typedCharCount = 0
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEachIndexed { lineIndex, line ->
            when {
                lineIndex < typedLineCount -> TermText(line.text, line.color)
                lineIndex == typedLineCount -> TermText(line.text.take(typedCharCount), line.color, cursor = true)
            }
        }
        if (done) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { onceTyped() }
            }
        }
    }
}

@Composable
private fun TermText(text: String, color: Color, cursor: Boolean = false) {
    val cursorAlpha = if (cursor) blinkingAlpha() else 0f
    Text(
        text = buildAnnotatedString {
            append(text)
            if (cursor) withStyle(SpanStyle(color = color.copy(alpha = cursorAlpha))) { append("█") }
        },
        fontFamily = Mono,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = color,
    )
}

@Composable
private fun blinkingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink",
    )
    return alpha
}

@Composable
fun TermButton(label: String, accent: Color = Palette.Amber, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            "[ $label ]",
            fontFamily = Mono,
            fontSize = 14.sp,
            color = accent,
        )
    }
}
