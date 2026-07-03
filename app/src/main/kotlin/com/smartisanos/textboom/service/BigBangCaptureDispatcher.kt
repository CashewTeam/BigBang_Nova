package com.cashewteam.novatext.android.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.ManualOcrSourceStore
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.domain.capture.CaptureRequestContract
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator
import com.cashewteam.novatext.android.util.NovaTextLogger
import kotlin.concurrent.thread
import java.util.UUID

object BigBangCaptureDispatcher {
    private val mainHandler = Handler(Looper.getMainLooper())

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
        val currentPackage = ForegroundAppResolver.resolveForegroundPackage(context)
        val cachedPackage = ForegroundAppResolver.cachedForegroundPackage(context)
        if (currentPackage.isNullOrBlank()) {
            if (!cachedPackage.isNullOrBlank()) {
                val cachedWhitelistHit = settings.ocrWhitelistPackages.contains(cachedPackage)
                logTrace(
                    enabled = traceEnabled,
                    traceId = traceId,
                    lines = listOf(
                        "phase=dispatcher",
                        "touch=($touchX,$touchY)",
                        "accessibility=true",
                        "foregroundResolver=accessibility",
                        "foregroundPackage=$cachedPackage",
                        "foregroundPackageSource=cache",
                        "whitelistHit=$cachedWhitelistHit",
                        "route=${if (cachedWhitelistHit) "ocr" else "accessibility"}",
                        "reason=foreground_package_cache",
                    ),
                )
                if (cachedWhitelistHit) {
                    launchOcrAfterScreenshot(
                        context = context,
                        touchX = touchX,
                        touchY = touchY,
                        callerPackage = cachedPackage,
                        traceId = traceId,
                        traceEnabled = traceEnabled,
                    )
                } else {
                    launchAccessibilityCapture(
                        context = context,
                        touchX = touchX,
                        touchY = touchY,
                        callerPackage = cachedPackage,
                        traceId = traceId,
                        traceEnabled = traceEnabled,
                        allowOcrFallback = true,
                    )
                }
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
                    "foregroundPackage=unknown",
                    "foregroundPackageSource=none",
                    "whitelistHit=false",
                    "route=ocr",
                    "reason=foreground_package_not_current",
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
        val whitelistHit = settings.ocrWhitelistPackages.contains(currentPackage)
        if (whitelistHit) {
            logTrace(
                enabled = traceEnabled,
                traceId = traceId,
                lines = listOf(
                    "phase=dispatcher",
                    "touch=($touchX,$touchY)",
                    "accessibility=true",
                    "foregroundResolver=accessibility",
                    "foregroundPackage=$currentPackage",
                    "foregroundPackageSource=current",
                    "whitelistHit=true",
                    "route=ocr",
                ),
            )
            launchOcrAfterScreenshot(
                context = context,
                touchX = touchX,
                touchY = touchY,
                callerPackage = currentPackage,
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
                "foregroundPackage=$currentPackage",
                "foregroundPackageSource=current",
                "whitelistHit=false",
                "route=accessibility",
            ),
        )
        launchAccessibilityCapture(
            context = context,
            touchX = touchX,
            touchY = touchY,
            callerPackage = currentPackage,
            traceId = traceId,
            traceEnabled = traceEnabled,
            allowOcrFallback = true,
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
        val sourceToken = ManualOcrSourceStore.newActiveToken()
        BoomOcrLauncher.launchCapture(
            context = context,
            touchX = touchX,
            touchY = touchY,
            fullscreen = true,
            callerPackage = callerPackage,
            manualOcrSourceToken = sourceToken,
            traceId = traceId,
            traceEnabled = traceEnabled,
            captureScreenshot = true,
        )
    }

    private fun launchAccessibilityCapture(
        context: Context,
        touchX: Int,
        touchY: Int,
        callerPackage: String,
        traceId: String,
        traceEnabled: Boolean,
        allowOcrFallback: Boolean,
    ) {
        val sourceToken = ManualOcrSourceStore.newActiveToken()
        thread(name = "bigbang-dispatcher-accessibility") {
            val snapshot = TextSessionCoordinator.runAccessibilityFirst(
                CaptureRequestContract(
                    touchX = touchX.toDouble(),
                    touchY = touchY.toDouble(),
                    packageName = callerPackage,
                    allowOcrFallback = allowOcrFallback,
                ),
                traceEnabled = traceEnabled,
                traceId = traceId,
            )
            val text = snapshot.originalText.trim()
            mainHandler.post {
                if (text.isEmpty()) {
                    if (allowOcrFallback) {
                        launchOcrAfterScreenshot(
                            context = context,
                            touchX = touchX,
                            touchY = touchY,
                            callerPackage = callerPackage,
                            traceId = traceId,
                            traceEnabled = traceEnabled,
                        )
                    } else {
                        FloatingBallService.clearCaptureLaunchSuppression()
                    }
                    return@post
                }
                ForegroundAppResolver.cacheForegroundPackage(context, callerPackage)
                openTextAfterManualOcrScreenshot(
                    context = context,
                    text = text,
                    touchX = touchX,
                    touchY = touchY,
                    callerPackage = callerPackage,
                    sourceToken = sourceToken,
                )
            }
        }
    }

    private fun openTextAfterManualOcrScreenshot(
        context: Context,
        text: String,
        touchX: Int,
        touchY: Int,
        callerPackage: String,
        sourceToken: String,
    ) {
        fun openText() {
            BoomActivityLauncher.openText(
                context = context,
                text = text,
                touchX = touchX,
                touchY = touchY,
                isPreview = false,
                animateLaunch = true,
                enableAdjacentSession = true,
                manualOcrSourceToken = sourceToken,
            )
        }
        val started = AccessibilityScreenshotCapture.captureToCache(
            context = context,
            onFinished = {
                openText()
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
                        sourceTag = "accessibility_capture",
                    ),
                )
            },
        )
        if (!started) {
            openText()
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
