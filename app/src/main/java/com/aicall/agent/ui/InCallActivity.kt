package com.aicall.agent.ui

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.components.SiriVoiceVisualizer
import com.aicall.agent.ui.components.VisualizerState
import com.aicall.agent.ui.theme.AICallTheme
import com.aicall.agent.ui.theme.BrandCyan
import com.aicall.agent.ui.theme.BrandPurple
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.SurfaceCanvas
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val session by CallAnswerService.currentSessionFlow.collectAsState()

            // If call ended, finish this in-call UI
            LaunchedEffect(session) {
                if (session == null || session?.state == android.telecom.Call.STATE_DISCONNECTED) {
                    delay(800)
                    finishAndRemoveTask()
                }
            }

            AICallTheme {
                InCallScreen(
                    session = session,
                    onAnswer = { CallAnswerService.answerCurrentCall() },
                    onDecline = {
                        CallAnswerService.hangUpCurrentCall()
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }
}

@Composable
fun InCallScreen(
    session: CallSession?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val isRinging = session?.state == android.telecom.Call.STATE_RINGING
    val isActive = session?.state == android.telecom.Call.STATE_ACTIVE

    var callDurationSeconds by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (isActive) {
            val start = System.currentTimeMillis()
            while (true) {
                callDurationSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        }
    }

    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // State indicator badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isActive) ColorActive.copy(alpha = 0.12f) else BrandPurple.copy(alpha = 0.12f),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) ColorActive else BrandPurple)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isRinging -> "INCOMING CALL"
                        isActive -> "CALL IN PROGRESS • $durationFormatted"
                        else -> "CONNECTING..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) ColorActive else BrandPurple,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Caller Details
        Text(
            text = session?.phoneNumber ?: "Unknown Caller",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "AI Call Assistant Intercept",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Harmonic Voice Visualizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), ambientColor = BrandPurple.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isActive) "AI Assistant Active" else "Ready to Answer",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandPurple,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                SiriVoiceVisualizer(
                    state = if (isActive) VisualizerState.RESPONDING else VisualizerState.LISTENING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    audioLevel = 0.65f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Acoustic Earpiece Uplink Connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Controls
        if (isRinging) {
            // Ringing state: Decline vs Answer with AI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline Button (Red)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, CircleShape, ambientColor = ColorInactive.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(ColorInactive)
                            .clickable(onClick = onDecline),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Decline", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }

                // Answer with AI Button (Green / Brand)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, CircleShape, ambientColor = ColorActive.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(ColorActive)
                            .clickable(onClick = onAnswer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Answer", style = MaterialTheme.typography.labelMedium, color = ColorActive, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Active call state: In-call action bar (Mute, Speaker, End)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFE5E5EA) else SurfaceOverlay)
                            .clickable { isMuted = !isMuted },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (isMuted) ColorInactive else TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(if (isMuted) "Muted" else "Mute", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }

                // End Call Button (Big Red)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .shadow(8.dp, CircleShape, ambientColor = ColorInactive.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(ColorInactive)
                            .clickable(onClick = onDecline),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("End Call", style = MaterialTheme.typography.labelSmall, color = ColorInactive, fontWeight = FontWeight.Bold)
                }

                // Speaker Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerOn) Color(0xFFE5E5EA) else SurfaceOverlay)
                            .clickable { isSpeakerOn = !isSpeakerOn },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerOn) BrandPurple else TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(if (isSpeakerOn) "Speaker" else "Earpiece", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
