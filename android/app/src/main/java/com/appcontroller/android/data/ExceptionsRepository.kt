package com.appcontroller.android.data

import android.content.Context

/**
 * Persists TWO separate exception lists:
 * 1. Force-stop exceptions — apps that should never be force-stopped
 * 2. Clear-cache exceptions — apps whose cache should never be cleared
 *
 * Stored as separate StringSets in SharedPreferences so they're independent.
 * An app can be in one, both, or neither list.
 */
class ExceptionsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Force-stop exceptions ----

    fun getForceStopExceptions(): Set<String> =
        prefs.getStringSet(KEY_FORCE_STOP_EXCEPTIONS, emptySet()) ?: emptySet()

    fun isForceStopException(packageName: String): Boolean =
        getForceStopExceptions().contains(packageName)

    fun addToForceStopExceptions(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val current = getForceStopExceptions().toMutableSet()
        current.addAll(packageNames)
        prefs.edit().putStringSet(KEY_FORCE_STOP_EXCEPTIONS, current).apply()
    }

    fun removeFromForceStopExceptions(packageName: String) {
        val current = getForceStopExceptions().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_FORCE_STOP_EXCEPTIONS, current).apply()
    }

    // ---- Clear-cache exceptions ----

    fun getClearCacheExceptions(): Set<String> =
        prefs.getStringSet(KEY_CLEAR_CACHE_EXCEPTIONS, emptySet()) ?: emptySet()

    fun isClearCacheException(packageName: String): Boolean =
        getClearCacheExceptions().contains(packageName)

    fun addToClearCacheExceptions(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val current = getClearCacheExceptions().toMutableSet()
        current.addAll(packageNames)
        prefs.edit().putStringSet(KEY_CLEAR_CACHE_EXCEPTIONS, current).apply()
    }

    fun removeFromClearCacheExceptions(packageName: String) {
        val current = getClearCacheExceptions().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_CLEAR_CACHE_EXCEPTIONS, current).apply()
    }

    // ---- Bulk operations ----

    fun clearForceStopExceptions() {
        prefs.edit().remove(KEY_FORCE_STOP_EXCEPTIONS).apply()
    }

    fun clearClearCacheExceptions() {
        prefs.edit().remove(KEY_CLEAR_CACHE_EXCEPTIONS).apply()
    }

    fun clearAll() {
        clearForceStopExceptions()
        clearClearCacheExceptions()
    }

    /**
     * Legacy compatibility — returns force-stop exceptions. Used by
     * ProcessRepository for the force-stop guardrail.
     */
    fun getAll(): Set<String> = getForceStopExceptions()

    /**
     * Legacy compatibility — checks force-stop exceptions only.
     */
    fun isException(packageName: String): Boolean = isForceStopException(packageName)

    /**
     * Legacy compatibility — adds to force-stop exceptions.
     */
    fun add(packageNames: Collection<String>) = addToForceStopExceptions(packageNames)

    /**
     * Legacy compatibility — removes from force-stop exceptions.
     */
    fun remove(packageName: String) = removeFromForceStopExceptions(packageName)

    fun clear() = clearAll()

    companion object {
        private const val PREFS_NAME = "force_stop_exceptions"
        private const val KEY_FORCE_STOP_EXCEPTIONS = "force_stop_exception_packages"
        private const val KEY_CLEAR_CACHE_EXCEPTIONS = "clear_cache_exception_packages"
    }
}
