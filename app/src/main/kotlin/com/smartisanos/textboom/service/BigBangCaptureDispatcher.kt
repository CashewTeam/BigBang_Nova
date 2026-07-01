package com.cashewteam.novatext.android.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.ManualOcrSourceStore
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
                    "foregroundResolver=accessibility",
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
            FloatingBallService.clearCaptureLaunchSuppression()
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=false",
                    "foregroundResolver=accessibility",
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
        val resolvedPackage = ForegroundAppResolver.resolveForegroundPackage(context)
        val foregroundPackage = resolvedPackage ?: ForegroundAppResolver.cachedForegroundPackage(context)
        if (foregroundPackage.isNullOrBlank()) {
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=true",
                    "foregroundResolver=accessibility",
                    "foregroundPackage=unknown",
                    "whitelistHit=false",
                    "route=ocr",
                    "reason=foreground_package_missing",
                ),
            )
            launchOcrAfterScreenshot(
                context = context,
                touchX = touchX,
                touchY = touchY,
                callerPackage = null,
                traceId = traceId,
                traceEnabled = traceEnabled,
            )
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
                    "foregroundResolver=accessibility",
                    "foregroundPackage=$foregroundPackage",
                    "foregroundPackageSource=${if (resolvedPackage == null) "cache" else "current"}",
                    "whitelistHit=true",
                    "route=ocr",
                ),
            )
            launchOcrAfterScreenshot(
                context = context,
                touchX = touchX,
                touchY = touchY,
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
                "foregroundResolver=accessibility",
                "foregroundPackage=$foregroundPackage",
                "foregroundPackageSource=${if (resolvedPackage == null) "cache" else "current"}",
                "whitelistHit=false",
                "route=accessibility",
            ),
        )
        launchAccessibilityAfterScreenshot(
            context = context,
            touchX = touchX,
            touchY = touchY,
            callerPackage = foregroundPackage,
            traceId = traceId,
            traceEnabled = traceEnabled,
        )
    }

    private fun launchOcrAfterScreenshot(
        context: Context,
        touchX: Int,
        touchY: Int,
        callerPackage: String?,
        traceId: String,
        traceEnabled: Boolean,
    ) {
        val sourceToken = ManualOcrSourceStore.newToken()
        val started = AccessibilityScreenshotCapture.captureToOcr(
            context = context,
            onFinished = {
                if (it == null) {
                    FloatingBallService.clearCaptureLaunchSuppression()
                }
            },
            onCaptured = { bitmap ->
                ManualOcrSourceStore.put(
                    ManualOcrSourceStore.Source(
                        token = sourceToken,
                        cachedBitmap = bitmap,
                        touchX = touchX,
                        touchY = touchY,
                        callerPackage = callerPackage,
                        fullscreen = true,
                        offsetX = 0,
                        offsetY = 0,
                        sourceTag = "ocr_capture",
                        replayMode = ManualOcrSourceStore.REPLAY_MODE_NEAREST_PARAGRAPH,
                    ),
                )
                BoomOcrLauncher.launchCapture(
                    context = context,
                    touchX = touchX,
                    touchY = touchY,
                    fullscreen = true,
                    callerPackage = callerPackage,
                    manualOcrSourceToken = sourceToken,
                    traceId = traceId,
                    traceEnabled = traceEnabled,
                )
            },
        )
        if (!started) {
            FloatingBallService.clearCaptureLaunchSuppression()
        }
    }

    private fun launchAccessibilityAfterScreenshot(
        context: Context,
        touchX: Int,
        touchY: Int,
        callerPackage: String,
        traceId: String,
        traceEnabled: Boolean,
    ) {
        val sourceToken = ManualOcrSourceStore.newToken()
        val started = AccessibilityScreenshotCapture.captureToCache(
            context = context,
            onFinished = { bitmap ->
                if (bitmap != null) {
                    ManualOcrSourceStore.put(
                        ManualOcrSourceStore.Source(
                            token = sourceToken,
                            cachedBitmap = bitmap,
                            touchX = touchX,
                            touchY = touchY,
                            callerPackage = callerPackage,
                            fullscreen = true,
                            offsetX = 0,
                            offsetY = 0,
                            sourceTag = "accessibility_capture",
                        ),
                    )
                }
                BoomActivityLauncher.launchCapture(
                    context = context,
                    touchX = touchX,
                    touchY = touchY,
                    callerPackage = callerPackage,
                    manualOcrSourceToken = bitmap?.let { sourceToken },
                    traceId = traceId,
                    traceEnabled = traceEnabled,
                )
            },
            onCaptured = {},
        )
        if (!started) {
            FloatingBallService.clearCaptureLaunchSuppression()
        }
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
