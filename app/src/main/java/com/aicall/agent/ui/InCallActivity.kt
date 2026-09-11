package com.aicall.agent.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aicall.agent.data.CallHistoryRepository
import com.aicall.agent.data.CallRecord
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.components.AfterCallSummarySheet
import com.aicall.agent.ui.components.InteractiveOrb
import com.aicall.agent.ui.components.OrbVisualState
import com.aicall.agent.ui.theme.AICallTheme
import com.aicall.agent.ui.theme.BgBottom
import com.aicall.agent.ui.theme.BgMid
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.LineMedium
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Wake screen and show over lock screen ─────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AICallTheme {
                InCallActivityRoot(onFinish = { finishAndRemoveTask() })
            }
        }
    }
}

/**
 * Root composable that owns the UI mode state machine:
 * ACTIVE_CALL -> (on disconnect) -> AFTER_CALL_SUMMARY -> (on dismiss) -> finish()
 */
@Composable
private fun InCallActivityRoot(onFinish: () -> Unit) {
    val context = LocalContext.current
    val session by CallAnswerService.currentSessionFlow.collectAsState()

    // ── UI state: active call or after-call summary ───────────────────────
    var showAfterCallSummary by remember { mutableStateOf(false) }
    var completedRecord by remember { mutableStateOf<CallRecord?>(null) }

    // Watch for call disconnect -> transition to summary rather than finishing
    LaunchedEffect(session) {
        if (session != null && session?.state == android.telecom.Call.STATE_DISCONNECTED) {
            // Give the repo a moment to finish writing the record
            delay(600)
            val repo = CallHistoryRepository.getInstance(context)
            completedRecord = session?.let { s -> repo.getRecordById(s.sessionId) }
            showAfterCallSummary = true
        }
    }

    // If session disappears entirely with no record, just finish
    LaunchedEffect(session, showAfterCallSummary) {
        if (session == null && !showAfterCallSummary) {
            delay(400)
            onFinish()
        }
    }

    AnimatedContent(
        targetState = showAfterCallSummary,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "InCallModeTransition"
    ) { inSummaryMode ->
        if (inSummaryMode && completedRecord != null) {
            // ── After-call summary fills bottom of screen over dim background ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                AfterCallSummarySheet(
                    record = completedRecord!!,
                    onDismiss = onFinish,
                    onCallBack = { /* handled inside the sheet via launchDialer */ },
                    onViewTranscript = { /* navigate to history — activity finish is enough */ }
                )
            }
        } else {
            // ── Active call / ringing UI ────────────────────────────────────
            InCallScreen(
                session = session,
                onAnswer = { CallAnswerService.answerCurrentCall() },
                onDecline = {
                    CallAnswerService.hangUpCurrentCall()
                }
            )
        }
    }
}

@Composable
fun InCallScreen(
    session: CallSession?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val prefs = PreferencesManager.getInstance(context)
    val isRinging = session?.state == android.telecom.Call.STATE_RINGING
    val isActive = session?.state == android.telecom.Call.STATE_ACTIVE

    var callDurationSeconds by remember { mutableLongStateOf(0L) }
    var ringCountdownSeconds by remember { mutableLongStateOf((prefs.answerDelayRings * 3).toLong()) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    LaunchedEffect(isRinging) {
        if (isRinging) {
            while (ringCountdownSeconds > 0) {
                delay(1000)
                ringCountdownSeconds--
            }
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            val start = System.currentTimeMillis()
            while (true) {
                callDurationSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        }
    }

    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BgTop, BgMid, BgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ── State indicator badge ─────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isActive) GreenPrimary.copy(alpha = 0.14f) else GreenDeep.copy(alpha = 0.12f),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) GreenPrimary else GreenDeep)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRinging -> if (session?.isPassiveMode == true) "RINGING (HUMAN PICKUP)" else "RINGING · AI AUTO-ANSWER IN ${ringCountdownSeconds}s"
                            isActive -> if (session?.isPassiveMode == true) "HUMAN ACTIVE · $durationFormatted" else "AI FRONT DESK · $durationFormatted"
                            else -> "CONNECTING..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = GreenDeep,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Caller Details ────────────────────────────────────────────
            Text(
                text = session?.phoneNumber ?: "Unknown Caller",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (session?.isPassiveMode == true) "Transcribing live conversation (Passive Mode)"
                       else "Front Desk Voice Agent Active",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Interactive Orb ───────────────────────────────────────────
            InteractiveOrb(
                state = when {
                    isRinging -> OrbVisualState.RINGING
                    isActive && session?.isPassiveMode == true -> OrbVisualState.LISTENING
                    isActive -> OrbVisualState.SPEAKING
                    else -> OrbVisualState.IDLE
                },
                size = 190.dp
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Bottom Controls ───────────────────────────────────────────
            if (isRinging) {
                RingingControls(onDecline = onDecline, onAnswer = onAnswer)
            } else {
                ActiveCallControls(
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn,
                    onMuteToggle = { isMuted = !isMuted },
                    onSpeakerToggle = {
                        isSpeakerOn = !isSpeakerOn
                        prefs.speakerphoneEnabled = isSpeakerOn
                        try {
                            val route = if (isSpeakerOn) android.telecom.CallAudioState.ROUTE_SPEAKER else android.telecom.CallAudioState.ROUTE_EARPIECE
                            CallAnswerService.activeServiceInstance?.setAudioRoute(route)
                        } catch (_: Exception) {}
                    },
                    onEndCall = onDecline
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RingingControls(onDecline: () -> Unit, onAnswer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decline
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(8.dp, CircleShape, ambientColor = ColorInactive.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(ColorInactive)
                    .clickable(onClick = onDecline),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Decline", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }

        // Answer Now (let human take over)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(8.dp, CircleShape, ambientColor = GreenPrimary.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(GreenPrimary)
                    .clickable(onClick = onAnswer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Answer Now", style = MaterialTheme.typography.labelMedium, color = GreenDeep, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveCallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) ColorInactive.copy(alpha = 0.14f) else SurfaceOverlay)
                    .clickable(onClick = onMuteToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) ColorInactive else TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(if (isMuted) "Muted" else "Mute", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        // End Call
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .shadow(8.dp, CircleShape, ambientColor = ColorInactive.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(ColorInactive)
                    .clickable(onClick = onEndCall),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("End Call", style = MaterialTheme.typography.labelSmall, color = ColorInactive, fontWeight = FontWeight.Bold)
        }

        // Speaker
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isSpeakerOn) GreenPrimary.copy(alpha = 0.14f) else SurfaceOverlay)
                    .clickable(onClick = onSpeakerToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Speaker",
                    tint = if (isSpeakerOn) GreenDeep else TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(if (isSpeakerOn) "Speaker" else "Earpiece", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}
