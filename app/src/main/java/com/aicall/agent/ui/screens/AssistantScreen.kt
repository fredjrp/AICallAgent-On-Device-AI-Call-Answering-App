package com.aicall.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.components.MorphingGradientButton
import com.aicall.agent.ui.components.SiriVoiceVisualizer
import com.aicall.agent.ui.components.VisualizerState
import com.aicall.agent.ui.theme.BorderLight
import com.aicall.agent.ui.theme.BrandCyan
import com.aicall.agent.ui.theme.BrandPurple
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.SurfaceCanvas
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

@Composable
fun AssistantScreen(
    currentSession: CallSession?,
    isAutoAnswerEnabled: Boolean,
    onToggleAutoAnswer: (Boolean) -> Unit,
    isDefaultDialer: Boolean,
    shizukuState: com.aicall.agent.shizuku.ShizukuState,
    onRequestDialerRole: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onTestAssistant: () -> Unit
) {
    val scrollState = rememberScrollState()

    var visualizerState by remember { mutableStateOf(VisualizerState.IDLE) }
    var audioLevel by remember { mutableFloatStateOf(0.5f) }

    // Update visualizer state based on session
    val activeState = when {
        currentSession != null && currentSession.state == android.telecom.Call.STATE_ACTIVE -> VisualizerState.RESPONDING
        currentSession != null -> VisualizerState.LISTENING
        else -> visualizerState
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Top App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Call Assistant",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary
                )
                Text(
                    text = if (isAutoAnswerEnabled) "Active • Monitoring Calls" else "Standby",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAutoAnswerEnabled) ColorActive else TextTertiary
                )
            }

            // Status Indicator Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isAutoAnswerEnabled) ColorActive.copy(alpha = 0.12f) else SurfaceOverlay)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isAutoAnswerEnabled) ColorActive else TextTertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAutoAnswerEnabled) "ARMED" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAutoAnswerEnabled) ColorActive else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Call Banner (Visible if a call is ongoing)
        AnimatedVisibility(
            visible = currentSession != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ColorActive.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = ColorActive,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSession?.phoneNumber ?: "Unknown",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Call in progress • ${currentSession?.durationSeconds ?: 0}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorActive
                        )
                    }
                }
            }
        }

        // Central Siri Voice Visualizer Glass Card
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
                    text = when (activeState) {
                        VisualizerState.IDLE -> "AI Engine Idle"
                        VisualizerState.LISTENING -> "Listening to Caller..."
                        VisualizerState.RESPONDING -> "Generating Response..."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandPurple,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // The Siri-like harmonic sine wave visualizer
                SiriVoiceVisualizer(
                    state = activeState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    audioLevel = audioLevel
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Acoustic Earpiece Uplink Loop Ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Controls Group
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Auto-Answer Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Answer Calls",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Direct incoming calls to AI assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isAutoAnswerEnabled,
                        onCheckedChange = onToggleAutoAnswer,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandPurple,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = BorderLight
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .height(1.dp)
                        .background(BorderLight)
                )

                // System Permission & Role Checklist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Telecom Default Dialer",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isDefaultDialer) "Configured as default dialer" else "Required for call intercept",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDefaultDialer) ColorActive else ColorWarning
                        )
                    }

                    if (isDefaultDialer) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ColorActive,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BrandPurple.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Set Role",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandPurple,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shizuku Setup & Diagnostics Card
        com.aicall.agent.ui.components.ShizukuSetupCard(
            state = shizukuState,
            onRequestPermission = onRequestShizukuPermission
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Morphing CTA Button
        MorphingGradientButton(
            text = if (visualizerState == VisualizerState.IDLE) "Test AI Voice Response" else "Stop Test Voice Loop",
            isActive = isAutoAnswerEnabled,
            onClick = {
                visualizerState = if (visualizerState == VisualizerState.IDLE) {
                    VisualizerState.LISTENING
                } else {
                    VisualizerState.IDLE
                }
                onTestAssistant()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom bar
    }
}
