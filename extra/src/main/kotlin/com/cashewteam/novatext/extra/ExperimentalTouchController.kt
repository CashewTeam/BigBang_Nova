package com.cashewteam.novatext.extra

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import android.accessibilityservice.GestureDescription
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ExperimentalTouchController {
    var running by mutableStateOf(false)
        private set
    var listening by mutableStateOf(false)
        private set

    fun start(context: Context): Boolean {
        val settings = ExtraSettings(context)
        val config = settings.config()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ExtraAccessibilityService.active == null || !TriggerPolicy.hasUsableThreshold(config) ||
            !TriggerPolicy.supportsExperimental(config.mode) || !hasBatteryExemption(context)
        ) return false
        settings.experimentalEnabled = true
        running = true
        ExtraAccessibilityService.active!!.let {
            it.startExperimentalForeground()
            Api33.attach(it)
        }
        return true
    }

    fun stop(context: Context) {
        ExtraSettings(context).experimentalEnabled = false
        ExtraAccessibilityService.active?.stopExperimentalForeground()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Api33.disconnect()
        running = false
        listening = false
    }

    fun connect(accessibilityService: ExtraAccessibilityService) {
        val settings = ExtraSettings(accessibilityService)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !settings.experimentalEnabled || !TriggerPolicy.supportsExperimental(settings.mode) ||
            !hasBatteryExemption(accessibilityService)
        ) return
        running = true
        accessibilityService.startExperimentalForeground()
        Api33.attach(accessibilityService)
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
        private val handler = Handler(Looper.getMainLooper())
        private var accessibilityService: AccessibilityService? = null
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
        private val tapStartPositions = mutableMapOf<Int, Pair<Float, Float>>()
        private var timeoutTask: Runnable? = null
        @Volatile private var foregroundPackage = ""

        fun attach(accessibilityService: AccessibilityService) {
            this.accessibilityService = accessibilityService
            connect()
        }

        private fun connect() {
            if (controller != null) return
            val accessibilityService = accessibilityService ?: return
            accessibilityService.serviceInfo = accessibilityService.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            }
            controller = accessibilityService.getTouchInteractionController(Display.DEFAULT_DISPLAY)
            callback = object : TouchInteractionController.Callback {
                override fun onMotionEvent(event: MotionEvent) {
                    val currentController = controller ?: return
                    logEvent(event, currentController.state)
                    if (replayingShortTap) {
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            requestDelegating(currentController, "replayed_short_tap")
                        } else {
                            Log.d(TAG, "route=replay_event action=${MotionEvent.actionToString(event.action)}")
                        }
                        return
                    }
                    if (delegationRequested) {
                        Log.d(TAG, "route=already_delegating action=${MotionEvent.actionToString(event.action)}")
                        return
                    }

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            clearCandidate()
                            consumingInteraction = false
                            val config = ExtraSettings(accessibilityService).config().copy(enabled = true)
                            val packageName = foregroundPackage
                            val sample = TriggerPolicy.readSample(event, config.mode, 0)
                            if (TriggerPolicy.isExcludedPackage(packageName)) {
                                requestDelegating(currentController, "excluded_package package=$packageName")
                            } else if (TriggerPolicy.isMultiFingerTap(config.mode)) {
                                beginMultiFingerCandidate(event, config, currentController)
                            } else if (!isStrongPress(event, config)) {
                                requestDelegating(currentController, "normal_down mode=${config.mode} sample=$sample threshold=${config.threshold} package=$packageName")
                            } else if (NovaTextLauncher.launch(accessibilityService, packageName, event.rawX.toInt(), event.rawY.toInt())) {
                                consumingInteraction = true
                                ExtraDiagnostics.record(accessibilityService, packageName, sample)
                                Log.d(TAG, "route=consume reason=threshold_triggered mode=${config.mode} sample=$sample threshold=${config.threshold} package=$packageName")
                            } else {
                                requestDelegating(currentController, "nova_launch_failed package=$packageName")
                            }
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> if (multiFingerCandidate) {
                            onPointerDown(event, currentController)
                        } else if (!consumingInteraction) {
                            requestDelegating(currentController, "unexpected_multi_pointer")
                        }
                        MotionEvent.ACTION_MOVE -> if (multiFingerCandidate) {
                            onCandidateMove(event, currentController)
                        } else if (consumingInteraction) {
                            Log.d(TAG, "route=consume reason=triggered_interaction_continues")
                        } else {
                            Log.d(TAG, "route=ignored reason=move_without_active_decision")
                        }
                        MotionEvent.ACTION_POINTER_UP -> if (multiFingerCandidate) {
                            finishMultiFingerTap(event, currentController)
                        }
                        MotionEvent.ACTION_UP -> {
                            if (multiFingerCandidate) {
                                val elapsedMs = elapsed(event.eventTime)
                                if (maximumPointerCount == 1) {
                                    replayShortTap(accessibilityService, candidateRawX, candidateRawY, elapsedMs)
                                } else {
                                    Log.d(TAG, "route=consume reason=incomplete_multi_finger_tap maxPointers=$maximumPointerCount elapsedMs=$elapsedMs")
                                }
                            } else {
                                Log.d(TAG, "route=${if (consumingInteraction) "consume" else "unhandled"} reason=interaction_end")
                            }
                            clearCandidate()
                            consumingInteraction = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            Log.d(TAG, "route=cancel mode=${candidateModeName()} maxPointers=$maximumPointerCount elapsedMs=${elapsed(event.eventTime)}")
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
            releaseController()
            accessibilityService = null
        }

        private fun releaseController() {
            callback?.let { controller?.unregisterCallback(it) }
            accessibilityService?.let { service ->
                service.serviceInfo = service.serviceInfo.apply {
                    flags = flags and AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv()
                }
            }
            callback = null
            controller = null
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

        private fun requestDelegating(controller: TouchInteractionController, reason: String) {
            clearCandidate()
            val state = controller.state
            if (state == TouchInteractionController.STATE_DELEGATING) {
                delegationRequested = true
                Log.d(TAG, "route=already_delegating reason=$reason")
                return
            }
            if (state != TouchInteractionController.STATE_TOUCH_INTERACTING &&
                state != TouchInteractionController.STATE_DRAGGING
            ) {
                Log.w(TAG, "route=ignored state=${TouchInteractionController.stateToString(state)} requestedBy=$reason")
                return
            }
            delegationRequested = true
            consumingInteraction = false
            Log.d(TAG, "route=delegate state=${TouchInteractionController.stateToString(state)} reason=$reason")
            controller.requestDelegating()
        }

        private fun beginMultiFingerCandidate(
            event: MotionEvent,
            config: TriggerConfig,
            controller: TouchInteractionController,
        ) {
            multiFingerCandidate = true
            gestureStartTime = event.eventTime
            requiredPointers = TriggerPolicy.requiredPointerCount(config.mode)
            maximumDurationMs = config.threshold
            maximumPointerCount = 1
            candidateRawX = event.rawX
            candidateRawY = event.rawY
            tapStartPositions[event.getPointerId(0)] = event.getX(0) to event.getY(0)
            val waitMs = minOf(ViewConfiguration.getTapTimeout().toFloat(), maximumDurationMs).toLong()
            scheduleTimeout(controller, waitMs, "pointer_wait_timeout")
            Log.d(TAG, "route=hold mode=${config.mode} requiredPointers=$requiredPointers timeoutMs=$waitMs")
        }

        private fun onPointerDown(event: MotionEvent, controller: TouchInteractionController) {
            maximumPointerCount = maxOf(maximumPointerCount, event.pointerCount)
            if (event.pointerCount > requiredPointers) {
                requestDelegating(controller, "extra_pointer maxPointers=$maximumPointerCount")
                return
            }
            val index = event.actionIndex
            tapStartPositions[event.getPointerId(index)] = event.getX(index) to event.getY(index)
            if (event.pointerCount == requiredPointers) {
                val remainingMs = maximumDurationMs - elapsed(event.eventTime)
                if (remainingMs <= 0f) {
                    requestDelegating(controller, "duration_exceeded_before_ready")
                } else {
                    scheduleTimeout(controller, remainingMs.toLong(), "tap_duration_timeout")
                    Log.d(TAG, "route=hold mode=${candidateModeName()} maxPointers=$maximumPointerCount remainingMs=$remainingMs")
                }
            }
        }

        private fun onCandidateMove(event: MotionEvent, controller: TouchInteractionController) {
            val slop = accessibilityService?.let { ViewConfiguration.get(it).scaledTouchSlop } ?: 0
            val moved = tapStartPositions.any { (pointerId, start) ->
                val index = event.findPointerIndex(pointerId)
                index < 0 || TriggerPolicy.movedBeyondSlop(start.first, start.second, event.getX(index), event.getY(index), slop)
            }
            if (moved) requestDelegating(controller, "movement_exceeded_slop maxPointers=$maximumPointerCount")
        }

        private fun finishMultiFingerTap(event: MotionEvent, controller: TouchInteractionController) {
            val service = accessibilityService ?: return
            val elapsedMs = elapsed(event.eventTime)
            val packageName = foregroundPackage
            val mode = when (requiredPointers) {
                2 -> TriggerMode.TWO_FINGER_TAP
                else -> TriggerMode.THREE_FINGER_TAP
            }
            if (!TriggerPolicy.hasExactPointerCount(mode, event.pointerCount, maximumPointerCount) ||
                !TriggerPolicy.isWithinDuration(gestureStartTime, event.eventTime, maximumDurationMs)
            ) {
                requestDelegating(controller, "tap_rejected pointers=${event.pointerCount} maxPointers=$maximumPointerCount elapsedMs=$elapsedMs")
                return
            }
            val x = (0 until event.pointerCount).sumOf { event.getRawX(it).toDouble() } / event.pointerCount
            val y = (0 until event.pointerCount).sumOf { event.getRawY(it).toDouble() } / event.pointerCount
            val modeName = mode.name
            val pointerCount = maximumPointerCount
            clearCandidate()
            if (NovaTextLauncher.launch(service, packageName, x.toInt(), y.toInt())) {
                consumingInteraction = true
                ExtraDiagnostics.record(service, packageName, elapsedMs)
                Log.d(TAG, "route=consume reason=multi_finger_tap mode=$modeName maxPointers=$pointerCount elapsedMs=$elapsedMs x=$x y=$y")
            } else {
                requestDelegating(controller, "nova_launch_failed mode=$modeName package=$packageName")
            }
        }

        private fun scheduleTimeout(controller: TouchInteractionController, delayMs: Long, reason: String) {
            timeoutTask?.let(handler::removeCallbacks)
            timeoutTask = Runnable {
                timeoutTask = null
                if (multiFingerCandidate) requestDelegating(controller, "$reason mode=${candidateModeName()} maxPointers=$maximumPointerCount")
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
            val path = Path().apply { moveTo(x, y) }
            val durationMs = elapsedMs.toLong().coerceIn(1L, ViewConfiguration.getTapTimeout().toLong())
            replayingShortTap = true
            val accepted = service.dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                    .build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        replayingShortTap = false
                        Log.d(TAG, "route=replay_complete")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        replayingShortTap = false
                        Log.d(TAG, "route=replay_cancelled")
                    }
                },
                null,
            )
            if (!accepted) replayingShortTap = false
            Log.d(TAG, "route=replay_short_tap accepted=$accepted elapsedMs=$elapsedMs durationMs=$durationMs x=$x y=$y")
        }

        private fun elapsed(eventTime: Long): Float = (eventTime - gestureStartTime).coerceAtLeast(0L).toFloat()

        private fun candidateModeName(): String = when (requiredPointers) {
            2 -> TriggerMode.TWO_FINGER_TAP.name
            3 -> TriggerMode.THREE_FINGER_TAP.name
            else -> "NONE"
        }

        private fun logEvent(event: MotionEvent, state: Int) {
            val values = if (event.pointerCount == 0) {
                "pointers=0"
            } else {
                "pointers=${event.pointerCount} pressure=${event.getPressure(0)} size=${event.getSize(0)} " +
                    "touchMajor=${event.getTouchMajor(0)} touchMinor=${event.getTouchMinor(0)} classification=${event.classification}"
            }
            val ageMs = SystemClock.uptimeMillis() - event.eventTime
            Log.d(TAG, "event=${MotionEvent.actionToString(event.action)} state=${TouchInteractionController.stateToString(state)} ageMs=$ageMs $values delegated=$delegationRequested consuming=$consumingInteraction")
        }
    }

    private fun isStrongPress(event: MotionEvent, config: TriggerConfig): Boolean =
        TriggerPolicy.isSensorMode(config.mode) && TriggerPolicy.hasUsableThreshold(config) &&
            TriggerPolicy.readSample(event, config.mode, 0) > config.threshold

    private const val TAG = "NovaExtraTouch"
}
