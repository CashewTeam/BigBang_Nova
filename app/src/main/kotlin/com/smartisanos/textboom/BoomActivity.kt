package com.smartisanos.textboom

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.smartisanos.textboom.data.CppJiebaTokenizer
import com.smartisanos.textboom.util.LogUtils

class BoomActivity : ComponentActivity() {
    private var boomChipPage: BoomChipPage? = null
    private lateinit var legacyContentView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        legacyContentView = layoutInflater.inflate(R.layout.boom_activity_layout, null, false)
        legacyContentView.findViewById<View>(R.id.boom_page).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            BoomAnimator.makeFadeIn(this, BoomAnimator.BOOM_DURATION)
        }
        boomChipPage = BoomChipPage(this, legacyContentView).also { page ->
            page.restoreSelectedState(savedInstanceState?.getSerializable(SELECTED_STATE))
        }

        setContent {
            BigBangOverlayContent(
                contentView = legacyContentView,
                onDismiss = { dismissPage() },
            )
        }

        val previewText = intent.getStringExtra(EXTRA_DEBUG_PREVIEW_TEXT)
        val inputText = previewText ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        if (inputText.isNullOrEmpty()) {
            finish()
            return
        }
        segmentLocally(inputText)
    }

    private fun dismissPage() {
        if (boomChipPage?.handleClick() != true) {
            finish()
        }
    }

    private fun segmentLocally(text: String) {
        if (DBG) {
            Log.d(TAG, "text=$text")
        }
        Thread {
            try {
                val result = CppJiebaTokenizer.get(this).segment(text)
                runOnUiThread {
                    if (!isFinishing) {
                        handleSegmentResult(text, result)
                    }
                }
            } catch (e: RuntimeException) {
                LogUtils.e(TAG, "local segmentation failed")
                LogUtils.e(e.message, e)
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun handleSegmentResult(text: String, result: IntArray?) {
        if (result == null || result.isEmpty()) {
            Log.e(TAG, "Segmentation fails for text=$text")
            finish()
            return
        }
        val touchIndex = intent.getIntExtra("boom_index", -1)
        val touchedX = intent.getIntExtra("boom_startx", -1)
        val touchedY = intent.getIntExtra("boom_starty", -1)
        if (boomChipPage?.initWords(result, text, touchIndex, touchedX, touchedY) != true) {
            val log = buildString {
                result.forEach {
                    append(it)
                    append(", ")
                }
            }
            Log.w(TAG, "No words left after segment, input=$text, output=$log")
            if (intent.getStringExtra("boom_image") != null) {
                Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        boomChipPage?.captureSelectedState()?.let {
            outState.putSerializable(SELECTED_STATE, it)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val DBG = true
        const val EXTRA_DEBUG_PREVIEW_TEXT = "extra_debug_preview_text"

        private const val TAG = "BoomActivity"
        private const val SELECTED_STATE = "selected_state"
    }
}

@Composable
private fun BigBangOverlayContent(
    contentView: View,
    onDismiss: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val panelBackground = if (dark) Color(0xFF171B20) else Color(0xFFF7F8FA)
    val panelBorder = if (dark) Color(0xFF2E353E) else Color(0xFFE3E7EC)
    val scrimColor = if (dark) Color.Black.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.42f)
    val shadowColor = Color.Black.copy(alpha = 0.5f)

    BackHandler(onBack = onDismiss)
    OverlayScene(scrimColor = scrimColor, onDismiss = onDismiss) {
        val panelWidth = dimensionResource(R.dimen.search_popup_width)
        val panelHeight = dimensionResource(R.dimen.search_popup_height)
        FloatingPanel(
            width = panelWidth,
            height = panelHeight,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp, vertical = 18.dp),
            backgroundColor = panelBackground,
            borderColor = panelBorder,
            shadowColor = shadowColor,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    contentView
                },
            )
        }
    }
}
