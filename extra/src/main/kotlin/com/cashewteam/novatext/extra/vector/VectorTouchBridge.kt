package com.cashewteam.novatext.extra.vector

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.MotionEvent
import com.cashewteam.novatext.extra.ExtraDiagnostics
import com.cashewteam.novatext.extra.ExtraSettings
import com.cashewteam.novatext.extra.NovaTextLauncher
import com.cashewteam.novatext.extra.TriggerConfig
import com.cashewteam.novatext.extra.TriggerMode
import com.cashewteam.novatext.extra.TriggerPolicy

object VectorTouchBridge {
    private val gate = TriggerPolicy.Gate()
    @Volatile private var config = TriggerConfig(false, false, TriggerMode.PRESSURE, 0f)
    @Volatile private var attachedPreferences: SharedPreferences? = null

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
            if (TriggerPolicy.isExcludedPackage(packageName) || !gate.onEvent(event, config)) return
            if (NovaTextLauncher.launch(context, packageName, event.rawX.toInt(), event.rawY.toInt())) {
                context.sendBroadcast(Intent(ExtraDiagnostics.ACTION_TRIGGERED).apply {
                    setPackage(TriggerPolicy.EXTRA)
                    putExtra(ExtraDiagnostics.EXTRA_PACKAGE, packageName)
                    putExtra(ExtraDiagnostics.EXTRA_VALUE, TriggerPolicy.readSample(event, config.mode, 0))
                })
            }
        }
    }

    private fun refresh(preferences: SharedPreferences) {
        val mode = runCatching {
            TriggerMode.valueOf(preferences.getString(ExtraSettings.KEY_MODE, TriggerMode.PRESSURE.name).orEmpty())
        }.getOrDefault(TriggerMode.PRESSURE)
        config = TriggerConfig(
            enabled = preferences.getBoolean(ExtraSettings.KEY_ENABLED, false),
            calibrated = preferences.getBoolean(ExtraSettings.KEY_CALIBRATED, false),
            mode = mode,
            threshold = if (mode == TriggerMode.PRESSURE) {
                preferences.getFloat(ExtraSettings.KEY_PRESSURE, 0f)
            } else {
                preferences.getFloat(ExtraSettings.KEY_SIZE, 0f)
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
}
