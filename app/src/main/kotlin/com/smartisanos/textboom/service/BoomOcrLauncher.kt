package com.cashewteam.novatext.android.service

import android.app.Activity
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cashewteam.novatext.android.BoomActivity
import com.cashewteam.novatext.android.BoomOcrActivity
import com.cashewteam.novatext.android.OcrLaunchActivity

object BoomOcrLauncher {
    const val EXTRA_CAPTURE_OCR_SCREENSHOT = "extra_capture_ocr_screenshot"
    const val EXTRA_CAPTURE_PROXY_RECOVERY_TOKEN = "extra_capture_proxy_recovery_token"

    @JvmStatic
    fun selectionCaptureIntent(context: Context, captureDelayMs: Int? = null): Intent {
        val metrics = context.resources.displayMetrics
        return Intent(context, OcrLaunchActivity::class.java).apply {
            putExtra(OcrLaunchActivity.EXTRA_CAPTURE_OCR_SELECTION_SCREENSHOT, true)
            captureDelayMs?.let {
                putExtra(OcrLaunchActivity.EXTRA_SELECTION_CAPTURE_DELAY_MS, it)
            }
            putExtra("boom_startx", metrics.widthPixels / 2)
            putExtra("boom_starty", metrics.heightPixels / 2)
            putExtra("boom_fullscreen", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }

    @JvmStatic
    fun launchSelectionCapture(
        context: Context,
        forceSystemBack: Boolean = true,
        captureDelayMs: Int? = null,
    ) {
        if (forceSystemBack) {
            NovaTextAccessibilityService.activeInstance?.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
            )
        }
        context.startActivity(selectionCaptureIntent(context, captureDelayMs))
    }

    @JvmStatic
    fun open(
        context: Context,
        imageUri: Uri? = null,
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
            if (imageUri != null) {
                putExtra(BoomOcrActivity.EXTRA_OCR_IMAGE_URI, imageUri.toString())
            }
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
        imageUri: Uri? = null,
        touchX: Int,
        touchY: Int,
        fullscreen: Boolean,
        callerPackage: String? = null,
        offsetX: Int = 0,
        offsetY: Int = 0,
        manualOcrSourceToken: String,
        traceId: String? = null,
        traceEnabled: Boolean = false,
        captureScreenshot: Boolean = false,
        captureProxyRecoveryToken: Int? = null,
    ) {
        val intent = Intent(context, OcrLaunchActivity::class.java).apply {
            if (imageUri != null) {
                putExtra(BoomOcrActivity.EXTRA_OCR_IMAGE_URI, imageUri.toString())
            }
            putExtra(OcrLaunchActivity.EXTRA_AUTO_NEAREST_OCR, true)
            putExtra(EXTRA_CAPTURE_OCR_SCREENSHOT, captureScreenshot)
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
            captureProxyRecoveryToken?.let {
                putExtra(EXTRA_CAPTURE_PROXY_RECOVERY_TOKEN, it)
            }
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
