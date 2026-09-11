package com.aicall.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.aicall.agent.data.CallActionItem
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.data.CallRecord
import com.aicall.agent.ui.theme.BgBottom
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.GreenSoft
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.SurfacePill
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

@Composable
fun CallHistoryScreen(
    selectedRecordId: String? = null,
    onClearSelectedRecord: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = CallHistoryRepository.getInstance(context)
    val records by repo.recordsFlow.collectAsState()

    var activeDetailRecordId by remember(selectedRecordId) { mutableStateOf(selectedRecordId) }

    val activeRecord = records.firstOrNull { it.id == activeDetailRecordId }

    if (activeRecord != null) {
        HistoryDetailView(
            record = activeRecord,
            allContactRecords = repo.getRecordsForContact(activeRecord.callerNumber),
            onBack = {
                activeDetailRecordId = null
                onClearSelectedRecord()
            }
        )
    } else {
        HistoryListView(
            records = records,
            onSelectRecord = { activeDetailRecordId = it.id }
        )
    }
}

@Composable
fun HistoryListView(
    records: List<CallRecord>,
    onSelectRecord: (CallRecord) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Call History",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Logged on-device transcripts and handled calls",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No call history recorded yet", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryRow(record = record, onClick = { onSelectRecord(record) })
                }
            }
        }
    }
}

@Composable
fun HistoryRow(
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
            .padding(vertical = 12.dp),
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
                text = "${record.formattedTime} · ${record.summarySnippet.ifBlank { record.outcome }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LineLight))
}

@Composable
fun HistoryDetailView(
    record: CallRecord,
    allContactRecords: List<CallRecord>,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val initials = if (!record.callerName.isNullOrBlank()) {
        record.callerName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
    } else {
        "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Back bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GreenDeep, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("History", style = MaterialTheme.typography.bodyMedium, color = GreenDeep, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contact Hero Card
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFD8F0E0), Color(0xFFA8E0C0)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineLarge,
                    color = GreenDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = record.callerName ?: record.callerNumber,
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = record.callerNumber,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GreenPrimary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "${record.tag.uppercase()} · ${record.handledBy.uppercase()} HANDLED",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenDeep,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroStatBox(value = "${allContactRecords.size}", label = "CALLS", modifier = Modifier.weight(1f))
            HeroStatBox(value = "${allContactRecords.count { it.outcome == "Booked" }}", label = "BOOKINGS", modifier = Modifier.weight(1f))
            HeroStatBox(value = record.formattedDuration, label = "DURATION", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = "TRANSCRIPT & ACTIVITY",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (record.transcript.isEmpty()) {
            Text("No transcript available for this call.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        } else {
            record.transcript.forEach { msg ->
                val isAgent = msg.speaker.equals("agent", ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isAgent) Color(0xFFC8F0C8) else Color(0xFFCFE2D5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAgent) "FD" else "C",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAgent) Color(0xFF0E3A22) else Color(0xFF2C4636),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAgent) Color(0xFF1F3327) else Color(0xFF556A5B),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        if (record.estimatedCostUsd > 0.0 || record.promptTokens > 0) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, LineLight)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("OpenRouter Cost & Usage", style = MaterialTheme.typography.labelSmall, color = GreenDeep, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tokens: ${record.promptTokens + record.completionTokens} (${record.promptTokens} in / ${record.completionTokens} out) · Est: $${String.format("%.5f", record.estimatedCostUsd)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
fun HeroStatBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SurfacePill,
        border = androidx.compose.foundation.BorderStroke(1.dp, LineLight)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
