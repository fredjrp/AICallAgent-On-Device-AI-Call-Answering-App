package com.aicall.agent.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aicall.agent.shizuku.ShizukuAudioAccess
import com.aicall.agent.shizuku.ShizukuState
import com.aicall.agent.shizuku.ShizukuStatusMonitor
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.DialerRoleManager
import com.aicall.agent.ui.navigation.AICallMainApp
import com.aicall.agent.ui.theme.AICallTheme
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private lateinit var roleManager: DialerRoleManager
    private lateinit var prefs: PreferencesManager

    private var isDefaultDialerState by mutableStateOf(false)
    private var isAutoAnswerState by mutableStateOf(true)
    private var isAgentPausedState by mutableStateOf(false)
    private var currentApiKeyState by mutableStateOf("")
    private var currentPromptState by mutableStateOf("")
    private var selectedModelState by mutableStateOf("meta-llama/llama-3.3-70b-instruct")

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK || roleManager.isDefaultDialer()
        isDefaultDialerState = granted
        if (granted) {
            Toast.makeText(this, "Default Dialer role granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Default Dialer role not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateDiagnosticStates()
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuAudioAccess.SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku permission granted! Audio tap ready.", Toast.LENGTH_SHORT).show()
                ShizukuStatusMonitor.updateState()
            } else {
                Toast.makeText(this, "Shizuku permission denied.", Toast.LENGTH_SHORT).show()
                ShizukuStatusMonitor.updateState()
            }
        }
    }

    private var initialScreenState by mutableStateOf(com.aicall.agent.ui.navigation.Screen.HOME)
    private var prefilledNumberState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDialIntent(intent)

        roleManager = DialerRoleManager(this)
        prefs = PreferencesManager.getInstance(this)

        // Initialize state from encrypted prefs
        isAutoAnswerState = prefs.isAutoAnswerEnabled
        isAgentPausedState = prefs.isAgentPaused
        currentApiKeyState = prefs.openRouterApiKey
        currentPromptState = prefs.systemPrompt
        selectedModelState = prefs.selectedModel

        // Register Shizuku permission listener & start monitoring
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            Logger.w("MainActivity", "Failed to add Shizuku permission listener: ${e.message}")
        }
        ShizukuStatusMonitor.startMonitoring()

        updateDiagnosticStates()
        requestInitialPermissions()

        setContent {
            val currentSession by CallAnswerService.currentSessionFlow.collectAsState()
            val shizukuState by ShizukuStatusMonitor.shizukuState.collectAsState()

            AICallTheme {
                AICallMainApp(
                    currentSession = currentSession,
                    isAutoAnswerEnabled = isAutoAnswerState,
                    onToggleAutoAnswer = { enabled ->
                        isAutoAnswerState = enabled
                        prefs.isAutoAnswerEnabled = enabled
                        Logger.i("MainActivity", "Auto-answer toggled: $enabled")
                    },
                    isAgentPaused = isAgentPausedState,
                    onTogglePause = {
                        val next = !isAgentPausedState
                        isAgentPausedState = next
                        prefs.isAgentPaused = next
                        val text = if (next) "Agent Paused. Calls ring directly to phone." else "Agent Resumed."
                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                    },
                    isDefaultDialer = isDefaultDialerState,
                    shizukuState = shizukuState,
                    initialScreen = initialScreenState,
                    prefilledNumber = prefilledNumberState,
                    onRequestDialerRole = {
                        val intent = roleManager.createDefaultDialerIntent()
                        if (intent != null) {
                            dialerRoleLauncher.launch(intent)
                        } else {
                            Toast.makeText(this, "RoleManager not available on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestShizukuPermission = {
                        if (ShizukuAudioAccess.isShizukuRunning()) {
                            ShizukuAudioAccess.requestShizukuPermission()
                        } else {
                            Toast.makeText(this, "Shizuku is not running. Please open Shizuku and tap Start.", Toast.LENGTH_LONG).show()
                        }
                    },
                    currentApiKey = currentApiKeyState,
                    onSaveApiKey = { key ->
                        currentApiKeyState = key
                        prefs.openRouterApiKey = key
                        Toast.makeText(this, "API Key saved securely in Keystore", Toast.LENGTH_SHORT).show()
                        Logger.i("MainActivity", "API key updated")
                    },
                    currentPrompt = currentPromptState,
                    onSavePrompt = { prompt ->
                        currentPromptState = prompt
                        prefs.systemPrompt = prompt
                        Toast.makeText(this, "System prompt updated", Toast.LENGTH_SHORT).show()
                        Logger.i("MainActivity", "System prompt updated")
                    },
                    selectedModel = selectedModelState,
                    onSelectModel = { model ->
                        selectedModelState = model
                        prefs.selectedModel = model
                    },
                    onTestAssistant = {
                        Toast.makeText(this, "Testing voice activity & model connection...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateDiagnosticStates()
        ShizukuStatusMonitor.updateState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
    }

    private fun updateDiagnosticStates() {
        isDefaultDialerState = roleManager.isDefaultDialer()
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.MANAGE_ONGOING_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == android.content.Intent.ACTION_DIAL || intent.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            val schemeSpecific = data?.schemeSpecificPart
            if (!schemeSpecific.isNullOrEmpty()) {
                prefilledNumberState = schemeSpecific
                initialScreenState = com.aicall.agent.ui.navigation.Screen.HOME
            } else if (intent.action == android.content.Intent.ACTION_DIAL) {
                initialScreenState = com.aicall.agent.ui.navigation.Screen.HOME
            }
        }
    }
}
