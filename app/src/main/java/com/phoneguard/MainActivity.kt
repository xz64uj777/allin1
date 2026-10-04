package com.phoneguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneGuardApp(this) }
    }
}

data class DeviceSnapshot(
    val model: String,
    val android: String,
    val battery: Int,
    val storageFree: Long,
    val storageTotal: Long,
    val memoryAvailable: Long,
    val memoryTotal: Long
)
data class ScanUiState(
    val running: Boolean = false,
    val stage: String = "Ready",
    val report: ScanReport? = null,
    val files: List<StorageItem> = emptyList(),
    val largeFiles: List<StorageItem> = emptyList(),
    val suspiciousFiles: List<StorageItem> = emptyList(),
    val duplicateGroups: List<List<StorageItem>> = emptyList(),
    val error: String? = null,
    val completedAt: Long? = null
)

private fun snapshot(context: Context): DeviceSnapshot {
    val battery = context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
    val stat = StatFs(Environment.getDataDirectory().path)
    val memory = android.app.ActivityManager.MemoryInfo()
    context.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(memory)
    return DeviceSnapshot(
        model = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.US) } + " " + Build.MODEL,
        android = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
        battery = battery,
        storageFree = stat.availableBytes,
        storageTotal = stat.totalBytes,
        memoryAvailable = memory.availMem,
        memoryTotal = memory.totalMem
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneGuardApp(activity: Activity) {
    var selected by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf(ScanUiState()) }
    val device = remember { snapshot(activity) }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun runFullScan() {
        if (state.running) return
        state = state.copy(running = true, stage = "Inspecting device", error = null)
        scope.launch {
            try {
                val report = withContext(Dispatchers.Default) { ScanEngine.scan(activity) }
                state = state.copy(stage = "Scanning shared storage", report = report)
                val files = withContext(Dispatchers.IO) { FileScanner.recentMedia(activity, 800) }
                val large = FileScanner.large(files)
                val suspicious = FileScanner.suspiciousExtension(files)
                val duplicateCandidates = files.groupBy { it.bytes }
                    .filter { it.key > 0 && it.value.size > 1 }
                    .values.flatten().take(250)
                val hashed = withContext(Dispatchers.IO) {
                    FileScanner.withHashes(activity, duplicateCandidates, 200)
                }
                val duplicates = FileScanner.duplicateGroups(hashed)
                state = state.copy(
                    running = false,
                    stage = "Complete",
                    files = files,
                    largeFiles = large,
                    suspiciousFiles = suspicious,
                    duplicateGroups = duplicates,
                    completedAt = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                state = state.copy(
                    running = false,
                    stage = "Scan stopped",
                    error = t.message ?: "Unexpected scan error"
                )
            }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PhoneGuard", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = {
                            openSettings(activity, Settings.ACTION_SECURITY_SETTINGS)
                        }) { Icon(Icons.Default.Settings, "Settings") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Security", "Apps", "Storage").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = selected == i,
                            onClick = { selected = i },
                            icon = {
                                Icon(
                                    when (i) {
                                        0 -> Icons.Default.Home
                                        1 -> Icons.Default.Security
                                        2 -> Icons.Default.Apps
                                        else -> Icons.Default.Folder
                                    }, label
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                0 -> HomeScreen(device, state, padding, ::runFullScan)
                1 -> SecurityScreen(state, padding, ::runFullScan)
                2 -> AppsScreen(state, padding)
                else -> StorageScreen(state, padding, permissionLauncher)
            }
        }
    }
}

