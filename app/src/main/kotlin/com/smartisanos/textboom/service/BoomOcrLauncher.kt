package com.cashewteam.novatext.android.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        touchX: Int,
        touchY: Int,
        fullscreen: Boolean,
        callerPackage: String? = null,
        offsetX: Int = 0,
        offsetY: Int = 0,
        traceId: String? = null,
        traceEnabled: Boolean = false,
    ) {
        val intent = Intent(context, OcrLaunchActivity::class.java).apply {
            putExtra(EXTRA_CAPTURE_OCR_SCREENSHOT, true)
            putExtra("boom_startx", touchX)
            putExtra("boom_starty", touchY)
            putExtra("boom_fullscreen", fullscreen)
            putExtra("boom_offsetx", offsetX)
            putExtra("boom_offsety", offsetY)
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
        }
        context.startActivity(intent)
    }
}
