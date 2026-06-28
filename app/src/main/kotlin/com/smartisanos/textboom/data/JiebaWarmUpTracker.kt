package com.cashewteam.novatext.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object JiebaWarmUpTracker {
    const val STATE_IDLE = 0
    const val STATE_RUNNING = 1
    const val STATE_READY = 2
    const val STATE_FAILED = 3

    private val mutableState = MutableStateFlow(STATE_IDLE)

    @JvmStatic
    fun getStateFlow(): StateFlow<Int> = mutableState

    @JvmStatic
    fun markRunning() {
        updateState(STATE_RUNNING)
    }

    @JvmStatic
    fun markReady() {
        updateState(STATE_READY)
    }

    @JvmStatic
    fun markFailed() {
        updateState(STATE_FAILED)
    }

    @JvmStatic
    fun getCurrentState(): Int = mutableState.value

    private fun updateState(state: Int) {
        if (mutableState.value != state) {
            mutableState.value = state
        }
    }
}
