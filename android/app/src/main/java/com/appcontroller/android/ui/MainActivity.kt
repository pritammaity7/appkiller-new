package com.appcontroller.android.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.appcontroller.android.data.ExceptionsRepository
import com.appcontroller.android.data.MemoryReader
import com.appcontroller.android.data.ProcessRepository
import com.appcontroller.android.model.MemoryVitals
import com.appcontroller.android.model.ProcessInfo
import com.appcontroller.android.service.AppControllerAccessibilityService
import com.appcontroller.android.service.AppControllerAccessibilityService.StoppingStatus
import com.appcontroller.android.util.PermissionChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: ProcessRepository
    private lateinit var exceptionsRepository: ExceptionsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exceptionsRepository = ExceptionsRepository(this)
        repository = ProcessRepository(this, exceptionsRepository)

        setContent {
            ForceStopTheme {
                ForceStopApp(
                    repository = repository,
                    exceptionsRepository = exceptionsRepository,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
        }
    }
}

// =====================================================================
//  Root composable — gates on permissions, then shows the main UI.
// =====================================================================

@Composable
fun ForceStopApp(
    repository: ProcessRepository,
    exceptionsRepository: ExceptionsRepository,
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccessibility by remember { mutableStateOf(PermissionChecker.isAccessibilityEnabled(context)) }
    var hasUsageAccess by remember { mutableStateOf(PermissionChecker.isUsageAccessEnabled(context)) }

    // Re-check on every resume — so if the user disables a permission in Settings
    // and comes back, we re-block the UI.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionChecker.isAccessibilityEnabled(context)
                hasUsageAccess = PermissionChecker.isUsageAccessEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasAccessibility || !hasUsageAccess) {
        PermissionGateScreen(
            hasAccessibility = hasAccessibility,
            hasUsageAccess = hasUsageAccess,
            onEnableAccessibility = onOpenAccessibility,
            onEnableUsageAccess = onOpenUsageAccess,
            onRecheck = {
                hasAccessibility = PermissionChecker.isAccessibilityEnabled(context)
                hasUsageAccess = PermissionChecker.isUsageAccessEnabled(context)
            }
        )
        return
    }

    MainScaffold(repository, exceptionsRepository, onOpenAccessibility, onOpenUsageAccess)
}

// =====================================================================
//  Permission gate — full-screen, blocks until both permissions granted.
// =====================================================================

