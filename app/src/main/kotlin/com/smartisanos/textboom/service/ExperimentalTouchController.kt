package com.cashewteam.novatext.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.TouchInteractionController
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cashewteam.novatext.android.data.BigBangSettings

enum class ExperimentalTriggerMode {
    PRESSURE,
    SIZE,
    TOUCH_AREA,
    TWO_FINGER_TAP,
    THREE_FINGER_TAP,
}

data class ExperimentalTriggerConfig(
    val configured: Boolean,
    val mode: ExperimentalTriggerMode,
    val threshold: Float,
)

object ExperimentalTouchPolicy {
    private const val COOLDOWN_MS = 750L
    const val NOVA_TEXT = "com.cashewteam.novatext.android"

    fun config(settings: BigBangSettings): ExperimentalTriggerConfig {
        val mode = runCatching { ExperimentalTriggerMode.valueOf(settings.experimentalTouchMode) }
            .getOrDefault(ExperimentalTriggerMode.PRESSURE)
        val threshold = when (mode) {
            ExperimentalTriggerMode.PRESSURE -> settings.experimentalTouchPressureThreshold
            ExperimentalTriggerMode.SIZE -> settings.experimentalTouchSizeThreshold
            ExperimentalTriggerMode.TOUCH_AREA -> settings.experimentalTouchAreaThreshold
            ExperimentalTriggerMode.TWO_FINGER_TAP -> settings.experimentalTouchTwoFingerDuration
            ExperimentalTriggerMode.THREE_FINGER_TAP -> settings.experimentalTouchThreeFingerDuration
        }
        return ExperimentalTriggerConfig(settings.isExperimentalTouchConfigured, mode, threshold)
    }

    fun isSensorMode(mode: ExperimentalTriggerMode): Boolean = when (mode) {
        ExperimentalTriggerMode.PRESSURE,
        ExperimentalTriggerMode.SIZE,
        ExperimentalTriggerMode.TOUCH_AREA -> true
        ExperimentalTriggerMode.TWO_FINGER_TAP,
        ExperimentalTriggerMode.THREE_FINGER_TAP -> false
    }

    fun isMultiFingerTap(mode: ExperimentalTriggerMode): Boolean = !isSensorMode(mode)

    fun requiredPointerCount(mode: ExperimentalTriggerMode): Int = when (mode) {
        ExperimentalTriggerMode.TWO_FINGER_TAP -> 2
        ExperimentalTriggerMode.THREE_FINGER_TAP -> 3
        else -> 1
    }

    fun readSample(event: MotionEvent, mode: ExperimentalTriggerMode, pointerIndex: Int): Float = when (mode) {
        ExperimentalTriggerMode.PRESSURE -> event.getPressure(pointerIndex)
        ExperimentalTriggerMode.SIZE -> event.getSize(pointerIndex)
        ExperimentalTriggerMode.TOUCH_AREA -> touchArea(event.getTouchMajor(pointerIndex), event.getTouchMinor(pointerIndex))
        ExperimentalTriggerMode.TWO_FINGER_TAP,
        ExperimentalTriggerMode.THREE_FINGER_TAP -> 0f
    }

    fun touchArea(touchMajor: Float, touchMinor: Float): Float =
        (Math.PI.toFloat() * touchMajor * touchMinor) / 4f

    fun hasUsableThreshold(config: ExperimentalTriggerConfig): Boolean =
        config.configured && config.threshold.isFinite() && config.threshold > 0f

    fun hasExactPointerCount(mode: ExperimentalTriggerMode, currentCount: Int, maximumCount: Int): Boolean =
        currentCount == requiredPointerCount(mode) && maximumCount == currentCount

    fun isWithinDuration(startTime: Long, endTime: Long, maximumMs: Float): Boolean =
        maximumMs > 0f && endTime >= startTime && endTime - startTime <= maximumMs

    fun movedBeyondSlop(startX: Float, startY: Float, x: Float, y: Float, slop: Int): Boolean {
        val dx = x - startX
        val dy = y - startY
        return dx * dx + dy * dy > slop * slop
    }

    fun isCooldownElapsed(lastTriggeredAt: Long, now: Long): Boolean =
        lastTriggeredAt == 0L || now - lastTriggeredAt >= COOLDOWN_MS

