package com.appcontroller.android.model

import android.graphics.drawable.Drawable

data class ProcessInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val memoryMb: Int = 0,
    val isSystemApp: Boolean = false,
    val isRunning: Boolean = true,
    val isStopped: Boolean = false,
    val oomAdj: Int? = null,
    val canStop: Boolean = true,
    val stateDetail: String = "Active in background",
    var isSelected: Boolean = false,
    val isException: Boolean = false
)

data class MemoryVitals(
    val memTotalMb: Int,
    val memAvailableMb: Int,
    val activeFileMb: Int,
    val swapUsedMb: Int,
    val swapTotalMb: Int
) {
    val usedMb: Int get() = (memTotalMb - memAvailableMb).coerceAtLeast(0)
    val usedPercentage: Int get() = if (memTotalMb > 0) ((usedMb * 100) / memTotalMb) else 0
}
