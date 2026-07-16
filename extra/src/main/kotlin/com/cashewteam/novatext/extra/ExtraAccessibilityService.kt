package com.cashewteam.novatext.extra

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class ExtraAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        active = this
        ExperimentalTouchController.connect(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val inputMethodPackage = TriggerPolicy.inputMethodPackage(
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
        )
        if (packageName == inputMethodPackage) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            ExperimentalTouchController.onForegroundPackage(packageName)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ExperimentalTouchController.disconnect()
        stopExperimentalForeground()
        if (active === this) {
            active = null
        }
        super.onDestroy()
    }

    fun startExperimentalForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "实验性触控监听", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Nova Text Extra 正在监听触控")
            .setContentText("保持运行以便立即委托普通触控")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, ExtraActivity::class.java),
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

    fun stopExperimentalForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun isInputMethodVisible(): Boolean {
        val activeWindows = windows
        val visible = activeWindows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        Log.d(TAG, "ime_visible=$visible source=windows count=${activeWindows.size}")
        return visible
    }

    companion object {
        private const val NOTIFICATION_CHANNEL = "experimental_touch"
        private const val NOTIFICATION_ID = 2201
        private const val TAG = "NovaExtraTouch"
        @Volatile var active: ExtraAccessibilityService? = null
            private set
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ExtraAccessibilityService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.mapNotNull(ComponentName::unflattenFromString)
                ?.any { it == expected } == true
        }
    }
}
