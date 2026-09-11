package com.aicall.agent.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.CallRecord
import com.aicall.agent.ui.theme.AccentAmber
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.GreenSoft
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.File

private const val AUTO_DISMISS_SECONDS = 8

/**
 * Truecaller-style post-call summary sheet.
 *
 * Appears immediately on call disconnect without destroying the InCallActivity.
 * Shows caller identity, handled-by badge, call outcome, AI summary snippet,
 * mini audio scrubber (if recording exists), and quick-action buttons.
 *
 * The sheet auto-dismisses after [AUTO_DISMISS_SECONDS] seconds.
 * Any touch on the sheet pauses the countdown timer.
 *
 * @param record       The completed [CallRecord] filed by the call pipeline.
 * @param onDismiss    Called when the user taps Done or the timer expires.
 * @param onCallBack   Called when the user taps the Call Back quick action.
 * @param onViewTranscript Called when the user taps View Transcript.
 */
@Composable
fun AfterCallSummarySheet(
    record: CallRecord,
    onDismiss: () -> Unit,
    onCallBack: (String) -> Unit = {},
    onViewTranscript: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // ── Auto-dismiss countdown ──────────────────────────────────────────────
    var secondsRemaining by remember { mutableIntStateOf(AUTO_DISMISS_SECONDS) }
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(isPaused) {
        while (secondsRemaining > 0) {
            delay(1000)
            if (!isPaused) secondsRemaining--
        }
        if (!isPaused) onDismiss()
    }

    val countdownProgress by animateFloatAsState(
        targetValue = secondsRemaining.toFloat() / AUTO_DISMISS_SECONDS,
        animationSpec = tween(durationMillis = 900),
        label = "countdown"
    )

    // ── Slide-in animation ─────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = tween(300))
    ) {
        // Pause auto-dismiss if user touches the sheet
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { isPaused = true },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = SurfaceCard,
            shadowElevation = 16.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Drag Handle ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(LineMedium)
                )

                Spacer(Modifier.height(16.dp))

                // ── Caller Header ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar initials circle
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(GreenSoft.copy(alpha = 0.35f))
                            .border(1.5.dp, GreenPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = (record.callerName?.take(2)
                            ?: record.callerNumber.filter { it.isDigit() }.takeLast(2).ifEmpty { "?" })
                            .uppercase()
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelLarge,
                            color = GreenDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.callerName ?: record.callerNumber,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (record.callerName != null) {
                            Text(
                                text = record.callerNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "${record.formattedDuration} duration · ${record.formattedTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    // Caller tag badge (New / Returning / VIP)
                    CallerTagBadge(tag = record.tag)
                }

                Spacer(Modifier.height(12.dp))

                // ── Handled-By + Outcome Row ──────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Handled by pill
                    OutcomePill(
                        label = if (record.handledBy == "human") "Handled by You" else "Handled by Front Desk AI",
                        color = if (record.handledBy == "human") ColorWarning else GreenPrimary
                    )
                    // Outcome type pill
                    OutcomePill(
                        label = record.outcome,
                        color = when (record.outcome.lowercase()) {
                            "booked" -> GreenDeep
                            "dropped" -> ColorInactive
                            "pricing" -> ColorWarning
                            else -> TextMuted
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── AI Summary Card ───────────────────────────────────────
                if (record.summarySnippet.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = GreenPrimary.copy(alpha = 0.07f),
                        tonalElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "AI OUTCOME SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = GreenDeep,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "\"${record.summarySnippet}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Mini Audio Player ─────────────────────────────────────
                val recordingFile = record.recordingPath?.let { File(it) }?.takeIf { it.exists() }
                if (recordingFile != null) {
                    MiniAudioPlayer(
                        durationFormatted = record.formattedDuration,
                        recordingFile = recordingFile
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // ── Quick Action Buttons ──────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Call Back
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.Call, contentDescription = "Call Back", tint = GreenDeep, modifier = Modifier.size(20.dp)) },
                        label = "Call Back",
                        backgroundColor = GreenPrimary.copy(alpha = 0.12f),
                        onClick = {
                            isPaused = true
                            onCallBack(record.callerNumber)
                            launchDialer(context, record.callerNumber)
                        }
                    )

                    // WhatsApp / SMS
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Message", tint = ColorWarning, modifier = Modifier.size(20.dp)) },
                        label = "Message",
                        backgroundColor = ColorWarning.copy(alpha = 0.10f),
                        onClick = {
                            isPaused = true
                            launchSmsOrWhatsApp(context, record.callerNumber, record.summarySnippet)
                        }
                    )

                    // View Transcript
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Default.Notes, contentDescription = "Transcript", tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                        label = "Transcript",
                        backgroundColor = SurfaceOverlay,
                        onClick = {
                            isPaused = true
                            onViewTranscript(record.id)
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Dismiss / Auto-close button with countdown ring ────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp)
                ) {
                    // Countdown ring
                    val ringColor = GreenPrimary
                    val trackColor = LineMedium
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .drawBehind {
                                val strokeWidth = 3.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2
                                val center = Offset(size.width / 2f, size.height / 2f)
                                // Track
                                drawCircle(
                                    color = trackColor,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = strokeWidth)
                                )
                                // Progress arc
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * countdownProgress,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (isPaused) "Done" else "$secondsRemaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPaused) GreenDeep else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isPaused) "Tap to dismiss" else "Auto-closing in $secondsRemaining s",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun CallerTagBadge(tag: String) {
    val (bgColor, textColor) = when {
        tag.contains("VIP", ignoreCase = true) -> Pair(ColorWarning.copy(alpha = 0.15f), ColorWarning)
        tag.contains("Returning", ignoreCase = true) -> Pair(GreenPrimary.copy(alpha = 0.12f), GreenDeep)
        else -> Pair(SurfaceOverlay, TextMuted)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OutcomePill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Minimal in-sheet audio player with a static waveform bar and play/pause toggle.
 * Actual playback is handled by tapping the play button which leverages Android's
 * MediaPlayer through the media intent, keeping this composable dependency-free.
 */
@Composable
private fun MiniAudioPlayer(
    durationFormatted: String,
    recordingFile: File
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceOverlay
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = 0.15f))
                    .clickable {
                        isPlaying = !isPlaying
                        if (isPlaying) {
                            try {
                                val uri = Uri.fromFile(recordingFile)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "audio/wav")
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = GreenDeep,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Static waveform bars
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barHeights = remember {
                    listOf(0.4f, 0.6f, 0.9f, 0.5f, 0.7f, 1.0f, 0.6f, 0.8f, 0.5f, 0.4f,
                           0.7f, 0.9f, 0.6f, 0.5f, 0.8f, 0.7f, 0.4f, 0.6f, 0.5f, 0.3f)
                }
                val maxBarHeightDp = 20.dp
                barHeights.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(maxBarHeightDp * fraction)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GreenPrimary.copy(alpha = if (isPlaying) 0.85f else 0.45f))
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Duration label in monospace
            Text(
                text = durationFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ── Intent helpers ─────────────────────────────────────────────────────────────

private fun launchDialer(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun launchSmsOrWhatsApp(context: Context, phoneNumber: String, summary: String) {
    val message = buildString {
        append("Hi, this is Front Desk following up on your call. ")
        if (summary.isNotBlank()) append(summary)
    }
    // Try WhatsApp first, fall back to SMS
    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
    try {
        val waIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
        }
        context.startActivity(waIntent)
        return
    } catch (_: Exception) {}

    try {
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$cleanNumber")
            putExtra("sms_body", message)
        }
        context.startActivity(smsIntent)
    } catch (_: Exception) {}
}
