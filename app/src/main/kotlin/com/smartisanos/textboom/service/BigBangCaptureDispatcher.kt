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
    ) {
        NovaTextLogger.d("capture queued at x=$touchX y=$touchY")
        BoomActivityLauncher.launchCapture(context, touchX, touchY)
    }
}
