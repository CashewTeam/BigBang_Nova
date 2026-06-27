package com.smartisanos.textboom.util

import android.util.Log

object NovaTextLogger {
    private const val TAG = "BigBangNova"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
