package com.aicall.agent.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.ui.theme.ColorActive
import com.aicall.agent.ui.theme.SurfaceCanvas
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

data class DialKey(
    val digit: String,
    val letters: String
)

@Composable
fun DialpadScreen(
    initialNumber: String = "",
    onCallPlaced: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var dialedNumber by remember(initialNumber) { mutableStateOf(initialNumber) }

    val keys = listOf(
        listOf(DialKey("1", ""), DialKey("2", "A B C"), DialKey("3", "D E F")),
        listOf(DialKey("4", "G H I"), DialKey("5", "J K L"), DialKey("6", "M N O")),
        listOf(DialKey("7", "P Q R S"), DialKey("8", "T U V"), DialKey("9", "W X Y Z")),
        listOf(DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCanvas)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Keypad",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.weight(0.6f))

        // Display area for entered number
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dialedNumber,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (dialedNumber.length > 12) 24.sp else 32.sp
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3x4 Dialpad Matrix
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            keys.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowKeys.forEach { key ->
                        DialKeyButton(
                            key = key,
                            onClick = { dialedNumber += key.digit }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Action Row: Spacer, Big Green Call Button, Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Invisible placeholder for symmetrical centering
            Box(modifier = Modifier.size(54.dp))

            // Call Button (Green Apple-style pill)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = ColorActive.copy(alpha = 0.4f))
                    .clip(CircleShape)
                    .background(ColorActive)
                    .clickable {
                        if (dialedNumber.isNotEmpty()) {
                            placePhoneCall(context, dialedNumber)
                            onCallPlaced(dialedNumber)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Place Call",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Backspace button
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                if (dialedNumber.isNotEmpty()) {
                    IconButton(onClick = { dialedNumber = dialedNumber.dropLast(1) }) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(70.dp)) // Nav bar inset
    }
}

@Composable
fun DialKeyButton(
    key: DialKey,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "key_scale"
    )

    Surface(
        modifier = Modifier
            .size(74.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (isPressed) Color(0xFFE5E5EA) else SurfaceOverlay
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextSecondary,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

private fun placePhoneCall(context: Context, phoneNumber: String) {
    try {
        val uri = Uri.fromParts("tel", phoneNumber, null)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val extras = Bundle()
        telecomManager?.placeCall(uri, extras)
    } catch (e: Exception) {
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(callIntent)
        } catch (_: Exception) {}
    }
}
