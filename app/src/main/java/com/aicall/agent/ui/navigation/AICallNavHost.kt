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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.ui.screens.BusinessInfoScreen
import com.aicall.agent.ui.screens.CallHistoryScreen
import com.aicall.agent.ui.screens.DialpadScreen
import com.aicall.agent.ui.screens.HomeScreen
import com.aicall.agent.ui.screens.LiveCallScreen
import com.aicall.agent.ui.screens.OnboardingWizard
import com.aicall.agent.ui.screens.SettingsScreen
import com.aicall.agent.ui.theme.BgBottom
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.TextMuted

enum class Screen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    LIVE("Live", Icons.Filled.PhoneInTalk, Icons.Outlined.PhoneInTalk),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETUP("Setup", Icons.Filled.Settings, Icons.Outlined.Settings)
}

enum class SubScreen {
    NONE,
    BUSINESS_KB,
    ONBOARDING
}

@Composable
fun AICallMainApp(
    currentSession: CallSession?,
    isAutoAnswerEnabled: Boolean,
    onToggleAutoAnswer: (Boolean) -> Unit,
    isAgentPaused: Boolean,
    onTogglePause: () -> Unit,
    isDefaultDialer: Boolean,
    shizukuState: ShizukuState,
    onRequestDialerRole: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    currentPrompt: String,
    onSavePrompt: (String) -> Unit,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    onTestAssistant: () -> Unit,
    initialScreen: Screen = Screen.HOME,
    prefilledNumber: String = ""
) {
    var currentScreen by remember(initialScreen) { mutableStateOf(initialScreen) }
    var activeSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    var targetHistoryId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = BgTop,
        bottomBar = {
            if (activeSubScreen == SubScreen.NONE) {
                FrontDeskNavBar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeSubScreen) {
                SubScreen.BUSINESS_KB -> {
                    BusinessInfoScreen(onBack = { activeSubScreen = SubScreen.NONE })
                }
                SubScreen.ONBOARDING -> {
                    OnboardingWizard(
                        isDefaultDialer = isDefaultDialer,
                        shizukuState = shizukuState,
                        onRequestDialerRole = onRequestDialerRole,
                        onRequestShizuku = onRequestShizukuPermission,
                        onComplete = { activeSubScreen = SubScreen.NONE }
                    )
                }
                SubScreen.NONE -> {
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen(
                            currentSession = currentSession,
                            isAutoAnswerEnabled = isAutoAnswerEnabled,
                            isAgentPaused = isAgentPaused,
                            onTogglePause = onTogglePause,
                            shizukuState = shizukuState,
                            isDefaultDialer = isDefaultDialer,
                            onRequestDialerRole = onRequestDialerRole,
                            onRequestShizuku = onRequestShizukuPermission,
                            onSeeAllHistory = { currentScreen = Screen.HISTORY },
                            onSelectRecord = { recordId ->
                                targetHistoryId = recordId
                                currentScreen = Screen.HISTORY
                            }
                        )
                        Screen.LIVE -> LiveCallScreen(
                            currentSession = currentSession,
                            onEndCall = { CallAnswerService.hangUpCurrentCall() }
                        )
                        Screen.HISTORY -> CallHistoryScreen(
                            selectedRecordId = targetHistoryId,
                            onClearSelectedRecord = { targetHistoryId = null }
                        )
                        Screen.SETUP -> SettingsScreen(
                            currentApiKey = currentApiKey,
                            onSaveApiKey = onSaveApiKey,
                            currentPrompt = currentPrompt,
                            onSavePrompt = onSavePrompt,
                            selectedModel = selectedModel,
                            onSelectModel = onSelectModel,
                            shizukuState = shizukuState,
                            onRequestShizukuPermission = onRequestShizukuPermission,
                            onOpenBusinessKb = { activeSubScreen = SubScreen.BUSINESS_KB },
                            onOpenOnboarding = { activeSubScreen = SubScreen.ONBOARDING }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FrontDeskNavBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = SurfaceCard.copy(alpha = 0.94f),
            border = androidx.compose.foundation.BorderStroke(1.dp, LineLight)
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
                        targetValue = if (isSelected) GreenDeep else TextMuted,
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
                            .padding(vertical = 6.dp),
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
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
