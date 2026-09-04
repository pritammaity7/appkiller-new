package com.appcontroller.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppControllerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || currentTargetPackage == null) return

        val rootNode = rootInActiveWindow ?: return

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
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    fun startStoppingQueue(packages: List<String>) {
        queue.clear()
        queue.addAll(packages)
        processNextInQueue()
    }

    fun cancelQueue() {
        queue.clear()
        currentTargetPackage = null
        waitingForDialogConfirmation = false
        _stoppingProgress.value = StoppingStatus.Idle
    }

    private fun processNextInQueue() {
        if (queue.isEmpty()) {
            currentTargetPackage = null
            _stoppingProgress.value = StoppingStatus.Completed
            return
        }

        val nextPkg = queue.removeAt(0)
        currentTargetPackage = nextPkg
        waitingForDialogConfirmation = false
        _stoppingProgress.value = StoppingStatus.Stopping(nextPkg, queue.size)

        // Launch Application Details Settings
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", nextPkg, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        cancelQueue()
    }

    companion object {
        var instance: AppControllerAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        private val _stoppingProgress = MutableStateFlow<StoppingStatus>(StoppingStatus.Idle)
        val stoppingProgress = _stoppingProgress.asStateFlow()
    }

    sealed class StoppingStatus {
        object Idle : StoppingStatus()
        data class Stopping(val packageName: String, val remaining: Int) : StoppingStatus()
        object Completed : StoppingStatus()
    }
}
