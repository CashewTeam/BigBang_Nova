package com.cashewteam.novatext.android.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Binder
import android.os.Parcel
import android.util.Log
import java.io.ByteArrayOutputStream

class ShizukuScreenshotUserService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_CAPTURE_SCREENSHOT -> {
                val width = data.readInt()
                val height = data.readInt()
                val debugLog = data.readInt() != 0
                val bitmap = captureScreenshot(width, height, debugLog)
                reply?.writeNoException()
                reply?.writeTypedObject<Bitmap>(bitmap, 0)
                bitmap?.recycle()
                true
            }
            TRANSACTION_DESTROY -> {
                reply?.writeNoException()
                System.exit(0)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun captureScreenshot(width: Int, height: Int, debugLog: Boolean): Bitmap? {
        try {
            val bitmap = screenshotViaSurfaceControl(width, height, debugLog)
            if (bitmap != null) {
                if (debugLog) Log.d(TAG, "surfacecontrol success ${bitmap.width}x${bitmap.height}")
                return if (bitmap.config == Bitmap.Config.HARDWARE) {
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    bitmap.recycle()
                    copy
                } else {
                    bitmap
                }
            }
            if (debugLog) Log.d(TAG, "surfacecontrol returned null")
        } catch (e: Throwable) {
            if (debugLog) Log.d(TAG, "surfacecontrol failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        return fallbackScreencapPng(debugLog)
    }

    private fun screenshotViaSurfaceControl(width: Int, height: Int, debugLog: Boolean): Bitmap? {
        val scClass = Class.forName("android.view.SurfaceControl")

        if (debugLog) {
            val methods = scClass.declaredMethods.filter { it.name == "screenshot" }
            Log.d(TAG, "surfacecontrol methods: ${methods.joinToString(" | ") { sig ->
                sig.parameterTypes.joinToString(",") { it.simpleName }
            }}")
        }

        val rect = Rect(0, 0, width, height)

        // screenshot(Rect, int, int, int)
        try {
            val m = scClass.getDeclaredMethod(
                "screenshot",
                Rect::class.java, Integer.TYPE, Integer.TYPE, Integer.TYPE,
            )
            m.isAccessible = true
            val bmp = m.invoke(null, rect, width, height, 0) as? Bitmap
            if (bmp != null) {
                if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,int) success")
                return bmp
            }
            if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,int) returned null")
        } catch (e: NoSuchMethodException) {
            if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,int) not found")
        } catch (e: Throwable) {
            if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,int) invoke failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // screenshot(Rect, int, int, boolean, int)
        try {
            val m = scClass.getDeclaredMethod(
                "screenshot",
                Rect::class.java, Integer.TYPE, Integer.TYPE, java.lang.Boolean.TYPE, Integer.TYPE,
            )
            m.isAccessible = true
            val bmp = m.invoke(null, rect, width, height, false, 0) as? Bitmap
            if (bmp != null) {
                if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,boolean,int) success")
                return bmp
            }
            if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,boolean,int) returned null")
        } catch (e: NoSuchMethodException) {
            if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,boolean,int) not found")
        } catch (e: Throwable) {
            if (debugLog) Log.d(TAG, "surfacecontrol screenshot(Rect,int,int,boolean,int) invoke failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        return null
    }

    private fun fallbackScreencapPng(debugLog: Boolean): Bitmap? {
        return try {
            if (debugLog) Log.d(TAG, "fallback to screencap -p")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p"))
            val bytes = process.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
            }
            process.errorStream.close()
            val exitCode = process.waitFor()
            if (exitCode == 0 && bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                if (debugLog) Log.d(TAG, "screencap fallback failed: exit=$exitCode size=${bytes.size}")
                null
            }
        } catch (e: Throwable) {
            if (debugLog) Log.d(TAG, "screencap fallback failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "BigBangNova"
        const val TRANSACTION_CAPTURE_SCREENSHOT = 1
        const val TRANSACTION_DESTROY = 16777115
    }
}