@Composable
fun PermissionGateScreen(
    hasAccessibility: Boolean,
    hasUsageAccess: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableUsageAccess: () -> Unit,
    onRecheck: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101417)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF4EDEA3),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Force Stop needs these permissions",
                color = Color(0xFFE0E3E7),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Force Stop cannot kill background apps without these. " +
                        "They are required by Android — not optional.",
                color = Color(0xFFBBABAF),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(28.dp))

            PermissionRow(
                title = "Accessibility Service",
                subtitle = "Used to automate the Force Stop button click",
                granted = hasAccessibility,
                onClick = onEnableAccessibility
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                title = "Usage Access",
                subtitle = "Used to detect which apps are actually running",
                granted = hasUsageAccess,
                onClick = onEnableUsageAccess
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRecheck,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4EDEA3)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I've enabled them — Continue", color = Color(0xFF003824), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: after enabling, come back to Force Stop and tap Continue.",
                color = Color(0xFF86948A),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PermissionRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF181C1F),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFFE0E3E7), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFFBBABAF), fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = Color(0xFF4EDEA3))
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4EDEA3),
                        contentColor = Color(0xFF003824)
                    )
                ) {
                    Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =====================================================================
//  Main scaffold — 3 tabs (Apps / Exceptions / Settings).
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    repository: ProcessRepository,
    exceptionsRepository: ExceptionsRepository,
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Apps", "Exceptions", "Settings")
    val scope = rememberCoroutineScope()

    // Shared process list — reloaded when the user pulls to refresh or a stop completes.
    var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("Running") } // default = only running apps

    val stoppingStatus by AppControllerAccessibilityService.stoppingProgress.collectAsState()
    val isAccessibilityActive by AppControllerAccessibilityService.isServiceActive.collectAsState()

    // RAM before/after for the "freed X MB" dialog.
    var memBefore by remember { mutableStateOf<MemoryVitals?>(null) }
    var memAfter by remember { mutableStateOf<MemoryVitals?>(null) }
    var showFreedDialog by remember { mutableStateOf(false) }

    // Snapshot current memory before the stopping sequence starts.
    LaunchedEffect(stoppingStatus) {
        when (stoppingStatus) {
            is StoppingStatus.Stopping -> {
                if (memBefore == null) {
                    memBefore = MemoryReader.getMemoryVitals()
                }
            }
            is StoppingStatus.Completed -> {
                memAfter = MemoryReader.getMemoryVitals()
                showFreedDialog = true
                AppControllerAccessibilityService.instance?.resetStatus()
                // Refresh the process list so stopped apps disappear.
                processes = repository.getInstalledProcesses()
                memBefore = null
            }
            else -> {}
        }
    }

    // Initial load.
    LaunchedEffect(Unit) {
        processes = repository.getInstalledProcesses()
        isLoading = false
    }

    fun refresh() {
        scope.launch {
            processes = repository.getInstalledProcesses()
        }
    }

    val runningCount = processes.count { it.isRunning && it.canStop }
    val userCount = processes.count { !it.isSystemApp && it.isRunning }
    val systemCount = processes.count { it.isSystemApp && it.isRunning }

    val filteredApps = remember(processes, searchQuery, filterType) {
        processes.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterType) {
                "User" -> !app.isSystemApp && app.isRunning
                "System" -> app.isSystemApp && app.isRunning
                "Running" -> app.isRunning
                else -> true // "All"
            }
            matchesSearch && matchesFilter
        }
    }

    val selectedCount = processes.count { it.isSelected && it.canStop && !it.isStopped }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Force Stop", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        if (isAccessibilityActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color(0xFF4EDEA3),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF4EDEA3))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101417),
                    titleContentColor = Color(0xFFE0E3E7)
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF181C1F)) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Apps, contentDescription = title)
                                1 -> Icon(Icons.Default.Shield, contentDescription = title)
                                else -> Icon(Icons.Default.Settings, contentDescription = title)
                            }
                        },
                        label = { Text(title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF003824),
                            selectedTextColor = Color(0xFF4EDEA3),
                            indicatorColor = Color(0xFF4EDEA3),
                            unselectedIconColor = Color(0xFFBBABAF),
                            unselectedTextColor = Color(0xFFBBABAF)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF101417)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> AppsScreen(
                    apps = filteredApps,
                    totalCount = processes.size,
                    runningCount = runningCount,
                    userCount = userCount,
                    systemCount = systemCount,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filterType = filterType,
                    onFilterChange = { filterType = it },
                    selectedCount = selectedCount,
                    isAccessibilityActive = isAccessibilityActive,
                    onToggleApp = { pkg ->
                        processes = processes.map {
                            if (it.packageName == pkg) it.copy(isSelected = !it.isSelected) else it
                        }
                    },
                    onSelectAll = { selectAll ->
                        val targetIds = filteredApps.map { it.packageName }.toSet()
                        processes = processes.map {
                            if (targetIds.contains(it.packageName) && it.canStop) it.copy(isSelected = selectAll) else it
                        }
                    },
                    onStopSelected = {
                        val targets = processes.filter { it.isSelected && it.canStop }.map { it.packageName }
                        if (targets.isNotEmpty()) {
                            scope.launch {
                                repository.stopSelectedPackages(targets)
                                // Optimistically clear selection; the real "stopped" state
                                // will be applied when the queue completes and we refresh.
                                processes = processes.map { it.copy(isSelected = false) }
                            }
                        }
                    }
                )
                1 -> ExceptionsScreen(
                    exceptionsRepository = exceptionsRepository,
                    allApps = processes,
                    onRefreshApps = { refresh() }
                )
                2 -> SettingsScreen(
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenUsageAccess = onOpenUsageAccess,
                    onResetExceptions = {
                        exceptionsRepository.clear()
                        refresh()
                    }
                )
            }
        }
    }

    // RAM-freed dialog after a stop completes.
    if (showFreedDialog && memAfter != null) {
        val before = memBefore
        val freed = if (before != null) {
            (memAfter!!.memAvailableMb - before.memAvailableMb).coerceAtLeast(0)
        } else 0
        AlertDialog(
            onDismissRequest = {
                showFreedDialog = false
                memAfter = null
            },
            confirmButton = {
                TextButton(onClick = {
                    showFreedDialog = false
                    memAfter = null
                }) { Text("OK", color = Color(0xFF4EDEA3)) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4EDEA3))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop complete", color = Color(0xFFE0E3E7))
                }
            },
            text = {
                Column {
                    Text(
                        if (freed > 0) "Freed ~${freed} MB of RAM" else "Stop sequence finished",
                        color = Color(0xFF4EDEA3),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (before != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Available: ${before.memAvailableMb} MB → ${memAfter!!.memAvailableMb} MB",
                            color = Color(0xFFBBABAF),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            containerColor = Color(0xFF181C1F)
        )
    }
}

// =====================================================================
//  Apps screen — filter chips, running counter, select-all, stop button.
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    apps: List<ProcessInfo>,
    totalCount: Int,
    runningCount: Int,
    userCount: Int,
    systemCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterType: String,
    onFilterChange: (String) -> Unit,
    selectedCount: Int,
    isAccessibilityActive: Boolean,
    onToggleApp: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onStopSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Top stat strip — running / user / system counts.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip("Running", runningCount.toString(), Modifier.weight(1f))
            StatChip("User", userCount.toString(), Modifier.weight(1f))
            StatChip("System", systemCount.toString(), Modifier.weight(1f))
        }

        // Search bar.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search apps…", fontSize = 13.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFBBABAF)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFBBABAF))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF181C1F),
                unfocusedContainerColor = Color(0xFF181C1F),
                focusedTextColor = Color(0xFFE0E3E7),
                unfocusedTextColor = Color(0xFFE0E3E7),
                focusedBorderColor = Color(0xFF4EDEA3),
                unfocusedBorderColor = Color(0xFF262A2E)
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Search
            )
        )

        // Filter chips — All / User / System / Running.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Running", "User", "System", "All").forEach { chip ->
                FilterChip(
                    selected = filterType == chip,
                    onClick = { onFilterChange(chip) },
                    label = { Text(chip, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4EDEA3),
                        selectedLabelColor = Color(0xFF003824),
                        containerColor = Color(0xFF262A2E),
                        labelColor = Color(0xFFBBABAF)
                    )
                )
            }
        }

        // Action toolbar — Select all + Stop button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = apps.isNotEmpty() && apps.all { it.isSelected },
                    onCheckedChange = { onSelectAll(it) },
                    enabled = apps.any { it.canStop }
                )
                Text(
                    "Select all (${apps.size})",
                    fontSize = 12.sp,
                    color = Color(0xFFBBABAF)
                )
            }
            Text(
                "$selectedCount selected",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4EDEA3)
            )
        }

        Button(
            onClick = onStopSelected,
            enabled = selectedCount > 0 && isAccessibilityActive,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4EDEA3),
                disabledContainerColor = Color(0xFF262A2E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = if (selectedCount > 0) Color(0xFF003824) else Color(0xFF86948A))
            Spacer(Modifier.width(6.dp))
            Text(
                "Force Stop ($selectedCount)",
                color = if (selectedCount > 0) Color(0xFF003824) else Color(0xFF86948A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isAccessibilityActive) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2A1F1A),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "Accessibility service is off. Re-enable it from Settings to stop apps.",
                    color = Color(0xFFE0A06A),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Process list.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        app.isSelected -> Color(0xFF262A2E)
                        app.isException -> Color(0xFF1F2A1F)
                        else -> Color(0xFF181C1F)
                    },
                    onClick = { if (app.canStop) onToggleApp(app.packageName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = app.isSelected,
                            onCheckedChange = { if (app.canStop) onToggleApp(app.packageName) },
                            enabled = app.canStop && !app.isStopped
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.appName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = when {
                                    app.isStopped -> Color(0xFF86948A)
                                    app.isException -> Color(0xFF4EDEA3)
                                    else -> Color(0xFFE0E3E7)
                                }
                            )
                            Text(
                                app.packageName,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFBBABAF)
                            )
                            Text(
                                app.stateDetail,
                                fontSize = 10.sp,
                                color = when {
                                    app.isException -> Color(0xFF4EDEA3)
                                    !app.isRunning -> Color(0xFF86948A)
                                    else -> Color(0xFF5A6064)
                                }
                            )
                        }
                        Text(
                            when {
                                app.isStopped -> "Stopped"
                                app.isRunning -> "${app.memoryMb} MB"
                                else -> "Idle"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (app.isStopped || !app.isRunning) Color(0xFF86948A) else Color(0xFF4EDEA3)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF181C1F),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                color = Color(0xFF4EDEA3),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(label, color = Color(0xFFBBABAF), fontSize = 10.sp)
        }
    }
}

