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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class AppControllerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentTargetPackage: String? = null
    private var waitingForDialogConfirmation = false
    private var queue = mutableListOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true

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
        serviceScope.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // onUnbind is called when the system unbinds the service (user toggled
        // accessibility off, or system killed it). Clear state here too —
        // onDestroy is NOT guaranteed to be called for system-killed services.
        instance = null
        _isServiceActive.value = false
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
                // App is stopped! Go back and process next
                serviceScope.launch {
                    delay(120)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(120)
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
                    delay(100)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(100)
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
        _batchProgress.value = BatchProgress.Idle
    }

    private fun processNextInQueue() {
        if (queue.isEmpty()) {
            currentTargetPackage = null
            // Emit ONE-SHOT event on the Channel — UI will consume it once.
            // No need to "reset" anything.
            serviceScope.launch {
                _killEvents.send(KillEvent.Completed)
            }
            _batchProgress.value = BatchProgress.Idle
            return
        }

        val nextPkg = queue.removeAt(0)
        currentTargetPackage = nextPkg
        waitingForDialogConfirmation = false
        _batchProgress.value = BatchProgress.Stopping(
            currentPackage = nextPkg,
            processed = originalBatchSize - queue.size,
            total = originalBatchSize
        )

        // Launch Application Details Settings — wrapped in try/catch so an
        // uninstalled/banned package doesn't crash the service.
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", nextPkg, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
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
