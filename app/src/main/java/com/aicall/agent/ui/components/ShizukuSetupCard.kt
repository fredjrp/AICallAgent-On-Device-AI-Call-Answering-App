package com.aicall.agent.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.ui.theme.BorderLight
import com.aicall.agent.ui.theme.BrandCyan
import com.aicall.agent.ui.theme.BrandPurple
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.ColorInactive
import com.aicall.agent.ui.theme.ColorWarning
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

@Composable
fun ShizukuSetupCard(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(state != ShizukuState.RUNNING_AUTHORIZED) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = 300f),
        label = "arrow_rot"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row with Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    ShizukuState.RUNNING_AUTHORIZED -> ColorActive.copy(alpha = 0.12f)
                                    ShizukuState.RUNNING_UNAUTHORIZED -> ColorWarning.copy(alpha = 0.12f)
                                    ShizukuState.NOT_RUNNING -> ColorInactive.copy(alpha = 0.12f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (state) {
                                ShizukuState.RUNNING_AUTHORIZED -> Icons.Default.CheckCircle
                                ShizukuState.RUNNING_UNAUTHORIZED -> Icons.Default.Security
                                ShizukuState.NOT_RUNNING -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when (state) {
                                ShizukuState.RUNNING_AUTHORIZED -> ColorActive
                                ShizukuState.RUNNING_UNAUTHORIZED -> ColorWarning
                                ShizukuState.NOT_RUNNING -> ColorInactive
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Shizuku Privileged Access",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = when (state) {
                                ShizukuState.RUNNING_AUTHORIZED -> "Running & Authorized (Tap for info)"
                                ShizukuState.RUNNING_UNAUTHORIZED -> "Running — Permission Needed"
                                ShizukuState.NOT_RUNNING -> "Not Detected — Setup Required"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state) {
                                ShizukuState.RUNNING_AUTHORIZED -> ColorActive
                                ShizukuState.RUNNING_UNAUTHORIZED -> ColorWarning
                                ShizukuState.NOT_RUNNING -> ColorInactive
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Toggle instructions",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation)
                )
            }

            // Action button if running but unauthorized
            if (state == ShizukuState.RUNNING_UNAUTHORIZED) {
                Spacer(modifier = Modifier.height(14.dp))
                ElevatedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = BrandPurple,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Permission in Shizuku", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Accordion: Setup Instructions & Download Links
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderLight)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "PAIRING & SETUP INSTRUCTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandPurple,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Shizuku provides ADB shell privileges without requiring root or unlocked bootloader.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Method 1: Wireless
                    SetupStepItem(
                        badge = "Option 1",
                        title = "Wireless Debugging (Android 11+)",
                        content = "1. Enable Developer Options on your device.\n" +
                                "2. Turn on 'Wireless Debugging' and tap 'Pair device with pairing code'.\n" +
                                "3. Open the Shizuku app, tap 'Pairing', and enter the 6-digit code.\n" +
                                "4. In Shizuku, tap 'Start' to start the service."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Method 2: USB One-Time
                    SetupStepItem(
                        badge = "Option 2",
                        title = "USB Debugging (One-time PC connection)",
                        content = "Connect to your PC with ADB installed and run this command in terminal:\n" +
                                "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons: Open Shizuku or Download
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ElevatedButton(
                            onClick = { openShizukuAppOrMarket(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = SurfaceOverlay,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Shizuku", style = MaterialTheme.typography.labelSmall)
                        }

                        ElevatedButton(
                            onClick = { openGitHubReleases(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = SurfaceOverlay,
                                contentColor = BrandCyan
                            )
                        ) {
                            Text("GitHub Releases", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: After rebooting your phone, reopen Shizuku and tap Start.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun SetupStepItem(
    badge: String,
    title: String,
    content: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceOverlay)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrandPurple.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = badge, style = MaterialTheme.typography.labelSmall, color = BrandPurple, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 18.sp
        )
    }
}

private fun openShizukuAppOrMarket(context: Context) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return
        }
    } catch (_: Exception) {}

    try {
        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(playStoreIntent)
    } catch (_: Exception) {
        openGitHubReleases(context)
    }
}

private fun openGitHubReleases(context: Context) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
    } catch (_: Exception) {}
}
