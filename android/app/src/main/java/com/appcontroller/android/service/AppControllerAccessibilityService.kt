package com.appcontroller.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.appcontroller.android.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class AppControllerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentTargetPackage: String? = null
    private var waitingForDialogConfirmation = false
    private var queue = mutableListOf<String>()
    private var overlayManager: KillOverlayManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        overlayManager = KillOverlayManager(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 40
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceActive.value = false
        overlayManager?.hide()
        overlayManager = null
        serviceScope.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // onUnbind is called when the system unbinds the service (user toggled
        // accessibility off, or system killed it). Clear state here too —
        // onDestroy is NOT guaranteed to be called for system-killed services.
        instance = null
        _isServiceActive.value = false
        overlayManager?.hide()
        return super.onUnbind(intent)
    }

    /**
     * Entire body wrapped in try/catch — never let an exception escape
     * onAccessibilityEvent or the service process will crash.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleAccessibilityEvent(event)
        } catch (t: Throwable) {
            Log.e(TAG, "onAccessibilityEvent threw", t)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || currentTargetPackage == null) return

        val rootNode = try {
            rootInActiveWindow ?: return
        } catch (e: IllegalStateException) {
            // "Could not get the source node" — window torn down mid-event on some OEMs
            Log.w(TAG, "rootInActiveWindow threw", e)
            return
        }

        // 1. If we already clicked "Force stop" and are waiting for confirmation dialog
        if (waitingForDialogConfirmation) {
            val confirmed = clickConfirmationDialogButton(rootNode)
            if (confirmed) {
                waitingForDialogConfirmation = false
                // App is stopped — process next. We don't need GLOBAL_ACTION_BACK
                // here because the overlay is hiding the screen anyway, and the
                // next App Info intent will replace the current one. (Audit bug V3:
                // GLOBAL_ACTION_BACK is broken on Android 15 + gesture nav, Google
                // Issue #369636231, Won't Fix.)
                serviceScope.launch {
                    delay(40)
                    processNextInQueue()
                }
            }
            return
        }

        // 2. We are in the App Info Settings screen
        val forceStopClicked = clickForceStopButton(rootNode)
        if (forceStopClicked) {
            waitingForDialogConfirmation = true
        } else {
            // Check if already stopped (disabled Force Stop button)
            if (isForceStopButtonDisabled(rootNode)) {
                serviceScope.launch {
                    delay(40)
                    processNextInQueue()
                }
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
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
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
                    clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
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
                if (!node.isEnabled) return true
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
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }

        val okLabels = listOf("OK", "Force stop", "FORCE STOP", "Aceptar", "Confirmer")
        for (label in okLabels) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isEnabled) {
                    val clickableNode = findClickableAncestor(node) ?: node
                    clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
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
        queue.clear()
        currentTargetPackage = null
        waitingForDialogConfirmation = false
        overlayManager?.hide()
        bringAppToForeground()
        _batchProgress.value = BatchProgress.Idle
    }

    private fun processNextInQueue() {
        if (queue.isEmpty()) {
            currentTargetPackage = null
            // Hide the overlay, bring our app back to the foreground, and emit
            // the Completed event. Replaces GLOBAL_ACTION_BACK which is broken
            // on Android 15 + gesture nav (Google Issue #369636231, Won't Fix).
            overlayManager?.hide()
            bringAppToForeground()
            serviceScope.launch {
                _killEvents.send(KillEvent.Completed)
            }
            _batchProgress.value = BatchProgress.Idle
            return
        }

        val nextPkg = queue.removeAt(0)
        currentTargetPackage = nextPkg
        waitingForDialogConfirmation = false
        val processed = originalBatchSize - queue.size
        _batchProgress.value = BatchProgress.Stopping(
            currentPackage = nextPkg,
            processed = processed,
            total = originalBatchSize
        )
        overlayManager?.update(nextPkg, processed, originalBatchSize)

        // Launch Application Details Settings — wrapped in try/catch so an
        // uninstalled/banned package doesn't crash the service.
        // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS keeps Settings out of recents.
        // NOT using FLAG_ACTIVITY_NO_HISTORY — audit found it can destroy the
        // App Info Activity before the confirmation dialog fires (race).
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", nextPkg, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        try {
            startActivity(intent)
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
     * Bring Force Stop back to the foreground after the kill sequence. Uses
     * FLAG_ACTIVITY_NEW_TASK (required from a Service context) +
     * FLAG_ACTIVITY_REORDER_TO_FRONT (brings the existing task forward instead
     * of creating a new instance).
     *
     * AccessibilityService is exempt from Background Activity Launch (BAL)
     * restrictions, so this works even when the service is in the background.
     *
     * Caveat (audit Feature G): Xiaomi MIUI blocks this unless the user has
     * enabled "Display pop-up windows while running in the background" in
     * system settings. If startActivity silently fails, the user stays on
     // whatever was last foregrounded — the overlay is already hidden, so
     // they'll see the App Info screen or the launcher.
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

    override fun onInterrupt() {
        cancelQueue()
    }

    /**
     * The batch size at the moment the queue was started. Used for progress
     * reporting.
     */
    private var originalBatchSize: Int = 0

    /**
     * Public entry — records the original batch size for progress reporting,
     * then starts processing. Keep the simple signature so the UI doesn't
     * need to change.
     */
    fun startStoppingQueue(packages: List<String>) {
        originalBatchSize = packages.size
        queue.clear()
        queue.addAll(packages)
        // Show the overlay BEFORE launching the first App Info intent so the
        // user never sees the Settings screen flash. The overlay will be
        // dismissed in processNextInQueue() when the queue is empty.
        overlayManager?.show()
        processNextInQueue()
    }

    companion object {
        private const val TAG = "ForceStopA11y"

        @Volatile
        var instance: AppControllerAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        /**
         * STATE: ongoing batch progress. Use this for the live UI (progress bar,
         * "Stopping 3 of 15…"). Conflates — that's fine for progress.
         */
        private val _batchProgress = MutableStateFlow<BatchProgress>(BatchProgress.Idle)
        val batchProgress = _batchProgress.asStateFlow()

        /**
         * EVENTS: one-shot kill results (per-app success/fail + batch complete).
         * Delivered via Channel + receiveAsFlow so each event is consumed by
         * exactly one collector and never re-triggers on recomposition.
         *
         * Replacing the old StateFlow<StoppingStatus> which caused the
         * LaunchedEffect self-cancellation crash when resetStatus() was called
         * mid-collection.
         */
        private val _killEvents = Channel<KillEvent>(
            capacity = Channel.BUFFERED,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val killEvents = _killEvents.receiveAsFlow()

        /**
         * No-op now — kept for any code that still calls it. The Channel
         * auto-consumes, so there's nothing to "reset". Will be removed in
         * Phase 4 once the UI fully migrates to killEvents.
         */
        fun resetStatus() {
            // Intentionally empty — Channel-based events don't need a reset.
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
}
