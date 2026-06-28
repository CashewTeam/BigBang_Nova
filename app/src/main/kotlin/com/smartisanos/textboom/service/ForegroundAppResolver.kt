package com.cashewteam.novatext.android.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - LOOKBACK_WINDOW_MS, now)
        val event = UsageEvents.Event()
        var resumedPackage: String? = null
        var foregroundPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName?.takeIf { it.isNotBlank() } ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedPackage = packageName
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundPackage = packageName
            }
        }
        return resumedPackage ?: foregroundPackage
    }

    private const val LOOKBACK_WINDOW_MS = 15_000L
}
