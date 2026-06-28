package com.cashewteam.novatext.android.service

import android.content.Context
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.util.NovaTextLogger

object BigBangCaptureDispatcher {
    fun captureAt(
        context: Context,
        touchX: Int,
        touchY: Int,
    ) {
        val settings = BigBangSettings.get(context)
        if (settings.isDebugSkipAccessibilityEnabled()) {
            val debugText = settings.debugPreviewText
            NovaTextLogger.d("capture debug bypass at x=$touchX y=$touchY")
            BoomActivityLauncher.openText(
                context = context,
                text = debugText,
                touchX = touchX,
                touchY = touchY,
                isPreview = false,
                animateLaunch = true,
            )
            return
        }
        NovaTextLogger.d("capture queued at x=$touchX y=$touchY")
        BoomActivityLauncher.launchCapture(context, touchX, touchY)
    }
}
