package com.cashewteam.novatext.android.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FloatingBallTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val enable = !FloatingBallService.isActive()
        if (!enable) {
            FloatingBallService.stop(this)
        } else {
            FloatingBallService.start(this)
        }
        updateTile(enable)
    }

    private fun updateTile(enabled: Boolean = FloatingBallService.isActive()) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            stateDescription = if (enabled) {
                getString(com.cashewteam.novatext.android.R.string.quick_settings_state_on)
            } else {
                getString(com.cashewteam.novatext.android.R.string.quick_settings_state_off)
            }
            updateTile()
        }
    }
}
