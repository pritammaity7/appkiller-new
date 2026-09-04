package com.appcontroller.android.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which packages have active notifications — a strong signal for
 * "this app has a foreground service running" (Android 8+ requires FGS to
 * show a persistent notification).
 *
 * Phase A — closes the blind spot that UsageStatsManager and
 * AccessibilityService.TYPE_WINDOW_STATE_CHANGED both have: neither can
 * see background-only services. NotificationListenerService CAN, because
 * any app with a foreground service MUST post a persistent notification.
 *
 * REQUIRES a separate user permission: "Notification Access" in Settings.
 * This is independent of Accessibility. If the user doesn't grant it,
 * we simply don't have this signal — the app still works, just with the
 * weaker detection.
 *
 * Also doubles as a keepalive: a bound NotificationListenerService is
 * harder for the system to kill than a plain service. This is the Hail
 * pattern — they extend NLS purely for the keepalive benefit, even
 * though they don't read notifications. We actually do read them.
 *
 * Usage:
 *   val pkgs = NotificationTracker.getActiveNotificationPackages()
 *   // -> set of package names with active notifications right now
 */
class ForceStopNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListener connected")
        // Refresh the cache on connect — getActiveNotifications returns the
        // current state synchronously.
        refreshCache()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.packageName?.let { pkg ->
            // Skip our own notifications (the foreground-service notification
            // we post during a batch).
            if (pkg != applicationContext.packageName) {
                NotificationTracker.activePackages[pkg] = System.currentTimeMillis()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.packageName?.let { pkg ->
            // Don't remove immediately — there might be multiple notifications
            // for the same package. Only remove if getActiveNotifications
            // confirms zero remain. This is a heuristic; we refresh the cache
            // periodically anyway.
            refreshCache()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "NotificationListener disconnected")
        NotificationTracker.activePackages.clear()
    }

    private fun refreshCache() {
        try {
            val active = getActiveNotifications() ?: return
            val now = System.currentTimeMillis()
            val ownPkg = applicationContext.packageName
            NotificationTracker.activePackages.clear()
            for (sbn in active) {
                val pkg = sbn.packageName
                if (pkg != ownPkg) {
                    NotificationTracker.activePackages[pkg] = now
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "refreshCache failed", e)
        }
    }

    companion object {
        private const val TAG = "FSNotifListener"
    }
}

/**
 * Singleton cache of packages with active notifications. Written by
 * ForceStopNotificationListener, read by ProcessRepository.
 *
 * Uses ConcurrentHashMap for thread-safety — onNotificationPosted/Removed
 * run on a binder thread, while ProcessRepository reads from a coroutine
 * on Dispatchers.IO.
 */
object NotificationTracker {
    val activePackages: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /**
     * Returns the set of packages with active notifications. Optionally
     * filter to only ongoing/foreground-service notifications (FLAG_ONGOING_EVENT
     * or FLAG_FOREGROUND_SERVICE) for a stronger "running service" signal.
     */
    fun getActivePackages(ongoingOnly: Boolean = false): Set<String> {
        if (!ongoingOnly) return activePackages.keys.toSet()
        // ongoingOnly requires querying the actual notification flags — but
        // we don't cache the flags (just the package + timestamp). For the
        // stronger filter, the caller should query NotificationListenerService
        // directly. For now, return all — the caller can decide.
        return activePackages.keys.toSet()
    }
}
