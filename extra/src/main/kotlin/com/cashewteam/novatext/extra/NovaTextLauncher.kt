package com.cashewteam.novatext.extra

import android.content.Context
import android.content.Intent

object NovaTextLauncher {
    const val ACTION_CAPTURE = "com.cashewteam.novatext.android.action.BIGBANG_CAPTURE"

    fun launch(context: Context, packageName: String, x: Int, y: Int): Boolean {
        if (packageName.isBlank() || TriggerPolicy.isExcludedPackage(packageName)) return false
        val intent = Intent(ACTION_CAPTURE).apply {
            setPackage(TriggerPolicy.NOVA_TEXT)
            putExtra("caller_pkg", packageName)
            putExtra("boom_startx", x)
            putExtra("boom_starty", y)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }
}
