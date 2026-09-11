package com.aicall.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.CallActionItem
import com.aicall.agent.data.CallTranscriptMessage
import com.aicall.agent.pipeline.ConversationOrchestrator
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.theme.BgBottom
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.GreenSoft
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

@Composable
fun LiveCallScreen(
    currentSession: CallSession?,
    onEndCall: () -> Unit
) {
    val orchestrator = CallAnswerService.activeOrchestrator
    val liveMessages by orchestrator?.liveMessages?.collectAsState() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val liveActions by orchestrator?.liveActions?.collectAsState() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
    val convState by orchestrator?.stateFlow?.collectAsState() ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ConversationOrchestrator.ConversationState.IDLE) }

    val listState = rememberLazyListState()

    LaunchedEffect(liveMessages.size, liveActions.size) {
        val totalCount = liveMessages.size + liveActions.size
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentSession?.phoneNumber ?: "+254 712 44 5XXX",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                val durationFormatted = String.format("%02d:%02d", (currentSession?.durationSeconds ?: 0) / 60, (currentSession?.durationSeconds ?: 0) % 60)
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (convState == ConversationOrchestrator.ConversationState.SPEAKING) GreenPrimary else ColorWarning)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (convState) {
                        ConversationOrchestrator.ConversationState.IDLE -> if (currentSession?.isPassiveMode == true) "Human call active" else "Standing by"
                        ConversationOrchestrator.ConversationState.LISTENING -> "Caller is talking"
                        ConversationOrchestrator.ConversationState.TRANSCRIBING -> "Transcribing speech…"
                        ConversationOrchestrator.ConversationState.THINKING -> "Working on response…"
                        ConversationOrchestrator.ConversationState.SYNTHESIZING -> "Preparing speech…"
                        ConversationOrchestrator.ConversationState.SPEAKING -> "Front Desk is speaking"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (convState == ConversationOrchestrator.ConversationState.SPEAKING) GreenDeep else TextMuted
                )
            }
        }

        // Live Chat Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (liveMessages.isEmpty() && liveActions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentSession != null) "Listening to audio stream…" else "No active call at the moment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }

            items(liveMessages) { msg ->
                ChatBubble(message = msg)
            }

            items(liveActions) { action ->
                ActionRowItem(action = action)
            }
        }

        // Bottom Bar (Pill + End Call button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, BgBottom.copy(alpha = 0.95f))))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                color = SurfaceCard.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LineLight)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = GreenDeep,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (currentSession != null) "Live audio active…" else "Listening…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // End Call Button
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onEndCall),
                shape = CircleShape,
                color = ColorInactive,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(70.dp)) // Nav bar offset
    }
}

@Composable
fun ChatBubble(message: CallTranscriptMessage) {
    val isAgent = message.speaker.equals("agent", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isAgent) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFC8F0C8), Color(0xFF8ED8B8)))
                    else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFCFE2D5), Color(0xFFA8C4B2)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isAgent) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = GreenDeep, modifier = Modifier.size(14.dp))
            } else {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2C4636),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.name,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAgent) Color(0xFF1F3327) else Color(0xFF556A5B),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ActionRowItem(action: CallActionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 39.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(GreenPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
