package com.cashewteam.novatext.android.service

import android.content.Context
import com.cashewteam.novatext.android.domain.capture.CaptureRequestContract
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator
import com.cashewteam.novatext.android.util.NovaTextLogger

object BigBangCaptureDispatcher {
    fun captureAt(
        context: Context,
        touchX: Int,
        touchY: Int,
    ): Boolean {
        val snapshot = TextSessionCoordinator.runAccessibilityFirst(
            CaptureRequestContract(
                touchX = touchX.toDouble(),
                touchY = touchY.toDouble(),
                packageName = context.packageName,
                allowOcrFallback = false,
            )
        )
        val text = snapshot.originalText.trim()
        if (text.isEmpty()) {
            NovaTextLogger.d("capture failed: no accessible text")
            return false
        }
        BoomActivityLauncher.openText(context, text, touchX, touchY, animateLaunch = true)
        return true
    }
}
