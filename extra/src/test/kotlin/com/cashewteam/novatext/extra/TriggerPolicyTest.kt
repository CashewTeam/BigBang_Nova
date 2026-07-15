package com.cashewteam.novatext.extra

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerPolicyTest {
    @Test
    fun exclusionKeepsSettingsAndContentAppsDistinct() {
        assertTrue(TriggerPolicy.isExcludedPackage("com.android.settings"))
        assertTrue(TriggerPolicy.isExcludedPackage(TriggerPolicy.NOVA_TEXT))
        assertFalse(TriggerPolicy.isExcludedPackage("com.android.chrome"))
        assertFalse(TriggerPolicy.isExcludedPackage("com.example.reader"))
    }
}
