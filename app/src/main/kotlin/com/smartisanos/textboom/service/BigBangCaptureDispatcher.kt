package com.cashewteam.novatext.android.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.util.NovaTextLogger
import java.util.UUID

object BigBangCaptureDispatcher {
    fun captureAt(
        context: Context,
        touchX: Int,
        touchY: Int,
    ) {
        val settings = BigBangSettings.get(context)
        val traceEnabled = settings.isDebugCaptureTraceEnabled
        val traceId = UUID.randomUUID().toString().take(8)
        val accessibilityEnabled = NovaTextAccessibilityService.activeInstance != null
        if (!accessibilityEnabled && settings.isDebugSkipAccessibilityEnabled()) {
            val debugText = settings.debugPreviewText
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=false",
                    "usageAccess=skipped",
                    "foregroundPackage=unknown",
                    "whitelistHit=false",
                    "route=debug_text",
                    "finalText=${sanitizeForLog(debugText)}",
                ),
            )
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
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=false",
                    "usageAccess=skipped",
                    "foregroundPackage=unknown",
                    "whitelistHit=false",
                    "route=blocked",
                    "reason=accessibility_disabled",
                ),
            )
            Toast.makeText(context, R.string.accessibility_required_message, Toast.LENGTH_SHORT).show()
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        val usageAccessEnabled = ForegroundAppResolver.hasUsageAccess(context)
        if (!usageAccessEnabled) {
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=true",
                    "usageAccess=false",
                    "foregroundPackage=unknown",
                    "whitelistHit=false",
                    "route=blocked",
                    "reason=usage_access_missing",
                ),
            )
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
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=true",
                    "usageAccess=true",
                    "foregroundPackage=unknown",
                    "whitelistHit=false",
                    "route=blocked",
                    "reason=foreground_package_missing",
                ),
            )
            Toast.makeText(context, R.string.ocr_foreground_package_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val whitelistHit = settings.ocrWhitelistPackages.contains(foregroundPackage)
        if (whitelistHit) {
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=true",
                    "usageAccess=true",
                    "foregroundPackage=$foregroundPackage",
                    "whitelistHit=true",
                    "route=ocr",
                ),
            )
            BoomOcrLauncher.launchCapture(
                context = context,
                touchX = touchX,
                touchY = touchY,
                fullscreen = true,
                callerPackage = foregroundPackage,
                traceId = traceId,
                traceEnabled = traceEnabled,
            )
            return
        }
        logTrace(
            enabled = traceEnabled,
            traceId = traceId,
            lines = listOf(
                "phase=dispatcher",
                "touch=($touchX,$touchY)",
                "accessibility=true",
                "usageAccess=true",
                "foregroundPackage=$foregroundPackage",
                "whitelistHit=false",
                "route=accessibility",
            ),
        )
        BoomActivityLauncher.launchCapture(
            context = context,
            touchX = touchX,
            touchY = touchY,
            callerPackage = foregroundPackage,
            traceId = traceId,
            traceEnabled = traceEnabled,
        )
    }

    private fun logTrace(
        enabled: Boolean,
        traceId: String,
        lines: List<String>,
    ) {
        if (!enabled) return
        NovaTextLogger.d("trace[$traceId] ${lines.firstOrNull().orEmpty()}")
        lines.drop(1).forEach { NovaTextLogger.d("trace[$traceId] $it") }
    }

    private fun sanitizeForLog(text: String): String {
        return text.replace("\n", "\\n")
    }
}
