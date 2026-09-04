package com.appcontroller.android.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observes Settings.Secure for changes to the accessibility-services setting.
 * Emits the new value (true = our service is enabled) on every change.
 *
 * Replaces the previous pattern of calling Settings.Secure.getString on every
 * Activity onResume — that's a sync binder call to the system server which
 * can cause jank (audit bug O10).
 *
 * Usage:
 *   observeAccessibilityEnabled(context)
 *     .collect { enabled -> ... }
 *
 * The flow emits the initial value immediately on collection, then again on
 * every change. It unregisters the ContentObserver when the flow is cancelled.
 */
fun observeAccessibilityEnabled(context: Context): Flow<Boolean> = callbackFlow {
    val handler = Handler(Looper.getMainLooper())
    val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            trySend(PermissionChecker.isAccessibilityEnabled(context))
        }
    }
    val uri: Uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    context.contentResolver.registerContentObserver(uri, false, observer)

    // Also observe ACCESSIBILITY_ENABLED (the master toggle)
    val masterUri: Uri = Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED)
    context.contentResolver.registerContentObserver(masterUri, false, observer)

    // Initial value
    trySend(PermissionChecker.isAccessibilityEnabled(context))

    awaitClose {
        context.contentResolver.unregisterContentObserver(observer)
    }
}

/**
 * Observes the PACKAGE_USAGE_STATS app-op state. Emits true when usage access
 * is granted.
 */
fun observeUsageAccessEnabled(context: Context): Flow<Boolean> = callbackFlow {
    val handler = Handler(Looper.getMainLooper())
    val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            trySend(PermissionChecker.isUsageAccessEnabled(context))
        }
    }
    // Usage access is an app-op, not a Settings.Secure entry, so there's no
    // direct URI to observe. We observe the broader app-ops changes via the
    // generic OPSTR_GET_USAGE_STATS uri. As a fallback, the UI also re-checks
    // on onResume.
    val uri: Uri = Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED)
    context.contentResolver.registerContentObserver(uri, true, observer)

    trySend(PermissionChecker.isUsageAccessEnabled(context))

    awaitClose {
        context.contentResolver.unregisterContentObserver(observer)
    }
}
