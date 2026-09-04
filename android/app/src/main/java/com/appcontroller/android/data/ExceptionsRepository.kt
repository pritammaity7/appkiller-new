package com.appcontroller.android.data

import android.content.Context

/**
 * Persists the user's exception list (apps that should never be force-stopped)
 * in SharedPreferences. Package names are used as keys so lookups are O(1).
 *
 * An app on this list is:
 *   - skipped during a "Stop selected" batch operation
 *   - shown as non-selectable in the apps list (with a "Protected" badge)
 */
class ExceptionsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): Set<String> = prefs.all.keys.toSet()

    fun isException(packageName: String): Boolean = prefs.getBoolean(packageName, false)

    fun add(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        prefs.edit().apply {
            packageNames.forEach { putBoolean(it, true) }
        }.apply()
    }

    fun remove(packageName: String) {
        prefs.edit().remove(packageName).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "force_stop_exceptions"
    }
}
