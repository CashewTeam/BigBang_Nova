package com.cashewteam.novatext.android.service

import android.app.AppOpsManager
import android.content.Context

object ForegroundAppResolver {
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun resolveForegroundPackage(context: Context): String? {
        val selfPackage = context.packageName
        val service = NovaTextAccessibilityService.activeInstance
        val windowPackage = service?.rootInActiveWindow
            ?.packageName
            ?.toString()
            ?.takeIf { it.isNotBlank() && it != selfPackage }
        return windowPackage ?: NovaTextAccessibilityService.latestActivePackage(selfPackage)
    }
}
