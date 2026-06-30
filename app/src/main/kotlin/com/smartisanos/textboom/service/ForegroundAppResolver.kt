package com.cashewteam.novatext.android.service

import android.content.Context

object ForegroundAppResolver {
    @Volatile
    private var cachedPackage: String? = null

    fun resolveForegroundPackage(context: Context): String? {
        val selfPackage = context.packageName
        val service = NovaTextAccessibilityService.activeInstance
        val windowPackage = service?.rootInActiveWindow
            ?.packageName
            ?.toString()
            ?.takeIf { it.isNotBlank() && it != selfPackage }
        val resolved = windowPackage ?: NovaTextAccessibilityService.latestActivePackage(selfPackage)
        if (!resolved.isNullOrBlank()) {
            cachedPackage = resolved
        }
        return resolved
    }

    fun cachedForegroundPackage(context: Context): String? {
        return cachedPackage?.takeIf { it.isNotBlank() && it != context.packageName }
    }
}
