package com.cashewteam.novatext.android.service

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OcrSelectionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!FloatingBallService.isAccessibilityEnabled(this)) return
        val intent = BoomOcrLauncher.selectionCaptureIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            val available = FloatingBallService.isAccessibilityEnabled(this@OcrSelectionTileService)
            state = if (available) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            stateDescription = getString(
                if (available) {
                    com.cashewteam.novatext.android.R.string.quick_settings_ocr_ready
                } else {
                    com.cashewteam.novatext.android.R.string.quick_settings_ocr_unavailable
                },
            )
            updateTile()
        }
    }
}
