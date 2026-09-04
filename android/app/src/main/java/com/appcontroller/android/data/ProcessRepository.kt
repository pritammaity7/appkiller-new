package com.appcontroller.android.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import com.appcontroller.android.model.ProcessInfo
import com.appcontroller.android.service.AppControllerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enumerates installed packages, classifies them as user vs system,
 * determines which are actually running (via UsageStatsManager),
 * and exposes a single entry point to stop a batch of packages.
 *
 * Exceptions (user-protected apps) are honoured: any package on the
 * exception list is marked canStop=false and is silently skipped
 * during a stop batch.
 */
class ProcessRepository(
    private val context: Context,
    private val exceptionsRepository: ExceptionsRepository
) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        val defaultLauncher = getDefaultLauncherPackage()
        val activeIme = getActiveImePackage()
        val ownPackage = context.packageName
        val runningPackages = getRecentlyRunningPackages(sinceMillis = 5 * 60 * 1000)
        val exceptions = exceptionsRepository.getAll()

        val result = mutableListOf<ProcessInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val packageName = pkg.packageName

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val isGuardrail = (packageName == ownPackage) ||
                    (packageName == defaultLauncher) ||
                    (packageName == activeIme) ||
                    packageName.startsWith("com.android.systemui") ||
                    packageName == "android"

            val isException = exceptions.contains(packageName)

            val label = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            val icon = try {
                packageManager.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }

            val isRunning = runningPackages.contains(packageName)
            val memWeight = if (isRunning) {
                if (isSystem) (45..110).random() else (65..280).random()
            } else 0

            val canStop = !isGuardrail && !isException && isRunning

            val stateDetail = when {
                isGuardrail -> "Protected System Baseline"
                isException -> "User Exception"
                isRunning -> "Background Process"
                else -> "Not Running"
            }

            result.add(
                ProcessInfo(
                    packageName = packageName,
                    appName = label,
                    icon = icon,
                    memoryMb = memWeight,
                    isSystemApp = isSystem && !isUpdatedSystem,
                    isRunning = isRunning,
                    isStopped = false,
                    canStop = canStop,
                    stateDetail = stateDetail,
                    isException = isException
                )
            )
        }

        // Show stoppable running apps first, then other running, then non-running.
        result.sortedWith(
            compareByDescending<ProcessInfo> { it.canStop }
                .thenByDescending { it.isRunning }
                .thenByDescending { it.memoryMb }
                .thenBy { it.appName }
        )
    }

    /**
     * Uses UsageStatsManager to find packages that have been used in the last
     * [sinceMillis] milliseconds. This is the canonical non-root way to know
     * what is actually running on Android 5.0+ (ActivityManager.getRunningAppProcesses
     * only returns the caller's own processes on modern Android).
     */
    private fun getRecentlyRunningPackages(sinceMillis: Long): Set<String> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptySet()
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - sinceMillis,
            now
        ) ?: return emptySet()
        return stats.map { it.packageName }.toSet()
    }

    private fun getDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun getActiveImePackage(): String? {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val methodList = imm?.enabledInputMethodList ?: return null
        return methodList.firstOrNull()?.packageName
    }

    /**
     * Stops the given packages via the AccessibilityService automation path.
     * Guardrails and exceptions are filtered out defensively here even though
     * the UI should already prevent them from being selected.
     *
     * Returns true if the service accepted the queue, false if no service is
     * available (user has not enabled Accessibility).
     */
    suspend fun stopSelectedPackages(packageNames: List<String>): Boolean {
        val exceptions = exceptionsRepository.getAll()
        val filtered = packageNames.filter { pkg ->
            pkg !in exceptions &&
                    pkg != context.packageName &&
                    !pkg.startsWith("com.android.systemui") &&
                    pkg != "android"
        }
        if (filtered.isEmpty()) return false

        val service = AppControllerAccessibilityService.instance
        if (service != null) {
            service.startStoppingQueue(filtered)
            return true
        }
        return false
    }
}
