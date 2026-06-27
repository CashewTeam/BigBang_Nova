package com.smartisanos.textboom

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class OverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, BoomActivity::class.java).apply {
                replaceExtras(intent)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )
        overridePendingTransition(0, 0)
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
