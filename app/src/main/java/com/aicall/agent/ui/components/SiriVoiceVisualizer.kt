package com.aicall.agent.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aicall.agent.ui.theme.BrandCyan
import com.aicall.agent.ui.theme.BrandMid
import com.aicall.agent.ui.theme.BrandPurple
import kotlin.math.PI
import kotlin.math.sin

enum class VisualizerState {
    IDLE,
    LISTENING,
    RESPONDING
}

@Composable
fun SiriVoiceVisualizer(
    state: VisualizerState,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
    audioLevel: Float = 0.5f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_anim"
    )

    val targetAmplitude = when (state) {
        VisualizerState.IDLE -> 12f
        VisualizerState.LISTENING -> (30f + audioLevel * 35f)
        VisualizerState.RESPONDING -> (45f + audioLevel * 45f)
    }

    val animatedAmplitude = remember { Animatable(12f) }

    LaunchedEffect(targetAmplitude) {
        animatedAmplitude.animateTo(
            targetValue = targetAmplitude,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 120f)
        )
    }

    val waveColors = listOf(
        listOf(BrandPurple.copy(alpha = 0.85f), BrandCyan.copy(alpha = 0.85f)),
        listOf(BrandCyan.copy(alpha = 0.6f), BrandMid.copy(alpha = 0.6f)),
        listOf(BrandMid.copy(alpha = 0.4f), BrandPurple.copy(alpha = 0.4f)),
        listOf(BrandPurple.copy(alpha = 0.25f), BrandCyan.copy(alpha = 0.25f))
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val amp = animatedAmplitude.value

        // Draw soft ambient glow behind waves
        if (state != VisualizerState.IDLE) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandPurple.copy(alpha = 0.18f),
                        BrandCyan.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(width / 2f, centerY),
                    radius = width * 0.45f
                )
            )
        }

        // Draw 4 harmonic sine waves with distinct frequencies & phase shifts
        waveColors.forEachIndexed { index, colors ->
            val path = Path()
            val freq = 1f + index * 0.4f
            val phaseOffset = phase * (1f + index * 0.3f) + (index * 0.7f)
            val strokeWidth = if (index == 0) 3.5f else 2f

            for (x in 0..width.toInt() step 4) {
                val normalizedX = x / width // 0..1
                // Parabolic envelope to taper edges smoothly to zero at ends
                val envelope = 4f * normalizedX * (1f - normalizedX)
                val y = centerY + sin(normalizedX * 2 * PI * freq + phaseOffset).toFloat() * amp * envelope

                if (x == 0) {
                    path.moveTo(x.toFloat(), y)
                } else {
                    path.lineTo(x.toFloat(), y)
                }
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = colors,
                    startX = 0f,
                    endX = width
                ),
                style = Stroke(
                    width = strokeWidth.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
