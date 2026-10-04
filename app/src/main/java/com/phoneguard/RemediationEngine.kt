package com.phoneguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object RemediationEngine {
    fun fix(context: Context, action: String): String? {
        if (action == "PROTECTED_INFO") return "Nothing was changed. These locations are protected by Android and are intentionally outside PhoneGuard's safe scan boundary."

        val intent = when (action) {
            "USAGE_SETTINGS" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "VPN_SETTINGS" -> Intent(Settings.ACTION_VPN_SETTINGS)
            "OVERLAY_SETTINGS" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            "ACCESSIBILITY_SETTINGS" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "DEVICE_ADMIN_SETTINGS" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
            "SYSTEM_UPDATE_SETTINGS" -> Intent(Settings.ACTION_SYSTEM_UPDATE_SETTINGS)
            "APP_LIST" -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            else -> null
        } ?: return "No safe remediation is available for this finding."

        return runCatching {
            if (intent.resolveActivity(context.packageManager) == null) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } else {
                context.startActivity(intent)
            }
            null
        }.getOrElse { "Android could not open the requested settings screen: " + (it.message ?: "unknown error") }
    }

    fun openApp(context: Context, packageName: String): String? =
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
            context.startActivity(intent)
            null
        }.getOrElse { "Could not open app settings: " + (it.message ?: "unknown error") }
}
