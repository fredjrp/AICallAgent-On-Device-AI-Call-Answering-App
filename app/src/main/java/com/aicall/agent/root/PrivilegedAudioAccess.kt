package com.aicall.agent.root

import com.aicall.agent.util.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Root & Magisk diagnostic helpers for empirical HAL validation and privilege inspection.
 */
object PrivilegedAudioAccess {

    private const val TAG = "PrivilegedAudioAccess"

    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    fun getMagiskVersion(): String? {
        return executeSuCommand("magisk -v")
    }

    fun isPrivAppInstalled(): Boolean {
        val privAppDir = File("/system/priv-app/AICallAgent")
        return privAppDir.exists()
    }

    fun getSelinuxStatus(): String {
        return executeSuCommand("getenforce") ?: "Unknown"
    }

    private fun executeSuCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output?.trim()
        } catch (e: Exception) {
            Logger.d(TAG, "su command '$command' failed: ${e.message}")
            null
        }
    }
}
