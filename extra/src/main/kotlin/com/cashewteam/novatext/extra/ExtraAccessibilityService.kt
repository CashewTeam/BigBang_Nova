package com.cashewteam.novatext.extra

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class ExtraAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        active = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            ExperimentalOverlayService.onForegroundPackage(packageName, event.className?.toString())
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active === this) {
            active = null
        }
        super.onDestroy()
    }

    fun replay(path: Path, duration: Long, onFinished: () -> Unit) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.coerceIn(20L, 60_000L)))
            .build()
        if (!dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onFinished()
                override fun onCancelled(gestureDescription: GestureDescription?) = onFinished()
            }, null)) {
            onFinished()
        }
    }

    companion object {
        @Volatile var active: ExtraAccessibilityService? = null
            private set
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ExtraAccessibilityService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.mapNotNull(ComponentName::unflattenFromString)
                ?.any { it == expected } == true
        }
    }
}
