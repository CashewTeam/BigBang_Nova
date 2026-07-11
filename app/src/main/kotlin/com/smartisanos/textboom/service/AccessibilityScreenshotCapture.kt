package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.util.NovaTextLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

object AccessibilityScreenshotCapture {
    private val captureInFlight = AtomicBoolean(false)
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun captureToOcr(
        context: Context,
        onFinished: ((Bitmap?) -> Unit)? = null,
        onCaptured: (Bitmap) -> Unit,
    ): Boolean {
        return captureInternal(
            context = context,
            silent = false,
            onFinished = onFinished,
            onCaptured = onCaptured,
        )
    }

    fun captureToCache(
        context: Context,
        onFinished: ((Bitmap?) -> Unit)? = null,
        onCaptured: (Bitmap) -> Unit,
    ): Boolean {
        return captureInternal(
            context = context,
            silent = true,
            onFinished = onFinished,
            onCaptured = onCaptured,
        )
    }

    private fun captureInternal(
        context: Context,
        silent: Boolean,
        onFinished: ((Bitmap?) -> Unit)?,
        onCaptured: (Bitmap) -> Unit,
    ): Boolean {
        val service = NovaTextAccessibilityService.activeInstance ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (BigBangSettings.get(context).isUseShizukuScreenshotEnabled) {
                    captureShizukuScreenshot(context, silent, onFinished, onCaptured)
                } else {
                    captureProjectionScreenshot(context, silent, onFinished, onCaptured)
                }
            } else {
                if (!silent) {
                    Toast.makeText(service, R.string.ocr_capture_unsupported, Toast.LENGTH_SHORT).show()
                }
                onFinished?.invoke(null)
            }
            return true
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            NovaTextLogger.d("accessibility screenshot skipped: capture in flight")
            if (!silent) {
                Toast.makeText(service, R.string.ocr_capture_in_progress, Toast.LENGTH_SHORT).show()
            }
            onFinished?.invoke(null)
            return true
        }
        FloatingBallService.hideForScreenshot {
            try {
                NovaTextLogger.d("accessibility screenshot request")
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bitmap = captureBitmap(screenshot.hardwareBuffer, screenshot.colorSpace)
                            mainHandler.post {
                                captureInFlight.set(false)
                                FloatingBallService.restoreAfterScreenshot()
                                if (bitmap == null) {
                                    NovaTextLogger.d("accessibility screenshot failed: bitmap null")
                                    onFinished?.invoke(null)
                                    if (!silent) {
                                        Toast.makeText(service, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                                    }
                                    return@post
                                }
                                NovaTextLogger.d("accessibility screenshot success ${bitmap.width}x${bitmap.height}")
                                onCaptured(bitmap)
                                onFinished?.invoke(bitmap)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            mainHandler.post {
                                captureInFlight.set(false)
                                FloatingBallService.restoreAfterScreenshot()
                                NovaTextLogger.d("accessibility screenshot failed: errorCode=$errorCode")
                                onFinished?.invoke(null)
                                if (!silent) {
                                    Toast.makeText(service, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
            } catch (exception: Exception) {
                captureInFlight.set(false)
                FloatingBallService.restoreAfterScreenshot()
                NovaTextLogger.d("accessibility screenshot failed: ${exception.javaClass.simpleName}")
                onFinished?.invoke(null)
                if (!silent) {
                    Toast.makeText(context, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    private fun captureShizukuScreenshot(
        context: Context,
        silent: Boolean,
        onFinished: ((Bitmap?) -> Unit)?,
        onCaptured: (Bitmap) -> Unit,
    ) {
        if (!captureInFlight.compareAndSet(false, true)) {
            NovaTextLogger.d("shizuku screenshot skipped: capture in flight")
            if (!silent) {
                Toast.makeText(context, R.string.ocr_capture_in_progress, Toast.LENGTH_SHORT).show()
            }
            onFinished?.invoke(null)
            return
        }
        FloatingBallService.hideForScreenshot {
            screenshotExecutor.execute {
                val debugLog = BigBangSettings.get(context).isDebugCaptureTraceEnabled
                if (debugLog) NovaTextLogger.d("shizuku screenshot request")
                val point = Point()
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay
                    .getRealSize(point)
                val bitmap = ShizukuScreenshotCapture.capture(point.x, point.y, debugLog)
                mainHandler.post {
                    captureInFlight.set(false)
                    FloatingBallService.restoreAfterScreenshot()
                    if (bitmap == null) {
                        if (debugLog) NovaTextLogger.d("shizuku screenshot failed")
                        if (!silent) {
                            Toast.makeText(context, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                        }
                        onFinished?.invoke(null)
                        return@post
                    }
                    if (debugLog) NovaTextLogger.d("shizuku screenshot success ${bitmap.width}x${bitmap.height}")
                    onCaptured(bitmap)
                    onFinished?.invoke(bitmap)
                }
            }
        }
    }

    private fun captureProjectionScreenshot(
        context: Context,
        silent: Boolean,
        onFinished: ((Bitmap?) -> Unit)?,
        onCaptured: (Bitmap) -> Unit,
    ) {
        if (!MediaProjectionScreenshotCapture.hasAuthorization()) {
            if (!silent) {
                Toast.makeText(context, R.string.projection_permission_required, Toast.LENGTH_SHORT).show()
            }
            onFinished?.invoke(null)
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            NovaTextLogger.d("projection screenshot skipped: capture in flight")
            if (!silent) {
                Toast.makeText(context, R.string.ocr_capture_in_progress, Toast.LENGTH_SHORT).show()
            }
            onFinished?.invoke(null)
            return
        }
        FloatingBallService.hideForScreenshot {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
                .getRealMetrics(metrics)
            val started = MediaProjectionScreenshotCapture.capture(
                context = context,
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                onFinished = { bitmap ->
                    mainHandler.post {
                        captureInFlight.set(false)
                        FloatingBallService.restoreAfterScreenshot()
                        if (bitmap == null && !silent) {
                            Toast.makeText(context, R.string.projection_capture_failed, Toast.LENGTH_SHORT).show()
                        }
                        onFinished?.invoke(bitmap)
                    }
                },
                onCaptured = onCaptured,
            )
            if (!started) {
                captureInFlight.set(false)
                FloatingBallService.restoreAfterScreenshot()
                if (!silent) {
                    Toast.makeText(context, R.string.projection_permission_required, Toast.LENGTH_SHORT).show()
                }
                onFinished?.invoke(null)
            }
        }
    }

    private fun captureBitmap(
        hardwareBuffer: android.hardware.HardwareBuffer,
        colorSpace: android.graphics.ColorSpace?,
    ): Bitmap? {
        try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) ?: return null
            val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBitmap.recycle()
            return bitmap
        } catch (_: Exception) {
            return null
        } finally {
            hardwareBuffer.close()
        }
    }
}
