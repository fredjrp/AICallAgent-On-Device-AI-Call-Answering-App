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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aicall.agent.data.BusinessKnowledgeManager
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
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
    val kb = BusinessKnowledgeManager.getInstance(context)
    val historyRepo = CallHistoryRepository.getInstance(context)
    val totalCostAndTokens = historyRepo.getTotalCostAndTokens()

    var isAutoAnswer by remember { mutableStateOf(prefs.isAutoAnswerEnabled) }
    var speakEarpiece by remember { mutableStateOf(prefs.speakThroughEarpiece) }
    var saveTranscripts by remember { mutableStateOf(prefs.saveTranscripts) }

    // Dialog states
    var showModelDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showTruecallerDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(currentApiKey) }
    var tempTruecallerToken by remember { mutableStateOf(prefs.truecallerToken) }

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
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Models, credentials, and behavior",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
        )

        // ── Group 1: Assistant & Business ───────────────────────────────────
        Text(
            text = "ASSISTANT & KNOWLEDGE BASE",
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
                    Text(
                        text = "Business Info & Assistant Name (${kb.assistantName})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text("Hours, rates, policies (100% on-device)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Group 2: Model & Credentials (with Free Models) ─────────────────
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
                // Model Row (Tap to change)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showModelDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Model", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            if (selectedModel.endsWith(":free")) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GreenPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GreenDeep,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(selectedModel, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Text("Change", style = MaterialTheme.typography.labelSmall, color = GreenDeep, fontWeight = FontWeight.Bold)
                }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineLight))

                // Voice Synthesizer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Voice Synthesizer", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Kokoro · on-device neural TTS", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineLight))

                // OpenRouter Key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            tempApiKey = currentApiKey
                            showApiKeyDialog = true
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OpenRouter API Key", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        val maskedKey = if (currentApiKey.length > 8) currentApiKey.take(7) + "••••••••" + currentApiKey.takeLast(4) else "Not set (tap to configure)"
                        Text(maskedKey, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Text("Edit", style = MaterialTheme.typography.labelSmall, color = GreenDeep, fontWeight = FontWeight.Bold)
                }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineLight))

                // Truecaller Token (truecallerjs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            tempTruecallerToken = prefs.truecallerToken
                            showTruecallerDialog = true
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Truecaller Token (Caller ID)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        val maskedTc = if (prefs.truecallerToken.length > 8) prefs.truecallerToken.take(5) + "••••••" else if (prefs.truecallerToken.isNotEmpty()) "Configured" else "Optional (for unsaved caller names)"
                        Text(maskedTc, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Text("Edit", style = MaterialTheme.typography.labelSmall, color = GreenDeep, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Group 3: Behaviour Toggles ──────────────────────────────────────
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
                        .padding(vertical = 12.dp)
                        .clickable { onRequestShizukuPermission() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shizuku", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = if (shizukuState == ShizukuState.RUNNING_AUTHORIZED) "Running · call audio access active" else "Tap to grant audio access permission",
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
                        Text("Quiet, private acoustic playback", style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
                        Text("On this device only (100-call limit)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
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

        // ── Group 4: Usage & Costs ──────────────────────────────────────────
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

        // ── Re-run Setup Wizard ─────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenOnboarding),
            shape = RoundedCornerShape(14.dp),
            color = LineLight
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Run Setup Wizard Again", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Reconfigure permissions, model, and business info", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // ── MODEL PICKER DIALOG (Free Models Highlighted) ───────────────────────
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = {
                Text(
                    text = "Select AI Model",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Choose an OpenRouter model. Models marked FREE incur zero API cost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    PreferencesManager.AVAILABLE_MODELS.forEach { opt ->
                        val isCurrent = selectedModel == opt.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectModel(opt.id)
                                    prefs.selectedModel = opt.id
                                    showModelDialog = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) GreenPrimary.copy(alpha = 0.12f) else SurfaceOverlay,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrent) GreenDeep else LineLight)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = opt.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrent) GreenDeep else TextPrimary
                                    )
                                    if (opt.isFree) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = GreenPrimary.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = "FREE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GreenDeep,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = opt.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = opt.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("Close", color = GreenDeep)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── API KEY DIALOG ──────────────────────────────────────────────────────
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("OpenRouter API Key", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        "Stored securely in Android Keystore with AES-GCM encryption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        singleLine = true,
                        placeholder = { Text("sk-or-v1-...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenDeep,
                            unfocusedBorderColor = LineMedium,
                            cursorColor = GreenDeep
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = tempApiKey.trim()
                        onSaveApiKey(cleaned)
                        prefs.openRouterApiKey = cleaned
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save", color = GreenDeep, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── TRUECALLER TOKEN DIALOG ─────────────────────────────────────────────
    if (showTruecallerDialog) {
        AlertDialog(
            onDismissRequest = { showTruecallerDialog = false },
            title = {
                Text("Truecaller Token", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        "Used by the app to identify unsaved incoming numbers in real-time using Truecaller's reverse search directory (truecallerjs architecture).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempTruecallerToken,
                        onValueChange = { tempTruecallerToken = it },
                        singleLine = true,
                        placeholder = { Text("Installation ID / Bearer token") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenDeep,
                            unfocusedBorderColor = LineMedium,
                            cursorColor = GreenDeep
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.truecallerToken = tempTruecallerToken.trim()
                        showTruecallerDialog = false
                    }
                ) {
                    Text("Save", color = GreenDeep, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTruecallerDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
