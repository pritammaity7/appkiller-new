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
     * Settings > Accessibility. Re-checked on every resume.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppControllerAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.equals(expected, ignoreCase = true)
        } && Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
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
}
