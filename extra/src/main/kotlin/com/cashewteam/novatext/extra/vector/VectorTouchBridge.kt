package com.cashewteam.novatext.extra.vector

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import com.cashewteam.novatext.extra.ExtraDiagnostics
import com.cashewteam.novatext.extra.ExtraSettings
import com.cashewteam.novatext.extra.NovaTextLauncher
import com.cashewteam.novatext.extra.TriggerConfig
import com.cashewteam.novatext.extra.TriggerMode
import com.cashewteam.novatext.extra.TriggerPolicy

object VectorTouchBridge {
    private val gate = TriggerPolicy.Gate()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var config = TriggerConfig(false, false, TriggerMode.PRESSURE, 0f)
    @Volatile private var attachedPreferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var activeMode = TriggerMode.PRESSURE
    private var longPressTask: Runnable? = null
    private var firstPointerId = MotionEvent.INVALID_POINTER_ID
    private var gestureStartTime = 0L
    private var firstX = 0f
    private var firstY = 0f
    private var triggerX = 0
    private var triggerY = 0
    private val tapStartPositions = mutableMapOf<Int, Pair<Float, Float>>()
    private var multiFingerCandidate = false
    private var maximumPointerCount = 0
    private var lastGestureTriggeredAt = 0L
    private var skippingInputMethod = false

