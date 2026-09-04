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
import com.appcontroller.android.service.NotificationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enumerates installed packages, classifies them as user vs system,
 * determines which are recently active (via FUSED signals), and exposes
 * a single entry point to stop a batch of packages.
 *
 * HONESTY NOTE on "running" detection:
 * There is NO public API for a non-root app to know if another app's
 * background service is currently running. ActivityManager.getRunningAppProcesses
 * only returns the caller's own process since Android 5.0. /proc/<pid>/status
 * is SELinux-blocked since Android 8.0. Even UsageStatsManager.queryEvents
 * only sees Activity transitions, not services.
 *
 * PHASE A — SIGNAL FUSION:
 * We now fuse THREE independent signals to approximate "recently active":
 *
 * 1. AccessibilityService TYPE_WINDOW_STATE_CHANGED events (REAL-TIME, sub-second)
 *    — captured by our own AccessibilityService for ALL packages. This is the
 *    most current signal available. See AppControllerAccessibilityService.
 *
 * 2. UsageStatsManager.queryEvents (60s window, ~10s lag)
 *    — backfills activity transitions the accessibility service might have
 *    missed (e.g. if the service was briefly killed). Shortened from 5min
 *    to 60s to reduce noise.
 *
 * 3. NotificationListenerService.getActiveNotifications (catches FOREGROUND SERVICES)
 *    — on Android 8+, every foreground service MUST show a persistent
 *    notification. So an app with an active notification very likely has a
 *    running service. This is the ONE signal that catches background services
 *    that the other two miss. Requires separate "Notification Access" permission.
 *
 * A package is considered "recently active" if ANY of these signals fires.
 *
 * recentlyKilled set:
 * When the user kills an app, we mark it as "recently killed" for 60 seconds.
 * During that window, we force isRunning=false and isStopped=true regardless
 * of what the signals say. This fixes the "killed apps still show as running"
 * staleness.
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

        // PHASE A: fuse three signals for "recently active".
        // 1. Accessibility events (real-time, sub-second).
        val accessibilityActive = AppControllerAccessibilityService.getRecentlyActiveSnapshot()
        // 2. UsageStatsManager.queryEvents (60s window, ~10s lag).
        val usageStatsActive = getRecentlyActivePackages(sinceMillis = 60 * 1000)
        // 3. NotificationListenerService active notifications (catches FGS).
        val notificationActive = NotificationTracker.getActivePackages()

        val exceptions = exceptionsRepository.getAll()
        val recentlyKilled = getRecentlyKilledPackages()

        val result = mutableListOf<ProcessInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val packageName = pkg.packageName

            // Don't include Force Stop itself in the list.
            if (packageName == ownPackage) continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // FLAG_STOPPED is the system's authoritative "this app was force-stopped
            // or never launched" signal.
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

            // FUSED running state: any of the three signals counts.
            // - If recently killed (within 60s), treat as not running.
            // - If the system says the package is FLAG_STOPPED, treat as not running.
            // - Otherwise, trust the fused signal.
            val isFusedActive = accessibilityActive.containsKey(packageName) ||
                    usageStatsActive.contains(packageName) ||
                    notificationActive.contains(packageName)
            val isRunning = !isRecentlyKilled && !isSystemStopped && isFusedActive

            // Effective stopped state for UI:
            val isStopped = isRecentlyKilled || isSystemStopped

            val canStop = !isGuardrail && !isException && isRunning

            // More detailed state for the user — distinguish "foreground" from
            // "background service" so the user understands WHY an app shows.
            val stateDetail = when {
                isGuardrail -> "Protected System Baseline"
                isException -> "User Exception"
                isRecentlyKilled -> "Just Stopped"
                isSystemStopped -> "Stopped"
                isRunning -> {
                    // Distinguish: was it a foreground switch (accessibility/usage)
                    // or a background service (notification only)?
                    val hasForegroundSignal = accessibilityActive.containsKey(packageName) ||
                            usageStatsActive.contains(packageName)
                    val hasNotificationSignal = notificationActive.contains(packageName)
                    when {
                        hasForegroundSignal && hasNotificationSignal -> "Active (FG + Service)"
                        hasForegroundSignal -> "Active in foreground"
                        hasNotificationSignal -> "Background service"
                        else -> "Recently Active"
                    }
                }
                else -> "Not Active"
            }

            result.add(
                ProcessInfo(
                    packageName = packageName,
                    appName = label,
                    icon = icon,
                    memoryMb = 0, // per-app memory impossible without root
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
