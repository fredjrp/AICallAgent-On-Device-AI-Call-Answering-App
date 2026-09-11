package com.aicall.agent.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.SurfacePill
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.util.PreferencesManager

@Composable
fun SettingsScreen(
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    currentPrompt: String,
    onSavePrompt: (String) -> Unit,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    shizukuState: ShizukuState,
    onRequestShizukuPermission: () -> Unit,
    onOpenBusinessKb: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val prefs = PreferencesManager.getInstance(context)
    val historyRepo = CallHistoryRepository.getInstance(context)
    val totalCostAndTokens = historyRepo.getTotalCostAndTokens()

    var isAutoAnswer by remember { mutableStateOf(prefs.isAutoAnswerEnabled) }
    var speakEarpiece by remember { mutableStateOf(prefs.speakThroughEarpiece) }
    var saveTranscripts by remember { mutableStateOf(prefs.saveTranscripts) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Setup",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Connections and behaviour",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
        )

        // Group 1: Business Knowledge Base
        Text(
            text = "KNOWLEDGE BASE",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBusinessKb),
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, LineLight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Business Info & Pricing", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("Hours, rates, policies (100% on-device)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Group 2: Model & Intelligence
        Text(
            text = "MODEL & CREDENTIALS",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, LineLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Model", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(selectedModel, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineLight))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Voice Synthesizer", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Kokoro · warm female", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineLight))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("OpenRouter Key", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        val maskedKey = if (currentApiKey.length > 8) currentApiKey.take(7) + "••••••••" + currentApiKey.takeLast(4) else "Not set"
                        Text(maskedKey, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Group 3: Behaviour Toggles matching HTML design
        Text(
            text = "BEHAVIOUR",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, LineLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                // Shizuku Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shizuku", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = if (shizukuState == ShizukuState.RUNNING_AUTHORIZED) "Running · call audio access" else "Action needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (shizukuState == ShizukuState.RUNNING_AUTHORIZED) GreenPrimary else ColorInactive)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LineLight))

                // Auto-answer calls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-answer calls", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Picks up after ${prefs.answerDelayRings} rings (~${prefs.answerDelayRings * 3}s)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = isAutoAnswer,
                        onCheckedChange = {
                            isAutoAnswer = it
                            prefs.isAutoAnswerEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GreenPrimary
                        )
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LineLight))

                // Speak through earpiece
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Speak through earpiece", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Quiet, private playback", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = speakEarpiece,
                        onCheckedChange = {
                            speakEarpiece = it
                            prefs.speakThroughEarpiece = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GreenPrimary
                        )
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LineLight))

                // Save transcripts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Save transcripts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("On this device only", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Switch(
                        checked = saveTranscripts,
                        onCheckedChange = {
                            saveTranscripts = it
                            prefs.saveTranscripts = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GreenPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Group 4: Cost & Token Tracker (2.5)
        Text(
            text = "USAGE & OPENROUTER COSTS",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, LineLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Tokens Processed", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("${totalCostAndTokens.second}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estimated Cumulative Cost", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("$${String.format("%.4f", totalCostAndTokens.first)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenDeep, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Re-run Onboarding button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenOnboarding),
            shape = RoundedCornerShape(14.dp),
            color = LineLight
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Re-run Setup Wizard", style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}
