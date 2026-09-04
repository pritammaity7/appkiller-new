package com.appcontroller.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.appcontroller.android.R
import com.appcontroller.android.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppControllerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateMutex = Mutex()

    @Volatile private var currentTargetPackage: String? = null
    @Volatile private var currentAction: AppAction = AppAction.ForceStop
    @Volatile private var waitingForDialogConfirmation = false
    @Volatile private var waitingForStorageScreen = false
    @Volatile private var waitingForClearCacheClick = false
    private var queue = mutableListOf<Pair<String, AppAction>>()
    private var overlayManager: KillOverlayManager? = null

    // Watchdog: per-app timeout. If the confirmation dialog never appears
    // within this many ms, give up on this app and move to the next.
    private var watchdogJob: Job? = null

    /**
     * RECENTLY-ACTIVE TRACKER (Phase A):
     * Captures TYPE_WINDOW_STATE_CHANGED events for ALL packages (not just
     * Settings) to build a real-time map of which apps have been in the
     * foreground. This is MORE current than UsageStatsManager.queryEvents
     * (which has ~10s lag) — accessibility events fire sub-second.
     *
     * The map is packageName -> last-seen-foreground-timestamp. Entries
     * older than RECENTLY_ACTIVE_TTL_MS are pruned on read.
     *
     * This is the technique used by ngdathd/ForegroundActivity (open source)
     * and is what makes our 'Recently Active' filter competitive with Play
     * Store app killers — we see foreground switches in real time, for free,
     * because we already have an AccessibilityService.
     */
    private val recentlyActiveMap = mutableMapOf<String, Long>()

    /**
     * Public read-only snapshot of the recently-active map.
     * Called by ProcessRepository via companion getter.
     */
    fun getRecentlyActiveSnapshot(): Map<String, Long> {
        val now = System.currentTimeMillis()
        val cutoff = now - RECENTLY_ACTIVE_TTL_MS
        return synchronized(recentlyActiveMap) {
            // Prune expired entries while we're here.
            recentlyActiveMap.entries.removeAll { it.value < cutoff }
            recentlyActiveMap.toMap()
        }
    }

    /**
     * Record a foreground transition for a package. Called from
     * onAccessibilityEvent for ANY TYPE_WINDOW_STATE_CHANGED event,
     * before the kill-queue filter.
     */
    private fun recordForegroundTransition(packageName: String) {
        val now = System.currentTimeMillis()
        synchronized(recentlyActiveMap) {
            recentlyActiveMap[packageName] = now
        }
    }

    // Heartbeat: written periodically so the UI can detect a silently-killed
    // service (Xiaomi MIUI/HyperOS, Oppo ColorOS).
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            try {
                getSharedPreferences(HEARTBEAT_PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_HEARTBEAT_TS, System.currentTimeMillis())
                    .apply()
            } catch (t: Throwable) {
                // ignore — best-effort
            }
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        overlayManager = KillOverlayManager(this)

        val info = AccessibilityServiceInfo().apply {
            // TYPE_NOTIFICATION_STATE_CHANGED is captured so we can use it as a
            // weak 'posted a notification -> probably running' signal in the
            // recently-active tracker (Phase A).
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 40
        }
        serviceInfo = info

        // Start the heartbeat — fires every 30 seconds so the UI can detect
        // if the service has been silently killed by an aggressive OEM.
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceActive.value = false
        overlayManager?.hide()
        overlayManager = null
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        stopForegroundCompat()
        serviceScope.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceActive.value = false
        overlayManager?.hide()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        return super.onUnbind(intent)
    }

    /**
     * Entire body wrapped in try/catch — never let an exception escape
     * onAccessibilityEvent or the service process will crash.
     *
     * TWO responsibilities, in order:
     * 1. Record foreground transitions for ALL packages (Phase A — real-time
     *    running-app detection). This happens BEFORE the kill-queue filter
     *    so we capture every app the user switches to.
     * 2. Handle the kill-queue state machine, but only for events from
     *    Settings + OEM variants (the App Info screen and confirmation
     *    dialog). A notification arriving mid-stop or another app changing
     *    windows must not cause us to click 'Force stop' in the wrong window.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val pkg = event.packageName?.toString() ?: return

            // RESPONSIBILITY 1: Track foreground transitions for ALL packages.
            // TYPE_WINDOW_STATE_CHANGED fires when an Activity comes to the
            // foreground. This is our real-time 'recently active' signal.
            // We also capture TYPE_NOTIFICATION_STATE_CHANGED as a weak
            // 'posted a notification -> probably running' hint.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            ) {
                // Skip our own package and system UI shell packages.
                if (pkg != packageName &&
                    pkg != "android" &&
                    !pkg.startsWith("com.android.systemui")
                ) {
                    recordForegroundTransition(pkg)
                }
            }

            // RESPONSIBILITY 2: Kill-queue state machine — only for Settings events.
            if (currentTargetPackage == null) return
            if (pkg !in HANDLED_PACKAGES) return

            handleAccessibilityEvent(event)
        } catch (t: Throwable) {
            Log.e(TAG, "onAccessibilityEvent threw", t)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        if (currentTargetPackage == null) return

        val rootNode = try {
            rootInActiveWindow ?: return
        } catch (e: IllegalStateException) {
            Log.w(TAG, "rootInActiveWindow threw", e)
            return
        }

        // Dispatch based on current action type.
        when (currentAction) {
            AppAction.ForceStop -> handleForceStopEvent(event, rootNode)
            AppAction.ClearCache -> handleClearCacheEvent(event, rootNode)
        }
    }

    // ---- Force Stop state machine ----

    private fun handleForceStopEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo) {
        // 1. If we already clicked "Force stop" and are waiting for confirmation dialog.
        if (waitingForDialogConfirmation) {
            val isDialog = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    (event.className?.toString()?.contains("Dialog") == true ||
                            event.className?.toString()?.contains("AlertController") == true)
            if (!isDialog && pkgNotFromDialogHost(event)) {
                return
            }
            val confirmed = clickConfirmationDialogButton(rootNode)
            if (confirmed) {
                serviceScope.launch {
                    stateMutex.withLock {
                        waitingForDialogConfirmation = false
                        watchdogJob?.cancel()
                    }
                    delay(40)
                    processNextInQueue()
                }
            }
            return
        }

        // 2. We are in the App Info Settings screen — click Force Stop.
        val forceStopClicked = clickForceStopButton(rootNode)
        if (forceStopClicked) {
            serviceScope.launch {
                stateMutex.withLock {
                    waitingForDialogConfirmation = true
                }
                startWatchdog()
            }
        } else {
            if (isForceStopButtonDisabled(rootNode)) {
                serviceScope.launch {
                    delay(40)
                    processNextInQueue()
                }
            }
        }
    }

    // ---- Clear Cache state machine (NEW) ----
    // Flow: App Info → click "Storage & cache" → Storage screen → click "Clear cache"
    // No confirmation dialog on AOSP (immediate clear). MIUI Security Center
    // may show a dialog — handled by the same clickConfirmationDialogButton.

    private fun handleClearCacheEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo) {
        // State 3: We clicked "Clear cache" and are waiting for a confirmation
        // dialog (MIUI Security Center path). AOSP doesn't show a dialog —
        // the cache is cleared immediately, so we'll never enter this state
        // on AOSP.
        if (waitingForDialogConfirmation) {
            val isDialog = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    (event.className?.toString()?.contains("Dialog") == true ||
                            event.className?.toString()?.contains("AlertController") == true)
            if (!isDialog && pkgNotFromDialogHost(event)) {
                return
            }
            val confirmed = clickConfirmationDialogButton(rootNode)
            if (confirmed) {
                serviceScope.launch {
                    stateMutex.withLock {
                        waitingForDialogConfirmation = false
                        watchdogJob?.cancel()
                    }
                    delay(40)
                    processNextInQueue()
                }
            }
            return
        }

        // State 2: We clicked "Storage & cache" and are now on the Storage
        // screen — find and click "Clear cache".
        if (waitingForClearCacheClick) {
            val clearCacheClicked = clickClearCacheButton(rootNode)
            if (clearCacheClicked) {
                serviceScope.launch {
                    stateMutex.withLock {
                        waitingForClearCacheClick = false
                        watchdogJob?.cancel()
                    }
                    delay(60)
                    processNextInQueue()
                }
            }
            return
        }

        // State 1: We are on the App Info screen — find and click
        // "Storage & cache" to navigate to the Storage sub-screen.
        if (waitingForStorageScreen) {
            val storageClicked = clickStoragePreference(rootNode)
            if (storageClicked) {
                serviceScope.launch {
                    stateMutex.withLock {
                        waitingForStorageScreen = false
                        waitingForClearCacheClick = true
                    }
                    // Restart watchdog for the Storage screen phase.
                    startWatchdog()
                }
            }
            return
        }
    }

    /**
     * Find and click the "Storage & cache" preference on the App Info screen.
     * On AOSP the text is "Storage & cache"; OEMs may use "Storage", "Storage
     * usage", etc.
     */
    private fun clickStoragePreference(rootNode: AccessibilityNodeInfo): Boolean {
        val labels = listOf(
            "Storage & cache", "Storage and cache", "Storage usage",
            "Storage", "Almacenamiento", "Stockage"
        )
        for (label in labels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isEnabled) {
                    val clickableNode = findClickableAncestor(node) ?: node
                    val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
            }
        }
        return false
    }

    /**
     * Find and click the "Clear cache" button on the Storage screen.
     * View ID: com.android.settings:id/button2 (AOSP).
     * Text: "Clear cache" (clear_cache_btn_text).
     * NEVER click "Clear storage" / "Clear data" (button1) — that's destructive.
     */
    private fun clickClearCacheButton(rootNode: AccessibilityNodeInfo): Boolean {
        // Search by known view IDs first.
        val viewIds = listOf(
            "com.android.settings:id/button2",          // AOSP Clear Cache
            "com.android.settings:id/clear_cache_button", // ColorOS variant
            "com.android.settings:id/left_button"       // some OEMs swap order
        )
        for (id in viewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (node.isEnabled && node.isClickable) {
                    // Verify this is actually "Clear cache" and not "Clear data"
                    // by checking the node's text or content description.
                    val text = node.text?.toString() ?: ""
                    if (text.contains("cache", ignoreCase = true) ||
                        text.contains("caché", ignoreCase = true) ||
                        text.isEmpty() // if no text, trust the view ID
                    ) {
                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) return true
                    }
                }
            }
        }

        // Search by text — "Clear cache" only. NEVER match "Clear data" or
        // "Clear storage" — those are destructive.
        val labels = listOf(
            "Clear cache", "CLEAR CACHE", "Clear Cache",
            "Borrar caché", "Vider le cache", "Clear cache"
        )
        for (label in labels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isEnabled) {
                    val clickableNode = findClickableAncestor(node) ?: node
                    val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
            }
        }
        return false
    }

    private fun pkgNotFromDialogHost(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName?.toString() ?: return false
        // Dialogs are typically hosted by the same Settings package or by SystemUI.
        // If the event comes from a different package entirely, it's not the dialog.
        return pkg != "com.android.settings" &&
                pkg != "com.android.systemui" &&
                !pkg.startsWith("com.miui") &&
                !pkg.startsWith("com.samsung.android.settings")
    }

    /**
     * Watchdog: if the confirmation dialog never appears within PER_APP_TIMEOUT_MS,
     * give up on this app, log, send GLOBAL_ACTION_BACK as a best-effort cleanup,
     * and move on. Prevents the queue from hanging forever on OEM UI differences.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            delay(PER_APP_TIMEOUT_MS)
            Log.w(TAG, "Watchdog timed out for $currentTargetPackage — skipping")
            stateMutex.withLock {
                waitingForDialogConfirmation = false
            }
            // Best-effort: try BACK (works on 3-button nav + pre-A15 gesture nav)
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (t: Throwable) {
                // ignore
            }
            delay(40)
            processNextInQueue()
        }
    }

    // ---- Node-finding helpers (with recycling) ----

    private inline fun <T> withNode(node: AccessibilityNodeInfo?, block: (AccessibilityNodeInfo) -> T): T? {
        if (node == null) return null
        return try {
            block(node)
        } finally {
            // recycle() is a no-op on API 33+ (deprecated). Safe to call on all versions.
            if (Build.VERSION.SDK_INT < 33) {
                try { node.recycle() } catch (t: Throwable) { /* ignore */ }
            }
        }
    }

    private fun clickForceStopButton(rootNode: AccessibilityNodeInfo): Boolean {
        // Search by known view IDs first (AOSP, Samsung, MIUI, Pixel)
        val viewIds = listOf(
            "com.android.settings:id/button_force_stop",
            "com.android.settings:id/right_button",
            "com.android.settings:id/force_stop_button"
        )
        for (id in viewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (node.isEnabled && node.isClickable) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                    // If click returned false, fall through to next candidate.
                }
            }
        }

        // Search by localized or standard text strings
        val labels = listOf("Force stop", "FORCE STOP", "Force Stop", "Detener", "Forzar detención")
        for (label in labels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isEnabled) {
                    val clickableNode = findClickableAncestor(node) ?: node
                    val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
            }
        }
        return false
    }

    private fun isForceStopButtonDisabled(rootNode: AccessibilityNodeInfo): Boolean {
        val labels = listOf("Force stop", "FORCE STOP", "Force Stop")
        for (label in labels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                // Walk up to clickable ancestor and check its enabled state —
                // on many OEMs the disabled state is on a parent LinearLayout,
                // not the text TextView itself.
                val ancestor = findClickableAncestor(node) ?: node
                if (!ancestor.isEnabled) return true
            }
        }
        return false
    }

    private fun clickConfirmationDialogButton(rootNode: AccessibilityNodeInfo): Boolean {
        val confirmIds = listOf(
            "android:id/button1",
            "com.android.settings:id/button1"
        )
        for (id in confirmIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (node.isEnabled) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
            }
        }

        val okLabels = listOf("OK", "Force stop", "FORCE STOP", "Aceptar", "Confirmer")
        for (label in okLabels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isEnabled) {
                    val clickableNode = findClickableAncestor(node) ?: node
                    val clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
            }
        }
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 20) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    fun cancelQueue() {
        serviceScope.launch {
            stateMutex.withLock {
                queue.clear()
                currentTargetPackage = null
                waitingForDialogConfirmation = false
                waitingForStorageScreen = false
                waitingForClearCacheClick = false
                watchdogJob?.cancel()
            }
            overlayManager?.hide()
            bringAppToForeground()
            stopForegroundCompat()
            _batchProgress.value = BatchProgress.Idle
        }
    }

    private fun processNextInQueue() {
        if (queue.isEmpty()) {
            currentTargetPackage = null
            overlayManager?.hide()
            bringAppToForeground()
            stopForegroundCompat()
            serviceScope.launch {
                _killEvents.send(KillEvent.Completed)
            }
            _batchProgress.value = BatchProgress.Idle
            return
        }

        val (nextPkg, action) = queue.removeAt(0)
        currentTargetPackage = nextPkg
        currentAction = action
        waitingForDialogConfirmation = false
        waitingForStorageScreen = false
        waitingForClearCacheClick = false
        watchdogJob?.cancel()
        val processed = originalBatchSize - queue.size
        _batchProgress.value = BatchProgress.Stopping(
            currentPackage = nextPkg,
            processed = processed,
            total = originalBatchSize
        )
        overlayManager?.update(nextPkg, processed, originalBatchSize)

        // For Clear Cache, we start in the "waiting for Storage screen" state
        // — the first event from App Info should trigger clicking the Storage
        // preference.
        if (action == AppAction.ClearCache) {
            waitingForStorageScreen = true
        }

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", nextPkg, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        try {
            startActivity(intent)
            startWatchdog()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "App Info screen not found for $nextPkg — skipping", e)
            serviceScope.launch {
                _killEvents.send(KillEvent.Failed(nextPkg, "Package not found"))
            }
            processNextInQueue()
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot startActivity for $nextPkg — skipping", e)
            serviceScope.launch {
                _killEvents.send(KillEvent.Failed(nextPkg, "Cannot launch settings"))
            }
            processNextInQueue()
        }
    }

    /**
     * Bring Force Stop back to the foreground after the kill sequence.
     * AccessibilityService is exempt from BAL restrictions.
     */
    private fun bringAppToForeground() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        try {
            startActivity(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Cannot bring app to foreground", e)
        }
    }

    // ---- Foreground service management ----

    private fun startForegroundCompat() {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Force Stop")
            .setContentText("Processing batch stop…")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setOngoing(true)
            .setSilent(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            // FGS start can fail in some OEM background-restricted states.
            // The kill can still proceed without foreground priority.
            Log.w(TAG, "startForeground failed", t)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (t: Throwable) {
            // ignore
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Force Stop Batch",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows when Force Stop is processing a batch"
                    setShowBadge(false)
                }
                nm?.createNotificationChannel(channel)
            }
        }
    }

    override fun onInterrupt() {
        cancelQueue()
    }

    private var originalBatchSize: Int = 0

    /**
     * Public entry — starts a batch of force-stop OR clear-cache operations.
     *
     * PHASE B v2: show() is called BEFORE startForegroundCompat() — on
     * Android 15, startForeground from background requires an active overlay
     * window to be visible first, otherwise it throws
     * ForegroundServiceStartNotAllowedException.
     *
     * show() is suspend and awaits the overlay's first actual draw (via
     * ViewTreeObserver.OnPreDrawListener, not Choreographer) before returning.
     * This guarantees the overlay is composited BEFORE the first App Info
     * intent is launched.
     */
    suspend fun startBatch(packages: List<String>, action: AppAction) {
        originalBatchSize = packages.size
        queue.clear()
        queue.addAll(packages.map { it to action })
        currentAction = action
        // Phase B v2: show overlay FIRST, then promote to foreground.
        overlayManager?.show()
        startForegroundCompat()
        processNextInQueue()
    }

    /**
     * Legacy entry — kept for backward compatibility. Equivalent to
     * startBatch(packages, AppAction.ForceStop).
     */
    suspend fun startStoppingQueue(packages: List<String>) {
        startBatch(packages, AppAction.ForceStop)
    }

    companion object {
        private const val TAG = "ForceStopA11y"
        private const val CHANNEL_ID = "force_stop_batch"
        private const val NOTIF_ID = 4242
        private const val PER_APP_TIMEOUT_MS = 8_000L
        private const val HEARTBEAT_PREFS = "force_stop_heartbeat"
        private const val KEY_HEARTBEAT_TS = "last_heartbeat_ts"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30 seconds
        private const val RECENTLY_ACTIVE_TTL_MS = 120_000L // 2 minutes

        /**
         * Packages whose accessibility events we handle. Settings is the same
         * package name on ALL major OEMs (AOSP/Pixel, Samsung OneUI, Xiaomi
         * MIUI/HyperOS, Oppo ColorOS, Vivo OriginOS, Huawei EMUI) — OEMs modify
         * Settings in-place rather than replace it. Some OEMs route App Info
         * through a separate package — include those variants.
         */
        private val HANDLED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui", // hosts the confirmation AlertDialog on some builds
            "com.miui.securitymanager", // Xiaomi App Info variant
            "com.miui.securitycenter",
            "com.samsung.android.settings" // Samsung sub-settings
        )

        @Volatile
        var instance: AppControllerAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        private val _batchProgress = MutableStateFlow<BatchProgress>(BatchProgress.Idle)
        val batchProgress = _batchProgress.asStateFlow()

        private val _killEvents = Channel<KillEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val killEvents = _killEvents.receiveAsFlow()

        /**
         * Returns the timestamp of the last heartbeat, or 0 if the service has
         * never run. The UI uses this to detect a silently-killed service
         * (Xiaomi MIUI/HyperOS, Oppo ColorOS kill accessibility services while
         * the setting still shows "enabled").
         */
        fun getLastHeartbeatTs(context: android.content.Context): Long {
            return context.getSharedPreferences(HEARTBEAT_PREFS, android.content.Context.MODE_PRIVATE)
                .getLong(KEY_HEARTBEAT_TS, 0L)
        }

        /**
         * Returns a snapshot of the recently-active map from the live service
         * instance. Empty if the service isn't running. Called by
         * ProcessRepository to fuse with UsageStatsManager + FLAG_STOPPED.
         */
        fun getRecentlyActiveSnapshot(): Map<String, Long> {
            return instance?.getRecentlyActiveSnapshot() ?: emptyMap()
        }

        /**
         * No-op now — kept for any code that still calls it.
         */
        fun resetStatus() {
            // Intentionally empty
        }
    }

    // ---- Public state types ----

    sealed class BatchProgress {
        object Idle : BatchProgress()
        data class Stopping(
            val currentPackage: String,
            val processed: Int,
            val total: Int
        ) : BatchProgress()
    }

    sealed class KillEvent {
        data class AppStopped(val packageName: String) : KillEvent()
        data class Failed(val packageName: String, val reason: String) : KillEvent()
        object Completed : KillEvent()
    }

    /**
     * The action to perform on a package. Determines which button to click
     * in the App Info / Storage screens.
     */
    sealed class AppAction {
        /** Force Stop: App Info → Force Stop button → confirmation dialog. */
        object ForceStop : AppAction()
        /** Clear Cache: App Info → Storage & cache → Clear cache button. */
        object ClearCache : AppAction()
    }
}
