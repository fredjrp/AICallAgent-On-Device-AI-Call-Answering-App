package com.aicall.agent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OrbVisualState(val label: String, val statusSub: String) {
    IDLE("Front Desk", "Standing by"),
    LISTENING("Caller is talking", "Listening"),
    THINKING("Working on it", "Thinking"),
    SPEAKING("Front Desk", "Speaking"),
    RINGING("Incoming call", "Ringing"),
    PAUSED("AI Disabled", "Paused")
}

/**
 * Native Jetpack Compose implementation of the Front Desk Orb.
 * Features multi-stop soft radial gradients, glowing breathing halo,
 * and state-based transitions (idle, listening, thinking, speaking, ringing, paused).
 */
@Composable
fun InteractiveOrb(
    state: OrbVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_anim")

    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state == OrbVisualState.RINGING) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == OrbVisualState.RINGING) 800 else 3200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == OrbVisualState.RINGING) 900 else 3800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension / 2f) * 0.72f

            // 1. Soft Halo Glow
            drawHalo(center, radius * haloPulse, state)

            // 2. Main Orb Body with state colors
            drawOrbBody(center, radius * breatheScale, state, rotateAngle)
        }
    }
}

private fun DrawScope.drawHalo(center: Offset, haloRadius: Float, state: OrbVisualState) {
    val haloColor = when (state) {
        OrbVisualState.IDLE -> Color(0x558FCFA8)
        OrbVisualState.LISTENING -> Color(0x66D9963D)
        OrbVisualState.THINKING -> Color(0x669F7AEA)
        OrbVisualState.SPEAKING -> Color(0x774CAF7D)
        OrbVisualState.RINGING -> Color(0x884CAF7D)
        OrbVisualState.PAUSED -> Color(0x337A8C80)
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(haloColor, haloColor.copy(alpha = 0.1f), Color.Transparent),
            center = center,
            radius = haloRadius * 1.35f
        ),
        radius = haloRadius * 1.35f,
        center = center
    )
}

private fun DrawScope.drawOrbBody(center: Offset, radius: Float, state: OrbVisualState, angle: Float) {
    val baseGradients = when (state) {
        OrbVisualState.IDLE -> listOf(
            Color(0xFFFCE4F0),
            Color(0xFFD8F5C2),
            Color(0xFFBEF0E8),
            Color(0xFFA8E8C8),
            Color(0xFF78CFA8)
        )
        OrbVisualState.LISTENING -> listOf(
            Color(0xFFFFF4E0),
            Color(0xFFFFDFBA),
            Color(0xFFFFC288),
            Color(0xFFEAA655)
        )
        OrbVisualState.THINKING -> listOf(
            Color(0xFFF3E8FF),
            Color(0xFFE9D5FF),
            Color(0xFFD8B4FE),
            Color(0xFFA855F7)
        )
        OrbVisualState.SPEAKING -> listOf(
            Color(0xFFE6FFFA),
            Color(0xFFB2F5EA),
            Color(0xFF81E6D9),
            Color(0xFF38B2AC),
            Color(0xFF2E7D56)
        )
        OrbVisualState.RINGING -> listOf(
            Color(0xFFFFFFFF),
            Color(0xFFC6F6D5),
            Color(0xFF68D391),
            Color(0xFF2E7D56)
        )
        OrbVisualState.PAUSED -> listOf(
            Color(0xFFF7FAFC),
            Color(0xFFEDF2F7),
            Color(0xFFCBD5E0),
            Color(0xFFA0AEC0)
        )
    }

    // Outer spherical gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = baseGradients,
            center = Offset(center.x - (radius * 0.25f), center.y - (radius * 0.25f)),
            radius = radius * 1.1f
        ),
        radius = radius,
        center = center
    )

    // Inner glossy soft-light highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(center.x - (radius * 0.35f), center.y - (radius * 0.38f)),
            radius = radius * 0.55f
        ),
        radius = radius,
        center = center
    )
}
