package com.cashewteam.novatext.extra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object ExtraDiagnostics {
    private const val PREFS = "nova_text_extra_diagnostics"
    private const val KEY_LAST = "last_trigger"
    const val ACTION_TRIGGERED = "com.cashewteam.novatext.extra.TRIGGERED"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_VALUE = "value"

    fun record(context: Context, packageName: String, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST, "$packageName  ·  %.3f".format(value))
            .apply()
    }

    fun last(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LAST, "暂无") ?: "暂无"
}

class TriggerDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(ExtraDiagnostics.EXTRA_PACKAGE).orEmpty()
        if (packageName.isBlank() || TriggerPolicy.isExcludedPackage(packageName)) return
        ExtraDiagnostics.record(context, packageName, intent.getFloatExtra(ExtraDiagnostics.EXTRA_VALUE, 0f))
    }
}
