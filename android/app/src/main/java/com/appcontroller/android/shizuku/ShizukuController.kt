package com.appcontroller.android.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * Executes `am force-stop <packageName>` via ADB privileged shell with zero screen flashing.
     *
     * NOTE: Shizuku.newProcess() is private in the published API (v13.1.5). The proper way to
     * run shell commands through Shizuku is to bind an IUserService (see Shizuku.bindUserService).
     * That requires an AIDL interface + a service implementation, which is intentionally left
     * as a follow-up. Until that is wired up, this function returns false so callers fall back
     * to the AccessibilityService-based automation path in AppControllerAccessibilityService,
     * which fully implements force-stop without Shizuku.
     *
     * TODO: Implement IUserService AIDL for true zero-UI Shizuku stopping.
     */
    suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        // Permission is still required, so the UI correctly shows "SHIZUKU" status.
        if (!hasShizukuPermission()) return@withContext false
        // Not yet implemented — return false so the caller falls back to AccessibilityService.
        false
    }
}
