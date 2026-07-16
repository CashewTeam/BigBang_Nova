package com.cashewteam.novatext.extra

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.TouchInteractionController
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.MotionEvent
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
            !hasBatteryExemption(context)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !ExtraSettings(accessibilityService).experimentalEnabled ||
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
        private var accessibilityService: AccessibilityService? = null
        private var controller: TouchInteractionController? = null
        private var callback: TouchInteractionController.Callback? = null
        private var delegationRequested = false
        private var consumingInteraction = false
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
                    if (delegationRequested) {
                        Log.d(TAG, "route=already_delegating action=${MotionEvent.actionToString(event.action)}")
                        return
                    }

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            consumingInteraction = false
                            val config = ExtraSettings(accessibilityService).config().copy(enabled = true)
                            val packageName = foregroundPackage
                            val sample = TriggerPolicy.readSample(event, config.mode, 0)
                            if (TriggerPolicy.isExcludedPackage(packageName) || !isStrongPress(event, config)) {
                                requestDelegating(currentController, "normal_down mode=${config.mode} sample=$sample threshold=${config.threshold} package=$packageName")
                            } else if (NovaTextLauncher.launch(accessibilityService, packageName, event.rawX.toInt(), event.rawY.toInt())) {
                                consumingInteraction = true
                                ExtraDiagnostics.record(accessibilityService, packageName, sample)
                                Log.d(TAG, "route=consume reason=threshold_triggered mode=${config.mode} sample=$sample threshold=${config.threshold} package=$packageName")
                            } else {
                                requestDelegating(currentController, "nova_launch_failed package=$packageName")
                            }
                        }
                        MotionEvent.ACTION_POINTER_DOWN ->
                            requestDelegating(currentController, "multi_pointer")
                        MotionEvent.ACTION_MOVE -> if (consumingInteraction) {
                            Log.d(TAG, "route=consume reason=triggered_interaction_continues")
                        } else {
                            Log.d(TAG, "route=ignored reason=move_without_active_decision")
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            Log.d(TAG, "route=${if (consumingInteraction) "consume" else "unhandled"} reason=interaction_end")
                            consumingInteraction = false
                        }
                    }
                }

                override fun onStateChanged(state: Int) {
                    Log.d(TAG, "state=${TouchInteractionController.stateToString(state)} delegated=$delegationRequested consuming=$consumingInteraction")
                    when (state) {
                        TouchInteractionController.STATE_CLEAR -> {
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
            listening = false
        }

        fun onForegroundPackage(packageName: String) {
            foregroundPackage = packageName
            Log.d(TAG, "foregroundPackage=$packageName")
        }

        private fun requestDelegating(controller: TouchInteractionController, reason: String) {
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
        TriggerPolicy.hasUsableThreshold(config) && TriggerPolicy.readSample(event, config.mode, 0) > config.threshold

    private const val TAG = "NovaExtraTouch"
}
