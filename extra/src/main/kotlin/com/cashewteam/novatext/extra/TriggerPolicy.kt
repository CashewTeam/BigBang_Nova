package com.cashewteam.novatext.extra

import android.view.MotionEvent

object TriggerPolicy {
    private const val COOLDOWN_MS = 750L

    @JvmStatic
    fun isExcludedPackage(packageName: String): Boolean {
        return packageName == NOVA_TEXT || packageName == EXTRA ||
            packageName == "com.android.settings" || packageName == "com.android.systemui" ||
            packageName == "android" || packageName.startsWith("com.android.launcher") ||
            packageName.startsWith("com.google.android.apps.nexuslauncher")
    }

    fun readSample(event: MotionEvent, mode: TriggerMode, pointerIndex: Int): Float {
        return when (mode) {
            TriggerMode.PRESSURE -> event.getPressure(pointerIndex)
            TriggerMode.SIZE -> event.getSize(pointerIndex)
            TriggerMode.TOUCH_AREA -> touchArea(event.getTouchMajor(pointerIndex), event.getTouchMinor(pointerIndex))
        }
    }

    fun touchArea(touchMajor: Float, touchMinor: Float): Float =
        (Math.PI.toFloat() * touchMajor * touchMinor) / 4f

    fun hasUsableThreshold(config: TriggerConfig): Boolean =
        config.calibrated && config.threshold.isFinite() && config.threshold > 0f

    const val NOVA_TEXT = "com.cashewteam.novatext.android"
    const val EXTRA = "com.cashewteam.novatext.extra"

    class Gate {
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var triggered = false
        private var lastTriggeredAt = 0L

        fun onEvent(event: MotionEvent, config: TriggerConfig): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerId = event.getPointerId(0)
                    triggered = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    reset()
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == pointerId) reset()
                    return false
                }
            }
            if (!config.enabled || !hasUsableThreshold(config) || triggered || pointerId == MotionEvent.INVALID_POINTER_ID) return false
            val index = event.findPointerIndex(pointerId)
            if (index < 0 || readSample(event, config.mode, index) <= config.threshold) return false
            val now = event.eventTime
            if (now - lastTriggeredAt < COOLDOWN_MS) return false
            lastTriggeredAt = now
            triggered = true
            return true
        }

        private fun reset() {
            pointerId = MotionEvent.INVALID_POINTER_ID
            triggered = false
        }
    }
}
