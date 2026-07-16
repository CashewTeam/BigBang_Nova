package com.cashewteam.novatext.extra

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerPolicyTest {
    @Test
    fun calculatesEllipseTouchArea() {
        assertEquals((2f * Math.PI).toFloat(), TriggerPolicy.touchArea(4f, 2f), 0.0001f)
    }

    @Test
    fun exclusionKeepsSettingsAndContentAppsDistinct() {
        assertTrue(TriggerPolicy.isExcludedPackage("com.android.settings"))
        assertTrue(TriggerPolicy.isExcludedPackage(TriggerPolicy.NOVA_TEXT))
        assertFalse(TriggerPolicy.isExcludedPackage("com.android.chrome"))
        assertFalse(TriggerPolicy.isExcludedPackage("com.example.reader"))
    }

    @Test
    fun zeroThresholdCannotConsumeEveryTouch() {
        assertFalse(TriggerPolicy.hasUsableThreshold(TriggerConfig(true, true, TriggerMode.PRESSURE, 0f)))
        assertTrue(TriggerPolicy.hasUsableThreshold(TriggerConfig(true, true, TriggerMode.PRESSURE, 1f)))
    }

    @Test
    fun xposedGestureTimingAndMovementAreBounded() {
        assertTrue(TriggerPolicy.isWithinDuration(1_000L, 1_300L, 300f))
        assertFalse(TriggerPolicy.isWithinDuration(1_000L, 1_301L, 300f))
        assertFalse(TriggerPolicy.movedBeyondSlop(0f, 0f, 3f, 4f, 5))
        assertTrue(TriggerPolicy.movedBeyondSlop(0f, 0f, 4f, 4f, 5))
    }

    @Test
    fun experimentalSupportAndPointerCountsMatchModes() {
        assertTrue(TriggerPolicy.supportsExperimental(TriggerMode.PRESSURE))
        assertTrue(TriggerPolicy.supportsExperimental(TriggerMode.TWO_FINGER_TAP))
        assertTrue(TriggerPolicy.supportsExperimental(TriggerMode.THREE_FINGER_TAP))
        assertFalse(TriggerPolicy.supportsExperimental(TriggerMode.SINGLE_LONG_PRESS))
        assertTrue(TriggerPolicy.hasExactPointerCount(TriggerMode.TWO_FINGER_TAP, 2, 2))
        assertTrue(TriggerPolicy.hasExactPointerCount(TriggerMode.THREE_FINGER_TAP, 3, 3))
        assertFalse(TriggerPolicy.hasExactPointerCount(TriggerMode.TWO_FINGER_TAP, 2, 3))
        assertFalse(TriggerPolicy.hasExactPointerCount(TriggerMode.THREE_FINGER_TAP, 2, 2))
        assertFalse(TriggerPolicy.isCooldownElapsed(1_000L, 1_749L))
        assertTrue(TriggerPolicy.isCooldownElapsed(1_000L, 1_750L))
    }

    @Test
    fun extractsInputMethodPackage() {
        assertEquals("com.example.ime", TriggerPolicy.inputMethodPackage("com.example.ime/.Keyboard"))
        assertEquals(null, TriggerPolicy.inputMethodPackage(null))
    }
}
