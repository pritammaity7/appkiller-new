package com.appcontroller.android.data

import android.content.Context

/**
 * Persists the user's exception list (apps that should never be force-stopped)
 * in SharedPreferences as a single StringSet.
 *
 * An app on this list is:
 *   - skipped during a "Stop selected" batch operation
 *   - shown as non-selectable in the apps list (with a "Protected" badge)
 *
 * Implementation note (audit bug O11):
 * Previously used prefs.all.keys which returns ALL keys in the file —
 * including any non-exception keys we might add in the future. Now uses
 * a single StringSet key so the storage is namespaced and unambiguous.
 */
class ExceptionsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): Set<String> = prefs.getStringSet(KEY_EXCEPTIONS, emptySet()) ?: emptySet()

    fun isException(packageName: String): Boolean = getAll().contains(packageName)

    fun add(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val current = getAll().toMutableSet()
        current.addAll(packageNames)
        prefs.edit().putStringSet(KEY_EXCEPTIONS, current).apply()
    }

    fun remove(packageName: String) {
        val current = getAll().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_EXCEPTIONS, current).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_EXCEPTIONS).apply()
    }

    companion object {
        private const val PREFS_NAME = "force_stop_exceptions"
        private const val KEY_EXCEPTIONS = "exception_packages"
    }
}