    fun isExcludedPackage(packageName: String): Boolean =
        packageName.isBlank() || packageName == NOVA_TEXT || packageName == "com.android.settings" ||
            packageName == "com.android.systemui" || packageName == "android" ||
            packageName.startsWith("com.android.launcher") ||
            packageName.startsWith("com.google.android.apps.nexuslauncher")
}

object ExperimentalTouchController {
    var running by mutableStateOf(false)
        private set
    var listening by mutableStateOf(false)
        private set

    fun start(context: Context): Boolean {
        val settings = BigBangSettings.get(context)
        val config = ExperimentalTouchPolicy.config(settings)
        val service = NovaTextAccessibilityService.activeInstance
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || service == null ||
            !ExperimentalTouchPolicy.hasUsableThreshold(config) || !hasBatteryExemption(context)
        ) return false
        settings.setExperimentalTouchEnabled(true)
        running = true
        service.startExperimentalTouchForeground()
        Api33.attach(service)
        return true
    }

    fun stop(context: Context) {
        BigBangSettings.get(context).setExperimentalTouchEnabled(false)
        NovaTextAccessibilityService.activeInstance?.stopExperimentalTouchForeground()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Api33.disconnect()
        running = false
        listening = false
    }

    fun connect(service: NovaTextAccessibilityService) {
        val settings = BigBangSettings.get(service)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !settings.isExperimentalTouchSelected ||
            !settings.isExperimentalTouchEnabled ||
            !ExperimentalTouchPolicy.hasUsableThreshold(ExperimentalTouchPolicy.config(settings)) ||
            !hasBatteryExemption(service)
        ) return
        running = true
        service.startExperimentalTouchForeground()
        Api33.attach(service)
    }

    fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Api33.disconnect()
        running = false
        listening = false
    }

    fun onForegroundPackage(packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Api33.onForegroundPackage(packageName)
    }

    fun hasBatteryExemption(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        private const val TAG = "NovaExperimentalTouch"
        private val handler = Handler(Looper.getMainLooper())
        private var accessibilityService: NovaTextAccessibilityService? = null
        private var controller: TouchInteractionController? = null
        private var callback: TouchInteractionController.Callback? = null
        private var delegationRequested = false
        private var consumingInteraction = false
        private var replayingShortTap = false
        private var multiFingerCandidate = false
        private var gestureStartTime = 0L
        private var requiredPointers = 0
        private var maximumDurationMs = 0f
        private var maximumPointerCount = 0
        private var candidateRawX = 0f
        private var candidateRawY = 0f
        private var lastTriggeredAt = 0L
        private val tapStartPositions = mutableMapOf<Int, Pair<Float, Float>>()
        private var timeoutTask: Runnable? = null
        @Volatile private var foregroundPackage = ""

        fun attach(service: NovaTextAccessibilityService) {
            accessibilityService = service
            connect()
        }

        private fun connect() {
            if (controller != null) return
            val service = accessibilityService ?: return
            service.serviceInfo = service.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            }
            controller = service.getTouchInteractionController(Display.DEFAULT_DISPLAY)
            callback = object : TouchInteractionController.Callback {
                override fun onMotionEvent(event: MotionEvent) {
                    val currentController = controller ?: return
                    logEvent(event, currentController.state)
                    if (replayingShortTap) {
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) requestDelegating(currentController, "replayed_short_tap")
                        return
                    }
                    if (delegationRequested) return
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> onDown(event, currentController)
                        MotionEvent.ACTION_POINTER_DOWN -> if (multiFingerCandidate) {
                            onPointerDown(event, currentController)
                        } else if (!consumingInteraction) {
                            requestDelegating(currentController, "unexpected_multi_pointer")
                        }
                        MotionEvent.ACTION_MOVE -> if (multiFingerCandidate) {
                            onCandidateMove(event, currentController)
                        }
                        MotionEvent.ACTION_POINTER_UP -> if (multiFingerCandidate) finishMultiFingerTap(event, currentController)
                        MotionEvent.ACTION_UP -> {
                            if (multiFingerCandidate && maximumPointerCount == 1) {
                                replayShortTap(accessibilityService ?: return, candidateRawX, candidateRawY, elapsed(event.eventTime))
                            }
                            clearCandidate()
                            consumingInteraction = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            Log.d(TAG, "route=cancel maxPointers=$maximumPointerCount")
                            clearCandidate()
                            consumingInteraction = false
                        }
                    }
                }

                override fun onStateChanged(state: Int) {
                    Log.d(TAG, "state=${TouchInteractionController.stateToString(state)} delegated=$delegationRequested consuming=$consumingInteraction")
                    when (state) {
                        TouchInteractionController.STATE_CLEAR -> {
                            clearCandidate()
                            delegationRequested = false
                            consumingInteraction = false
                        }
                        TouchInteractionController.STATE_DELEGATING -> delegationRequested = true
                    }
                }
            }
            controller?.registerCallback(null, callback!!)
            listening = true
            Log.d(TAG, "controller=listening package=$foregroundPackage")
        }

        fun disconnect() {
            callback?.let { controller?.unregisterCallback(it) }
            accessibilityService?.serviceInfo = accessibilityService?.serviceInfo?.apply {
                flags = flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
            }
            callback = null
            controller = null
            accessibilityService = null
            delegationRequested = false
            consumingInteraction = false
            replayingShortTap = false
            clearCandidate()
            listening = false
        }

        fun onForegroundPackage(packageName: String) {
            foregroundPackage = packageName
            Log.d(TAG, "foregroundPackage=$packageName")
        }

        private fun onDown(event: MotionEvent, controller: TouchInteractionController) {
            clearCandidate()
            consumingInteraction = false
            val service = accessibilityService ?: return
            val config = ExperimentalTouchPolicy.config(BigBangSettings.get(service))
            val sample = ExperimentalTouchPolicy.readSample(event, config.mode, 0)
            when {
                service.isInputMethodVisible() -> requestDelegating(controller, "input_method_visible")
                ExperimentalTouchPolicy.isExcludedPackage(foregroundPackage) -> requestDelegating(controller, "excluded_package package=$foregroundPackage")
                ExperimentalTouchPolicy.isMultiFingerTap(config.mode) -> beginMultiFingerCandidate(event, config, controller)
                !ExperimentalTouchPolicy.hasUsableThreshold(config) || sample <= config.threshold ->
                    requestDelegating(controller, "normal_down mode=${config.mode} sample=$sample threshold=${config.threshold}")
                !ExperimentalTouchPolicy.isCooldownElapsed(lastTriggeredAt, event.eventTime) ->
                    requestDelegating(controller, "cooldown")
                else -> trigger(service, foregroundPackage, event.rawX.toInt(), event.rawY.toInt(), sample, controller)
            }
        }

        private fun trigger(
            service: NovaTextAccessibilityService,
            packageName: String,
            x: Int,
            y: Int,
            sample: Float,
            controller: TouchInteractionController,
        ) {
            if (ExperimentalTouchPolicy.isExcludedPackage(packageName)) {
                requestDelegating(controller, "invalid_launch_package")
                return
            }
            lastTriggeredAt = SystemClock.uptimeMillis()
            consumingInteraction = true
            BigBangCaptureDispatcher.captureAt(service.applicationContext, x, y, packageName)
            Log.d(TAG, "route=consume reason=triggered sample=$sample package=$packageName x=$x y=$y")
        }

        private fun requestDelegating(controller: TouchInteractionController, reason: String) {
            clearCandidate()
            when (controller.state) {
                TouchInteractionController.STATE_DELEGATING -> delegationRequested = true
                TouchInteractionController.STATE_TOUCH_INTERACTING,
                TouchInteractionController.STATE_DRAGGING -> {
                    delegationRequested = true
                    consumingInteraction = false
                    Log.d(TAG, "route=delegate reason=$reason")
                    controller.requestDelegating()
                }
                else -> Log.w(TAG, "route=ignored state=${TouchInteractionController.stateToString(controller.state)} requestedBy=$reason")
            }
        }

        private fun beginMultiFingerCandidate(event: MotionEvent, config: ExperimentalTriggerConfig, controller: TouchInteractionController) {
            multiFingerCandidate = true
            gestureStartTime = event.eventTime
            requiredPointers = ExperimentalTouchPolicy.requiredPointerCount(config.mode)
            maximumDurationMs = config.threshold
            maximumPointerCount = 1
            candidateRawX = event.rawX
            candidateRawY = event.rawY
            tapStartPositions[event.getPointerId(0)] = event.getX(0) to event.getY(0)
            scheduleTimeout(controller, minOf(ViewConfiguration.getTapTimeout().toFloat(), maximumDurationMs).toLong(), "pointer_wait_timeout")
            Log.d(TAG, "route=hold mode=${config.mode} requiredPointers=$requiredPointers")
        }

        private fun onPointerDown(event: MotionEvent, controller: TouchInteractionController) {
            maximumPointerCount = maxOf(maximumPointerCount, event.pointerCount)
            if (event.pointerCount > requiredPointers) {
                requestDelegating(controller, "extra_pointer")
                return
            }
            val index = event.actionIndex
            tapStartPositions[event.getPointerId(index)] = event.getX(index) to event.getY(index)
            if (event.pointerCount == requiredPointers) {
                val remainingMs = maximumDurationMs - elapsed(event.eventTime)
                if (remainingMs <= 0f) requestDelegating(controller, "duration_exceeded_before_ready")
                else scheduleTimeout(controller, remainingMs.toLong(), "tap_duration_timeout")
            }
        }

        private fun onCandidateMove(event: MotionEvent, controller: TouchInteractionController) {
            val slop = accessibilityService?.let { ViewConfiguration.get(it).scaledTouchSlop } ?: 0
            val moved = tapStartPositions.any { (pointerId, start) ->
                val index = event.findPointerIndex(pointerId)
                index < 0 || ExperimentalTouchPolicy.movedBeyondSlop(start.first, start.second, event.getX(index), event.getY(index), slop)
            }
            if (moved) requestDelegating(controller, "movement_exceeded_slop")
        }

        private fun finishMultiFingerTap(event: MotionEvent, controller: TouchInteractionController) {
            val service = accessibilityService ?: return
            val config = ExperimentalTouchPolicy.config(BigBangSettings.get(service))
            val valid = ExperimentalTouchPolicy.hasExactPointerCount(config.mode, event.pointerCount, maximumPointerCount) &&
                ExperimentalTouchPolicy.isWithinDuration(gestureStartTime, event.eventTime, maximumDurationMs) &&
                ExperimentalTouchPolicy.isCooldownElapsed(lastTriggeredAt, event.eventTime)
            if (!valid) {
                requestDelegating(controller, "tap_rejected")
                return
            }
            val x = (0 until event.pointerCount).sumOf { event.getRawX(it).toDouble() } / event.pointerCount
            val y = (0 until event.pointerCount).sumOf { event.getRawY(it).toDouble() } / event.pointerCount
            clearCandidate()
            trigger(service, foregroundPackage, x.toInt(), y.toInt(), elapsed(event.eventTime), controller)
        }

        private fun scheduleTimeout(controller: TouchInteractionController, delayMs: Long, reason: String) {
            timeoutTask?.let(handler::removeCallbacks)
            timeoutTask = Runnable {
                timeoutTask = null
                if (multiFingerCandidate) requestDelegating(controller, reason)
            }.also { handler.postDelayed(it, delayMs) }
        }

        private fun clearCandidate() {
            timeoutTask?.let(handler::removeCallbacks)
            timeoutTask = null
            multiFingerCandidate = false
            gestureStartTime = 0L
            requiredPointers = 0
            maximumDurationMs = 0f
            maximumPointerCount = 0
            candidateRawX = 0f
            candidateRawY = 0f
            tapStartPositions.clear()
        }

        private fun replayShortTap(service: AccessibilityService, x: Float, y: Float, elapsedMs: Float) {
            val durationMs = elapsedMs.toLong().coerceIn(1L, ViewConfiguration.getTapTimeout().toLong())
            replayingShortTap = true
            val accepted = service.dispatchGesture(
                GestureDescription.Builder().addStroke(
                    GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0L, durationMs),
                ).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) { replayingShortTap = false }
                    override fun onCancelled(gestureDescription: GestureDescription) { replayingShortTap = false }
                },
                null,
            )
            if (!accepted) replayingShortTap = false
            Log.d(TAG, "route=replay_short_tap accepted=$accepted durationMs=$durationMs")
        }

        private fun elapsed(eventTime: Long): Float = (eventTime - gestureStartTime).coerceAtLeast(0L).toFloat()

        private fun logEvent(event: MotionEvent, state: Int) {
            val values = if (event.pointerCount == 0) "pointers=0" else {
                "pointers=${event.pointerCount} pressure=${event.getPressure(0)} size=${event.getSize(0)} " +
                    "touchMajor=${event.getTouchMajor(0)} touchMinor=${event.getTouchMinor(0)}"
            }
            Log.d(TAG, "event=${MotionEvent.actionToString(event.action)} state=${TouchInteractionController.stateToString(state)} $values delegated=$delegationRequested consuming=$consumingInteraction")
        }
    }
}
