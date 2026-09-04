package com.appcontroller.android.data

import com.appcontroller.android.model.MemoryVitals
import java.io.BufferedReader
import java.io.FileReader

object MemoryReader {

    fun getMemoryVitals(): MemoryVitals {
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
            e.printStackTrace()
            // Fallback default approximation
            memTotalKb = 8 * 1024 * 1024
            memAvailableKb = 3 * 1024 * 1024
            activeFileKb = 1024 * 1024
        }

        val swapUsedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
        return MemoryVitals(
            memTotalMb = memTotalKb / 1024,
            memAvailableMb = memAvailableKb / 1024,
            activeFileMb = activeFileKb / 1024,
            swapUsedMb = swapUsedKb / 1024,
            swapTotalMb = swapTotalKb / 1024
        )
    }
}
