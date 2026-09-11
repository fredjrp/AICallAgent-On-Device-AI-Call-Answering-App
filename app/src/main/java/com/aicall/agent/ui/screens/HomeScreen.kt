package com.aicall.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.BusinessKnowledgeManager
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.data.CallRecord
import com.aicall.agent.pipeline.KokoroTtsEngine
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.components.InteractiveOrb
import com.aicall.agent.ui.components.OrbVisualState
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clean, frictionless Home Screen:
 * - Dominant, pulsating Interactive Orb (240dp) with tap-to-test voice sandbox.
 * - Beneath the Orb: "Standing by" sub-status and editable Assistant Persona (default: "Linda").
 * - Zero media pause button, zero disjointed stat rectangles, zero quick shortcuts.
 * - Directly anchored Recent Calls feed that gracefully fills the screen down to bottom navigation.
 */
@Composable
fun HomeScreen(
    currentSession: CallSession?,
    isAutoAnswerEnabled: Boolean,
    isAgentPaused: Boolean,
    shizukuState: ShizukuState,
    isDefaultDialer: Boolean,
    onRequestDialerRole: () -> Unit,
    onRequestShizuku: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onSelectRecord: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kb = BusinessKnowledgeManager.getInstance(context)
    val historyRepo = CallHistoryRepository.getInstance(context)
    val callRecords by historyRepo.recordsFlow.collectAsState()

    var assistantName by remember { mutableStateOf(kb.assistantName) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var tempRenameText by remember { mutableStateOf(assistantName) }

    // Test Sandbox State for Orb interaction
    var isTestingAssistant by remember { mutableStateOf(false) }
    var testFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val orbState = when {
        isTestingAssistant -> OrbVisualState.SPEAKING
        isAgentPaused -> OrbVisualState.PAUSED
        currentSession != null && currentSession.state == android.telecom.Call.STATE_RINGING -> OrbVisualState.RINGING
        currentSession != null && currentSession.state == android.telecom.Call.STATE_ACTIVE -> OrbVisualState.SPEAKING
        isAutoAnswerEnabled -> OrbVisualState.IDLE
        else -> OrbVisualState.PAUSED
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Diagnostic Banners (Only shown when setup incomplete)
        if (!isDefaultDialer) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestDialerRole),
                shape = RoundedCornerShape(14.dp),
                color = ColorWarning.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ColorWarning.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ColorWarning, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Set as Default Phone app to receive calls",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Set", style = MaterialTheme.typography.labelSmall, color = GreenDeep, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (shizukuState != ShizukuState.RUNNING_AUTHORIZED) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestShizuku),
                shape = RoundedCornerShape(14.dp),
                color = ColorInactive.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ColorInactive.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ColorInactive, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (shizukuState == ShizukuState.NOT_RUNNING) "Shizuku not running (audio access needed)" else "Grant Shizuku permission",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Fix", style = MaterialTheme.typography.labelSmall, color = GreenDeep, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Active Call in-progress banner
        if (currentSession != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = GreenPrimary.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = GreenDeep, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSession.phoneNumber,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentSession.isPassiveMode) "Human call (transcribing) • ${currentSession.durationSeconds}s" else "$assistantName is active • ${currentSession.durationSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenDeep
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── DOMINANT HERO INTERACTIVE ORB (240dp) ───────────────────────────
        // Tapping the Orb lets the user test the voice assistant live
        Box(
            modifier = Modifier
                .size(240.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isTestingAssistant && currentSession == null) {
                        scope.launch {
                            isTestingAssistant = true
                            testFeedbackMessage = "\"Hello, I'm $assistantName! I'm ready to answer your calls.\""
                            // Simulate quick voice test pulse
                            delay(2800)
                            isTestingAssistant = false
                            delay(2000)
                            testFeedbackMessage = null
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            InteractiveOrb(
                state = orbState,
                size = 240.dp
            )
        }

        // Live Voice Feedback Pill when testing
        AnimatedVisibility(
            visible = testFeedbackMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = GreenPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = testFeedbackMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = GreenDeep,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── SUB-STATUS & EDITABLE ASSISTANT PERSONA ─────────────────────────
        Text(
            text = if (isTestingAssistant) "Speaking test greeting..." else orbState.statusSub,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            letterSpacing = 0.2.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Assistant Name with subtle edit hint (tap to rename)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    tempRenameText = assistantName
                    showRenameDialog = true
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = assistantName,
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Rename Assistant",
                tint = TextMuted.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── RECENT CALLS FEED ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "See all",
                style = MaterialTheme.typography.bodySmall,
                color = GreenDeep,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onSeeAllHistory)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val recentList = callRecords.take(5)
        if (recentList.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = SurfaceOverlay.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LineLight)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No calls recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$assistantName is standing by to answer your incoming calls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            recentList.forEach { record ->
                RecentRowItem(
                    record = record,
                    onClick = { onSelectRecord(record.id) }
                )
            }
        }

        // Bottom navigation bar breathing room
        Spacer(modifier = Modifier.height(100.dp))
    }

    // ── RENAME ASSISTANT DIALOG ─────────────────────────────────────────────
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    text = "Name your Assistant",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Give your phone assistant a friendly name like Linda, Sarah, or Alex. She will introduce herself with this name when answering calls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = tempRenameText,
                        onValueChange = { tempRenameText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Linda") },
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
                        val cleaned = tempRenameText.trim().ifEmpty { "Linda" }
                        kb.assistantName = cleaned
                        assistantName = cleaned
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = GreenDeep, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun RecentRowItem(
    record: CallRecord,
    onClick: () -> Unit
) {
    val initials = if (!record.callerName.isNullOrBlank()) {
        record.callerName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
    } else {
        record.callerNumber.filter { it.isDigit() }.takeLast(2).ifEmpty { "?" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFD8F0E0), Color(0xFFB8E4C8)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = GreenDeep,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.callerName ?: record.callerNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${record.formattedTime} · ${record.outcome}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }

        Text(
            text = record.formattedDuration,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LineLight)
    )
}
