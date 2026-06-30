package com.cashewteam.novatext.android.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.cashewteam.novatext.android.BoomActivity
import com.cashewteam.novatext.android.OcrLaunchActivity
import com.cashewteam.novatext.android.OverlayActivity

object BoomActivityLauncher {
    @JvmStatic
    fun openText(
        context: Context,
        text: String,
        touchX: Int,
        touchY: Int,
        isPreview: Boolean = false,
        animateLaunch: Boolean = false,
        enableAdjacentSession: Boolean = false,
        manualOcrSourceToken: String? = null,
    ) {
        val targetActivity =
            if (!animateLaunch && context is Activity) OverlayActivity::class.java
            else OcrLaunchActivity::class.java
        val intent = Intent(context, targetActivity).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra("boom_index", -1)
            putExtra("boom_startx", touchX)
            putExtra("boom_starty", touchY)
            putExtra(OcrLaunchActivity.EXTRA_CAPTURE_ACCESSIBILITY, false)
            putExtra(BoomActivity.EXTRA_ENABLE_ADJACENT_SESSION, enableAdjacentSession)
            if (!manualOcrSourceToken.isNullOrEmpty()) {
                putExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN, manualOcrSourceToken)
            }
            if (targetActivity == OverlayActivity::class.java) {
                putExtra(OcrLaunchActivity.EXTRA_SKIP_LEGACY_FADE_IN, true)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            if (isPreview) {
                putExtra(BoomActivity.EXTRA_DEBUG_PREVIEW_TEXT, text)
            }
        }
        context.startActivity(intent)
    }

    internal fun launchCapture(
        context: Context,
        touchX: Int,
        touchY: Int,
        callerPackage: String? = null,
        traceId: String? = null,
        traceEnabled: Boolean = false,
    ) {
        context.startActivity(
            Intent(context, OcrLaunchActivity::class.java).apply {
                putExtra("boom_startx", touchX)
                putExtra("boom_starty", touchY)
                putExtra(OcrLaunchActivity.EXTRA_CAPTURE_ACCESSIBILITY, true)
                if (!callerPackage.isNullOrEmpty()) {
                    putExtra("caller_pkg", callerPackage)
                }
                if (!traceId.isNullOrEmpty()) {
                    putExtra(OcrLaunchActivity.EXTRA_CAPTURE_TRACE_ID, traceId)
                }
                putExtra(OcrLaunchActivity.EXTRA_CAPTURE_TRACE_ENABLED, traceEnabled)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            },
        )
    }
}
