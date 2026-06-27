package com.smartisanos.textboom.service

import android.content.Context
import com.smartisanos.textboom.domain.capture.CaptureRequestContract
import com.smartisanos.textboom.domain.capture.TextSessionCoordinator
import com.smartisanos.textboom.util.NovaTextLogger

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
        BoomActivityLauncher.openText(context, text, touchX, touchY)
        return true
    }
}
