package com.phoneguard

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

data class AppRisk(
    val label: String,
    val packageName: String,
    val permissions: List<String>,
    val risk: String,
    val reasons: List<String>
)
data class ScanReport(
    val score: Int,
    val findings: List<Finding>,
    val apps: List<AppRisk>,
    val usageAccessGranted: Boolean,
    val vpnActive: Boolean
)

object ScanEngine {
    private val sensitive = setOf(
        "android.permission.CAMERA","android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION","android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_SMS","android.permission.SEND_SMS",
        "android.permission.READ_CONTACTS","android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG","android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE","android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW"
    )

    fun scan(context: Context): ScanReport {
        val pm = context.packageManager
        val apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { pkg ->
            val info = pkg.applicationInfo ?: return@mapNotNull null
            val requested = pkg.requestedPermissions?.filter { it in sensitive }.orEmpty()
            if (requested.isEmpty()) return@mapNotNull null
            val reasons = mutableListOf<String>()
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in requested) reasons += "Can request package installation."
            if ("android.permission.SYSTEM_ALERT_WINDOW" in requested) reasons += "Can potentially draw over other apps."
            if ("android.permission.RECORD_AUDIO" in requested) reasons += "Requests microphone access."
            if ("android.permission.READ_SMS" in requested || "android.permission.SEND_SMS" in requested) reasons += "Requests SMS access."
            val risk = when {
                requested.size >= 5 || "android.permission.SYSTEM_ALERT_WINDOW" in requested -> "REVIEW"
                requested.size >= 2 -> "ATTENTION"
                else -> "INFO"
            }
            AppRisk(info.loadLabel(pm).toString(), pkg.packageName, requested, risk, reasons)
        }.sortedByDescending { it.permissions.size }

        val usage = hasUsageAccess(context)
        val vpn = isVpnActive(context)
        val findings = buildList {
            add(Finding("Sensitive-app permissions", "${apps.size} installed apps request sensitive capabilities.", if (apps.any { it.risk == "REVIEW" }) "WARN" else "INFO"))
            add(Finding("Usage access", if (usage) "Granted to PhoneGuard." else "Not granted; usage-based diagnostics are limited.", if (usage) "INFO" else "COVERAGE"))
            add(Finding("VPN state", if (vpn) "An active VPN transport is detected." else "No active VPN transport detected.", "INFO"))
            add(Finding("Security patch", Build.VERSION.SECURITY_PATCH, "INFO"))
            add(Finding("Protected areas", "Root-only and Android-protected locations are not scanned.", "COVERAGE"))
        }
        val penalty = apps.count { it.risk == "REVIEW" }.coerceAtMost(3) * 7 + apps.count { it.risk == "ATTENTION" }.coerceAtMost(4) * 3
        return ScanReport((100 - penalty).coerceIn(0, 100), findings, apps, usage, vpn)
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}