    @JvmStatic
    fun attach(preferences: SharedPreferences) {
        if (attachedPreferences === preferences) return
        attachedPreferences?.let { current -> preferenceListener?.let(current::unregisterOnSharedPreferenceChangeListener) }
        attachedPreferences = preferences
        refresh(preferences)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh(preferences) }
        preferences.registerOnSharedPreferenceChangeListener(requireNotNull(preferenceListener))
    }

    @JvmStatic
    fun onMotionEvent(event: MotionEvent, receiver: Any?) {
        contextFrom(receiver)?.let { context ->
            val packageName = context.packageName
            if (TriggerPolicy.isExcludedPackage(packageName)) return
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val inputMethodPackage = TriggerPolicy.inputMethodPackage(
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
                )
                skippingInputMethod = packageName == inputMethodPackage || isInputMethodVisible(receiver)
                if (skippingInputMethod) resetGesture()
            }
            if (skippingInputMethod) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    skippingInputMethod = false
                }
                return
            }
            if (activeMode != config.mode) {
                resetGesture()
                activeMode = config.mode
            }
            when (config.mode) {
                TriggerMode.SINGLE_LONG_PRESS -> if (config.enabled && TriggerPolicy.hasUsableThreshold(config)) {
                    onSingleLongPress(event, context, packageName)
                } else resetGesture()
                TriggerMode.TWO_FINGER_TAP, TriggerMode.THREE_FINGER_TAP -> if (config.enabled && TriggerPolicy.hasUsableThreshold(config)) {
                    onMultiFingerTap(event, context, packageName)
                } else resetGesture()
                else -> if (gate.onEvent(event, config)) {
                    launch(context, packageName, event.rawX.toInt(), event.rawY.toInt(), TriggerPolicy.readSample(event, config.mode, 0))
                }
            }
        }
    }

    private fun onSingleLongPress(event: MotionEvent, context: Context, packageName: String) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                firstPointerId = event.getPointerId(0)
                firstX = event.getX(0)
                firstY = event.getY(0)
                triggerX = event.rawX.toInt()
                triggerY = event.rawY.toInt()
                val duration = config.threshold
                longPressTask = Runnable {
                    if (firstPointerId != MotionEvent.INVALID_POINTER_ID && config.mode == TriggerMode.SINGLE_LONG_PRESS &&
                        config.enabled && TriggerPolicy.hasUsableThreshold(config)
                    ) {
                        launch(context, packageName, triggerX, triggerY, duration)
                        firstPointerId = MotionEvent.INVALID_POINTER_ID
                    }
                    longPressTask = null
                }.also { handler.postDelayed(it, duration.toLong()) }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(firstPointerId)
                if (index < 0 || TriggerPolicy.movedBeyondSlop(
                        firstX,
                        firstY,
                        event.getX(index),
                        event.getY(index),
                        ViewConfiguration.get(context).scaledTouchSlop,
                    )
                ) resetGesture()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()
        }
    }

    private fun onMultiFingerTap(event: MotionEvent, context: Context, packageName: String) {
        val requiredPointers = TriggerPolicy.requiredPointerCount(config.mode)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                firstPointerId = event.getPointerId(0)
                gestureStartTime = event.eventTime
                tapStartPositions[firstPointerId] = event.getX(0) to event.getY(0)
                multiFingerCandidate = true
                maximumPointerCount = 1
                Log.d(TAG, "route=observe mode=${config.mode} requiredPointers=$requiredPointers")
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maximumPointerCount = maxOf(maximumPointerCount, event.pointerCount)
                val index = event.actionIndex
                tapStartPositions[event.getPointerId(index)] = event.getX(index) to event.getY(index)
                multiFingerCandidate = multiFingerCandidate && event.pointerCount <= requiredPointers &&
                    TriggerPolicy.isWithinDuration(gestureStartTime, event.eventTime, config.threshold)
                if (!multiFingerCandidate) {
                    Log.d(TAG, "route=ignore reason=pointer_or_duration mode=${config.mode} maxPointers=$maximumPointerCount elapsedMs=${event.eventTime - gestureStartTime}")
                }
            }
            MotionEvent.ACTION_MOVE -> if (multiFingerCandidate) {
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                val stillWithinSlop = tapStartPositions.all { (pointerId, start) ->
                    val index = event.findPointerIndex(pointerId)
                    index >= 0 && !TriggerPolicy.movedBeyondSlop(start.first, start.second, event.getX(index), event.getY(index), slop)
                }
                if (!stillWithinSlop) Log.d(TAG, "route=ignore reason=movement mode=${config.mode} maxPointers=$maximumPointerCount")
                multiFingerCandidate = stillWithinSlop
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val elapsedMs = event.eventTime - gestureStartTime
                if (multiFingerCandidate && TriggerPolicy.hasExactPointerCount(config.mode, event.pointerCount, maximumPointerCount) &&
                    TriggerPolicy.isWithinDuration(gestureStartTime, event.eventTime, config.threshold)
                ) {
                    val x = (0 until event.pointerCount).sumOf { event.getRawX(it).toDouble() } / event.pointerCount
                    val y = (0 until event.pointerCount).sumOf { event.getRawY(it).toDouble() } / event.pointerCount
                    Log.d(TAG, "route=trigger mode=${config.mode} maxPointers=$maximumPointerCount elapsedMs=$elapsedMs")
                    launch(context, packageName, x.toInt(), y.toInt(), elapsedMs.toFloat())
                } else {
                    Log.d(TAG, "route=ignore reason=tap_rejected mode=${config.mode} maxPointers=$maximumPointerCount elapsedMs=$elapsedMs")
                }
                resetGesture()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()
        }
    }

    private fun launch(context: Context, packageName: String, x: Int, y: Int, value: Float) {
        val now = SystemClock.uptimeMillis()
        if (!TriggerPolicy.isCooldownElapsed(lastGestureTriggeredAt, now) || !NovaTextLauncher.launch(context, packageName, x, y)) return
        lastGestureTriggeredAt = now
        context.sendBroadcast(Intent(ExtraDiagnostics.ACTION_TRIGGERED).apply {
            setPackage(TriggerPolicy.EXTRA)
            putExtra(ExtraDiagnostics.EXTRA_PACKAGE, packageName)
            putExtra(ExtraDiagnostics.EXTRA_VALUE, value)
        })
    }

    private fun resetGesture() {
        longPressTask?.let(handler::removeCallbacks)
        longPressTask = null
        firstPointerId = MotionEvent.INVALID_POINTER_ID
        tapStartPositions.clear()
        multiFingerCandidate = false
        maximumPointerCount = 0
    }

    private fun refresh(preferences: SharedPreferences) {
        val mode = runCatching {
            TriggerMode.valueOf(preferences.getString(ExtraSettings.KEY_MODE, TriggerMode.PRESSURE.name).orEmpty())
        }.getOrDefault(TriggerMode.PRESSURE)
        config = TriggerConfig(
            enabled = preferences.getBoolean(ExtraSettings.KEY_ENABLED, false),
            calibrated = preferences.getBoolean(ExtraSettings.KEY_CALIBRATED, false),
            mode = mode,
            threshold = when (mode) {
                TriggerMode.PRESSURE -> preferences.getFloat(ExtraSettings.KEY_PRESSURE, 0f)
                TriggerMode.SIZE -> preferences.getFloat(ExtraSettings.KEY_SIZE, 0f)
                TriggerMode.TOUCH_AREA -> preferences.getFloat(ExtraSettings.KEY_TOUCH_AREA, 500f)
                TriggerMode.SINGLE_LONG_PRESS -> preferences.getFloat(ExtraSettings.KEY_LONG_PRESS_DURATION, 600f)
                TriggerMode.TWO_FINGER_TAP -> preferences.getFloat(ExtraSettings.KEY_TWO_FINGER_TAP_DURATION, 300f)
                TriggerMode.THREE_FINGER_TAP -> preferences.getFloat(ExtraSettings.KEY_THREE_FINGER_TAP_DURATION, 300f)
            },
        )
    }

    private fun contextFrom(receiver: Any?): Context? {
        if (receiver == null) return null
        return runCatching {
            val outer = receiver.javaClass.getDeclaredField("this$0").apply { isAccessible = true }.get(receiver)
            outer.javaClass.getDeclaredField("mContext").apply { isAccessible = true }.get(outer) as? Context
        }.getOrNull()
    }

    private fun isInputMethodVisible(receiver: Any?): Boolean {
        val rootView = rootViewFrom(receiver) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        } else {
            val visibleFrame = Rect().also(rootView::getWindowVisibleDisplayFrame)
            rootView.rootView.height > 0 &&
                rootView.rootView.height - visibleFrame.height() > rootView.rootView.height * 0.15f
        }
    }

    private fun rootViewFrom(receiver: Any?): View? = runCatching {
        val outer = receiver?.javaClass?.getDeclaredField("this$0")?.apply { isAccessible = true }?.get(receiver)
        outer?.javaClass?.getDeclaredField("mView")?.apply { isAccessible = true }?.get(outer) as? View
    }.getOrNull()

    private const val TAG = "NovaExtraTouch"
}
