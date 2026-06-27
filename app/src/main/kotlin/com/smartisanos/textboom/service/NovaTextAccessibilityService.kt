package com.smartisanos.textboom.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.smartisanos.textboom.util.NovaTextLogger

class NovaTextAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        NovaTextLogger.d("accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

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
        @Volatile
        var activeInstance: NovaTextAccessibilityService? = null
            private set
    }
}
