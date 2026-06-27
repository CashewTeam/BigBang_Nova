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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                onEditMode = { showPlaceholder() },
                onSelectAll = { selectAll() },
                onShareAll = { shareAll() },
                onMore = { showPlaceholder() },
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

    private fun selectAll() {
        boomChipPage?.selectAll()
    }

    private fun shareAll() {
        val shareText = boomChipPage?.originalText ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        })
        finish()
    }

    private fun showPlaceholder() {
        Toast.makeText(this, R.string.bigbang_action_placeholder, Toast.LENGTH_SHORT).show()
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
    onEditMode: () -> Unit,
    onSelectAll: () -> Unit,
    onShareAll: () -> Unit,
    onMore: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val panelMetrics = rememberOverlayPanelMetrics()
    val panelBackground = if (dark) Color(0xFF171B20) else Color(0xFFF3F3F4)
    val panelBorder = if (dark) Color(0xFF2E353E) else Color(0xFFD7D7DA)
    val scrimColor = if (dark) Color.Black.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.48f)
    val shadowColor = Color.Black.copy(alpha = 0.5f)
    val panelShape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)

    BackHandler(onBack = onDismiss)
    OverlayScene(scrimColor = scrimColor, onDismiss = onDismiss) {
        FloatingPanel(
            width = panelMetrics.width,
            height = panelMetrics.height,
            modifier = overlayPanelPlacement(panelMetrics),
            shape = panelShape,
            backgroundColor = panelBackground,
            borderColor = panelBorder,
            shadowColor = shadowColor,
        ) {
            OverlayPanelScaffold(
                topBar = {
                    OverlayHeaderBar(
                        backgroundColor = if (dark) Color(0xFF1D2126) else Color.White,
                        leading = {
                            OverlayIconAction(
                                iconRes = R.drawable.bigbang_ic_edit,
                                tint = if (dark) Color(0xFFD7DEE7) else Color(0xFF6F6962),
                                onClick = onEditMode,
                                contentDescription = stringResource(R.string.bigbang_action_edit),
                            )
                            OverlayIconAction(
                                iconRes = R.drawable.bigbang_ic_select_all,
                                tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF6C6760),
                                onClick = onSelectAll,
                                contentDescription = stringResource(R.string.bigbang_action_select_all),
                            )
                        },
                        center = {
                            androidx.compose.material3.Text(
                                text = stringResource(R.string.bigbang_overlay_title),
                                color = if (dark) Color(0xFFF2F5F8) else Color(0xFFD1CCC6),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        trailing = {
                            OverlayIconAction(
                                iconRes = R.drawable.bigbang_ic_share,
                                tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF6C6760),
                                onClick = onShareAll,
                                contentDescription = stringResource(R.string.bigbang_action_share_all),
                            )
                            OverlayIconAction(
                                iconRes = R.drawable.bigbang_ic_more,
                                tint = if (dark) Color(0xFFD7DEE7) else Color(0xFF6F6962),
                                onClick = onMore,
                                contentDescription = stringResource(R.string.bigbang_action_more),
                            )
                        },
                    )
                },
                bottomBar = {
                    OverlayBottomBar(
                        backgroundColor = if (dark) Color(0xFF1D2126) else Color.White,
                    ) {
                        OverlayIconAction(
                            iconRes = R.drawable.boom_cancel,
                            tint = if (dark) Color(0xFFF2F5F8) else Color(0xFF8D8983),
                            onClick = onDismiss,
                            contentDescription = stringResource(R.string.search_overlay_close),
                        )
                    }
                },
            ) { bodyModifier ->
                Column(modifier = bodyModifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 4.dp),
                        factory = {
                            contentView
                        },
                    )
                }
            }
        }
    }
}
