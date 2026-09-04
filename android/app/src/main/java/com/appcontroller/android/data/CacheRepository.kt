package com.appcontroller.android.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.storage.StorageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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
     * because queryStatsForPackage is a binder call per package.
     *
     * For 100+ apps this takes ~2-5 seconds. The UI should show a loading
     * indicator while this runs.
     *
     * @return list of AppCacheInfo sorted by cache size descending
     */
    suspend fun getAllCacheSizes(): List<AppCacheInfo> = withContext(Dispatchers.IO) {
        if (storageStatsManager == null) return@withContext emptyList()

        val storageUuid = StorageManager.UUID_DEFAULT
        val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        val result = mutableListOf<AppCacheInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val packageName = pkg.packageName

            // Skip our own package — no point showing our own cache.
            if (packageName == ownPackage) continue

            try {
                val stats = storageStatsManager.queryStatsForPackage(
                    storageUuid,
                    packageName,
                    android.os.Process.myUserHandle()
                )
                val label = try {
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
                result.add(
                    AppCacheInfo(
                        packageName = packageName,
                        appName = label,
                        cacheBytes = stats.cacheBytes,
                        appBytes = stats.appBytes,
                        dataBytes = stats.dataBytes
                    )
                )
            } catch (e: SecurityException) {
                // PACKAGE_USAGE_STATS not granted — can't query this package.
                // Skip silently; the UI will show only packages we can read.
            } catch (e: Exception) {
                // PackageManager.NameNotFoundException or other error — skip.
            }
        }

        // Sort by cache size descending — apps with most cache first.
        result.sortedByDescending { it.cacheBytes }
    }

    /**
     * Total cache across all apps. Useful for showing "X MB total cache
     * found" at the top of the Clear Cache screen.
     */
    fun totalCacheBytes(infos: List<AppCacheInfo>): Long {
        return infos.sumOf { it.cacheBytes }
    }
}
