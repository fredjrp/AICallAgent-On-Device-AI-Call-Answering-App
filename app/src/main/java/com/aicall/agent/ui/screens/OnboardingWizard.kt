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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.aicall.agent.shizuku.ShizukuAudioAccess
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDark
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.GreenSoft
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.util.PreferencesManager

@Composable
fun OnboardingWizard(
    isDefaultDialer: Boolean,
    shizukuState: ShizukuState,
    onRequestDialerRole: () -> Unit,
    onRequestShizuku: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val prefs = PreferencesManager.getInstance(context)
    val kb = BusinessKnowledgeManager.getInstance(context)

    var step by remember { mutableIntStateOf(1) }
    var apiKey by remember { mutableStateOf(prefs.openRouterApiKey) }
    var bizName by remember { mutableStateOf(kb.businessName) }
    var bizHours by remember { mutableStateOf(kb.businessHours) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Progress Bar (6 steps)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 1..6) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) GreenDeep else LineLight)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        when (step) {
            1 -> {
                // Step 1: Welcome & Value Prop
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GreenSoft.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = GreenDeep, modifier = Modifier.size(44.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Welcome to Front Desk", style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Your private on-device voice assistant that answers incoming calls, quotes real business pricing and hours, and schedules appointments automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            2 -> {
                // Step 2: Default Phone App
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isDefaultDialer) GreenSoft.copy(alpha = 0.3f) else ColorWarning.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = if (isDefaultDialer) GreenDeep else ColorWarning, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Default Phone App", style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android requires Front Desk to be registered as your default phone dialer to intercept incoming calls before they ring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                ElevatedButton(
                    onClick = onRequestDialerRole,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (isDefaultDialer) GreenSoft.copy(alpha = 0.4f) else GreenDeep,
                        contentColor = if (isDefaultDialer) GreenDark else Color.White
                    )
                ) {
                    Text(if (isDefaultDialer) "✓ Default Dialer Granted" else "Grant Default Dialer Role")
                }
            }

            3 -> {
                // Step 3: Shizuku Pairing
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (shizukuState == ShizukuState.RUNNING_AUTHORIZED) GreenSoft.copy(alpha = 0.3f) else ColorInactive.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = if (shizukuState == ShizukuState.RUNNING_AUTHORIZED) GreenDeep else ColorInactive, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Shizuku Audio Privileges", style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android requires high-trust ADB privileges to capture clean digital phone call audio. Shizuku runs without needing root or unlocking your bootloader.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceOverlay,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Status: ${shizukuState.name.replace("_", " ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (shizukuState == ShizukuState.RUNNING_AUTHORIZED) GreenDeep else ColorWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                ElevatedButton(
                    onClick = onRequestShizuku,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = GreenDeep, contentColor = Color.White)
                ) {
                    Text("Check / Request Shizuku Access")
                }
            }

            4 -> {
                // Step 4: Safaricom / Carrier USSD Forwarding
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GreenSoft.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhoneForwarded, contentDescription = null, tint = GreenDeep, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Carrier Call Forwarding", style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "If using Safaricom or Airtel on a secondary SIM, set calls to forward when busy or unanswered:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LineLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Forward when busy: **67*<Phone>#", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Forward when unanswered: **61*<Phone>#", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Check status: *#67#", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            5 -> {
                // Step 5: OpenRouter Key
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GreenSoft.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = GreenDeep, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "OpenRouter API Key", style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your API key is stored securely in Android Keystore. The ONLY external connection is for live conversational completions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("OpenRouter API Key") },
                    placeholder = { Text("sk-or-v1-...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            6 -> {
                // Step 6: Business Info Baseline
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GreenSoft.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = GreenDeep, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Business Information", style = MaterialTheme.typography.displaySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Provide your business details so the AI speaks with accurate hours, pricing, and services.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = bizName,
                    onValueChange = { bizName = it },
                    label = { Text("Business Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = bizHours,
                    onValueChange = { bizHours = it },
                    label = { Text("Operating Hours") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Navigation CTA
        ElevatedButton(
            onClick = {
                if (step < 6) {
                    step++
                } else {
                    prefs.openRouterApiKey = apiKey.trim()
                    kb.businessName = bizName.trim()
                    kb.businessHours = bizHours.trim()
                    prefs.isOnboardingCompleted = true
                    onComplete()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.elevatedButtonColors(containerColor = GreenDeep, contentColor = Color.White)
        ) {
            Text(if (step < 6) "Continue" else "Finish Setup & Start Agent", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}
