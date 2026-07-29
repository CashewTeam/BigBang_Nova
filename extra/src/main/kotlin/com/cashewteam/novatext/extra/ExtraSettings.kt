package com.cashewteam.novatext.extra

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

enum class TriggerMode { PRESSURE, SIZE, TOUCH_AREA, SINGLE_LONG_PRESS, TWO_FINGER_TAP, THREE_FINGER_TAP }

data class TriggerConfig(
    val enabled: Boolean,
    val calibrated: Boolean,
    val mode: TriggerMode,
    val threshold: Float,
)

class ExtraSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var triggerEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = write { putBoolean(KEY_ENABLED, value) }
    var calibrated: Boolean
        get() = preferences.getBoolean(KEY_CALIBRATED, false)
        set(value) = write { putBoolean(KEY_CALIBRATED, value) }
    var mode: TriggerMode
        get() = runCatching { TriggerMode.valueOf(preferences.getString(KEY_MODE, TriggerMode.PRESSURE.name).orEmpty()) }
            .getOrDefault(TriggerMode.PRESSURE)
        set(value) = write { putString(KEY_MODE, value.name) }
    var pressureThreshold: Float
        get() = preferences.getFloat(KEY_PRESSURE, 0f)
        set(value) = write { putFloat(KEY_PRESSURE, value) }
    var sizeThreshold: Float
        get() = preferences.getFloat(KEY_SIZE, 0f)
        set(value) = write { putFloat(KEY_SIZE, value) }
    var touchAreaThreshold: Float
        get() = preferences.getFloat(KEY_TOUCH_AREA, 500f)
        set(value) = write { putFloat(KEY_TOUCH_AREA, value) }
    var longPressDuration: Float
        get() = preferences.getFloat(KEY_LONG_PRESS_DURATION, 600f)
        set(value) = write { putFloat(KEY_LONG_PRESS_DURATION, value) }
    var twoFingerTapDuration: Float
        get() = preferences.getFloat(KEY_TWO_FINGER_TAP_DURATION, 300f)
        set(value) = write { putFloat(KEY_TWO_FINGER_TAP_DURATION, value) }
    var threeFingerTapDuration: Float
        get() = preferences.getFloat(KEY_THREE_FINGER_TAP_DURATION, 300f)
        set(value) = write { putFloat(KEY_THREE_FINGER_TAP_DURATION, value) }
    var autoEnableNovaTextAccessibility: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ENABLE_NOVA_TEXT_ACCESSIBILITY, false)
        set(value) = write { putBoolean(KEY_AUTO_ENABLE_NOVA_TEXT_ACCESSIBILITY, value) }
    fun config(): TriggerConfig = TriggerConfig(
        enabled = triggerEnabled,
        calibrated = calibrated,
        mode = mode,
        threshold = when (mode) {
            TriggerMode.PRESSURE -> pressureThreshold
            TriggerMode.SIZE -> sizeThreshold
            TriggerMode.TOUCH_AREA -> touchAreaThreshold
            TriggerMode.SINGLE_LONG_PRESS -> longPressDuration
            TriggerMode.TWO_FINGER_TAP -> twoFingerTapDuration
            TriggerMode.THREE_FINGER_TAP -> threeFingerTapDuration
        },
    )

    private fun write(change: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(change).apply()
        VectorServiceBridge.sync(this)
    }

    companion object {
        const val GROUP = "nova_text_extra"
        const val KEY_ENABLED = "trigger_enabled"
        const val KEY_CALIBRATED = "calibrated"
        const val KEY_MODE = "trigger_mode"
        const val KEY_PRESSURE = "pressure_threshold"
        const val KEY_SIZE = "size_threshold"
        const val KEY_TOUCH_AREA = "touch_area_threshold"
        const val KEY_LONG_PRESS_DURATION = "long_press_duration"
        const val KEY_TWO_FINGER_TAP_DURATION = "two_finger_tap_duration"
        const val KEY_THREE_FINGER_TAP_DURATION = "three_finger_tap_duration"
        const val KEY_AUTO_ENABLE_NOVA_TEXT_ACCESSIBILITY = "auto_enable_nova_text_accessibility"
        private const val PREFS = "nova_text_extra"
    }
}

