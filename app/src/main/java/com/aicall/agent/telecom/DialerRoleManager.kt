package com.aicall.agent.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.aicall.agent.util.Logger

/**
 * Manages default dialer role and system telecom permissions.
 */
class DialerRoleManager(private val context: Context) {

    private val roleManager: RoleManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java)
    } else {
        null
    }

    /**
     * Checks whether the app currently holds the Default Dialer role.
     */
    fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        } else {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    /**
     * Returns an intent to prompt the user to make this app the default dialer.
     */
    fun createDefaultDialerIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            @Suppress("DEPRECATION")
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
    }

    /**
     * Checks if privileged CAPTURE_AUDIO_OUTPUT permission is granted.
     * Note: This permission is only granted to system priv-apps (via Magisk module).
     */
    fun hasCaptureAudioOutputPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            "android.permission.CAPTURE_AUDIO_OUTPUT"
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if privileged CONTROL_INCALL_EXPERIENCE permission is granted.
     */
    fun hasControlInCallExperiencePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            "android.permission.CONTROL_INCALL_EXPERIENCE"
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks standard RECORD_AUDIO permission.
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
