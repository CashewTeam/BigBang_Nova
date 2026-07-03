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
        if (!windowPackage.isNullOrBlank()) {
            cachedPackage = windowPackage
        }
        return windowPackage
    }

    fun cachedForegroundPackage(context: Context): String? {
        return cachedPackage
            ?: NovaTextAccessibilityService.latestActivePackage(context.packageName)
                ?.also { cachedPackage = it }
    }

    fun cacheForegroundPackage(context: Context, packageName: String?) {
        if (!packageName.isNullOrBlank() && packageName != context.packageName) {
            cachedPackage = packageName
        }
    }
}
