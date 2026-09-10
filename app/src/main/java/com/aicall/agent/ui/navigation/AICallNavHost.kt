package com.aicall.agent.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.screens.AssistantScreen
import com.aicall.agent.ui.screens.CallHistoryScreen
import com.aicall.agent.ui.screens.SettingsScreen
import com.aicall.agent.ui.theme.BrandCyan
import com.aicall.agent.ui.theme.BrandPurple
import com.aicall.agent.ui.theme.SurfaceCanvas
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary
import com.aicall.agent.ui.theme.TextTertiary

import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.outlined.Dialpad
import com.aicall.agent.ui.screens.DialpadScreen

enum class Screen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CALLS("History", Icons.Filled.History, Icons.Outlined.History),
    KEYPAD("Keypad", Icons.Filled.Dialpad, Icons.Outlined.Dialpad),
    ASSISTANT("Assistant", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AICallMainApp(
    currentSession: CallSession?,
    isAutoAnswerEnabled: Boolean,
    onToggleAutoAnswer: (Boolean) -> Unit,
    isDefaultDialer: Boolean,
    shizukuState: com.aicall.agent.shizuku.ShizukuState,
    onRequestDialerRole: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    currentPrompt: String,
    onSavePrompt: (String) -> Unit,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    onTestAssistant: () -> Unit,
    initialScreen: Screen = Screen.ASSISTANT,
    prefilledNumber: String = ""
) {
    var currentScreen by remember(initialScreen) { mutableStateOf(initialScreen) }

    Scaffold(
        containerColor = SurfaceCanvas,
        bottomBar = {
            AppleGlassNavBar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.CALLS -> CallHistoryScreen()
                Screen.KEYPAD -> DialpadScreen(initialNumber = prefilledNumber)
                Screen.ASSISTANT -> AssistantScreen(
                    currentSession = currentSession,
                    isAutoAnswerEnabled = isAutoAnswerEnabled,
                    onToggleAutoAnswer = onToggleAutoAnswer,
                    isDefaultDialer = isDefaultDialer,
                    shizukuState = shizukuState,
                    onRequestDialerRole = onRequestDialerRole,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onTestAssistant = onTestAssistant
                )
                Screen.SETTINGS -> SettingsScreen(
                    currentApiKey = currentApiKey,
                    onSaveApiKey = onSaveApiKey,
                    currentPrompt = currentPrompt,
                    onSavePrompt = onSavePrompt,
                    selectedModel = selectedModel,
                    onSelectModel = onSelectModel,
                    shizukuState = shizukuState,
                    onRequestShizukuPermission = onRequestShizukuPermission
                )
            }
        }
    }
}

@Composable
fun AppleGlassNavBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = SurfaceCard.copy(alpha = 0.96f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Screen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) BrandPurple else TextTertiary,
                        animationSpec = spring(stiffness = 300f),
                        label = "tab_tint"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onScreenSelected(screen) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
