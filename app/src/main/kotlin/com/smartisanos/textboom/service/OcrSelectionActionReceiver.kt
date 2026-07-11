package com.cashewteam.novatext.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OcrSelectionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BoomOcrLauncher.launchSelectionCapture(context)
    }
}
