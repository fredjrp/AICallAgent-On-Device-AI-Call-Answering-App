package com.aicall.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.BorderLight
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

data class CallHistoryItem(
    val id: String,
    val callerName: String?,
    val phoneNumber: String,
    val timestamp: String,
    val durationText: String,
    val wasAiHandled: Boolean,
    val summarySnippet: String,
    val transcript: List<Pair<String, String>>, // Speaker ("Caller" or "AI") to text
    val recordingAvailable: Boolean = false
)

@Composable
fun CallHistoryScreen(
    items: List<CallHistoryItem> = getSampleCallHistory(),
    onPlayRecording: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Screen Header
        Text(
            text = "Call Activity",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary
        )
        Text(
            text = "Recent calls handled by AI and recorded sessions",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Stat Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val aiCount = items.count { it.wasAiHandled }
            StatCard(title = "AI Handled", count = "$aiCount", color = BrandPurple, modifier = Modifier.weight(1f))
            StatCard(title = "Total Calls", count = "${items.size}", color = BrandCyan, modifier = Modifier.weight(1f))
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No calls recorded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    CallHistoryCard(
                        item = item,
                        onPlayRecording = { onPlayRecording(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = count, style = MaterialTheme.typography.headlineLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CallHistoryCard(
    item: CallHistoryItem,
    onPlayRecording: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = 300f),
        label = "arrow_rot"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Icon, Caller details, Time, Expand Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (item.wasAiHandled) BrandPurple.copy(alpha = 0.12f) else SurfaceOverlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.wasAiHandled) Icons.Default.SmartToy else Icons.Default.CallReceived,
                        contentDescription = null,
                        tint = if (item.wasAiHandled) BrandPurple else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & phone
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.callerName ?: item.phoneNumber,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (item.callerName != null) item.phoneNumber else "Incoming Call",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Time and Chevron
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Short Summary Badge & duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.summarySnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.durationText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.wasAiHandled) ColorActive else TextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Accordion: Expanded content (full conversation transcript & audio playback)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderLight)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "CONVERSATION TRANSCRIPT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    item.transcript.forEach { (speaker, text) ->
                        val isAI = speaker.equals("AI", ignoreCase = true)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = if (isAI) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = speaker,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAI) BrandPurple else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isAI) 12.dp else 2.dp,
                                            bottomEnd = if (isAI) 2.dp else 12.dp
                                        )
                                    )
                                    .background(
                                        if (isAI) BrandPurple.copy(alpha = 0.1f) else SurfaceOverlay
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    if (item.recordingAvailable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceOverlay)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio Recording (WAV)",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onPlayRecording, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play recording",
                                    tint = BrandPurple
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getSampleCallHistory(): List<CallHistoryItem> = listOf(
    CallHistoryItem(
        id = "call-1",
        callerName = "Dr. Evans Clinic",
        phoneNumber = "+1 (555) 234-5678",
        timestamp = "Today, 11:42 AM",
        durationText = "1m 14s",
        wasAiHandled = true,
        summarySnippet = "Confirmed appointment for Thursday at 2:30 PM",
        transcript = listOf(
            "Caller" to "Hello, calling to confirm tomorrow's appointment for 2:30 PM.",
            "AI" to "Hello, this is the AI assistant. I have confirmed the 2:30 PM appointment on Thursday. Thank you!",
            "Caller" to "Great, see you then. Bye."
        ),
        recordingAvailable = true
    ),
    CallHistoryItem(
        id = "call-2",
        callerName = "Amazon Delivery",
        phoneNumber = "+1 (555) 987-6543",
        timestamp = "Today, 9:15 AM",
        durationText = "48s",
        wasAiHandled = true,
        summarySnippet = "Instructed courier to leave package at front door",
        transcript = listOf(
            "Caller" to "Hi, I'm downstairs with your package. Need a code or gate key.",
            "AI" to "Hello! Please leave the package securely by the front door behind the planter. Thanks!",
            "Caller" to "Got it, left it by the planter. Have a good one."
        ),
        recordingAvailable = true
    ),
    CallHistoryItem(
        id = "call-3",
        callerName = null,
        phoneNumber = "+1 (800) 444-1234",
        timestamp = "Yesterday",
        durationText = "12s",
        wasAiHandled = false,
        summarySnippet = "Potential Telemarketer — Auto-rejected",
        transcript = listOf(
            "Caller" to "This is an urgent notice regarding your vehicle warranty...",
            "AI" to "The recipient is unavailable and has requested not to receive sales calls. Goodbye."
        ),
        recordingAvailable = false
    )
)
