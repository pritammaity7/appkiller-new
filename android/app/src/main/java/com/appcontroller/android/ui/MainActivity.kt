package com.appcontroller.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appcontroller.android.data.MemoryReader
import com.appcontroller.android.data.ProcessRepository
import com.appcontroller.android.model.MemoryVitals
import com.appcontroller.android.model.ProcessInfo
import com.appcontroller.android.service.AppControllerAccessibilityService
import com.appcontroller.android.shizuku.ShizukuController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: ProcessRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ProcessRepository(this)

        setContent {
            AppControllerTheme {
                MainScreen(
                    repository = repository,
                    onRequestAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRequestUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onRequestShizuku = {
                        ShizukuController.requestShizukuPermission { }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: ProcessRepository,
    onRequestAccessibility: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestShizuku: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Apps", "Exceptions", "Safety", "Settings")
    val scope = rememberCoroutineScope()

    var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var memoryVitals by remember { mutableStateOf<MemoryVitals?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }

    val isAccessibilityActive by AppControllerAccessibilityService.isServiceActive.collectAsState()
    val stoppingStatus by AppControllerAccessibilityService.stoppingProgress.collectAsState()
    var isShizukuReady by remember { mutableStateOf(ShizukuController.hasShizukuPermission()) }

    LaunchedEffect(Unit) {
        memoryVitals = MemoryReader.getMemoryVitals()
        processes = repository.getInstalledProcesses()
        isShizukuReady = ShizukuController.hasShizukuPermission()
        isLoading = false
    }

    val filteredApps = remember(processes, searchQuery, filterType) {
        processes.filter { app ->
            val matchesSearch = app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterType) {
                "User" -> !app.isSystemApp
                "System" -> app.isSystemApp
                else -> true
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
                        Text(
                            "App Controller",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isShizukuReady) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF262A2E)
                        ) {
                            Text(
                                text = if (isShizukuReady) "SHIZUKU" else "A11Y",
                                color = if (isShizukuReady) Color(0xFF4EDEA3) else Color(0xFFBBABAF),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
                                2 -> Icon(Icons.Default.VerifiedUser, contentDescription = title)
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
                0 -> AppsView(
                    apps = filteredApps,
                    memoryVitals = memoryVitals,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filterType = filterType,
                    onFilterChange = { filterType = it },
                    selectedCount = selectedCount,
                    isAccessibilityActive = isAccessibilityActive,
                    isShizukuReady = isShizukuReady,
                    onRequestAccessibility = onRequestAccessibility,
                    onRequestShizuku = onRequestShizuku,
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
                        scope.launch {
                            repository.stopSelectedPackages(targets, useShizukuIfAvailable = true)
                            // Update local states if Shizuku stopped immediately
                            if (isShizukuReady) {
                                processes = processes.map {
                                    if (targets.contains(it.packageName)) it.copy(isStopped = true, isSelected = false) else it
                                }
                                memoryVitals = MemoryReader.getMemoryVitals()
                            }
                        }
                    }
                )
                1 -> ExceptionsView()
                2 -> SafetyView()
                3 -> SettingsView(onRequestAccessibility, onRequestUsageAccess, onRequestShizuku)
            }
        }
    }
}

@Composable
fun AppsView(
    apps: List<ProcessInfo>,
    memoryVitals: MemoryVitals?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterType: String,
    onFilterChange: (String) -> Unit,
    selectedCount: Int,
    isAccessibilityActive: Boolean,
    isShizukuReady: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestShizuku: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onStopSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Privileges Banner if not enabled
        if (!isAccessibilityActive && !isShizukuReady) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1C2023),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFF4EDEA3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Setup Accessibility or Shizuku",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E3E7)
                        )
                        Text(
                            "Required to automate force-stop",
                            fontSize = 11.sp,
                            color = Color(0xFFBBABAF)
                        )
                    }
                    Button(
                        onClick = onRequestAccessibility,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4EDEA3))
                    ) {
                        Text("Enable", color = Color(0xFF003824), fontSize = 11.sp)
                    }
                }
            }
        }

        // Memory Vitals Card
        memoryVitals?.let { vitals ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF181C1F),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "/proc/meminfo Vitals",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF4EDEA3),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${vitals.memAvailableMb} MB Available",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE0E3E7)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = vitals.usedPercentage / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(0xFF4EDEA3),
                        trackColor = Color(0xFF262A2E)
                    )
                }
            }
        }

        // Search & Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search process or package...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C2023),
                    unfocusedContainerColor = Color(0xFF181C1F),
                    focusedTextColor = Color(0xFFE0E3E7),
                    unfocusedTextColor = Color(0xFFE0E3E7)
                )
            )
        }

        // Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${apps.size} packages (${selectedCount} selected)",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFBBABAF)
            )
            Button(
                onClick = onStopSelected,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4EDEA3),
                    disabledContainerColor = Color(0xFF262A2E)
                )
            ) {
                Text(
                    "Stop ($selectedCount)",
                    color = if (selectedCount > 0) Color(0xFF003824) else Color(0xFF86948A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Process List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (app.isSelected) Color(0xFF262A2E) else Color(0xFF181C1F),
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
                                color = if (app.isStopped) Color(0xFF86948A) else Color(0xFFE0E3E7)
                            )
                            Text(
                                app.packageName,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFBBABAF)
                            )
                        }
                        Text(
                            if (app.isStopped) "Stopped" else "${app.memoryMb} MB",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (app.isStopped) Color(0xFF86948A) else Color(0xFF4EDEA3)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExceptionsView() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Protected Guardrails", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE0E3E7))
        Spacer(Modifier.height(8.dp))
        Text(
            "System UI, active IME, default home launcher, and App Controller are protected by kernel safety guardrails.",
            fontSize = 12.sp,
            color = Color(0xFFBBABAF)
        )
    }
}

@Composable
fun SafetyView() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Accessibility Disclosure", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE0E3E7))
        Spacer(Modifier.height(8.dp))
        Text(
            "App Controller uses AccessibilityService API solely to automate navigation to App Settings and click Force Stop. Zero user data is collected or transmitted.",
            fontSize = 12.sp,
            color = Color(0xFFBBABAF)
        )
    }
}

@Composable
fun SettingsView(
    onRequestAccessibility: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestShizuku: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Permissions & Privileges", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE0E3E7))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequestAccessibility, modifier = Modifier.fillMaxWidth()) {
            Text("Open Accessibility Settings")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRequestUsageAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Open Usage Access Settings")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) {
            Text("Request Shizuku Privileged Access")
        }
    }
}

@Composable
fun AppControllerTheme(content: @Composable () -> Unit) {
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
