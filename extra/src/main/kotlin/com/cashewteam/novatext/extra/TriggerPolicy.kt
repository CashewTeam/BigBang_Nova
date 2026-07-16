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
            TriggerMode.SINGLE_LONG_PRESS, TriggerMode.TWO_FINGER_TAP, TriggerMode.THREE_FINGER_TAP -> 0f
        }
    }

    fun isSensorMode(mode: TriggerMode): Boolean =
        mode == TriggerMode.PRESSURE || mode == TriggerMode.SIZE || mode == TriggerMode.TOUCH_AREA

    fun isMultiFingerTap(mode: TriggerMode): Boolean =
        mode == TriggerMode.TWO_FINGER_TAP || mode == TriggerMode.THREE_FINGER_TAP

    fun supportsExperimental(mode: TriggerMode): Boolean = isSensorMode(mode) || isMultiFingerTap(mode)

    fun requiredPointerCount(mode: TriggerMode): Int = when (mode) {
        TriggerMode.TWO_FINGER_TAP -> 2
        TriggerMode.THREE_FINGER_TAP -> 3
        else -> 1
    }

    fun hasExactPointerCount(mode: TriggerMode, currentCount: Int, maximumCount: Int): Boolean =
        currentCount == requiredPointerCount(mode) && maximumCount == currentCount

    fun isCooldownElapsed(lastTriggeredAt: Long, now: Long): Boolean =
        lastTriggeredAt == 0L || now - lastTriggeredAt >= COOLDOWN_MS

    fun inputMethodPackage(setting: String?): String? =
        setting?.substringBefore('/')?.takeIf(String::isNotBlank)

    fun isWithinDuration(startTime: Long, endTime: Long, maximumMs: Float): Boolean =
        maximumMs > 0f && endTime >= startTime && endTime - startTime <= maximumMs

    fun movedBeyondSlop(startX: Float, startY: Float, x: Float, y: Float, slop: Int): Boolean {
        val dx = x - startX
        val dy = y - startY
        return dx * dx + dy * dy > slop * slop
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
            if (!config.enabled || !hasUsableThreshold(config) || !isSensorMode(config.mode) ||
                triggered || pointerId == MotionEvent.INVALID_POINTER_ID
            ) return false
            val index = event.findPointerIndex(pointerId)
            if (index < 0 || readSample(event, config.mode, index) <= config.threshold) return false
            val now = event.eventTime
            if (!isCooldownElapsed(lastTriggeredAt, now)) return false
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
