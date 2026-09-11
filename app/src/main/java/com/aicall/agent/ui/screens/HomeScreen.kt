package com.aicall.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.aicall.agent.data.CallRecord
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.components.InteractiveOrb
import com.aicall.agent.ui.components.OrbVisualState
import com.aicall.agent.ui.theme.BgBottom
import com.aicall.agent.ui.theme.BgMid
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDark
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.GreenSoft
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfacePill
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

@Composable
fun HomeScreen(
    currentSession: CallSession?,
    isAutoAnswerEnabled: Boolean,
    isAgentPaused: Boolean,
    onTogglePause: () -> Unit,
    shizukuState: ShizukuState,
    isDefaultDialer: Boolean,
    onRequestDialerRole: () -> Unit,
    onRequestShizuku: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onSelectRecord: (String) -> Unit
) {
    val context = LocalContext.current
    val historyRepo = CallHistoryRepository.getInstance(context)
    val callRecords by historyRepo.recordsFlow.collectAsState()
    val todayStats = historyRepo.getTodayStats()

    val orbState = when {
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
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // System Diagnostic Warning Banners if not ready
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
                    modifier = Modifier.padding(12.dp),
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
                    modifier = Modifier.padding(12.dp),
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                            text = if (currentSession.isPassiveMode) "Human call (transcribing) • ${currentSession.durationSeconds}s" else "AI agent active • ${currentSession.durationSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenDeep
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // The Central Interactive Orb
        Spacer(modifier = Modifier.height(12.dp))
        InteractiveOrb(
            state = orbState,
            size = 200.dp
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = orbState.statusSub,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            letterSpacing = 0.2.sp
        )
        Text(
            text = orbState.label,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        // Pause/Resume Quick Toggle
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.clickable(onClick = onTogglePause),
            shape = RoundedCornerShape(20.dp),
            color = if (isAgentPaused) ColorWarning.copy(alpha = 0.15f) else LineLight,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isAgentPaused) ColorWarning else LineMedium)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAgentPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = if (isAgentPaused) ColorWarning else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAgentPaused) "Resume Agent" else "Pause Agent",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAgentPaused) ColorWarning else TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3-Stat Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatPill(number = "${todayStats.first}", label = "Calls today", modifier = Modifier.weight(1f))
            StatPill(number = todayStats.second, label = "Avg. handled", modifier = Modifier.weight(1f))
            StatPill(number = "${todayStats.third}", label = "Missed", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Recent Section Header
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

        Spacer(modifier = Modifier.height(10.dp))

        // Recent Calls List
        val recentList = callRecords.take(4)
        if (recentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No calls recorded yet. Incoming calls will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            recentList.forEach { record ->
                RecentRowItem(
                    record = record,
                    onClick = { onSelectRecord(record.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(90.dp)) // padding for bottom nav
    }
}

@Composable
fun StatPill(
    number: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfacePill,
        border = androidx.compose.foundation.BorderStroke(1.dp, LineLight),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
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
        "?"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFD8F0E0), Color(0xFFB8E4C8)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = GreenDeep,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

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
                fontSize = 11.5.sp
            )
        }

        Text(
            text = record.formattedDuration,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontFamily = FontFamily.Monospace
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LineLight)
    )
}
