package com.appcontroller.android.data

import com.appcontroller.android.model.MemoryVitals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader

object MemoryReader {

    /**
     * Reads /proc/meminfo on Dispatchers.IO. /proc/meminfo is world-readable
     * and works on all Android versions. This is system-wide RAM only — per-app
     * memory is impossible without root (getProcessMemoryInfo restricted,
     * /proc/<pid>/status SELinux-blocked since Android 8.0).
     *
     * Returns null if /proc/meminfo cannot be read. Callers should handle null
     * by skipping the "freed X MB" dialog instead of showing meaningless numbers.
     */
    suspend fun getMemoryVitals(): MemoryVitals? = withContext(Dispatchers.IO) {
        var memTotalKb = 0
        var memAvailableKb = 0
        var activeFileKb = 0
        var swapTotalKb = 0
        var swapFreeKb = 0

        try {
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val key = parts[0]
                        val value = parts[1].toIntOrNull() ?: 0
                        when {
                            key.startsWith("MemTotal:") -> memTotalKb = value
                            key.startsWith("MemAvailable:") -> memAvailableKb = value
                            key.startsWith("Active(file):") -> activeFileKb = value
                            key.startsWith("SwapTotal:") -> swapTotalKb = value
                            key.startsWith("SwapFree:") -> swapFreeKb = value
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            // Don't fabricate numbers — return null so the UI skips the dialog.
            return@withContext null
        }

        // If we couldn't parse MemTotal, the read was effectively useless.
        if (memTotalKb == 0) return@withContext null

        val swapUsedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
        MemoryVitals(
            memTotalMb = memTotalKb / 1024,
            memAvailableMb = memAvailableKb / 1024,
            activeFileMb = activeFileKb / 1024,
            swapUsedMb = swapUsedKb / 1024,
            swapTotalMb = swapTotalKb / 1024
        )
    }
}