// =====================================================================
//  Exceptions screen — list with search + "+" FAB + bulk add dialog.
// =====================================================================

@Composable
fun ExceptionsScreen(
    exceptionsRepository: ExceptionsRepository,
    allApps: List<ProcessInfo>,
    onRefreshApps: () -> Unit
) {
    var exceptionList by remember { mutableStateOf(exceptionsRepository.getAll().toList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredExceptions = remember(exceptionList, searchQuery) {
        if (searchQuery.isBlank()) exceptionList
        else exceptionList.filter { pkg ->
            val app = allApps.find { it.packageName == pkg }
            val label = app?.appName ?: pkg
            label.contains(searchQuery, ignoreCase = true) || pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Exceptions",
                color = Color(0xFFE0E3E7),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Apps on this list are never force-stopped, even if selected.",
                color = Color(0xFFBBABAF),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search exceptions…", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFBBABAF)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFBBABAF))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF181C1F),
                    unfocusedContainerColor = Color(0xFF181C1F),
                    focusedTextColor = Color(0xFFE0E3E7),
                    unfocusedTextColor = Color(0xFFE0E3E7),
                    focusedBorderColor = Color(0xFF4EDEA3),
                    unfocusedBorderColor = Color(0xFF262A2E)
                )
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "${filteredExceptions.size} exceptions",
                color = Color(0xFF4EDEA3),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))

            if (filteredExceptions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF262A2E), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (exceptionList.isEmpty()) "No exceptions yet.\nTap + to add some."
                            else "No matches.",
                            color = Color(0xFF86948A),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredExceptions, key = { it }) { pkg ->
                        val app = allApps.find { it.packageName == pkg }
                        val label = app?.appName ?: pkg
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1F2A1F),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4EDEA3))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = Color(0xFFE0E3E7), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(pkg, color = Color(0xFFBBABAF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                IconButton(onClick = {
                                    exceptionsRepository.remove(pkg)
                                    exceptionList = exceptionsRepository.getAll().toList()
                                    onRefreshApps()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFE0A06A))
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB "+"
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = Color(0xFF4EDEA3),
            contentColor = Color(0xFF003824),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add exception")
        }
    }

    if (showAddDialog) {
        AddExceptionsDialog(
            allApps = allApps,
            currentExceptions = exceptionList.toSet(),
            onAdd = { packages ->
                exceptionsRepository.add(packages)
                exceptionList = exceptionsRepository.getAll().toList()
                onRefreshApps()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddExceptionsDialog(
    allApps: List<ProcessInfo>,
    currentExceptions: Set<String>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    val candidates = remember(allApps, search) {
        allApps
            .filter { it.packageName !in currentExceptions }
            .filter {
                search.isBlank() ||
                        it.appName.contains(search, ignoreCase = true) ||
                        it.packageName.contains(search, ignoreCase = true)
            }
            .sortedBy { it.appName.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onAdd(selected.toList()) },
                enabled = selected.isNotEmpty()
            ) {
                Text(
                    if (selected.isEmpty()) "Add" else "Add ${selected.size}",
                    color = if (selected.isEmpty()) Color(0xFF86948A) else Color(0xFF4EDEA3),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFFBBABAF)) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4EDEA3))
                Spacer(Modifier.width(8.dp))
                Text("Add to exceptions", color = Color(0xFFE0E3E7))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search apps to add…", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFBBABAF)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF181C1F),
                        unfocusedContainerColor = Color(0xFF181C1F),
                        focusedTextColor = Color(0xFFE0E3E7),
                        unfocusedTextColor = Color(0xFFE0E3E7),
                        focusedBorderColor = Color(0xFF4EDEA3),
                        unfocusedBorderColor = Color(0xFF262A2E)
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        if (selected.size == candidates.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(candidates.map { it.packageName })
                        }
                    }) {
                        Text(
                            if (selected.size == candidates.size && candidates.isNotEmpty()) "Deselect all" else "Select all",
                            color = Color(0xFF4EDEA3),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "${selected.size} / ${candidates.size}",
                        color = Color(0xFFBBABAF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))

                // Candidate list — constrained height so the dialog stays scrollable.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(candidates, key = { it.packageName }) { app ->
                        val isSel = selected.contains(app.packageName)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFF262A2E) else Color(0xFF181C1F),
                            onClick = {
                                if (isSel) selected.remove(app.packageName)
                                else selected.add(app.packageName)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSel,
                                    onCheckedChange = {
                                        if (it) selected.add(app.packageName)
                                        else selected.remove(app.packageName)
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.appName, color = Color(0xFFE0E3E7), fontSize = 13.sp)
                                    Text(app.packageName, color = Color(0xFFBBABAF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF181C1F)
    )
}

// =====================================================================
//  Settings screen — Accessibility + Usage Access buttons + reset.
// =====================================================================

@Composable
fun SettingsScreen(
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onResetExceptions: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE0E3E7))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Accessibility, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Open Accessibility Settings")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.BarChart, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Open Usage Access Settings")
        }

        Spacer(Modifier.height(32.dp))
        Text("Exceptions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE0E3E7))
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                onResetExceptions()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE0A06A))
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Clear all exceptions")
        }
    }
}

// =====================================================================
//  Theme.
// =====================================================================

@Composable
fun ForceStopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4EDEA3),
            onPrimary = Color(0xFF003824),
            surface = Color(0xFF101417),
            onSurface = Color(0xFFE0E3E7),
            background = Color(0xFF101417)
        ),
        content = content
    )
}
