package com.cashewteam.novatext.android.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cashewteam.novatext.android.BoomActivity
import com.cashewteam.novatext.android.BoomOcrActivity
import com.cashewteam.novatext.android.OcrLaunchActivity

object BoomOcrLauncher {
    const val EXTRA_CAPTURE_OCR_SCREENSHOT = "extra_capture_ocr_screenshot"

    @JvmStatic
    fun open(
        context: Context,
        imageUri: Uri,
        touchX: Int,
        touchY: Int,
        fullscreen: Boolean,
        callerPackage: String? = null,
        offsetX: Int = 0,
        offsetY: Int = 0,
        manualOcrSourceToken: String? = null,
    ) {
        val targetActivity = if (context is Activity) {
            BoomOcrActivity::class.java
        } else {
            OcrLaunchActivity::class.java
        }
        val intent = Intent(context, targetActivity).apply {
            putExtra(BoomOcrActivity.EXTRA_OCR_IMAGE_URI, imageUri.toString())
            putExtra("boom_startx", touchX)
            putExtra("boom_starty", touchY)
            putExtra("boom_fullscreen", fullscreen)
            putExtra("boom_offsetx", offsetX)
            putExtra("boom_offsety", offsetY)
            if (!callerPackage.isNullOrEmpty()) {
                putExtra("caller_pkg", callerPackage)
            }
            if (!manualOcrSourceToken.isNullOrEmpty()) {
                putExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN, manualOcrSourceToken)
            }
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }
        context.startActivity(intent)
        if (context is Activity) {
            context.overridePendingTransition(0, 0)
        }
    }

    @JvmStatic
    fun launchCapture(
        context: Context,
        imageUri: Uri,
        touchX: Int,
        touchY: Int,
        fullscreen: Boolean,
        callerPackage: String? = null,
        offsetX: Int = 0,
        offsetY: Int = 0,
        manualOcrSourceToken: String,
        traceId: String? = null,
        traceEnabled: Boolean = false,
    ) {
        val intent = Intent(context, OcrLaunchActivity::class.java).apply {
            putExtra(BoomOcrActivity.EXTRA_OCR_IMAGE_URI, imageUri.toString())
            putExtra(OcrLaunchActivity.EXTRA_AUTO_NEAREST_OCR, true)
            putExtra("boom_startx", touchX)
            putExtra("boom_starty", touchY)
            putExtra("boom_fullscreen", fullscreen)
            putExtra("boom_offsetx", offsetX)
            putExtra("boom_offsety", offsetY)
            putExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN, manualOcrSourceToken)
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    @JvmStatic
    fun replayWithLanguage(
        context: Context,
        sourceToken: String,
        touchX: Int,
        touchY: Int,
        mode: String,
        replayMode: String,
    ) {
        context.startActivity(
            Intent(context, OcrLaunchActivity::class.java).apply {
                putExtra(BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN, sourceToken)
                putExtra("boom_startx", touchX)
                putExtra("boom_starty", touchY)
                putExtra(OcrLaunchActivity.EXTRA_REPLAY_OCR_MODE, mode)
                putExtra(OcrLaunchActivity.EXTRA_REPLAY_MODE, replayMode)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
            },
        )
        if (context is Activity) {
            context.overridePendingTransition(0, 0)
        }
    }
}
