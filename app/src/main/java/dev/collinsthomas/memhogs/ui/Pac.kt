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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

private const val MOUTH_NEARLY_CLOSED_DEGREES = 8f
private const val MOUTH_WIDE_OPEN_DEGREES = 72f
private const val MOUTH_MID_BITE_DEGREES = 44f

@Composable
fun Pac(size: Dp, chomping: Boolean, modifier: Modifier = Modifier) {
    val mouth: Float = if (chomping) {
        val transition = rememberInfiniteTransition(label = "pac")
        transition.animateFloat(
            initialValue = MOUTH_NEARLY_CLOSED_DEGREES,
            targetValue = MOUTH_WIDE_OPEN_DEGREES,
            animationSpec = infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse),
            label = "mouth",
        ).value
    } else {
        MOUTH_MID_BITE_DEGREES
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

@Composable
fun MemoryBits(active: Boolean, motion: Boolean, modifier: Modifier = Modifier) {
    val shift: Float = if (active && motion) {
        val transition = rememberInfiniteTransition(label = "bits")
        transition.animateFloat(
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
        repeat(3) { bit ->
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = -shift * step.toPx()
                        alpha = if (bit == 0) 1f - shift else 1f
                    }
                    .size(5.dp)
                    .background(Palette.Cyan.copy(alpha = if (bit == 2) 0.6f else 1f)),
            )
            if (bit < 2) Spacer(Modifier.width(step - 5.dp))
        }
    }
}

@Composable
fun EatingLoader(motion: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Pac(size = 18.dp, chomping = motion)
        Spacer(Modifier.width(10.dp))
        MemoryBits(active = true, motion = motion)
    }
}

@Composable
fun RamGauge(usedFraction: Float, modifier: Modifier = Modifier, hotAt: Float = HOT_DEVICE_USED_FRACTION) {
    val fill by animateFloatAsState(
        targetValue = usedFraction.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "fill",
    )
    val hot = usedFraction >= hotAt
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 3.dp.toPx()
        val cellWidth = 7.dp.toPx()
        val cellCount = ((size.width + gap) / (cellWidth + gap)).toInt().coerceAtLeast(1)
        val filledCells = fill * cellCount
        val fillColor = if (hot) Palette.Red else Palette.Amber
        for (cell in 0 until cellCount) {
            val color = when {
                cell + 1 <= filledCells -> fillColor
                cell < filledCells -> fillColor.copy(alpha = (filledCells - cell).coerceIn(0.15f, 1f))
                else -> Palette.Cell
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(cell * (cellWidth + gap), 0f),
                size = Size(cellWidth, size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}
