package com.smartisanos.textboom.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.smartisanos.textboom.R
import com.smartisanos.textboom.data.BigBangPreferences
import com.smartisanos.textboom.util.NovaTextLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingBallService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: BigBangPreferences
    private var bubbleView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val bubbleHandler = Handler(Looper.getMainLooper())
    private val fadeBubbleRunnable = Runnable {
        bubbleView?.animate()?.alpha(IDLE_ALPHA)?.setDuration(FADE_DURATION_MS)?.start()
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var lastTapAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        isRunning = true
        activeState.value = true
        preferences = BigBangPreferences(this)
        preferences.setFloatingBallEnabled(true)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prepare(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        attachBubble()
        NovaTextLogger.d("floating ball service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                preferences.setFloatingBallEnabled(false)
                stopSelf()
            }
            ACTION_RESET_POSITION -> resetPositionNow()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
        isRunning = false
        activeState.value = false
        activeService = null
        NovaTextLogger.d("floating ball service destroyed")
        super.onDestroy()
    }

    private fun attachBubble() {
        if (bubbleView != null) return
        val bubble = ImageView(this).apply {
            setImageResource(R.drawable.icon_bigbang)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.overlay_notification_title)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            elevation = 18f
            alpha = IDLE_ALPHA
        }

        layoutParams = WindowManager.LayoutParams(
            BUBBLE_SIZE_PX,
            BUBBLE_SIZE_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX
            y = anchorY
        }
        if (anchorX == DEFAULT_ANCHOR_X && anchorY == DEFAULT_ANCHOR_Y) {
            moveToDefaultPosition()
        } else {
            clampPositionInPlace(layoutParams)
        }

        bubble.setOnClickListener {
            showActiveBubble()
        }
        bubble.setOnTouchListener { _, event -> handleTouch(event) }
        bubbleView = bubble
        windowManager.addView(bubble, layoutParams)
        scheduleBubbleFade()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                showActiveBubble()
                downRawX = event.rawX
                downRawY = event.rawY
                downX = layoutParams.x
                downY = layoutParams.y
                val now = System.currentTimeMillis()
                if (now - lastTapAt <= DOUBLE_TAP_WINDOW_MS) {
                    mode = MODE_RELOCATE
                } else if (mode != MODE_RELOCATE) {
                    mode = MODE_DETECT
                }
                lastTapAt = now
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                layoutParams.x = downX + (event.rawX - downRawX).toInt()
                layoutParams.y = downY + (event.rawY - downRawY).toInt()
                clampPositionInPlace(layoutParams)
                if (mode == MODE_RELOCATE) {
                    anchorX = layoutParams.x
                    anchorY = layoutParams.y
                }
                updateBubbleLayout()
                return true
            }

            MotionEvent.ACTION_UP -> {
                scheduleBubbleFade()
                val moved = abs(event.rawX - downRawX) > MOVE_THRESHOLD_PX ||
                    abs(event.rawY - downRawY) > MOVE_THRESHOLD_PX
                if (!moved) {
                    bubbleView?.performClick()
                    return true
                }

                if (mode == MODE_RELOCATE) {
                    saveCurrentPositionAsAnchor()
                    mode = MODE_IDLE
                } else {
                    val sampleX = layoutParams.x + BUBBLE_SIZE_PX / 2
                    val sampleY = layoutParams.y + BUBBLE_SIZE_PX / 2
                    layoutParams.x = anchorX
                    layoutParams.y = anchorY
                    clampPositionInPlace(layoutParams)
                    updateBubbleLayout()
                    mode = MODE_IDLE
                    BigBangCaptureDispatcher.captureAt(applicationContext, sampleX, sampleY)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                scheduleBubbleFade()
                mode = MODE_IDLE
                return true
            }
        }
        return false
    }

    private fun showActiveBubble() {
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        bubbleView?.animate()?.cancel()
        bubbleView?.alpha = 1f
    }

    private fun scheduleBubbleFade() {
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        bubbleHandler.postDelayed(fadeBubbleRunnable, IDLE_FADE_DELAY_MS)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    private fun resetPositionNow() {
        moveToDefaultPosition()
        updateBubbleLayout()
    }

    private fun clampPositionInPlace(params: WindowManager.LayoutParams) {
        val safeArea = getSafeArea()
        val safeOutsideOffset = (BUBBLE_SIZE_PX * MAX_OFFSCREEN_RATIO).roundToInt()
        val minX = safeArea.left - safeOutsideOffset
        val maxX = safeArea.right - BUBBLE_SIZE_PX + safeOutsideOffset
        val minY = safeArea.top - safeOutsideOffset
        val maxY = safeArea.bottom - BUBBLE_SIZE_PX + safeOutsideOffset
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
    }

    private fun moveToDefaultPosition() {
        val safeArea = getSafeArea()
        val offset = (BUBBLE_SIZE_PX * MAX_OFFSCREEN_RATIO).roundToInt()
        layoutParams.x = safeArea.right - BUBBLE_SIZE_PX + offset
        layoutParams.y = safeArea.top + DEFAULT_TOP_MARGIN_PX
        clampPositionInPlace(layoutParams)
        anchorX = layoutParams.x
        anchorY = layoutParams.y
    }

    private fun saveCurrentPositionAsAnchor() {
        clampPositionInPlace(layoutParams)
        anchorX = layoutParams.x
        anchorY = layoutParams.y
    }

    @Suppress("DEPRECATION")
    private fun getWindowSize(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        val display: Display = windowManager.defaultDisplay
        return Point().also { display.getSize(it) }
    }

    @Suppress("DEPRECATION")
    private fun getSafeArea(): Rect {
        val windowSize = getWindowSize()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
            )
            return Rect(
                insets.left,
                insets.top,
                metrics.bounds.width() - insets.right,
                metrics.bounds.height() - insets.bottom,
            )
        }
        val statusBarHeight = getSystemDimension("status_bar_height")
        val navigationBarHeight = getSystemDimension("navigation_bar_height")
        return Rect(0, statusBarHeight, windowSize.x, windowSize.y - navigationBarHeight)
    }

    private fun getSystemDimension(name: String): Int {
        val resourceId = resources.getIdentifier(name, "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun updateBubbleLayout() {
        bubbleView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    companion object {
        private const val CHANNEL_ID = "bigbang.overlay"
        private const val NOTIFICATION_ID = 1001
        private const val DOUBLE_TAP_WINDOW_MS = 320L
        private const val MOVE_THRESHOLD_PX = 8f
        private const val IDLE_FADE_DELAY_MS = 3_000L
        private const val FADE_DURATION_MS = 240L
        private const val IDLE_ALPHA = 0.15f
        private const val BUBBLE_SIZE_PX = 160
        private const val MAX_OFFSCREEN_RATIO = 0.25f
        private const val DEFAULT_ANCHOR_X = 0
        private const val DEFAULT_ANCHOR_Y = 280
        private const val DEFAULT_TOP_MARGIN_PX = 220
        private const val MODE_IDLE = "idle"
        private const val MODE_DETECT = "detect"
        private const val MODE_RELOCATE = "relocate"

        const val ACTION_START = "com.smartisanos.textboom.action.START_FLOATING_BALL"
        const val ACTION_STOP = "com.smartisanos.textboom.action.STOP_FLOATING_BALL"
        const val ACTION_RESET_POSITION = "com.smartisanos.textboom.action.RESET_FLOATING_BALL"

        @Volatile
        private var isRunning = false

        @Volatile
        private var activeService: FloatingBallService? = null

        @Volatile
        private var mode = MODE_IDLE

        @Volatile
        private var anchorX = DEFAULT_ANCHOR_X

        @Volatile
        private var anchorY = DEFAULT_ANCHOR_Y

        @Volatile
        private var prepared = false

        private val activeState = MutableStateFlow(false)

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                NovaTextLogger.d("overlay permission missing, skip starting floating ball")
                return
            }
            if (isRunning) return
            val intent = Intent(context, FloatingBallService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (!isRunning) {
                BigBangPreferences(context).setFloatingBallEnabled(false)
                activeState.value = false
                return
            }
            val intent = Intent(context, FloatingBallService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun resetPosition(context: Context) {
            if (!isRunning) {
                anchorX = DEFAULT_ANCHOR_X
                anchorY = DEFAULT_ANCHOR_Y
                return
            }
            val intent = Intent(context, FloatingBallService::class.java).setAction(ACTION_RESET_POSITION)
            context.startService(intent)
        }

        fun isActive(): Boolean = isRunning

        fun getActiveStateFlow(): StateFlow<Boolean> = activeState

        private fun prepare(context: Context) {
            if (prepared) return
            synchronized(this) {
                if (prepared) return
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.overlay_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.overlay_channel_description)
                    }
                    manager.createNotificationChannel(channel)
                }
                prepared = true
            }
        }
    }
}
