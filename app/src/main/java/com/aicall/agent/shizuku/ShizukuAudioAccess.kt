package com.aicall.agent.shizuku

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aicall.agent.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * High-level Shizuku state enumeration for UI and lifecycle monitoring.
 */
enum class ShizukuState {
    NOT_RUNNING,          // Shizuku service is not running or binder is dead
    RUNNING_UNAUTHORIZED, // Shizuku is running, but permission not yet granted by user
    RUNNING_AUTHORIZED    // Shizuku running and permission granted
}

/**
 * Shizuku privileged access layer.
 * Replaces root/Magisk with Shizuku ADB-level privileged operations.
 */
object ShizukuAudioAccess {

    private const val TAG = "ShizukuAudioAccess"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 4001

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Logger.i(TAG, "Shizuku binder received")
        ShizukuStatusMonitor.updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Logger.w(TAG, "Shizuku binder died")
        ShizukuStatusMonitor.updateState()
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Logger.i(TAG, "Shizuku listeners registered")
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to register Shizuku listeners", tr = e)
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to unregister Shizuku listeners", tr = e)
        }
    }

    /**
     * Checks if the Shizuku server binder is currently responding.
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Checks if the app has been granted Shizuku permissions by the user.
     */
    fun hasShizukuPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Error checking Shizuku permission: ${e.message}")
            false
        }
    }

    /**
     * Checks if the app has the required CAPTURE_AUDIO_OUTPUT permission.
     */
    fun hasCaptureAudioOutputPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAPTURE_AUDIO_OUTPUT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests Shizuku permission via the standard Shizuku runtime dialog.
     */
    fun requestShizukuPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE) {
        if (!isShizukuRunning()) {
            Logger.w(TAG, "Cannot request Shizuku permission: Shizuku binder is not running")
            return
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to request Shizuku permission", tr = e)
        }
    }
}

/**
 * Periodic status monitor for Shizuku during app / foreground service lifecycle.
 */
object ShizukuStatusMonitor {

    private const val TAG = "ShizukuStatusMonitor"
    private val _shizukuState = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    private var monitorJob: Job? = null
    private val monitorScope = CoroutineScope(Dispatchers.Default)

    fun startMonitoring() {
        updateState()
        if (monitorJob?.isActive == true) return

        monitorJob = monitorScope.launch {
            while (isActive) {
                updateState()
                delay(5000) // Poll every 5 seconds
            }
        }
        Logger.i(TAG, "ShizukuStatusMonitor started")
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        Logger.i(TAG, "ShizukuStatusMonitor stopped")
    }

    fun updateState() {
        val running = ShizukuAudioAccess.isShizukuRunning()
        val authorized = if (running) ShizukuAudioAccess.hasShizukuPermission() else false

        _shizukuState.value = when {
            !running -> ShizukuState.NOT_RUNNING
            !authorized -> ShizukuState.RUNNING_UNAUTHORIZED
            else -> ShizukuState.RUNNING_AUTHORIZED
        }
    }
}
