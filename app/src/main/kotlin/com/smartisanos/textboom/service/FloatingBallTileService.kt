package com.cashewteam.novatext.android.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.ComponentName
import com.cashewteam.novatext.android.data.BigBangSettings

class FloatingBallTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val useExperimentalTouch = BigBangSettings.get(this).isExperimentalTouchSelected
        if (useExperimentalTouch) {
            if (ExperimentalTouchController.running) {
                ExperimentalTouchController.stop(this)
            } else {
                FloatingBallService.stop(this)
                ExperimentalTouchController.start(this)
            }
        } else if (FloatingBallService.isActive()) {
            FloatingBallService.stop(this)
        } else {
            FloatingBallService.start(this)
        }
        updateTile()
    }

    private fun updateTile() {
        val enabled = if (BigBangSettings.get(this).isExperimentalTouchSelected) {
            ExperimentalTouchController.running
        } else {
            FloatingBallService.isActive()
        }
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

    companion object {
        fun requestRefresh(context: android.content.Context) {
            requestListeningState(context, ComponentName(context, FloatingBallTileService::class.java))
        }
    }
}
