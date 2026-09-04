package com.appcontroller.android.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuController {

    const val SHIZUKU_REQ_CODE = 4421

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return if (!isShizukuAvailable()) {
            false
        } else {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
        }
    }

    fun requestShizukuPermission(onResult: (granted: Boolean) -> Unit) {
        if (!isShizukuAvailable()) {
            onResult(false)
            return
        }

        if (hasShizukuPermission()) {
            onResult(true)
            return
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == SHIZUKU_REQ_CODE) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(SHIZUKU_REQ_CODE)
    }

    /**
     * Executes `am force-stop <packageName>` via ADB privileged shell with zero screen flashing!
     */
    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) return@withContext false

        try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "am force-stop $packageName"),
                null,
                null
            )
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
