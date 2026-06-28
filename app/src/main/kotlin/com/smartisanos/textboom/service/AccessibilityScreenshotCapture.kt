package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.net.Uri
import android.os.Build
import android.view.Display
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cashewteam.novatext.android.R
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object AccessibilityScreenshotCapture {
    private val captureInFlight = AtomicBoolean(false)

    fun captureToOcr(
        callerPackage: String,
        touchX: Int,
        touchY: Int,
    ): Boolean {
        val service = NovaTextAccessibilityService.activeInstance ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(service, R.string.ocr_capture_unsupported, Toast.LENGTH_SHORT).show()
            return true
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            Toast.makeText(service, R.string.ocr_capture_in_progress, Toast.LENGTH_SHORT).show()
            return true
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val uri = saveScreenshot(service, screenshot.hardwareBuffer, screenshot.colorSpace)
                    captureInFlight.set(false)
                    if (uri == null) {
                        Toast.makeText(service, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                        return
                    }
                    BoomOcrLauncher.open(
                        context = service,
                        imageUri = uri,
                        touchX = touchX,
                        touchY = touchY,
                        fullscreen = true,
                        callerPackage = callerPackage,
                    )
                }

                override fun onFailure(errorCode: Int) {
                    captureInFlight.set(false)
                    Toast.makeText(service, R.string.ocr_capture_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
        return true
    }

    private fun saveScreenshot(
        service: NovaTextAccessibilityService,
        hardwareBuffer: HardwareBuffer,
        colorSpace: android.graphics.ColorSpace?,
    ): Uri? {
        try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) ?: return null
            val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            hardwareBitmap.recycle()
            if (bitmap == null) {
                return null
            }
            val dir = File(service.cacheDir, "ocr").apply { mkdirs() }
            val file = File.createTempFile("capture_", ".png", dir)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
            return FileProvider.getUriForFile(
                service,
                "${service.packageName}.fileprovider",
                file,
            )
        } catch (_: Exception) {
            return null
        } finally {
            hardwareBuffer.close()
        }
    }
}
