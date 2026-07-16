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
}
