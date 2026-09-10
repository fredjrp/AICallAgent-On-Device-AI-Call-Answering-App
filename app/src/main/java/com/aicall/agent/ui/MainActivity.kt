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
import com.aicall.agent.root.PrivilegedAudioAccess
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.DialerRoleManager
import com.aicall.agent.ui.navigation.AICallMainApp
import com.aicall.agent.ui.theme.AICallTheme
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var roleManager: DialerRoleManager
    private lateinit var prefs: PreferencesManager

    private var isDefaultDialerState by mutableStateOf(false)
    private var hasPrivAppPermissionsState by mutableStateOf(false)
    private var isAutoAnswerState by mutableStateOf(true)
    private var currentApiKeyState by mutableStateOf("")
    private var currentPromptState by mutableStateOf("")
    private var selectedModelState by mutableStateOf("mistralai/mistral-7b-instruct")

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        roleManager = DialerRoleManager(this)
        prefs = PreferencesManager.getInstance(this)

        // Initialize state from encrypted prefs
        isAutoAnswerState = prefs.isAutoAnswerEnabled
        currentApiKeyState = prefs.openRouterApiKey
        currentPromptState = prefs.systemPrompt
        selectedModelState = prefs.selectedModel

        updateDiagnosticStates()
        requestInitialPermissions()

        setContent {
            val currentSession by CallAnswerService.currentSessionFlow.collectAsState()

            AICallTheme {
                AICallMainApp(
                    currentSession = currentSession,
                    isAutoAnswerEnabled = isAutoAnswerState,
                    onToggleAutoAnswer = { enabled ->
                        isAutoAnswerState = enabled
                        prefs.isAutoAnswerEnabled = enabled
                        Logger.i("MainActivity", "Auto-answer toggled: $enabled")
                    },
                    isDefaultDialer = isDefaultDialerState,
                    hasPrivAppPermissions = hasPrivAppPermissionsState,
                    onRequestDialerRole = {
                        val intent = roleManager.createDefaultDialerIntent()
                        if (intent != null) {
                            dialerRoleLauncher.launch(intent)
                        } else {
                            Toast.makeText(this, "RoleManager not available on this device", Toast.LENGTH_SHORT).show()
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
    }

    private fun updateDiagnosticStates() {
        isDefaultDialerState = roleManager.isDefaultDialer()
        hasPrivAppPermissionsState = PrivilegedAudioAccess.hasPrivAppAudioPermission(this)
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
}
