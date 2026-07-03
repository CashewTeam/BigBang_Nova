package com.cashewteam.novatext.android.service

import android.os.Binder
import android.os.Parcel
import java.io.ByteArrayOutputStream

class ShizukuScreenshotUserService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_CAPTURE_SCREENSHOT -> {
                val bytes = captureScreenshotBytes()
                reply?.writeNoException()
                reply?.writeByteArray(bytes)
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

    private fun captureScreenshotBytes(): ByteArray? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "screencap -p"))
            val bytes = process.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toByteArray()
                }
            }
            process.errorStream.close()
            val exitCode = process.waitFor()
            if (exitCode == 0 && bytes.isNotEmpty()) bytes else null
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val TRANSACTION_CAPTURE_SCREENSHOT = 1
        const val TRANSACTION_DESTROY = 16777115
    }
}
