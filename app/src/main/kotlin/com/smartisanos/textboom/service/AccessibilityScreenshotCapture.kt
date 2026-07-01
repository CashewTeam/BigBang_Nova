package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.widget.Toast
import com.cashewteam.novatext.android.R
import java.util.concurrent.atomic.AtomicBoolean

object AccessibilityScreenshotCapture {
    private val captureInFlight = AtomicBoolean(false)

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
            if (!silent) {
                Toast.makeText(service, R.string.ocr_capture_unsupported, Toast.LENGTH_SHORT).show()
            }
            onFinished?.invoke(null)
            return true
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            if (!silent) {
                Toast.makeText(service, R.string.ocr_capture_in_progress, Toast.LENGTH_SHORT).show()
            }
            onFinished?.invoke(null)
            return true
        }
        FloatingBallService.hideForScreenshot {
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bitmap = captureBitmap(screenshot.hardwareBuffer, screenshot.colorSpace)
                            captureInFlight.set(false)
                            FloatingBallService.restoreAfterScreenshot()
                            if (bitmap == null) {
                                onFinished?.invoke(null)
                                if (!silent) {
                                    Toast.makeText(service, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                                }
                                return
                            }
                            onCaptured(bitmap)
                            onFinished?.invoke(bitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            captureInFlight.set(false)
                            FloatingBallService.restoreAfterScreenshot()
                            onFinished?.invoke(null)
                            if (!silent) {
                                Toast.makeText(service, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            } catch (_: SecurityException) {
                captureInFlight.set(false)
                FloatingBallService.restoreAfterScreenshot()
                onFinished?.invoke(null)
                if (!silent) {
                    Toast.makeText(context, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
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
