package com.smartisanos.textboom.service

import android.content.Context
import android.content.Intent
import com.smartisanos.textboom.BoomActivity
import com.smartisanos.textboom.OverlayActivity

object BoomActivityLauncher {
    fun openText(
        context: Context,
        text: String,
        touchX: Int,
        touchY: Int,
        isPreview: Boolean = false,
    ) {
        val intent = Intent(context, OverlayActivity::class.java).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra("boom_index", -1)
            putExtra("boom_startx", touchX)
            putExtra("boom_starty", touchY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            if (isPreview) {
                putExtra(BoomActivity.EXTRA_DEBUG_PREVIEW_TEXT, text)
            }
        }
        context.startActivity(intent)
    }
}
