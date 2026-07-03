package com.cashewteam.novatext.android.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import com.cashewteam.novatext.android.BuildConfig
import com.cashewteam.novatext.android.util.NovaTextLogger
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object ShizukuScreenshotCapture {
    private const val REQUEST_CODE = 2901
    private const val SERVICE_VERSION = 1
    private const val SERVICE_TIMEOUT_SECONDS = 5L

    enum class Status {
        NOT_ANDROID_10,
        SERVICE_UNAVAILABLE,
        PERMISSION_REQUIRED,
        READY,
    }

    fun getStatus(): Status {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            return Status.NOT_ANDROID_10
        }
        return try {
            if (!Shizuku.pingBinder()) {
                Status.SERVICE_UNAVAILABLE
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Status.READY
            } else {
                Status.PERMISSION_REQUIRED
            }
        } catch (_: Throwable) {
            Status.SERVICE_UNAVAILABLE
        }
    }

    fun requestPermission(): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) return false
        return try {
            if (!Shizuku.pingBinder()) {
                false
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                false
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun capture(): Bitmap? {
        if (getStatus() != Status.READY) return null
        return try {
            val bytes = captureBytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (exception: Throwable) {
            NovaTextLogger.d("shizuku screenshot failed: ${exception.javaClass.simpleName}")
            null
        }
    }

    private fun captureBytes(): ByteArray? {
        val binder = bindScreenshotService() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            val ok = binder.transact(
                ShizukuScreenshotUserService.TRANSACTION_CAPTURE_SCREENSHOT,
                data,
                reply,
                0,
            )
            if (!ok) {
                NovaTextLogger.d("shizuku screenshot failed: transact false")
                return null
            }
            reply.readException()
            return reply.createByteArray()
        } finally {
            data.recycle()
            reply.recycle()
            unbindScreenshotService()
        }
    }

    private fun bindScreenshotService(): IBinder? {
        val latch = CountDownLatch(1)
        val binderRef = AtomicReference<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binderRef.set(service)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                latch.countDown()
            }
        }
        return try {
            Shizuku.bindUserService(serviceArgs(), connection)
            if (!latch.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                NovaTextLogger.d("shizuku screenshot failed: service timeout")
                null
            } else {
                binderRef.get()
            }
        } catch (exception: Throwable) {
            NovaTextLogger.d("shizuku screenshot failed: bind ${exception.javaClass.simpleName}")
            null
        }
    }

    private fun unbindScreenshotService() {
        try {
            Shizuku.unbindUserService(serviceArgs(), null, true)
        } catch (_: Throwable) {
        }
    }

    private fun serviceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(
                BuildConfig.APPLICATION_ID,
                ShizukuScreenshotUserService::class.java.name,
            ),
        )
            .processNameSuffix("shizuku_screenshot")
            .tag("screenshot")
            .version(SERVICE_VERSION)
            .daemon(false)
    }
}
