package dev.collinsthomas.memhogs.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The memhogs pac: an amber wedge. When [chomping], the mouth opens and
 * closes; otherwise it holds a mid-bite pose, same as the site logo.
 */
@Composable
fun Pac(size: Dp, chomping: Boolean, modifier: Modifier = Modifier) {
    val mouth: Float = if (chomping) {
        val t = rememberInfiniteTransition(label = "pac")
        t.animateFloat(
            initialValue = 8f,
            targetValue = 72f,
            animationSpec = infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse),
            label = "mouth",
        ).value
    } else {
        44f
    }
    Canvas(modifier.size(size)) {
        drawArc(
            color = Palette.Amber,
            startAngle = mouth / 2f,
            sweepAngle = 360f - mouth,
            useCenter = true,
        )
    }
}

/**
 * Three cyan memory bits queued in front of the pac. While [active] they
 * ride a conveyor into its mouth.
 */
@Composable
fun Bits(active: Boolean, motion: Boolean, modifier: Modifier = Modifier) {
    val shift: Float = if (active && motion) {
        val t = rememberInfiniteTransition(label = "bits")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing)),
            label = "shift",
        ).value
    } else {
        0f
    }
    val step = 9.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = -shift * step.toPx()
                        alpha = if (i == 0) 1f - shift else 1f
                    }
                    .size(5.dp)
                    .background(Palette.Cyan.copy(alpha = if (i == 2) 0.6f else 1f))
            )
            if (i < 2) Spacer(Modifier.width(step - 5.dp))
        }
    }
}

/** Loading state: the pac eats an endless queue of bits. */
@Composable
fun EatingLoader(motion: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Pac(size = 18.dp, chomping = motion)
        Spacer(Modifier.width(10.dp))
        Bits(active = true, motion = motion)
    }
}

/**
 * The RAM gauge as a row of memory cells that fill left to right, amber
 * until [hotAt], red past it. While [refreshing], the pac rides the fill
 * edge, chomping its way across.
 */
@Composable
fun BitBar(
    frac: Float,
    refreshing: Boolean,
    motion: Boolean,
    modifier: Modifier = Modifier,
    hotAt: Float = 0.85f,
) {
    val fill by animateFloatAsState(
        targetValue = frac.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "fill",
    )
    val hot = frac >= hotAt
    BoxWithConstraints(modifier.fillMaxWidth().height(14.dp)) {
        val barWidth = maxWidth
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val gap = 3.dp.toPx()
            val cellW = 7.dp.toPx()
            val n = ((size.width + gap) / (cellW + gap)).toInt().coerceAtLeast(1)
            val filled = fill * n
            val on = if (hot) Palette.Red else Palette.Amber
            for (i in 0 until n) {
                val color = when {
                    i + 1 <= filled -> on
                    i < filled -> on.copy(alpha = (filled - i).coerceIn(0.15f, 1f))
                    else -> Palette.Cell
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * (cellW + gap), 0f),
                    size = Size(cellW, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        if (refreshing && motion) {
            Pac(
                size = 20.dp,
                chomping = true,
                modifier = Modifier
                    .offset(x = (barWidth * fill) - 10.dp, y = (-3).dp),
            )
        }
    }
}
