package com.cashewteam.novatext.android.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.cashewteam.novatext.android.util.NovaTextLogger

/** Single-frame MediaProjection capture for Android 7-10. */
object MediaProjectionScreenshotCapture {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var resultCode: Int? = null
    private var resultData: Intent? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var surface: Surface? = null
    private var captureActive = false
    private var pendingCaptured: ((Bitmap) -> Unit)? = null
    private var pendingFinished: ((Bitmap?) -> Unit)? = null
    private var captureTimeout: Runnable? = null
    private var projectionStoppedListener: (() -> Unit)? = null

    fun hasAuthorization(): Boolean = synchronized(lock) {
        resultCode == android.app.Activity.RESULT_OK && resultData != null
    }

    fun setAuthorization(resultCode: Int, data: Intent) {
        synchronized(lock) {
            releaseProjectionLocked()
            this.resultCode = resultCode
            resultData = Intent(data)
        }
    }

    fun clearAuthorization() {
        synchronized(lock) {
            releaseProjectionLocked()
            resultCode = null
            resultData = null
        }
    }

    fun setProjectionStoppedListener(listener: (() -> Unit)?) {
        synchronized(lock) {
            projectionStoppedListener = listener
        }
    }

    fun capture(
        context: Context,
        width: Int,
        height: Int,
        densityDpi: Int,
        onFinished: ((Bitmap?) -> Unit)? = null,
        onCaptured: (Bitmap) -> Unit,
    ): Boolean {
        synchronized(lock) {
            if (captureActive || !hasAuthorization()) return false
            captureActive = true
            pendingCaptured = onCaptured
            pendingFinished = onFinished
        }
        mainHandler.post {
            if (!startCapture(context.applicationContext, width, height, densityDpi)) {
                finishCapture(null)
            }
        }
        return true
    }

    private fun startCapture(context: Context, width: Int, height: Int, densityDpi: Int): Boolean {
        synchronized(lock) {
            if (!captureActive || mediaProjection == null) {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                val code = resultCode ?: return false
                val data = resultData ?: return false
                val projection = try {
                    manager.getMediaProjection(code, Intent(data))
                } catch (exception: Throwable) {
                    NovaTextLogger.d("projection authorization failed: ${exception.javaClass.simpleName}")
                    null
                } ?: return false
                val callback = object : MediaProjection.Callback() {
                    override fun onStop() {
                        val stoppedListener: (() -> Unit)?
                        synchronized(lock) {
                            releaseDisplayLocked()
                            mediaProjection = null
                            projectionCallback = null
                            resultCode = null
                            resultData = null
                            stoppedListener = projectionStoppedListener
                        }
                        finishCapture(null)
                        stoppedListener?.let { mainHandler.post(it) }
                    }
                }
                projection.registerCallback(callback, mainHandler)
                mediaProjection = projection
                projectionCallback = callback
            }

            return try {
                val reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
                val outputSurface = reader.surface
                reader.setOnImageAvailableListener({ source ->
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val bitmap = try {
                        imageToBitmap(image, width, height)
                    } catch (exception: Throwable) {
                        NovaTextLogger.d("projection image conversion failed: ${exception.javaClass.simpleName}")
                        null
                    } finally {
                        image.close()
                    }
                    finishCapture(bitmap)
                }, mainHandler)
                imageReader = reader
                surface = outputSurface
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "BigBangNovaProjection",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    outputSurface,
                    null,
                    mainHandler,
                )
                if (virtualDisplay == null) {
                    releaseDisplayLocked()
                    false
                } else {
                    val timeout = Runnable { finishCapture(null) }
                    captureTimeout = timeout
                    mainHandler.postDelayed(timeout, CAPTURE_TIMEOUT_MS)
                    true
                }
            } catch (exception: Throwable) {
                NovaTextLogger.d("projection screenshot failed: ${exception.javaClass.simpleName}")
                releaseDisplayLocked()
                false
            }
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)
        if (paddedWidth == width) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        return cropped
    }

    private fun finishCapture(bitmap: Bitmap?) {
        val callbacks: Pair<((Bitmap) -> Unit)?, ((Bitmap?) -> Unit)?> = synchronized(lock) {
            if (!captureActive) return
            captureActive = false
            val result = pendingCaptured to pendingFinished
            pendingCaptured = null
            pendingFinished = null
            captureTimeout?.let(mainHandler::removeCallbacks)
            captureTimeout = null
            releaseProjectionLocked()
            result
        }
        mainHandler.post {
            if (bitmap != null) callbacks.first?.invoke(bitmap)
            callbacks.second?.invoke(bitmap)
        }
    }

    private fun releaseDisplayLocked() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        surface?.release()
        surface = null
    }

    private fun releaseProjectionLocked() {
        releaseDisplayLocked()
        projectionCallback?.let { callback ->
            try {
                mediaProjection?.unregisterCallback(callback)
            } catch (_: Throwable) {
            }
        }
        projectionCallback = null
        mediaProjection?.stop()
        mediaProjection = null
        captureActive = false
        pendingCaptured = null
        pendingFinished = null
        captureTimeout?.let(mainHandler::removeCallbacks)
        captureTimeout = null
    }

    private const val CAPTURE_TIMEOUT_MS = 2_000L
}
