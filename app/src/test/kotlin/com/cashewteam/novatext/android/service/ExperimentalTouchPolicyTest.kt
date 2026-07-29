package com.cashewteam.novatext.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalTouchPolicyTest {
    @Test
    fun touchAreaUsesEllipseAxes() {
        assertEquals((2f * Math.PI).toFloat(), ExperimentalTouchPolicy.touchArea(4f, 2f), 0.0001f)
    }

    @Test
    fun thresholdRequiresSavedPositiveFiniteValue() {
        assertFalse(ExperimentalTouchPolicy.hasUsableThreshold(ExperimentalTriggerConfig(false, ExperimentalTriggerMode.PRESSURE, 1f, 300f)))
        assertFalse(ExperimentalTouchPolicy.hasUsableThreshold(ExperimentalTriggerConfig(true, ExperimentalTriggerMode.PRESSURE, 0f, 300f)))
        assertTrue(ExperimentalTouchPolicy.hasUsableThreshold(ExperimentalTriggerConfig(true, ExperimentalTriggerMode.PRESSURE, 1f, 300f)))
        assertFalse(ExperimentalTouchPolicy.hasUsableCandidateDuration(ExperimentalTriggerConfig(true, ExperimentalTriggerMode.PRESSURE, 1f, 0f)))
        assertTrue(ExperimentalTouchPolicy.hasUsableCandidateDuration(ExperimentalTriggerConfig(true, ExperimentalTriggerMode.PRESSURE, 1f, 300f)))
    }

    @Test
    fun gestureModesHaveExpectedSupportAndPointerCounts() {
        assertTrue(ExperimentalTouchPolicy.isSensorMode(ExperimentalTriggerMode.TOUCH_AREA))
        assertTrue(ExperimentalTouchPolicy.isMultiFingerTap(ExperimentalTriggerMode.TWO_FINGER_TAP))
        assertEquals(2, ExperimentalTouchPolicy.requiredPointerCount(ExperimentalTriggerMode.TWO_FINGER_TAP))
        assertEquals(3, ExperimentalTouchPolicy.requiredPointerCount(ExperimentalTriggerMode.THREE_FINGER_TAP))
        assertTrue(ExperimentalTouchPolicy.hasExactPointerCount(ExperimentalTriggerMode.TWO_FINGER_TAP, 2, 2))
        assertFalse(ExperimentalTouchPolicy.hasExactPointerCount(ExperimentalTriggerMode.THREE_FINGER_TAP, 2, 2))
    }

    @Test
    fun movementDurationCooldownAndPackagesAreBounded() {
        assertTrue(ExperimentalTouchPolicy.isWithinDuration(1_000L, 1_300L, 300f))
        assertFalse(ExperimentalTouchPolicy.isWithinDuration(1_000L, 1_301L, 300f))
        assertTrue(ExperimentalTouchPolicy.movedBeyondSlop(0f, 0f, 4f, 4f, 5))
        assertFalse(ExperimentalTouchPolicy.isCooldownElapsed(1_000L, 1_749L))
        assertTrue(ExperimentalTouchPolicy.isCooldownElapsed(1_000L, 1_750L))
        assertTrue(ExperimentalTouchPolicy.isExcludedPackage(ExperimentalTouchPolicy.NOVA_TEXT))
        assertTrue(ExperimentalTouchPolicy.isExcludedPackage("com.android.systemui"))
        assertFalse(ExperimentalTouchPolicy.isExcludedPackage("com.example.reader"))
    }
}
