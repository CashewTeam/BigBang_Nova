package com.cashewteam.novatext.android

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPanelInsetsTest {
    @Test
    fun editBottomInsetUsesImeOrNavigationWithoutAddingThem() {
        assertEquals(320.dp, effectiveOverlayBottomInset(24.dp, 320.dp, editMode = true))
        assertEquals(48.dp, effectiveOverlayBottomInset(48.dp, 20.dp, editMode = true))
    }

    @Test
    fun normalModeIgnoresImeInset() {
        assertEquals(24.dp, effectiveOverlayBottomInset(24.dp, 320.dp, editMode = false))
    }
}
