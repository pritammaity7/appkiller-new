package com.appcontroller.android.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Queries real per-app cache sizes using StorageStatsManager (API 26+).
 *
 * StorageStatsManager.queryStatsForPackage() returns a StorageStats object
 * with getCacheBytes(), getAppBytes(), getDataBytes(). This is the official,
 * stable API that matches what the system Settings > Storage screen displays.
 *
 * Requires PACKAGE_USAGE_STATS permission (which we already request for
 * UsageStatsManager). Without it, cross-app queries throw SecurityException.
 *
 * This replaced the old deprecated PackageManager.getPackageSizeInfo()
 * reflection hacks. StorageStatsManager is the correct API since Android 8.0.
 */
class CacheRepository(context: Context) {

    private val packageManager = context.packageManager
    private val storageStatsManager =
        context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
    private val ownPackage = context.packageName

    data class AppCacheInfo(
        val packageName: String,
        val appName: String,
        val cacheBytes: Long,
        val appBytes: Long,
        val dataBytes: Long
    ) {
        val cacheMb: Int get() = (cacheBytes / (1024 * 1024)).toInt()
        val hasCache: Boolean get() = cacheBytes > 0
    }

    /**
     * Queries cache sizes for all installed packages. Runs on Dispatchers.IO
     * with PARALLEL queries (coroutine per package) for speed.
     *
     * v5.5: parallelized with async + awaitAll — 3-5x faster than sequential
     * for 100+ apps. Uses a bounded coroutine dispatcher to avoid overwhelming
     * the binder.
     *
     * @return list of AppCacheInfo sorted by cache size descending
     */
    suspend fun getAllCacheSizes(): List<AppCacheInfo> = withContext(Dispatchers.IO) {
        if (storageStatsManager == null) return@withContext emptyList()

        val storageUuid = StorageManager.UUID_DEFAULT
        val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { it.packageName != ownPackage }

        // Parallel query — each package gets its own coroutine.
        // Use a limited parallelism dispatcher to avoid overwhelming binder.
        val limitedDispatcher = Dispatchers.IO.limitedParallelism(8)
        val deferredResults = packages.map { pkg ->
            async(limitedDispatcher) {
                try {
                    val stats = storageStatsManager.queryStatsForPackage(
                        storageUuid,
                        pkg.packageName,
                        android.os.Process.myUserHandle()
                    )
                    val label = try {
                        packageManager.getApplicationLabel(pkg.applicationInfo).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }
                    AppCacheInfo(
                        packageName = pkg.packageName,
                        appName = label,
                        cacheBytes = stats.cacheBytes,
                        appBytes = stats.appBytes,
                        dataBytes = stats.dataBytes
                    )
                } catch (e: SecurityException) {
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }
        deferredResults.awaitAll().filterNotNull().sortedByDescending { it.cacheBytes }
    }

    /**
     * Total cache across all apps. Useful for showing "X MB total cache
     * found" at the top of the Clear Cache screen.
     */
    fun totalCacheBytes(infos: List<AppCacheInfo>): Long {
        return infos.sumOf { it.cacheBytes }
    }
}
