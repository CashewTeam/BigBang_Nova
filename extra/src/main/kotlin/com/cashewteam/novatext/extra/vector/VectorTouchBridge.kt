package com.cashewteam.novatext.extra.vector

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
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
    private var activeMode = TriggerMode.PRESSURE
    private var longPressTask: Runnable? = null
    private var firstPointerId = MotionEvent.INVALID_POINTER_ID
    private var secondPointerId = MotionEvent.INVALID_POINTER_ID
    private var gestureStartTime = 0L
    private var firstX = 0f
    private var firstY = 0f
    private var secondX = 0f
    private var secondY = 0f
    private var triggerX = 0
    private var triggerY = 0
    private var twoFingerCandidate = false
    private var lastGestureTriggeredAt = 0L

    @JvmStatic
    fun attach(preferences: SharedPreferences) {
        if (attachedPreferences === preferences) return
        attachedPreferences = preferences
        refresh(preferences)
        preferences.registerOnSharedPreferenceChangeListener { _, _ -> refresh(preferences) }
    }

    @JvmStatic
    fun onMotionEvent(event: MotionEvent, receiver: Any?) {
        contextFrom(receiver)?.let { context ->
            val packageName = context.packageName
            if (TriggerPolicy.isExcludedPackage(packageName)) return
            if (activeMode != config.mode) {
                resetGesture()
                activeMode = config.mode
            }
            when (config.mode) {
                TriggerMode.SINGLE_LONG_PRESS -> if (config.enabled && TriggerPolicy.hasUsableThreshold(config)) {
                    onSingleLongPress(event, context, packageName)
                } else resetGesture()
                TriggerMode.TWO_FINGER_TAP -> if (config.enabled && TriggerPolicy.hasUsableThreshold(config)) {
                    onTwoFingerTap(event, context, packageName)
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

    private fun onTwoFingerTap(event: MotionEvent, context: Context, packageName: String) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                firstPointerId = event.getPointerId(0)
                gestureStartTime = event.eventTime
                firstX = event.getX(0)
                firstY = event.getY(0)
                triggerX = event.rawX.toInt()
                triggerY = event.rawY.toInt()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && TriggerPolicy.isWithinDuration(gestureStartTime, event.eventTime, config.threshold)) {
                    secondPointerId = event.getPointerId(event.actionIndex)
                    secondX = event.getX(event.actionIndex)
                    secondY = event.getY(event.actionIndex)
                    twoFingerCandidate = true
                } else {
                    twoFingerCandidate = false
                }
            }
            MotionEvent.ACTION_MOVE -> if (twoFingerCandidate) {
                val firstIndex = event.findPointerIndex(firstPointerId)
                val secondIndex = event.findPointerIndex(secondPointerId)
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (firstIndex < 0 || secondIndex < 0 ||
                    TriggerPolicy.movedBeyondSlop(firstX, firstY, event.getX(firstIndex), event.getY(firstIndex), slop) ||
                    TriggerPolicy.movedBeyondSlop(secondX, secondY, event.getX(secondIndex), event.getY(secondIndex), slop)
                ) twoFingerCandidate = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (twoFingerCandidate && event.pointerCount == 2 &&
                    TriggerPolicy.isWithinDuration(gestureStartTime, event.eventTime, config.threshold)
                ) launch(context, packageName, triggerX, triggerY, (event.eventTime - gestureStartTime).toFloat())
                resetGesture()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()
        }
    }

    private fun launch(context: Context, packageName: String, x: Int, y: Int, value: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - lastGestureTriggeredAt < COOLDOWN_MS || !NovaTextLauncher.launch(context, packageName, x, y)) return
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
        secondPointerId = MotionEvent.INVALID_POINTER_ID
        twoFingerCandidate = false
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

    private const val COOLDOWN_MS = 750L
}
