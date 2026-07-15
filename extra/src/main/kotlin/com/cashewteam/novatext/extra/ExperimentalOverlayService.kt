package com.cashewteam.novatext.extra

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ExperimentalOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var settings: ExtraSettings
    private val handler = Handler(Looper.getMainLooper())
    private val gate = TriggerPolicy.Gate()
    private var overlay: View? = null
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var triggered = false
    private var blocked = false
    private val replayPath = Path()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = this
        settings = ExtraSettings(this)
        if (!settings.experimentalEnabled) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        blocked = TriggerPolicy.isExcludedPackage(lastForegroundPackage)
        running = true
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Nova Text Extra 实验性触控浮窗")
                .setContentText("正在接管触摸；点按此处可停止")
                .setOngoing(true)
                .setContentIntent(stopPendingIntent())
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent())
                .build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        if (active === this) {
            settings.experimentalEnabled = false
            active = null
            running = false
        }
        super.onDestroy()
    }

    private fun addOverlay() {
        if (blocked || overlay != null || !settings.experimentalEnabled) return
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event -> handleTouch(event) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        params.title = "Nova Text Extra Experimental Overlay"
        try {
            windowManager.addView(view, params)
            overlay = view
            overlayCreated = true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to create experimental overlay", error)
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        overlayCreated = false
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val config = settings.config().copy(enabled = true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                lastX = startX
                lastY = startY
                downAt = event.eventTime
                replayPath.rewind()
                replayPath.moveTo(startX, startY)
                triggered = gate.onEvent(event, config)
                if (triggered) launchNova(event)
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = event.rawX
                lastY = event.rawY
                replayPath.lineTo(lastX, lastY)
                if (!triggered && gate.onEvent(event, config)) {
                    triggered = true
                    launchNova(event)
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasTriggered = triggered
                lastX = event.rawX
                lastY = event.rawY
                replayPath.lineTo(lastX, lastY)
                gate.onEvent(event, config)
                triggered = false
                if (!wasTriggered) replay(Path(replayPath), event.eventTime - downAt)
            }
            MotionEvent.ACTION_CANCEL -> {
                gate.onEvent(event, config)
                triggered = false
                replayPath.rewind()
            }
        }
        return true
    }

    private fun launchNova(event: MotionEvent) {
        val packageName = packageNameForTrigger()
        removeOverlay()
        if (!NovaTextLauncher.launch(this, packageName, event.rawX.toInt(), event.rawY.toInt())) {
            triggered = false
            addOverlay()
        } else {
            ExtraDiagnostics.record(this, packageName, TriggerPolicy.readSample(event, settings.config().mode, 0))
        }
    }

    private fun replay(path: Path, duration: Long) {
        removeOverlay()
        val accessibility = ExtraAccessibilityService.active
        if (accessibility == null) {
            addOverlay()
            return
        }
        handler.postDelayed({
            accessibility.replay(path, duration) {
                if (!blocked && settings.experimentalEnabled) handler.postDelayed(::addOverlay, OVERLAY_RESTORE_DELAY_MS)
            }
        }, INPUT_WINDOW_RELEASE_DELAY_MS)
    }

    private fun packageNameForTrigger(): String = lastForegroundPackage
        .takeUnless { TriggerPolicy.isExcludedPackage(it) }
        ?: packageName

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Nova Text Extra", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun stopPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, ExperimentalOverlayService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "nova_text_extra.experimental"
        private const val NOTIFICATION_ID = 2301
        private const val ACTION_STOP = "com.cashewteam.novatext.extra.STOP_OVERLAY"
        private const val TAG = "ExperimentalOverlay"
        private const val INPUT_WINDOW_RELEASE_DELAY_MS = 40L
        private const val OVERLAY_RESTORE_DELAY_MS = 16L
        @Volatile private var active: ExperimentalOverlayService? = null
        @Volatile private var lastForegroundPackage = ""
        var running by mutableStateOf(false)
            private set
        var overlayCreated by mutableStateOf(false)
            private set

        fun start(context: Context): Boolean {
            val settings = ExtraSettings(context)
            if (!Settings.canDrawOverlays(context) || ExtraAccessibilityService.active == null || !settings.calibrated) return false
            settings.experimentalEnabled = true
            running = true
            ContextCompat.startForegroundService(context, Intent(context, ExperimentalOverlayService::class.java))
            return true
        }

        fun stop(context: Context) {
            ExtraSettings(context).experimentalEnabled = false
            running = false
            active?.stopSelf()
        }

        fun isRunning(): Boolean = running

        fun onForegroundPackage(packageName: String, className: String?) {
            if (packageName == TriggerPolicy.EXTRA && overlayCreated && className != ExtraActivity::class.java.name) return
            lastForegroundPackage = packageName
            active?.let { service ->
                service.blocked = TriggerPolicy.isExcludedPackage(packageName)
                if (service.blocked) service.removeOverlay() else service.addOverlay()
            }
        }
    }
}
