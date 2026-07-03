package com.cashewteam.novatext.android.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import com.cashewteam.novatext.android.BuildConfig
import com.cashewteam.novatext.android.util.NovaTextLogger
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object ShizukuScreenshotCapture {
    private const val REQUEST_CODE = 2901
    private const val SERVICE_VERSION = 6
    private const val SERVICE_TIMEOUT_SECONDS = 5L

    @Volatile
    private var cachedBinder: IBinder? = null
    private var cachedConnection: ServiceConnection? = null
    private val bindLock = Any()

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

    fun capture(width: Int, height: Int, debugLog: Boolean = false): Bitmap? {
        val status = getStatus()
        if (status != Status.READY) {
            if (debugLog) NovaTextLogger.d("shizuku screenshot skipped: status=$status")
            return null
        }
        return try {
            captureBitmap(width, height, debugLog)
        } catch (exception: Throwable) {
            if (debugLog) NovaTextLogger.d("shizuku screenshot failed: ${exception.javaClass.simpleName}")
            null
        }
    }

    fun preBind() {
        if (getStatus() != Status.READY) return
        if (cachedBinder?.isBinderAlive == true) return
        thread(name = "shizuku-prebind") {
            obtainBinder()
        }
    }

    private fun captureBitmap(width: Int, height: Int, debugLog: Boolean): Bitmap? {
        val binder = obtainBinder()
        if (binder == null) {
            if (debugLog) NovaTextLogger.d("shizuku screenshot failed: no binder")
            return null
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInt(width)
            data.writeInt(height)
            data.writeInt(if (debugLog) 1 else 0)
            val ok = binder.transact(
                ShizukuScreenshotUserService.TRANSACTION_CAPTURE_SCREENSHOT,
                data,
                reply,
                0,
            )
            if (!ok) {
                if (debugLog) NovaTextLogger.d("shizuku screenshot failed: transact false")
                invalidateCache()
                return null
            }
            reply.readException()
            val bitmap = reply.readTypedObject(Bitmap.CREATOR)
            if (bitmap == null && debugLog) {
                NovaTextLogger.d("shizuku screenshot failed: service returned null bitmap")
            }
            return bitmap
        } catch (e: RemoteException) {
            if (debugLog) NovaTextLogger.d("shizuku screenshot failed: ${e.javaClass.simpleName}")
            invalidateCache()
            return null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun obtainBinder(): IBinder? {
        cachedBinder?.let { cached ->
            if (cached.isBinderAlive) return cached
        }
        synchronized(bindLock) {
            cachedBinder?.let { cached ->
                if (cached.isBinderAlive) return cached
            }
            return bindFresh()
        }
    }

    private fun bindFresh(): IBinder? {
        val latch = CountDownLatch(1)
        val binderRef = AtomicReference<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binderRef.set(service)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                cachedBinder = null
            }
        }
        return try {
            Shizuku.bindUserService(serviceArgs(), connection)
            if (!latch.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                NovaTextLogger.d("shizuku screenshot failed: service timeout")
                null
            } else {
                val binder = binderRef.get()
                if (binder != null) {
                    cachedBinder = binder
                    cachedConnection = connection
                }
                binder
            }
        } catch (exception: Throwable) {
            NovaTextLogger.d("shizuku screenshot failed: bind ${exception.javaClass.simpleName}")
            null
        }
    }

    private fun invalidateCache() {
        cachedBinder = null
        val conn = cachedConnection
        if (conn != null) {
            try {
                Shizuku.unbindUserService(serviceArgs(), conn, false)
            } catch (_: Throwable) {
            }
            cachedConnection = null
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
            .daemon(true)
    }
}