@Composable
private fun HomeScreen(device: DeviceSnapshot, state: ScanUiState, padding: PaddingValues, runScan: () -> Unit) {
    val report = state.report
    val score = report?.score ?: 0
    LazyColumn(
        Modifier.padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Your phone command center", style = MaterialTheme.typography.headlineSmall)
            Text("Local diagnostics. Personal files are not uploaded by default.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (report == null) "Phone Health" else "Security & Health Score", style = MaterialTheme.typography.titleMedium)
                    Text(if (report == null) "—" else "${score} / 100", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(if (report == null) "Run a full check to calculate a live score." else "Heuristic risk score; it is not a malware verdict.")
                    if (report != null) {
                        LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item { DeviceCard(device) }
        if (state.running) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scan in progress", fontWeight = FontWeight.Bold)
                        Text(state.stage)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        report?.let { r ->
            item { Text("Latest findings", style = MaterialTheme.typography.titleLarge) }
            items(r.findings.take(5)) { FindingRow(it) }
            item {
                Text(
                    "Files checked: ${state.files.size} • large: ${state.largeFiles.size} • suspicious extensions: ${state.suspiciousFiles.size} • duplicate groups: ${state.duplicateGroups.size}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Button(onClick = runScan, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Shield, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.running) "Scanning…" else "Run full device check")
            }
        }
    }
}

@Composable
private fun SecurityScreen(state: ScanUiState, padding: PaddingValues, runScan: () -> Unit) {
    val context = LocalContext.current
    val report = state.report
    LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Security", style = MaterialTheme.typography.headlineSmall) }
        item { Text("PhoneGuard uses Android-exposed signals and clearly marks coverage limits.") }
        if (report == null) {
            item { Button(onClick = runScan, enabled = !state.running, modifier = Modifier.fillMaxWidth()) { Text("Run security scan") } }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Live score: ${report.score}/100", fontWeight = FontWeight.Bold)
                        Text("${report.apps.size} apps with sensitive capabilities detected.")
                        Text("VPN: ${if (report.vpnActive) "active" else "not detected"} • admins: ${report.activeDeviceAdmins}")
                    }
                }
            }
            items(report.findings) { FindingRow(it) }
        }
        item { OutlinedButton(onClick = { openSettings(context, Settings.ACTION_SECURITY_SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("Open Android security settings") } }
        item { OutlinedButton(onClick = { openSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:" + context.packageName) }, modifier = Modifier.fillMaxWidth()) { Text("Review overlay access") } }
        item { OutlinedButton(onClick = { openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("Review accessibility services") } }
    }
}

@Composable
private fun AppsScreen(state: ScanUiState, padding: PaddingValues) {
    val apps = state.report?.apps.orEmpty()
    LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Installed apps", style = MaterialTheme.typography.headlineSmall) }
        item { Text(if (state.report == null) "Run a security scan to analyze installed apps." else "${apps.size} apps request at least one sensitive capability.") }
        if (apps.isEmpty() && state.report != null) {
            item { Text("No apps matched PhoneGuard's sensitive-permission heuristics.") }
        }
        items(apps.take(200)) { app ->
            val context = LocalContext.current
            var expanded by remember(app.packageName) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.Bold)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(app.risk, fontWeight = FontWeight.Bold)
                    }
                    Text(app.reasons.joinToString(" "))
                    Text("Sensitive permissions: ${app.permissions.size} requested • ${app.grantedPermissions.size} granted")
                    if (expanded) {
                        HorizontalDivider()
                        Text("Evidence", fontWeight = FontWeight.Bold)
                        Text("Granted: " + (app.grantedPermissions.joinToString(", ").ifBlank { "None" }))
                        Text("Requested but not granted: " + (app.permissions.filter { it !in app.grantedPermissions }.joinToString(", ").ifBlank { "None" }))
                        Text("Installer: " + (app.installer ?: "Not reported"))
                        Text("Installed: " + formatDate(app.firstInstalledAt))
                        Text("Updated: " + formatDate(app.lastUpdatedAt))
                        Text("Why review it: PhoneGuard uses capability-based heuristics. This is not proof that the app is malicious.")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                            Text(if (expanded) "Hide evidence" else "Evidence")
                        }
                        Button(onClick = { RemediationEngine.openApp(context, app.packageName) }, modifier = Modifier.weight(1f)) {
                            Text("Fix / Review")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageScreen(
    state: ScanUiState,
    padding: PaddingValues,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current
    val device = remember { snapshot(context) }
    val used = (device.storageTotal - device.storageFree).coerceAtLeast(0)
    val ratio = if (device.storageTotal > 0) used.toFloat() / device.storageTotal else 0f
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    fun requestDelete() {
        if (Build.VERSION.SDK_INT < 30) return
        val candidates = state.duplicateGroups.flatten().drop(1).take(50).mapNotNull {
            runCatching { Uri.parse(it.uri) }.getOrNull()
        }
        if (candidates.isEmpty()) return
        val pending = android.provider.MediaStore.createDeleteRequest(context.contentResolver, candidates)
        deleteLauncher.launch(
            androidx.activity.result.IntentSenderRequest.Builder(pending.intentSender).build()
        )
    }

    LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Storage", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Internal storage")
                    Text("${formatBytes(used)} used of ${formatBytes(device.storageTotal)}")
                    LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${formatBytes(device.storageFree)} available")
                }
            }
        }
        item {
            Text("Shared-storage scan results", style = MaterialTheme.typography.titleMedium)
            Text("${state.files.size} sampled • ${state.largeFiles.size} large • ${state.suspiciousFiles.size} suspicious extensions • ${state.duplicateGroups.size} duplicate groups")
        }
        item {
            OutlinedButton(
                onClick = {
                    val permissions = if (Build.VERSION.SDK_INT >= 33) {
                        arrayOf(
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VIDEO,
                            android.Manifest.permission.READ_MEDIA_AUDIO
                        )
                    } else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissionLauncher.launch(permissions)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Grant media access for a deeper shared-storage scan") }
        }
        item {
            OutlinedButton(onClick = { openSettings(context, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Android storage settings")
            }
        }
        if (state.duplicateGroups.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
            item {
                Button(onClick = { requestDelete() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Review duplicate files for deletion")
                }
            }
            item {
                Text(
                    "Deletion is never silent: Android shows a system confirmation dialog before media files are permanently deleted.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item { Text("Protected/system and private app data are not silently scanned or deleted.") }
    }
}

@Composable
private fun DeviceCard(device: DeviceSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(device.model, fontWeight = FontWeight.Bold)
            Text(device.android)
            Text("Battery: ${device.battery}%")
            Text("Free storage: ${formatBytes(device.storageFree)}")
            Text("Memory available: ${formatBytes(device.memoryAvailable)} / ${formatBytes(device.memoryTotal)}")
        }
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val icon = when (finding.severity) {
        "WARN", "REVIEW" -> Icons.Default.Warning
        "COVERAGE" -> Icons.Default.Info
        else -> Icons.Default.CheckCircle
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null)
                    Column {
                        Text(finding.title, fontWeight = FontWeight.Bold)
                        Text(finding.severity, style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "Details")
                }
            }
            Text(finding.detail)
            if (expanded) {
                HorizontalDivider()
                Text("What this means", fontWeight = FontWeight.Bold)
                Text(finding.explanation)
                Text(
                    when (finding.fixMode) {
                        FixMode.AUTOMATIC -> "Automatic fix: PhoneGuard can perform this safely with your confirmation."
                        FixMode.GUIDED -> "Guided fix: Android requires you to make the security-sensitive change in Settings."
                        FixMode.INFORMATIONAL -> "No change is needed; this item explains an Android protection or coverage limit."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { message = RemediationEngine.fix(context, finding.fixAction ?: "") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(finding.fixLabel)
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun openSettings(context: Context, action: String, data: String? = null) {
    runCatching {
        context.startActivity(Intent(action).apply {
            if (data != null) this.data = Uri.parse(data)
        })
    }
}

private fun formatDate(time: Long): String = if (time <= 0L) "Not reported" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
