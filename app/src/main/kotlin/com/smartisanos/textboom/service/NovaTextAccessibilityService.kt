package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.cashewteam.novatext.android.util.NovaTextLogger

class NovaTextAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        NovaTextLogger.d("accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank() && packageName != packageName()) {
            latestExternalPackage = packageName
            latestExternalPackageAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onInterrupt() {
        NovaTextLogger.d("accessibility service interrupted")
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        NovaTextLogger.d("accessibility service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val PACKAGE_CACHE_TTL_MS = 2_000L

        @Volatile
        var activeInstance: NovaTextAccessibilityService? = null
            private set

        @Volatile
        private var latestExternalPackage: String? = null

        @Volatile
        private var latestExternalPackageAt: Long = 0L

        fun latestActivePackage(selfPackage: String): String? {
            val cached = latestExternalPackage
            if (cached.isNullOrBlank() || cached == selfPackage) return null
            val ageMs = SystemClock.elapsedRealtime() - latestExternalPackageAt
            return cached.takeIf { ageMs in 0..PACKAGE_CACHE_TTL_MS }
        }
    }

    private fun packageName(): String = applicationContext.packageName
}
