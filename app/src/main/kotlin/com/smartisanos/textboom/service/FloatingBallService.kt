package com.cashewteam.novatext.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cashewteam.novatext.android.R
import com.cashewteam.novatext.android.data.BigBangPreferences
import com.cashewteam.novatext.android.data.BigBangSettings
import com.cashewteam.novatext.android.util.NovaTextLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicInteger

class FloatingBallService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: BigBangPreferences
    private lateinit var settings: BigBangSettings
    private var bubbleView: View? = null
    private var bubbleIconView: ImageView? = null
    private var blueCapsuleView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val bubbleHandler = Handler(Looper.getMainLooper())
    private val fadeBubbleRunnable = Runnable {
        ballIdle = true
        updateBubbleChrome()
        bubbleView?.animate()?.alpha(idleAlpha())?.setDuration(FADE_DURATION_MS)?.start()
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var lastTapAt = 0L
    private var capsuleBackgroundVisible = true
    private var ballIdle = true
    private var lastSafeArea: Rect? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var oneHandSensorRegistered = false
    private var lastOneHandCheckAt = 0L
    private var launchLoopView: View? = null
    private var launchLoopFrame: FrameLayout? = null
    private var launchLoopRotateView: ImageView? = null
    private var launchLoopAnimator: ObjectAnimator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        isRunning = true
        activeState.value = true
        preferences = BigBangPreferences(this)
        settings = BigBangSettings.get(this)
        preferences.setFloatingBallEnabled(true)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        prepare(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            when {
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    !settings.isUseShizukuScreenshotEnabled ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else -> 0
            },
        )
        attachBubble()
        updateOneHandSensor()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        realignForConfigurationChange()
    }

    override fun onDestroy() {
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        unregisterOneHandSensor()
        hideLaunchLoopInternal()
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
        isRunning = false
        activeState.value = false
        activeService = null
        NovaTextLogger.d("floating ball service destroyed")
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()
        if (now - lastOneHandCheckAt < ONE_HAND_CHECK_INTERVAL_MS) return
        lastOneHandCheckAt = now
        if (!settings.isFloatingBallOneHandModeEnabled || mode != MODE_IDLE) return
        val targetSide = resolveGravityDockSide(event.values[0], event.values[1]) ?: return
        if (targetSide == dockedSide || !::layoutParams.isInitialized) return
        dockToSide(targetSide, layoutParams.y)
        updateBubbleLayout()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun attachBubble() {
        if (bubbleView != null) return
        val iconSizePx = bubbleSizePx()
        val bubble = FrameLayout(this).apply {
            contentDescription = getString(R.string.overlay_notification_title)
            elevation = 18f
            alpha = 0f
            scaleX = BUBBLE_APPEAR_START_SCALE
            scaleY = BUBBLE_APPEAR_START_SCALE
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.icon_bigbang)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }
        bubble.addView(icon)
        val blueCapsule = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = iconSizePx / 2f
                setColor(BLUE_CAPSULE_COLOR)
            }
        }
        bubble.addView(blueCapsule)
        bubbleView = bubble
        bubbleIconView = icon
        blueCapsuleView = blueCapsule
        updateBubbleChrome()

        layoutParams = WindowManager.LayoutParams(
            capsuleWidthPx(iconSizePx),
            iconSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX
            y = anchorY
        }
        if (!anchorInitialized) {
            moveToDefaultPosition()
        } else {
            dockToSide(dockedSide, anchorY)
        }

        bubble.setOnClickListener { /* handled by handleTouch */ }
        bubble.setOnTouchListener { _, event -> handleTouch(event) }
        windowManager.addView(bubble, layoutParams)
        applyVisibilitySuppressionState()
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
                setCapsuleBackgroundVisible(false)
                clampPositionInPlace(layoutParams)
                updateBubbleLayout()
                return true
            }

            MotionEvent.ACTION_UP -> {
                setCapsuleBackgroundVisible(true)
                val moved = abs(event.rawX - downRawX) > MOVE_THRESHOLD_PX ||
                    abs(event.rawY - downRawY) > MOVE_THRESHOLD_PX
                if (!moved) {
                    if (mode == MODE_RELOCATE) {
                        mode = MODE_IDLE
                        lastTapAt = 0L
                        BoomOcrLauncher.launchSelectionCapture(
                            context = applicationContext,
                            forceSystemBack = false,
                            captureDelayMs = 0,
                        )
                        return true
                    }
                    scheduleBubbleFade()
                    bubbleView?.performClick()
                    return true
                }

                if (mode == MODE_RELOCATE) {
                    scheduleBubbleFade()
                    saveCurrentPositionAsAnchor()
                    updateBubbleLayout()
                    mode = MODE_IDLE
                } else {
                    val bubbleCenter = getBubbleIconCenterOnScreen()
                    val sampleX = bubbleCenter?.x ?: (layoutParams.x + layoutParams.width / 2)
                    val sampleY = bubbleCenter?.y ?: (layoutParams.y + layoutParams.height / 2)
                    beginCaptureLaunchSuppression()
                    val nextY = if (settings.isFloatingBallHeightLocked) anchorY else layoutParams.y
                    if (settings.isFloatingBallSideLocked) {
                        dockToSide(dockedSide, nextY)
                    } else {
                        dockToNearestSide(sampleX, nextY)
                    }
                    updateBubbleLayout()
                    mode = MODE_IDLE
                    BigBangCaptureDispatcher.captureAt(applicationContext, sampleX, sampleY)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                setCapsuleBackgroundVisible(true)
                scheduleBubbleFade()
                mode = MODE_IDLE
                return true
            }
        }
        return false
    }

    private fun showActiveBubble() {
        if (isVisibilitySuppressed()) return
        ballIdle = false
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        bubbleView?.animate()?.cancel()
        bubbleView?.alpha = activeAlpha()
        updateBubbleChrome()
    }

    private fun scheduleBubbleFade() {
        if (isVisibilitySuppressed()) return
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        bubbleHandler.postDelayed(fadeBubbleRunnable, IDLE_FADE_DELAY_MS)
    }

    private fun buildNotification(): Notification {
        val captureIntent = Intent(this, OcrSelectionActionReceiver::class.java)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(
                PendingIntent.getBroadcast(
                    this,
                    0,
                    captureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .build()
    }

    private fun showLaunchLoopAtInternal(x: Int, y: Int) {
        if (!::windowManager.isInitialized) return
        if (launchLoopView == null) {
            val view = LayoutInflater.from(this).inflate(R.layout.boom_ocr_launch_layout, null)
            view.visibility = View.INVISIBLE
            launchLoopView = view
            launchLoopFrame = view.findViewById(R.id.anim_loop)
            launchLoopRotateView = view.findViewById(R.id.loop_rotate)
            windowManager.addView(
                view,
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayWindowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                },
            )
        }
        val frame = launchLoopFrame ?: return
        val rotate = launchLoopRotateView ?: return
        val root = launchLoopView ?: return
        frame.visibility = View.INVISIBLE
        rotate.visibility = View.INVISIBLE
        frame.animate().cancel()
        frame.alpha = 0f
        frame.scaleX = LAUNCH_LOOP_START_SCALE
        frame.scaleY = LAUNCH_LOOP_START_SCALE
        frame.post {
            val width = frame.width.takeIf { it > 0 } ?: frame.measuredWidth
            val height = frame.height.takeIf { it > 0 } ?: frame.measuredHeight
            if (width > 0 && height > 0) {
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                frame.translationX = (x - location[0]) - width / 2f
                frame.translationY = (y - location[1]) - height / 2f
            }
            root.visibility = View.VISIBLE
            frame.visibility = View.VISIBLE
            rotate.visibility = View.VISIBLE
            frame.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(LAUNCH_LOOP_SCALE_DURATION_MS)
                .start()
        }
        if (launchLoopAnimator?.isRunning == true) return
        launchLoopAnimator = ObjectAnimator.ofFloat(rotate, "rotation", rotate.rotation, rotate.rotation + 360f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun hideLaunchLoopInternal() {
        launchLoopAnimator?.cancel()
        launchLoopAnimator = null
        launchLoopRotateView = null
        launchLoopFrame = null
        launchLoopView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        launchLoopView = null
    }

    private fun resetPositionNow() {
        moveToDefaultPosition()
        updateBubbleLayout()
    }

    private fun clampPositionInPlace(params: WindowManager.LayoutParams) {
        val safeArea = getSafeArea()
        val horizontalBounds = getHorizontalBounds()
        val iconInset = params.width - params.height
        val minX = horizontalBounds.left - iconInset
        val maxX = horizontalBounds.right - rightDockedWidth(params)
        val minY = 0
        val maxY = safeArea.bottom - params.height
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
    }

    private fun moveToDefaultPosition() {
        val safeArea = getSafeArea()
        dockToSide(DOCK_RIGHT, safeArea.top + DEFAULT_TOP_MARGIN_PX)
    }

    private fun saveCurrentPositionAsAnchor() {
        dockToNearestSide(layoutParams.x + layoutParams.width / 2, layoutParams.y)
    }

    private fun dockToNearestSide(centerX: Int, y: Int) {
        val horizontalBounds = getHorizontalBounds()
        val width = horizontalBounds.width().toFloat()
        val leftSwitchBoundary =
            horizontalBounds.left + (width * EDGE_SWITCH_REGION_RATIO).roundToInt()
        val rightSwitchBoundary =
            horizontalBounds.right - (width * EDGE_SWITCH_REGION_RATIO).roundToInt()
        val targetSide = when {
            centerX <= leftSwitchBoundary -> DOCK_LEFT
            centerX >= rightSwitchBoundary -> DOCK_RIGHT
            else -> dockedSide
        }
        dockToSide(targetSide, y)
    }

    private fun dockToSide(side: Int, y: Int) {
        dockedSide = side
        val iconSizePx = bubbleSizePx()
        layoutParams.width = capsuleWidthPx(iconSizePx)
        layoutParams.height = iconSizePx
        val horizontalBounds = getHorizontalBounds()
        val safeArea = getSafeArea()
        layoutParams.x = if (side == DOCK_LEFT) {
            horizontalBounds.left - (layoutParams.width - layoutParams.height)
        } else {
            horizontalBounds.right - rightDockedWidth(layoutParams)
        }
        layoutParams.y = y
        clampPositionInPlace(layoutParams)
        anchorX = layoutParams.x
        anchorY = layoutParams.y
        anchorInitialized = true
        lastSafeArea = Rect(safeArea)
        updateBubbleChrome()
    }

    private fun realignForConfigurationChange() {
        if (!::layoutParams.isInitialized || bubbleView == null) return
        val previousSafeArea = lastSafeArea ?: getSafeArea()
        val previousRange = (previousSafeArea.height() - layoutParams.height).coerceAtLeast(0)
        val verticalRatio = if (previousRange == 0) {
            0f
        } else {
            ((anchorY - previousSafeArea.top).toFloat() / previousRange).coerceIn(0f, 1f)
        }
        val newSafeArea = getSafeArea()
        val newRange = (newSafeArea.height() - bubbleSizePx()).coerceAtLeast(0)
        val newY = newSafeArea.top + (newRange * verticalRatio).roundToInt()
        setCapsuleBackgroundVisible(true)
        dockToSide(dockedSide, newY)
        bubbleView?.alpha = idleAlpha()
        updateBubbleLayout()
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

    private fun getHorizontalBounds(): Rect {
        val windowSize = getWindowSize()
        if (isLandscape()) {
            if (settings.isFloatingBallLandscapeSafeAreaEnabled) {
                val safeArea = getSafeArea()
                return Rect(0, 0, safeArea.width(), windowSize.y)
            }
            return Rect(0, 0, windowSize.x, windowSize.y)
        }
        return Rect(0, 0, windowSize.x, windowSize.y)
    }

    private fun getSystemDimension(name: String): Int {
        val resourceId = resources.getIdentifier(name, "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun updateBubbleLayout() {
        bubbleView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun updateOneHandSensor() {
        if (settings.isFloatingBallOneHandModeEnabled) {
            registerOneHandSensor()
        } else {
            unregisterOneHandSensor()
        }
    }

    private fun registerOneHandSensor() {
        if (oneHandSensorRegistered) return
        val sensor = accelerometer ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        oneHandSensorRegistered = true
    }

    private fun unregisterOneHandSensor() {
        if (!oneHandSensorRegistered) return
        sensorManager?.unregisterListener(this)
        oneHandSensorRegistered = false
    }

    @Suppress("DEPRECATION")
    private fun resolveGravityDockSide(x: Float, y: Float): Int? {
        val rotation = windowManager.defaultDisplay.rotation
        val horizontalGravity = when (rotation) {
            Surface.ROTATION_90 -> -y
            Surface.ROTATION_270 -> y
            Surface.ROTATION_180 -> -x
            else -> x
        }
        val angle = Math.toDegrees(atan2(horizontalGravity.toDouble(), EARTH_GRAVITY)).toFloat()
        val threshold = settings.floatingBallOneHandAngleDegrees.toFloat()
        return when {
            angle >= threshold -> DOCK_LEFT
            angle <= -threshold -> DOCK_RIGHT
            else -> null
        }
    }

    private fun getBubbleIconCenterOnScreen(): Point? {
        val view = bubbleIconView ?: bubbleView ?: return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Point(
            location[0] + view.width / 2,
            location[1] + view.height / 2,
        )
    }

    private fun bubbleSizePx(): Int {
        return (BASE_BUBBLE_SIZE_PX * (settings.floatingBallSizePercent / 100f))
            .roundToInt()
            .coerceAtLeast(MIN_BUBBLE_SIZE_PX)
    }

    private fun capsuleWidthPx(iconSizePx: Int): Int {
        return (iconSizePx * CAPSULE_WIDTH_RATIO).roundToInt()
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun rightDockedWidth(params: WindowManager.LayoutParams): Int {
        if (isLandscape()) {
            return if (settings.isFloatingBallLandscapeSafeAreaEnabled) {
                params.width + params.height / 2
                
            } else {
                params.width - params.height / 2
            }
        }
        return params.height
    }

    private fun activeAlpha(): Float {
        return (settings.floatingBallActiveAlphaPercent / 100f).coerceIn(0f, 1f)
    }

    private fun idleAlpha(): Float {
        return (settings.floatingBallIdleAlphaPercent / 100f).coerceIn(0f, 1f)
    }

    private fun refreshBubbleAppearance() {
        if (!::layoutParams.isInitialized) return
        updateOneHandSensor()
        val bubbleSizePx = bubbleSizePx()
        layoutParams.width = capsuleWidthPx(bubbleSizePx)
        layoutParams.height = bubbleSizePx
        clampPositionInPlace(layoutParams)
        dockToSide(dockedSide, layoutParams.y)
        if (!isVisibilitySuppressed()) {
            bubbleView?.alpha = idleAlpha()
        }
        updateBubbleChrome()
        updateBubbleLayout()
    }

    private fun applyVisibilitySuppressionState() {
        bubbleHandler.removeCallbacks(fadeBubbleRunnable)
        val bubble = bubbleView ?: return
        bubble.animate()?.cancel()
        if (shouldHideBubble()) {
            ballIdle = false
            bubble.visibility = View.INVISIBLE
            bubble.alpha = 0f
            bubble.scaleX = BUBBLE_APPEAR_START_SCALE
            bubble.scaleY = BUBBLE_APPEAR_START_SCALE
        } else {
            val startIdle = isSearchOverlayActive()
            ballIdle = startIdle
            val targetAlpha = if (startIdle) idleAlpha() else activeAlpha()
            val shouldAnimateIn = bubble.visibility != View.VISIBLE || bubble.alpha <= 0f
            bubble.visibility = View.VISIBLE
            updateBubbleChrome()
            if (shouldAnimateIn) {
                bubble.alpha = 0f
                bubble.scaleX = BUBBLE_APPEAR_START_SCALE
                bubble.scaleY = BUBBLE_APPEAR_START_SCALE
                bubble.animate()
                    .alpha(targetAlpha)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(BUBBLE_APPEAR_DURATION_MS)
                    .start()
            } else {
                bubble.alpha = targetAlpha
                bubble.scaleX = 1f
                bubble.scaleY = 1f
            }
            if (!startIdle) {
                scheduleBubbleFade()
            }
        }
    }

    private fun updateBubbleChrome() {
        val iconSizePx = bubbleSizePx()
        val hiddenMode = settings.isFloatingBallHidden()
        val isIdle = ballIdle && capsuleBackgroundVisible
        val showCapsuleBg = capsuleBackgroundVisible && !isLandscape()
        bubbleView?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = iconSizePx / 2f
            setColor(
                if (hiddenMode && isIdle) {
                    Color.TRANSPARENT
                } else if (showCapsuleBg) {
                    CAPSULE_BACKGROUND_COLOR
                } else {
                    Color.TRANSPARENT
                },
            )
        }
        bubbleIconView?.layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx).apply {
            gravity = if (dockedSide == DOCK_LEFT) {
                Gravity.END or Gravity.CENTER_VERTICAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }
        }
        bubbleIconView?.visibility = if (hiddenMode && isIdle) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
        val capsuleW = capsuleWidthPx(iconSizePx)
        val iconCenterX = if (dockedSide == DOCK_LEFT) {
            capsuleW - iconSizePx / 2
        } else {
            iconSizePx / 2
        }
        val blueWidthPx = dp(4f)
        val gapPx = dp(10f)
        blueCapsuleView?.layoutParams = FrameLayout.LayoutParams(blueWidthPx, iconSizePx).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginStart = if (dockedSide == DOCK_RIGHT) {
                iconCenterX + gapPx + (0.2 * blueWidthPx).toInt()//确保和左侧对称
            } else {
                iconCenterX - gapPx - blueWidthPx
            }
        }
        blueCapsuleView?.visibility = if (hiddenMode && isIdle) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun setCapsuleBackgroundVisible(visible: Boolean) {
        if (capsuleBackgroundVisible == visible) return
        capsuleBackgroundVisible = visible
        updateBubbleChrome()
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    companion object {
        private const val CHANNEL_ID = "bigbang.overlay"
        private const val NOTIFICATION_ID = 1001
        private const val DOUBLE_TAP_WINDOW_MS = 320L
        private const val MOVE_THRESHOLD_PX = 8f
        private const val IDLE_FADE_DELAY_MS = 3_000L
        private const val FADE_DURATION_MS = 240L
        private const val BUBBLE_APPEAR_DURATION_MS = 220L
        private const val BUBBLE_APPEAR_START_SCALE = 0.86f
        private const val LAUNCH_LOOP_SCALE_DURATION_MS = 160L
        private const val LAUNCH_LOOP_START_SCALE = 0.72f
        private const val SCREENSHOT_HIDE_SETTLE_MS = 48L
        private const val LAUNCH_FALLBACK_TIMEOUT_MS = 3_000L
        private const val ONE_HAND_CHECK_INTERVAL_MS = 1_000L
        private const val EARTH_GRAVITY = 9.80665
        private const val BASE_BUBBLE_SIZE_PX = 160
        private const val MIN_BUBBLE_SIZE_PX = 80
        private const val CAPSULE_WIDTH_RATIO = 1.45f
        private const val EDGE_SWITCH_REGION_RATIO = 0.30f
        private const val DEFAULT_ANCHOR_X = 0
        private const val DEFAULT_ANCHOR_Y = 280
        private const val DEFAULT_TOP_MARGIN_PX = 220
        private const val DOCK_LEFT = 0
        private const val DOCK_RIGHT = 1
        private val CAPSULE_BACKGROUND_COLOR = Color.argb(150, 0, 0, 0)
        private val BLUE_CAPSULE_COLOR = Color.parseColor("#5D91FF")
        private const val MODE_IDLE = "idle"
        private const val MODE_DETECT = "detect"
        private const val MODE_RELOCATE = "relocate"

        const val ACTION_START = "com.cashewteam.novatext.android.action.START_FLOATING_BALL"
        const val ACTION_STOP = "com.cashewteam.novatext.android.action.STOP_FLOATING_BALL"
        const val ACTION_RESET_POSITION = "com.cashewteam.novatext.android.action.RESET_FLOATING_BALL"

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
        private var anchorInitialized = false

        @Volatile
        private var dockedSide = DOCK_RIGHT

        @Volatile
        private var prepared = false

        private val visibilitySuppressionTokens = linkedSetOf<Int>()
        private val nextVisibilitySuppressionToken = AtomicInteger(1)
        private val launchFallbackGeneration = AtomicInteger(0)
        private val activeState = MutableStateFlow(false)

        fun start(context: Context) {
            resetStateMachine()
            if (!Settings.canDrawOverlays(context)) {
                NovaTextLogger.d("overlay permission missing, skip starting floating ball")
                return
            }
            if (!isAccessibilityEnabled(context)) {
                NovaTextLogger.d("accessibility service missing, skip starting floating ball")
                return
            }
            if (isRunning) {
                activeService?.settings = BigBangSettings.get(context)
                activeService?.refreshBubbleAppearance()
                return
            }
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
                anchorInitialized = false
                dockedSide = DOCK_RIGHT
                return
            }
            val intent = Intent(context, FloatingBallService::class.java).setAction(ACTION_RESET_POSITION)
            context.startService(intent)
        }

        fun isActive(): Boolean = isRunning

        fun getActiveStateFlow(): StateFlow<Boolean> = activeState

        fun isAccessibilityEnabled(context: Context): Boolean {
            val serviceComponent = ComponentName(context, NovaTextAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val expected = serviceComponent.flattenToString()
            val expectedShort = serviceComponent.flattenToShortString()
            return enabledServices.split(':').any { value ->
                val normalized = value.trim()
                normalized.equals(expected, ignoreCase = true) ||
                    normalized.equals(expectedShort, ignoreCase = true)
            }
        }

        fun refreshAppearance(context: Context) {
            if (!isRunning) return
            activeService?.settings = BigBangSettings.get(context)
            activeService?.refreshBubbleAppearance()
        }

        fun resetStateMachine() {
            launchFallbackGeneration.incrementAndGet()
            mode = MODE_IDLE
            synchronized(this) {
                visibilitySuppressionTokens.clear()
            }
            lastScreenshotSuppressionToken = null
            pendingLaunchSuppressionToken = null
            searchOverlayVisible = false
            activeService?.let { service ->
                service.lastTapAt = 0L
                service.bubbleHandler.post {
                    service.hideLaunchLoopInternal()
                    service.applyVisibilitySuppressionState()
                    service.scheduleBubbleFade()
                }
            }
        }

        fun hideForScreenshot(afterHidden: (() -> Unit)? = null) {
            val service = activeService
            if (service == null) {
                lastScreenshotSuppressionToken = acquireVisibilitySuppression()
                afterHidden?.invoke()
                return
            }
            val token = synchronized(this) {
                nextVisibilitySuppressionToken.getAndIncrement().also {
                    visibilitySuppressionTokens += it
                }
            }
            lastScreenshotSuppressionToken = token
            service.bubbleHandler.post {
                val active = activeService
                active?.applyVisibilitySuppressionState()
                val callback = afterHidden ?: return@post
                service.bubbleHandler.postDelayed({ callback.invoke() }, SCREENSHOT_HIDE_SETTLE_MS)
            }
        }

        fun restoreAfterScreenshot() {
            releaseVisibilitySuppression(lastScreenshotSuppressionToken).also {
                lastScreenshotSuppressionToken = null
            }
        }

        fun acquireVisibilitySuppression(immediate: Boolean = false): Int? {
            val service = activeService ?: return null
            val token = synchronized(this) {
                nextVisibilitySuppressionToken.getAndIncrement().also {
                    visibilitySuppressionTokens += it
                }
            }
            if (immediate && Looper.myLooper() == service.bubbleHandler.looper) {
                activeService?.applyVisibilitySuppressionState()
            } else {
                service.bubbleHandler.post {
                    activeService?.applyVisibilitySuppressionState()
                }
            }
            return token
        }

        fun beginCaptureLaunchSuppression() {
            clearCaptureLaunchSuppression()
            pendingLaunchSuppressionToken = acquireVisibilitySuppression(immediate = true)
            scheduleLaunchFallback()
        }

        fun clearCaptureLaunchSuppression() {
            val token = pendingLaunchSuppressionToken
            pendingLaunchSuppressionToken = null
            releaseVisibilitySuppression(token)
        }

        fun notifyBigBangShellShown() {
            launchFallbackGeneration.incrementAndGet()
            hideLaunchLoop()
            clearCaptureLaunchSuppression()
        }

        fun prepareCaptureProxyLaunch() {
            launchFallbackGeneration.incrementAndGet()
        }

        fun notifyCaptureProxyStarted() {
            launchFallbackGeneration.incrementAndGet()
            clearCaptureLaunchSuppression()
        }

        fun showLaunchLoopAt(x: Int, y: Int) {
            activeService?.bubbleHandler?.post {
                activeService?.showLaunchLoopAtInternal(x, y)
            }
        }

        fun hideLaunchLoop() {
            activeService?.bubbleHandler?.post {
                activeService?.hideLaunchLoopInternal()
            }
        }

        private fun scheduleLaunchFallback() {
            val service = activeService ?: return
            val generation = launchFallbackGeneration.incrementAndGet()
            service.bubbleHandler.postDelayed(
                {
                    if (launchFallbackGeneration.get() != generation) return@postDelayed
                    NovaTextLogger.d("floating ball launch fallback timeout, force show")
                    resetStateMachine()
                },
                LAUNCH_FALLBACK_TIMEOUT_MS,
            )
        }

        fun setSearchOverlayVisible(visible: Boolean) {
            searchOverlayVisible = visible
            activeService?.bubbleHandler?.post {
                activeService?.applyVisibilitySuppressionState()
            }
        }

        fun releaseVisibilitySuppression(token: Int?) {
            if (token == null) return
            val service = activeService
            synchronized(this) {
                visibilitySuppressionTokens.remove(token)
            }
            service?.bubbleHandler?.post {
                activeService?.applyVisibilitySuppressionState()
            }
        }

        private fun isVisibilitySuppressed(): Boolean {
            return synchronized(this) { visibilitySuppressionTokens.isNotEmpty() }
        }

        private fun shouldHideBubble(): Boolean {
            if (lastScreenshotSuppressionToken != null || pendingLaunchSuppressionToken != null) {
                return true
            }
            if (searchOverlayVisible) {
                return false
            }
            return isVisibilitySuppressed()
        }

        @Volatile
        private var lastScreenshotSuppressionToken: Int? = null

        @Volatile
        private var pendingLaunchSuppressionToken: Int? = null

        @Volatile
        private var searchOverlayVisible = false

        private fun isSearchOverlayActive(): Boolean = searchOverlayVisible

        private fun prepare(context: Context) {
            if (prepared) return
            synchronized(this) {
                if (prepared) return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                }
                prepared = true
            }
        }

        private fun overlayWindowType(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        }
    }
}
