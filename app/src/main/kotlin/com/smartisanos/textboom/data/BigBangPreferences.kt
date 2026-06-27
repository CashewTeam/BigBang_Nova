package com.smartisanos.textboom.data

import android.content.Context

class BigBangPreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(BigBangSettings.PREF_NAME, Context.MODE_PRIVATE)

    fun isFloatingBallEnabled(): Boolean {
        return preferences.getBoolean(KEY_FLOATING_BALL_ENABLED, false)
    }

    fun setFloatingBallEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FLOATING_BALL_ENABLED, enabled).apply()
    }

    companion object {
        private const val KEY_FLOATING_BALL_ENABLED = "floating_ball_enabled"
    }
}
