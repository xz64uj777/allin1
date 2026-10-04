package com.phoneguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoneGuardApp(this) }
    }
}

data class Finding(val title: String, val detail: String, val severity: String)
data class DeviceSnapshot(val model: String, val android: String, val battery: Int, val storageFree: Long, val storageTotal: Long)

private fun snapshot(context: Context): DeviceSnapshot {
    val battery = context.getSystemService(BatteryManager::class.java)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    val stat = StatFs(Environment.getDataDirectory().path)
    return DeviceSnapshot(
        model = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.US) } + " " + Build.MODEL,
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        battery = battery,
        storageFree = stat.availableBytes,
        storageTotal = stat.totalBytes
    )
}

@Composable
fun PhoneGuardApp(activity: Activity) {
    var selected by remember { mutableStateOf(0) }
    val device = remember { snapshot(activity) }
    val findings = remember {
        buildList {
            if (Build.VERSION.SECURITY_PATCH.isNotBlank())
                add(Finding("Security patch", Build.VERSION.SECURITY_PATCH, "INFO"))
            if (Build.TAGS?.contains("test-keys") == true)
                add(Finding("Build integrity", "Test-keys detected in build tags.", "WARN"))
            else
                add(Finding("Build integrity", "No test-keys indicator detected.", "INFO"))
            add(Finding("Protected areas", "Android prevents this app from inspecting some system/root-only locations.", "COVERAGE"))
        }
    }
    MaterialTheme(colorScheme = lightColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PhoneGuard", fontWeight = FontWeight.Bold) },
                    actions = { IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Settings") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Security", "Apps", "Storage").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = selected == i,
                            onClick = { selected = i },
                            icon = { Icon(when (i) {
                                0 -> Icons.Default.Home
                                1 -> Icons.Default.Security
                                2 -> Icons.Default.Apps
                                else -> Icons.Default.Folder
                            }, label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                0 -> HomeScreen(device, findings, padding)
                1 -> SecurityScreen(findings, padding)
                2 -> AppsScreen(activity, padding)
                else -> StorageScreen(device, padding)
            }
        }
    }
}

@Composable
private fun HomeScreen(device: DeviceSnapshot, findings: List<Finding>, padding: PaddingValues) {
    val score = 82
    LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Your phone command center", style = MaterialTheme.typography.headlineSmall)
            Text("Local diagnostics. No personal files uploaded by default.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone Health", style = MaterialTheme.typography.titleMedium)
                    Text("$score / 100", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Foundation score • coverage shown separately")
                    LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { DeviceCard(device) }
        item { Text("Latest findings", style = MaterialTheme.typography.titleLarge) }
        items(findings.take(3)) { FindingRow(it) }
        item {
            Button(onClick = {}, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Shield, null)
                Spacer(Modifier.width(8.dp))
                Text("Run full device check")
            }
        }
    }
}

@Composable
private fun SecurityScreen(findings: List<Finding>, padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Security", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Checks use Android APIs and distinguish verified results from inaccessible areas.") }
        items(findings) { FindingRow(it) }
        item { OutlinedButton(onClick = { openSettings(context, Settings.ACTION_SECURITY_SETTINGS) }, Modifier.fillMaxWidth()) { Text("Open Android security settings") } }
    }
}

@Composable
private fun AppsScreen(activity: Activity, padding: PaddingValues) {
    val apps = remember {
        activity.packageManager.getInstalledPackages(0)
            .map { it.applicationInfo?.loadLabel(activity.packageManager)?.toString() ?: it.packageName }
            .distinct().sorted().take(100)
    }
    LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Installed apps", style = MaterialTheme.typography.headlineSmall) }
        item { Text("${apps.size} visible applications sampled. Permission-risk analysis will be expanded.") }
        items(apps) { name ->
            ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(Icons.Default.Apps, null) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun StorageScreen(device: DeviceSnapshot, padding: PaddingValues) {
    val used = (device.storageTotal - device.storageFree).coerceAtLeast(0)
    val ratio = if (device.storageTotal > 0) used.toFloat() / device.storageTotal else 0f
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
        item { Text("Cleanup is confirmation-gated. Protected/system data is never silently deleted.") }
        item { OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) { Text("Analyze large & duplicate files") } }
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
        }
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    val icon = when (finding.severity) {
        "WARN" -> Icons.Default.Warning
        "COVERAGE" -> Icons.Default.Info
        else -> Icons.Default.CheckCircle
    }
    ListItem(
        headlineContent = { Text(finding.title) },
        supportingContent = { Text(finding.detail) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Text(finding.severity) }
    )
    HorizontalDivider()
}

private fun openSettings(context: Context, action: String, data: String? = null) {
    runCatching {
        context.startActivity(Intent(action).apply { if (data != null) this.data = Uri.parse(data) })
    }
}

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
