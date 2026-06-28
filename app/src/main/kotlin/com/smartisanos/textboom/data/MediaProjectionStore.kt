package com.cashewteam.novatext.android.data

import android.content.Intent

object MediaProjectionStore {
    @Volatile
    private var resultCode: Int? = null

    @Volatile
    private var resultData: Intent? = null

    fun saveProjectionGrant(code: Int, data: Intent?) {
        resultCode = code
        resultData = data
    }

    fun hasActiveGrant(): Boolean {
        return resultCode != null && resultData != null
    }
}
