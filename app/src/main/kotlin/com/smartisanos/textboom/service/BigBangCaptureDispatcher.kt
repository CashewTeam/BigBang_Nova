package com.cashewteam.novatext.android.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.util.NovaTextLogger

object BigBangCaptureDispatcher {
    fun captureAt(
        context: Context,
        touchX: Int,
        touchY: Int,
    ) {
        val settings = BigBangSettings.get(context)
        val accessibilityEnabled = NovaTextAccessibilityService.activeInstance != null
        if (!accessibilityEnabled && settings.isDebugSkipAccessibilityEnabled()) {
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
        if (!accessibilityEnabled) {
            Toast.makeText(context, R.string.accessibility_required_message, Toast.LENGTH_SHORT).show()
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        if (!ForegroundAppResolver.hasUsageAccess(context)) {
            Toast.makeText(context, R.string.usage_access_missing_message, Toast.LENGTH_SHORT).show()
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        val foregroundPackage = ForegroundAppResolver.resolveForegroundPackage(context)
        if (foregroundPackage.isNullOrBlank()) {
            Toast.makeText(context, R.string.ocr_foreground_package_missing, Toast.LENGTH_SHORT).show()
            return
        }
        if (settings.ocrWhitelistPackages.contains(foregroundPackage)) {
            BoomOcrLauncher.launchCapture(
                context = context,
                touchX = touchX,
                touchY = touchY,
                fullscreen = true,
                callerPackage = foregroundPackage,
            )
            return
        }
        NovaTextLogger.d("capture queued at x=$touchX y=$touchY")
        BoomActivityLauncher.launchCapture(context, touchX, touchY)
    }
}
