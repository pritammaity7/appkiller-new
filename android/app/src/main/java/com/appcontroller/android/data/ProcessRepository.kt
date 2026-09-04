package com.appcontroller.android.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.appcontroller.android.model.ProcessInfo
import com.appcontroller.android.service.AppControllerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enumerates installed packages, classifies them as user vs system,
 * determines which are recently active (via UsageStatsManager.queryEvents),
 * and exposes a single entry point to stop a batch of packages.
 *
 * HONESTY NOTE on "running" detection:
 * There is NO public API for a non-root app to know if another app's
 * background service is currently running. ActivityManager.getRunningAppProcesses
 * only returns the caller's own process since Android 5.0. /proc/<pid>/status
 * is SELinux-blocked since Android 8.0. UsageStatsManager.queryEvents tells us
 * about Activity foreground/background transitions — NOT about background
 * services or scheduled jobs. An app whose Activity went to MOVE_TO_BACKGROUND
 * may still have running services.
 *
 * So the "Recently Active" filter is approximate. We also use
 * ApplicationInfo.FLAG_STOPPED to override: a stopped package is never shown
 * as active, even if UsageStatsManager has a stale entry for it.
 *
 * recentlyKilled set:
 * When the user kills an app, we mark it as "recently killed" for 60 seconds.
 * During that window, we force isRunning=false and isStopped=true regardless
 * of what UsageStatsManager says. This fixes the "killed apps still show as
 * running" staleness that UsageStatsManager's multi-minute lag would otherwise
 * cause.
 */
class ProcessRepository(
    private val context: Context,
    private val exceptionsRepository: ExceptionsRepository
) {

    private val packageManager: PackageManager = context.packageManager
    private val recentlyKilledPrefs = context.getSharedPreferences(PREFS_RECENTLY_KILLED, Context.MODE_PRIVATE)

    suspend fun getInstalledProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        val defaultLauncher = getDefaultLauncherPackage()
        val activeIme = getActiveImePackage()
        val ownPackage = context.packageName
        val activePackages = getRecentlyActivePackages(sinceMillis = 5 * 60 * 1000)
        val exceptions = exceptionsRepository.getAll()
        val recentlyKilled = getRecentlyKilledPackages()

        val result = mutableListOf<ProcessInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val packageName = pkg.packageName

            // Don't include Force Stop itself in the list — it's misleading
            // to show "Force Stop: 0 MB / Running" to the user.
            if (packageName == ownPackage) continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // FLAG_STOPPED is the system's authoritative "this app was force-stopped
            // or never launched" signal. It corresponds to the App Info screen's
            // Force Stop button being greyed-out.
            val isSystemStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            val isGuardrail = (packageName == defaultLauncher) ||
                    (packageName == activeIme) ||
                    packageName.startsWith("com.android.systemui") ||
                    packageName == "android"

            val isException = exceptions.contains(packageName)
            val isRecentlyKilled = recentlyKilled.contains(packageName)

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

            // Effective running state:
            // - If recently killed (within 60s), treat as not running.
            // - If the system says the package is FLAG_STOPPED, treat as not running.
            // - Otherwise, trust UsageStatsManager's recent-activity signal.
            val isRunning = !isRecentlyKilled && !isSystemStopped && activePackages.contains(packageName)

            // Effective stopped state for UI:
            val isStopped = isRecentlyKilled || isSystemStopped

            val canStop = !isGuardrail && !isException && isRunning

            val stateDetail = when {
                isGuardrail -> "Protected System Baseline"
                isException -> "User Exception"
                isRecentlyKilled -> "Just Stopped"
                isSystemStopped -> "Stopped"
                isRunning -> "Recently Active"
                else -> "Not Active"
            }

            result.add(
                ProcessInfo(
                    packageName = packageName,
                    appName = label,
                    icon = icon,
                    memoryMb = 0, // per-app memory impossible without root (audit Feature O)
                    isSystemApp = isSystem && !isUpdatedSystem,
                    isRunning = isRunning,
                    isStopped = isStopped,
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
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Uses UsageStatsManager.queryEvents (not queryUsageStats) to find packages
     * that have had Activity foreground/background transitions in the last
     * [sinceMillis]. queryEvents is more current than queryUsageStats (which
     * returns aggregated buckets with multi-minute lag).
     *
     * Still imperfect: only tracks Activity transitions, not background services.
     * See class doc for the honest limitation.
     */
    private fun getRecentlyActivePackages(sinceMillis: Long): Set<String> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptySet()
        val now = System.currentTimeMillis()
        val events = try {
            usageStatsManager.queryEvents(now - sinceMillis, now)
        } catch (e: SecurityException) {
            Log.w(TAG, "queryEvents denied — usage access not granted?", e)
            return emptySet()
        } ?: return emptySet()

        val active = mutableSetOf<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND both indicate the app
            // was recently active. Either is enough to consider it "active"
            // for our purposes.
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                active.add(event.packageName)
            }
        }
        return active
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
     * Read the recently-killed set from SharedPreferences and filter out
     * entries older than 60 seconds.
     */
    private fun getRecentlyKilledPackages(): Set<String> {
        val now = System.currentTimeMillis()
        val all = recentlyKilledPrefs.all
        val valid = mutableSetOf<String>()
        val toRemove = mutableListOf<String>()
        for ((pkg, tsValue) in all) {
            val ts = (tsValue as? Long) ?: 0L
            if (ts > 0 && now - ts < RECENTLY_KILLED_TTL_MS) {
                valid.add(pkg)
            } else {
                toRemove.add(pkg)
            }
        }
        // Clean up expired entries in the background.
        if (toRemove.isNotEmpty()) {
            recentlyKilledPrefs.edit().apply {
                toRemove.forEach { remove(it) }
            }.apply()
        }
        return valid
    }

    /**
     * Mark a package as just killed. It will be considered "not running"
     * for the next 60 seconds, regardless of what UsageStatsManager says.
     */
    fun markKilled(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val now = System.currentTimeMillis()
        recentlyKilledPrefs.edit().apply {
            packageNames.forEach { putLong(it, now) }
        }.apply()
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
            // Optimistically mark them as killed so the next list refresh
            // (which happens when the queue completes) shows them as stopped
            // immediately, even if UsageStatsManager has stale data.
            markKilled(filtered)
            service.startStoppingQueue(filtered)
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "ProcessRepository"
        private const val PREFS_RECENTLY_KILLED = "force_stop_recently_killed"
        private const val RECENTLY_KILLED_TTL_MS = 60_000L // 60 seconds
    }
}