object VectorServiceBridge {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var service: XposedService? = null
    var status by mutableStateOf("未连接 Xposed")
        private set
    private var registered = false

    fun connect(settings: ExtraSettings) {
        if (registered) return
        registered = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                service = bound
                mainHandler.post { status = "已连接 Xposed（${bound.frameworkVersion}）" }
                sync(settings)
            }

            override fun onServiceDied(dead: XposedService) {
                if (service === dead) service = null
                mainHandler.post { status = "Xposed 服务已断开" }
            }
        })
    }

    fun sync(settings: ExtraSettings) {
        val remote = service?.getRemotePreferences(ExtraSettings.GROUP) ?: return
        remote.edit()
            .putBoolean(ExtraSettings.KEY_ENABLED, settings.triggerEnabled)
            .putBoolean(ExtraSettings.KEY_CALIBRATED, settings.calibrated)
            .putString(ExtraSettings.KEY_MODE, settings.mode.name)
            .putFloat(ExtraSettings.KEY_PRESSURE, settings.pressureThreshold)
            .putFloat(ExtraSettings.KEY_SIZE, settings.sizeThreshold)
            .putFloat(ExtraSettings.KEY_TOUCH_AREA, settings.touchAreaThreshold)
            .putFloat(ExtraSettings.KEY_LONG_PRESS_DURATION, settings.longPressDuration)
            .putFloat(ExtraSettings.KEY_TWO_FINGER_TAP_DURATION, settings.twoFingerTapDuration)
            .putFloat(ExtraSettings.KEY_THREE_FINGER_TAP_DURATION, settings.threeFingerTapDuration)
            .putBoolean(ExtraSettings.KEY_AUTO_ENABLE_NOVA_TEXT_ACCESSIBILITY, settings.autoEnableNovaTextAccessibility)
            .apply()
    }

    fun requestAllThirdPartyApps(context: Context, onResult: (String) -> Unit) {
        val vector = service ?: run {
            mainHandler.post { onResult("请先在 Xposed 管理器中启用模块") }
            return
        }
        val own = context.packageName
        val packages = context.packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != own && !TriggerPolicy.isExcludedPackage(it.packageName) }
            .map { it.packageName }
            .toList()
        runCatching {
            vector.requestScope(packages, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    mainHandler.post { onResult("已授权 ${approved.size} 个应用，请重启目标应用") }
                }

                override fun onScopeRequestFailed(message: String) {
                    mainHandler.post { onResult(message) }
                }
            })
        }.onFailure { mainHandler.post { onResult(it.message ?: "Xposed 作用域请求失败") } }
    }

    fun scopeStatus(): String = service?.let {
        runCatching { "已授权 ${it.scope.size} 个应用" }.getOrElse { "Xposed 服务不可用" }
    } ?: "未连接 Xposed"

    fun enableNovaTextAccessibility(settings: ExtraSettings, onResult: (String) -> Unit) {
        val vector = service ?: run {
            mainHandler.post { onResult("请先在 Xposed 管理器中启用模块") }
            return
        }
        val systemScope = listOf("android")
        val enable = {
            settings.autoEnableNovaTextAccessibility = true
            mainHandler.post { onResult("已保存自动开启请求；若刚授权系统作用域，请重启设备后生效") }
        }
        if (runCatching { vector.scope.contains("android") }.getOrDefault(false)) {
            enable()
            return
        }
        runCatching {
            vector.requestScope(systemScope, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    if (approved.contains("android")) enable()
                    else mainHandler.post { onResult("Xposed 未授权 Android 系统作用域") }
                }

                override fun onScopeRequestFailed(message: String) {
                    mainHandler.post { onResult(message) }
                }
            })
        }.onFailure { mainHandler.post { onResult(it.message ?: "Android 系统作用域请求失败") } }
    }
}
