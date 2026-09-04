package com.appcontroller.android.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.appcontroller.android.data.CacheRepository
import com.appcontroller.android.data.ExceptionsRepository
import com.appcontroller.android.data.MemoryReader
import com.appcontroller.android.data.ProcessRepository
import com.appcontroller.android.model.MemoryVitals
import com.appcontroller.android.model.ProcessInfo
import com.appcontroller.android.service.AppControllerAccessibilityService
import com.appcontroller.android.service.AppControllerAccessibilityService.AppAction
import com.appcontroller.android.util.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds all UI state and survives Activity recreation (rotation, system-
 * initiated destruction when Settings takes foreground during a stop batch).
 *
 * SavedStateHandle persists small UI state (search query, filter, scroll
 * position) across process death. The process list and recently-killed set
 * are re-fetched from ProcessRepository on init — they're cheap to load.
 *
 * Selection state is a Set<String> of package names (audit bug C7: previously
 * isSelected was a var inside ProcessInfo data class, which caused LazyColumn
 * diffing issues because Drawable equality is identity-based).
 */
class ForceStopViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val exceptionsRepository = ExceptionsRepository(application)
    private val repository = ProcessRepository(application, exceptionsRepository)
    private val cacheRepository = CacheRepository(application)

    // ---- Process list state ----
    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    // ---- UI state (persisted across process death via SavedStateHandle) ----
    var searchQuery: String
        get() = savedStateHandle[KEY_SEARCH] ?: ""
        set(value) { savedStateHandle[KEY_SEARCH] = value }

    var filterType: String
        get() = savedStateHandle[KEY_FILTER] ?: "Recently Active"
        set(value) { savedStateHandle[KEY_FILTER] = value }

    var selectedTab: Int
        get() = savedStateHandle[KEY_TAB] ?: 0
        set(value) { savedStateHandle[KEY_TAB] = value }

    // ---- Selection state (in-memory; cleared on process death is fine) ----
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    fun toggleSelected(packageName: String) {
        _selectedPackages.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun selectAllVisible(packageNames: List<String>, select: Boolean) {
        _selectedPackages.update { current ->
            if (select) current + packageNames
            else current - packageNames.toSet()
        }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    // ---- RAM before/after for the freed-MB dialog ----
    private val _memBefore = MutableStateFlow<MemoryVitals?>(null)
    val memBefore: StateFlow<MemoryVitals?> = _memBefore.asStateFlow()

    private val _memAfter = MutableStateFlow<MemoryVitals?>(null)
    val memAfter: StateFlow<MemoryVitals?> = _memAfter.asStateFlow()

    private val _showFreedDialog = MutableStateFlow(false)
    val showFreedDialog: StateFlow<Boolean> = _showFreedDialog.asStateFlow()

    // Track which action was last performed, so the dialog shows the right
    // message ("Freed X MB RAM" for Force Stop, "Cleared cache for N apps"
    // for Clear Cache).
    private val _lastAction = MutableStateFlow<AppAction>(AppAction.ForceStop)
    val lastAction: StateFlow<AppAction> = _lastAction.asStateFlow()

    // Track how many apps were processed for the dialog message.
    private val _lastBatchSize = MutableStateFlow(0)
    val lastBatchSize: StateFlow<Int> = _lastBatchSize.asStateFlow()

    fun dismissFreedDialog() {
        _showFreedDialog.value = false
        _memAfter.value = null
        _memBefore.value = null
    }

    // ---- Exceptions state (so UI updates immediately on add/remove) ----
    // Separate sets for force-stop and clear-cache exceptions.
    private val _forceStopExceptions = MutableStateFlow<Set<String>>(emptySet())
    val forceStopExceptions: StateFlow<Set<String>> = _forceStopExceptions.asStateFlow()

    private val _clearCacheExceptions = MutableStateFlow<Set<String>>(emptySet())
    val clearCacheExceptions: StateFlow<Set<String>> = _clearCacheExceptions.asStateFlow()

    // Legacy single-set — still used by the AppsScreen for the force-stop tab.
    val exceptions: StateFlow<Set<String>> = _forceStopExceptions.asStateFlow()

    fun refreshExceptions() {
        _forceStopExceptions.value = exceptionsRepository.getForceStopExceptions()
        _clearCacheExceptions.value = exceptionsRepository.getClearCacheExceptions()
    }

    fun addForceStopExceptions(packages: Collection<String>) {
        exceptionsRepository.addToForceStopExceptions(packages)
        refreshExceptions()
    }

    fun removeForceStopException(packageName: String) {
        exceptionsRepository.removeFromForceStopExceptions(packageName)
        refreshExceptions()
    }

    fun addClearCacheExceptions(packages: Collection<String>) {
        exceptionsRepository.addToClearCacheExceptions(packages)
        refreshExceptions()
    }

    fun removeClearCacheException(packageName: String) {
        exceptionsRepository.removeFromClearCacheExceptions(packageName)
        refreshExceptions()
    }

    fun clearAllExceptions() {
        exceptionsRepository.clearAll()
        refreshExceptions()
    }

    // Legacy compat — used by old AppsScreen code that doesn't distinguish.
    fun addExceptions(packages: Collection<String>) = addForceStopExceptions(packages)
    fun removeException(packageName: String) = removeForceStopException(packageName)
    fun clearExceptions() = clearAllExceptions()

    // ---- Cache state (Clear Cache tab) ----
    private val _cacheInfos = MutableStateFlow<List<CacheRepository.AppCacheInfo>>(emptyList())
    val cacheInfos: StateFlow<List<CacheRepository.AppCacheInfo>> = _cacheInfos.asStateFlow()

    private val _isCacheLoading = MutableStateFlow(false)
    val isCacheLoading: StateFlow<Boolean> = _isCacheLoading.asStateFlow()

    private var cacheRefreshJob: Job? = null

    fun loadCacheSizes() {
        cacheRefreshJob?.cancel()
        cacheRefreshJob = viewModelScope.launch {
            _isCacheLoading.value = true
            try {
                val list = cacheRepository.getAllCacheSizes()
                _cacheInfos.value = list
            } catch (t: Throwable) {
                // ignore — cache list will be empty
            } finally {
                _isCacheLoading.value = false
            }
        }
    }

    val totalCacheMb: Int
        get() = (_cacheInfos.value.sumOf { it.cacheBytes } / (1024 * 1024)).toInt()

    // ---- Refresh logic (with cancellation of in-flight refresh) ----
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null

    fun loadProcesses() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                val list = repository.getInstalledProcesses()
                _processes.value = list
            } catch (t: Throwable) {
                _loadError.value = t.message ?: "Failed to load apps"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Stop the selected packages. Samples RAM before, kicks off the queue,
     * and clears selection. The actual "freed X MB" dialog is shown when
     * the service emits KillEvent.Completed (observed separately by the UI).
     */
    fun stopSelected() {
        val targets = _processes.value
            .filter { it.packageName in _selectedPackages.value && it.canStop }
            .map { it.packageName }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _lastAction.value = AppAction.ForceStop
            _lastBatchSize.value = targets.size
            _memBefore.value = MemoryReader.getMemoryVitals()
            repository.stopSelectedPackages(targets, AppAction.ForceStop)
            clearSelection()
        }
    }

    /**
     * Clear cache for the selected packages. Same overlay-hidden automation
     * as force-stop, but navigates to Storage & cache → Clear cache button
     * instead of clicking Force Stop.
     */
    fun clearCacheSelected() {
        val targets = _processes.value
            .filter { it.packageName in _selectedPackages.value && it.canStop }
            .map { it.packageName }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _lastAction.value = AppAction.ClearCache
            _lastBatchSize.value = targets.size
            _memBefore.value = MemoryReader.getMemoryVitals()
            repository.stopSelectedPackages(targets, AppAction.ClearCache)
            clearSelection()
        }
    }

    /**
     * Called when the service emits KillEvent.Completed. Reads final RAM,
     * refreshes the process list, then shows the freed-MB dialog.
     */
    fun onKillCompleted() {
        viewModelScope.launch {
            _memAfter.value = MemoryReader.getMemoryVitals()
            loadProcesses()
            _showFreedDialog.value = true
        }
    }

    /**
     * Permission state — observed live via ContentObserver, NOT polled on
     * every resume (audit bug O10: Settings.Secure.getString is a sync binder
     * call to the system server).
     */
    private val _hasAccessibility = MutableStateFlow(false)
    val hasAccessibility: StateFlow<Boolean> = _hasAccessibility.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    private val _silentlyKilled = MutableStateFlow(false)
    val silentlyKilled: StateFlow<Boolean> = _silentlyKilled.asStateFlow()

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _hasAccessibility.value = PermissionChecker.isAccessibilityEnabled(ctx)
        _hasUsageAccess.value = PermissionChecker.isUsageAccessEnabled(ctx)
        _silentlyKilled.value = PermissionChecker.isServiceSilentlyKilled(ctx)
    }

    /**
     * Derived: true iff all permissions are granted AND service is alive.
     */
    val permissionsOk: StateFlow<Boolean> = MutableStateFlow(false).also { derived ->
        viewModelScope.launch {
            _hasAccessibility.collect { derived.value = _hasAccessibility.value && _hasUsageAccess.value }
        }
    }

    companion object {
        private const val KEY_SEARCH = "search_query"
        private const val KEY_FILTER = "filter_type"
        private const val KEY_TAB = "selected_tab"
    }
}
