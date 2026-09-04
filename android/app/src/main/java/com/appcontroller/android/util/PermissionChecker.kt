package com.appcontroller.android.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.appcontroller.android.service.AppControllerAccessibilityService

object PermissionChecker {

    /**
     * Returns true iff our AccessibilityService is enabled in
     * Settings > Accessibility AND has sent a heartbeat recently (within 90s).
     *
     * The heartbeat check catches the case where Xiaomi MIUI/HyperOS or
     * Oppo ColorOS silently kills the service process while the Settings
     * toggle still shows "enabled" (audit Features E, F).
     *
     * On first launch (never run yet), the heartbeat timestamp is 0 — we
     * consider the service "enabled" if the setting is on, even without a
     * recent heartbeat, so we don't block the user from a fresh install.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppControllerAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val settingOn = enabled.split(':').any {
            it.equals(expected, ignoreCase = true)
        } && Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1

        if (!settingOn) return false

        // Check heartbeat — if the service has run before but the heartbeat
        // is stale, the service was likely killed by an aggressive OEM.
        val lastHeartbeat = AppControllerAccessibilityService.getLastHeartbeatTs(context)
        if (lastHeartbeat == 0L) {
            // Never run yet — give it the benefit of the doubt (fresh install
            // or first enable).
            return true
        }
        val now = System.currentTimeMillis()
        return now - lastHeartbeat < HEARTBEAT_FRESH_TOLERANCE_MS
    }

    /**
     * Returns true iff the user has granted PACKAGE_USAGE_STATS
     * (Settings > Apps > Special access > Usage access).
     */
    fun isUsageAccessEnabled(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns true if the service has been silently killed (setting still
     * shows "enabled" but heartbeat is stale). Used by the UI to show a
     * different warning ("Service killed by system — please re-enable")
     * instead of the standard "Enable Accessibility" prompt.
     */
    fun isServiceSilentlyKilled(context: Context): Boolean {
        val expected = ComponentName(context, AppControllerAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val settingOn = enabled.split(':').any {
            it.equals(expected, ignoreCase = true)
        }
        if (!settingOn) return false

        val lastHeartbeat = AppControllerAccessibilityService.getLastHeartbeatTs(context)
        if (lastHeartbeat == 0L) return false // never run — not "killed"
        val now = System.currentTimeMillis()
        return now - lastHeartbeat >= HEARTBEAT_FRESH_TOLERANCE_MS
    }

    private const val HEARTBEAT_FRESH_TOLERANCE_MS = 90_000L // 90s (3 missed 30s heartbeats)
}
