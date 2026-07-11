package com.cashewteam.novatext.android.util

import android.content.Context
import android.util.Log
import com.cashewteam.novatext.android.data.BigBangSettings

object NovaTextLogger {
    private const val TAG = "BigBangNova"
    private var settings: BigBangSettings? = null

    fun initialize(context: Context) {
        settings = BigBangSettings.get(context)
    }

    fun d(message: String) {
        if (settings?.isDebugModeEnabled == true) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
