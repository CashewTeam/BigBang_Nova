package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
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

        fun performClick(x: Int, y: Int, time: Long): Boolean {
            val service = activeInstance ?: return false
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, time))
                .build()
            return service.dispatchGesture(gesture, null, null)
        }

        fun performSwipe(start: Pair<Int, Int>, end: Pair<Int, Int>, duration: Long): Boolean {
            val service = activeInstance ?: return false
            val path = Path().apply {
                moveTo(start.first.toFloat(), start.second.toFloat())
                lineTo(end.first.toFloat(), end.second.toFloat())
            }
            val gestureBuilder = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            return service.dispatchGesture(gestureBuilder, null, null)
        }
    }

    private fun packageName(): String = applicationContext.packageName
}
