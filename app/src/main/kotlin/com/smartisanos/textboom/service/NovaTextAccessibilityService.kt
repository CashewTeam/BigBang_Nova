package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.cashewteam.novatext.android.TextBoomSettingsActivity
import com.cashewteam.novatext.android.util.NovaTextLogger

class NovaTextAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        ExperimentalTouchController.connect(this)
        NovaTextLogger.d("accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val packageName = event?.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            ExperimentalTouchController.onForegroundPackage(packageName)
            if (packageName != packageName()) {
                latestExternalPackage = packageName
                latestExternalPackageAt = SystemClock.elapsedRealtime()
            }
        }
    }

    override fun onInterrupt() {
        NovaTextLogger.d("accessibility service interrupted")
    }

    override fun onDestroy() {
        ExperimentalTouchController.disconnect()
        stopExperimentalTouchForeground()
        if (activeInstance === this) {
            activeInstance = null
        }
        NovaTextLogger.d("accessibility service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val PACKAGE_CACHE_TTL_MS = 2_000L
        private const val NOTIFICATION_CHANNEL = "experimental_touch"
        private const val NOTIFICATION_ID = 2202

        @Volatile
        var activeInstance: NovaTextAccessibilityService? = null
            private set

        @Volatile
        private var latestExternalPackage: String? = null

        @Volatile
        private var latestExternalPackageAt: Long = 0L

        fun latestActivePackage(selfPackage: String): String? {
            val cached = latestExternalPackage
            if (cached.isNullOrBlank() || cached == selfPackage) return null
            val ageMs = SystemClock.elapsedRealtime() - latestExternalPackageAt
            return cached.takeIf { ageMs in 0..PACKAGE_CACHE_TTL_MS }
        }
    }

    private fun packageName(): String = applicationContext.packageName

    fun startExperimentalTouchForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "实验性触控监听", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Nova Text 正在监听触控")
            .setContentText("点按可打开实验性触控设置并停止监听")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    TextBoomSettingsActivity.createExperimentalTouchIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun stopExperimentalTouchForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun isInputMethodVisible(): Boolean {
        val activeWindows = windows
        val visible = activeWindows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        NovaTextLogger.d("experimental_touch ime_visible=$visible windowCount=${activeWindows.size}")
        return visible
    }

}
