package com.aicall.agent.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aicall.agent.R
import com.aicall.agent.audio.AudioCaptureConfig
import com.aicall.agent.audio.CallAudioCapture
import com.aicall.agent.databinding.ActivityMainBinding
import com.aicall.agent.telecom.CallAnswerService
import com.aicall.agent.telecom.CallSession
import com.aicall.agent.telecom.DialerRoleManager
import com.aicall.agent.util.Logger
import com.aicall.agent.util.PreferencesManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var roleManager: DialerRoleManager
    private lateinit var prefs: PreferencesManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callTimerRunnable: Runnable? = null

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || roleManager.isDefaultDialer()) {
            Toast.makeText(this, "Default Dialer role granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Default Dialer role not granted.", Toast.LENGTH_SHORT).show()
        }
        updateRoleAndPermissionStatus()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updateRoleAndPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roleManager = DialerRoleManager(this)
        prefs = PreferencesManager.getInstance(this)

        setupUI()
        setupListeners()
        observeCallState()
        requestInitialPermissions()
        setupLoggingConsole()
    }

    override fun onResume() {
        super.onResume()
        updateRoleAndPermissionStatus()
        refreshCallHistory()
    }

    private fun setupUI() {
        // Load API key if set
        val savedKey = prefs.openRouterApiKey
        if (savedKey.isNotEmpty()) {
            binding.etApiKey.setText(savedKey)
        }

        binding.switchAutoAnswer.isChecked = prefs.isAutoAnswerEnabled
        binding.tvSystemLogs.movementMethod = ScrollingMovementMethod()
    }

    private fun setupListeners() {
        binding.btnRequestDialerRole.setOnClickListener {
            val intent = roleManager.createDefaultDialerIntent()
            if (intent != null) {
                dialerRoleLauncher.launch(intent)
            } else {
                Toast.makeText(this, "RoleManager not available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchAutoAnswer.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAutoAnswerEnabled = isChecked
            Logger.i("MainActivity", "Auto-answer toggled: $isChecked")
            Toast.makeText(this, "Auto-answer set to $isChecked", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            prefs.openRouterApiKey = key
            Toast.makeText(this, "API Key saved securely in Keystore", Toast.LENGTH_SHORT).show()
            Logger.i("MainActivity", "OpenRouter API Key updated")
        }

        binding.btnAnswerCall.setOnClickListener {
            CallAnswerService.answerCurrentCall()
        }

        binding.btnHangupCall.setOnClickListener {
            CallAnswerService.hangUpCurrentCall()
        }

        binding.btnTestCapture.setOnClickListener {
            runEmpiricalHalTest()
        }

        binding.btnClearLogs.setOnClickListener {
            Logger.clearLogs()
            binding.tvSystemLogs.text = ""
        }
    }

    private fun updateRoleAndPermissionStatus() {
        // 1. Dialer Role
        val isDialer = roleManager.isDefaultDialer()
        if (isDialer) {
            binding.tvDialerRoleStatus.text = "Active"
            binding.tvDialerRoleStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_emerald))
            binding.btnRequestDialerRole.isEnabled = false
            binding.btnRequestDialerRole.text = "Role Granted"
        } else {
            binding.tvDialerRoleStatus.text = "Not Granted"
            binding.tvDialerRoleStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_rose))
            binding.btnRequestDialerRole.isEnabled = true
            binding.btnRequestDialerRole.text = "Grant Role"
        }

        // 2. Priv-App Permissions (CAPTURE_AUDIO_OUTPUT & CONTROL_INCALL_EXPERIENCE)
        val hasCaptureAudio = roleManager.hasCaptureAudioOutputPermission()
        val hasControlInCall = roleManager.hasControlInCallExperiencePermission()
        val hasRecordAudio = roleManager.hasRecordAudioPermission()

        if (hasCaptureAudio && hasControlInCall) {
            binding.tvPrivAppBadge.text = "PRIV-APP ACTIVE"
            binding.tvPrivAppBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_success_text))
            binding.tvPrivAppBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.badge_success_bg))
            binding.tvCapturePermStatus.text = "Ready (VOICE_CALL)"
            binding.tvCapturePermStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_emerald))
        } else {
            binding.tvPrivAppBadge.text = "MAGISK REQUIRED"
            binding.tvPrivAppBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_warning_text))
            binding.tvPrivAppBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.badge_warning_bg))
            binding.tvCapturePermStatus.text = "Missing Magisk Grant"
            binding.tvCapturePermStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_rose))
        }
    }

    private fun observeCallState() {
        lifecycleScope.launch {
            CallAnswerService.currentSessionFlow.collectLatest { session ->
                runOnUiThread {
                    updateCallUI(session)
                }
            }
        }
    }

    private fun updateCallUI(session: CallSession?) {
        if (session == null) {
            // Idle State
            binding.tvHeroStatus.text = getString(R.string.hero_status_idle)
            binding.tvHeroStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_emerald))
            binding.viewHeroPulse.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_emerald))
            binding.tvCallerInfo.text = "Ready for incoming calls"
            binding.tvCallDetails.text = "Auto-answer is ${if (prefs.isAutoAnswerEnabled) "ENABLED" else "DISABLED"}"
            binding.layoutCallControls.visibility = View.GONE
            binding.layoutAudioMeter.visibility = View.GONE
            binding.tvCallTimer.visibility = View.GONE
            stopCallTimer()
            refreshCallHistory()
            return
        }

        when (session.state) {
            android.telecom.Call.STATE_RINGING -> {
                binding.tvHeroStatus.text = getString(R.string.hero_status_ringing)
                binding.tvHeroStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_amber))
                binding.viewHeroPulse.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_amber))
                binding.tvCallerInfo.text = session.phoneNumber
                binding.tvCallDetails.text = "Incoming phone call..."
                binding.layoutCallControls.visibility = View.VISIBLE
                binding.btnAnswerCall.visibility = View.VISIBLE
                binding.btnHangupCall.visibility = View.VISIBLE
                binding.layoutAudioMeter.visibility = View.GONE
                binding.tvCallTimer.visibility = View.GONE
                stopCallTimer()
            }
            android.telecom.Call.STATE_ACTIVE -> {
                binding.tvHeroStatus.text = getString(R.string.hero_status_incall)
                binding.tvHeroStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_emerald))
                binding.viewHeroPulse.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_emerald))
                binding.tvCallerInfo.text = session.phoneNumber
                binding.tvCallDetails.text = "Recording via VOICE_CALL tap"
                binding.layoutCallControls.visibility = View.VISIBLE
                binding.btnAnswerCall.visibility = View.GONE
                binding.btnHangupCall.visibility = View.VISIBLE
                binding.layoutAudioMeter.visibility = View.VISIBLE
                binding.tvCallTimer.visibility = View.VISIBLE
                startCallTimer(session.startTimeMs)
            }
            else -> {
                binding.tvHeroStatus.text = "Call Status: ${session.state}"
                binding.tvCallerInfo.text = session.phoneNumber
            }
        }
    }

    private fun startCallTimer(startTime: Long) {
        stopCallTimer()
        callTimerRunnable = object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                binding.tvCallTimer.text = String.format("%02d:%02d", minutes, seconds)
                mainHandler.postDelayed(this, 1000)
            }
        }.also {
            mainHandler.post(it)
        }
    }

    private fun stopCallTimer() {
        callTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        callTimerRunnable = null
    }

    private fun refreshCallHistory() {
        val sessions = CallAnswerService.completedSessions
        binding.containerCallSessions.removeAllViews()

        if (sessions.isEmpty()) {
            binding.tvEmptyCalls.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyCalls.visibility = View.GONE
        for (session in sessions.take(5)) {
            val item = TextView(this).apply {
                text = "• ${session.phoneNumber} (${session.durationSeconds}s) [ID: ${session.sessionId}]\n  Path: ${session.recordingPath ?: "No file"}"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 11f
                setPadding(0, 6, 0, 10)
            }
            binding.containerCallSessions.addView(item)
        }
    }

    /**
     * Tests AudioRecord on MediaRecorder.AudioSource.VOICE_CALL for 3 seconds
     * to empirically confirm Qualcomm HAL compatibility.
     */
    @SuppressLint("MissingPermission")
    private fun runEmpiricalHalTest() {
        binding.btnTestCapture.isEnabled = false
        binding.btnTestCapture.text = "Testing HAL..."
        Logger.i("MainActivity", "Starting 3-second empirical AudioRecord HAL test...")

        Thread {
            val testFile = File(getExternalFilesDir(null), "hal_test_${System.currentTimeMillis()}.wav")
            val capture = CallAudioCapture(this, audioSource = prefs.audioSource)

            capture.addAmplitudeListener { amp ->
                runOnUiThread {
                    binding.progressAudioMeter.progress = amp
                }
            }

            val started = capture.startCapture("test_hal", testFile)
            if (!started) {
                runOnUiThread {
                    binding.btnTestCapture.isEnabled = true
                    binding.btnTestCapture.text = "Test HAL (3s)"
                    Toast.makeText(this, "HAL test FAILED to initialize AudioRecord. Magisk module required.", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }

            runOnUiThread {
                binding.layoutAudioMeter.visibility = View.VISIBLE
            }

            Thread.sleep(3000)
            capture.stopCapture()

            runOnUiThread {
                binding.btnTestCapture.isEnabled = true
                binding.btnTestCapture.text = "Test HAL (3s)"
                val size = testFile.length()
                if (size > 1000) {
                    Toast.makeText(this, "HAL test PASSED! Generated: ${size}B WAV", Toast.LENGTH_LONG).show()
                    Logger.i("MainActivity", "HAL test PASSED! Output file: ${testFile.absolutePath} (${size} bytes)")
                } else {
                    Toast.makeText(this, "HAL test warning: file is empty (${size}B).", Toast.LENGTH_LONG).show()
                    Logger.w("MainActivity", "HAL test produced empty file. Chipset may require active call.")
                }
                refreshCallHistory()
            }
        }.start()
    }

    private fun setupLoggingConsole() {
        Logger.addListener { entry ->
            runOnUiThread {
                if (binding.tvSystemLogs.lineCount > 100) {
                    val currentText = binding.tvSystemLogs.text.toString()
                    val split = currentText.indexOf("\n")
                    if (split != -1) {
                        binding.tvSystemLogs.text = currentText.substring(split + 1)
                    }
                }
                binding.tvSystemLogs.append("\n${entry.formattedText}")
            }
        }
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCallTimer()
    }
}
